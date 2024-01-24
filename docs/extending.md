# Extending your Target with Groovy Classes

You can use the REPL's bundled Groovy environment to extend your target application with your own Groovy classes.
Those classes might either be entirely new - ready to be integrated using the application's own dependency injection
mechanism - or they might be new versions of the application's own classes, which you can tweak to your liking. When you
override a pre-existing class, you can alter the application's behavior even if it does not provide a mechanism for
dependency injection.

> **Important**
> 
> If you distribute your own application-specific Groovy classes, you need to comply with your target application's
> license. Licenses such as the GPL have special provisions for aggregated works such as these. Always remember to check
> what is permitted according to your target's license conditions.

Adding new classes to your application is generally very simple. The exact method depends on whether on you use the
universal REPL Java agent, or the MyCoRe plugin.


## Adding new classes - Agent

To add new classes to your application using the universal agent, it needs to be properly set up first. The relevant
configuration properties that you need to set up concern the ClassPath (`CAU.Groovy.ClassPath` and maybe
`CAU.JavaAgent.ClassPath`), the class loader (you need to enable either `CAU.Groovy.UseSystemClassLoader` or
`CAU.JavaAgent.Triggers`) and the locations of your `.groovy` sources (`CAU.Groovy.SourceDirs`). See the
[Installation section](installation.md#loading-the-agent) and the
[Configuration section](configuration.md#system-properties-for-the-universal-java-agent) for details.

Once you have set these properties correctly, you can just place `.groovy` files in the source directories that you
configured. The agent will automatically compile them and place them into the ClassLoader that you configured as soon
as possible.

Load order and timing are often critical to integrate you classes into an existing application, as is choosing the
correct ClassLoader.
For dependency injection to work, your class must be available before your target's mechanism resolves the configured
classes and they need to be findable in the ClassLoader hierarchy.

**Adding classes to the system ClassLoader**

When you enable the `CAU.Groovy.UseSystemClassLoader`
property, the agent will compile your classes before any other class of your target gets loaded. This provides you with
a clean slate. On the other hand, this may have certain disadvantages if your own class uses classes of the target -
these will also be loaded into the system ClassLoader, which can cause problems in the target if it expects its classes
to be loaded in a special separate ClassLoader (web applications running in servlet containers are a typical example).
You should have the agent trigger on the proper ClassLoader in those cases.

**Adding classes to an application ClassLoader**

To place your classes in a special ClassLoader, use the `CAU.JavaAgent.Triggers` property instead (see the 
[Installation section](installation.md#classloader-selection)). In this case, your Groovy sources will be compiled right
after the first trigger class of your target was loaded and placed in the same ClassLoader as that triggering class.

**Adding classes to the REPL only**

If you only intend to use your classes from the interactive REPL, you don't have to set either of these two properties
and can just leave the REPL at its default settings. in this case, they will be loaded into a separate ClassLoader that
only the REPL itself can see.


## Adding new classes - MyCoRe

To add classes to your MyCoRe installation, add their source directories to your `mycore.properties` file as lines in
the form of `CAU.Groovy.SourceDirs.1=...`, `CAU.Groovy.SourceDirs.2=...`, and so forth. They will
be compiled and loaded into MyCoRe's own ClassLoader as soon as MyCoRe initializes the REPL plugin. This is typically
early enough to use your new classes (e.g. as an event handler) by simply referencing it by name from the same
`mycore.properties` file.

This allows you to easily extend MyCoRe without creating your own plugins.

If you need your own class available earlier in MyCoRe's lifecycle, you should employ the hybrid approach of using both
the MyCoRe plugin and the agent at the same time. See the
[Installation section](installation.md#loading-the-plugin-earlier-with-the-agent) for details. Remember that in the
hybrid case, only the sources you directly pass via the Java command-line are compiled early. The sources from
`mycore.properties` will still be compiled later during the normal MyCoRe plugin initialization.

**Examples:**
```groovy
// here is a MyCoRe event handler implemented in Groovy
package foo

import org.mycore.common.events.MCREventHandlerBase
import org.mycore.datamodel.metadata.MCRObject
import org.mycore.common.events.MCREvent
import de.uni_kiel.rz.fdr.repl.REPLLog

class GroovyTestEventHandler extends MCREventHandlerBase {
   @Override
   protected void handleObjectUpdated(MCREvent evt, MCRObject obj) { 
       REPLLog.info("Groovy Event Handler saw: {}", obj.getId())
   }
}

/*
 1. Save this file with a ".groovy" extension to a new directory
 2. echo "CAU.Groovy.SourceDirs.1=/path/to/your/dir" >> /path/to/mycore.properties
 3. echo "MCR.EventHandler.MCRObject.933.Class=foo.GroovyTestEventHandler" >> /path/to/mycore.properties
 */
```

## Patching existing classes

You might want to override the behavior of your target's own classes or of libraries which your target uses (*"patching"*
a class). The
easiest way to achieve this is to simply add a Groovy source file that contains a class with the same name in the same
package as your target class and make sure that it is compiled before your target tries to load its version. Using this
method, you need to re-implement all the methods of the class you are replacing yourself, even those whose behavior you
did not mean to alter.

To make this process more convenient, cau-repl has a special feature that simplifies the patching process and does not
require access to the target's source code. To use cau-repl's patching feature, annotate your own version of the class
with a special annotation:

<a name="patches"></a>**Patch a class**
> **Class Annotation**
>
> `@Patches`
>
> You should add this annotation to your own Groovy class with which you
> intend to override an existing class of your
> target or its libraries. Do not specify a superclass: it will be set up automatically. The package and name of your
> class must be identical to package and name of the target
> class.<br/>
> When cau-repl encounters the `@Patches` annotation during compilation, it will first locate the target class in
> the class path, load it into the ClassLoader under a different name (the suffix `_CAUREPL` will be appended to the
> original name) and set this renamed class as the superclass of your Groovy class. <br/>
> The result of this transformation is that your Groovy class will replace the target class in the
> application, while the functionality of the target continues to be available to you from your Groovy class.
> You only have to override the methods that you would like to alter and can inherit the rest.
> One may invoke its methods via `super()`, including access to all its private methods and fields, which cau-repl will
> transparently patch to `public` access.<br/>
> Two classes that make use of the `@Patches` annotation cannot have a mutual dependency on each other (a cycle).
> Your compilation will fail if this is the case.
> <br/>
> You do not have to import this annotation manually. It is always available.
> 
> > **Warning**
> > 
> > This is an experimental feature. Expect problems. Do not patch database-backed classes: the renaming and
> > subclassing of them may cause damage to your database structure and state.
>
> **Optional Named Parameters**
>
> `String classPath = <REPL's class path>` - Specify an alternate class path in which the target class and its
> dependencies can be found.
>
> `boolean force = false` - cau-repl will try to prevent a database-backed class (i.e. JPA-enabled) from being patched.
> The goal is to prevent destructive changes to your database induced by renaming classes and methods during the
> patching process. Set this to `true` to override this check, but only if you know what you are doing at the risk of
> your database state.
>
> `boolean makePublic = true` - By default, non-public methods and fields as well as final methods and classes of the
> target will be set public and not-final during the patching process. Set this to `false` to preserve all original
> access modifiers, at the cost of making those methods not-overridable.
>
> `boolean preserveSuper = true` - By default, we will try to preserve access to the target's original superclass during
> the patching process. Disable this to prevent this logic, at the cost of losing this access.
> 
> `String stripAnnotations` - A comma separated list of annotations to remove from your target class before it is
> loaded. You can either pass the annotation names fully qualified (i.e. including their package prefix) or in short
> form (just their name without any package).

*Hint:* If your patch target class contains inner classes, these will be renamed along with the target. Depending on the
types of interactions between outer and inner classes, that may or may not be appropriate for your situation. If this
causes your trouble (e.g. method signature mismatches), consider adding an inner class with the same name as the target's
to your own patching class, which inherits from the `Target_CAUREPL.InnerClass` class. Then you can fine-tune its
behavior.

> **Function**
>
> `patcheeSuper(method, ...parameters)`
>
> This function provides access to the methods of the `@Patches` target's original superclass, which are lost after
> patching, because the target class is now the superclass.<br/>
> You do not have to import this function manually. It is always available.
> 
> **Positional Parameters**
>
> `String method` - The name of the method you would like to invoke from the target's superclass.
> 
> `Object parameters` *optional, repeatable* - The parameters of the function call.
>
> **Returns** the result that invoking `super.method(parameters)` from the target would have returned.

**Examples:**
```groovy
// Let's patch the apache-commons-text TextStringBuilder class.
// Install its .jar to your classpath. MyCoRe already includes it.
// api: https://commons.apache.org/proper/commons-text/apidocs/org/apache/commons/text/TextStringBuilder.html
// source: https://github.com/apache/commons-text/blob/master/src/main/java/org/apache/commons/text/TextStringBuilder.java  

package org.apache.commons.text

@Patches
class TextStringBuilder {
    @Override 
    String toString() { 
        def s = super.toString()
        return "Bork".equals(s) ? "Bork Bork Bork" : s
    }
}

/*
groovy:000> x = new org.apache.commons.text.TextStringBuilder()
===>
groovy:000> x.append("Bo")
===> Bo
groovy:000> x.append("rk")
===> Bork Bork Bork
 */
```

## Adding interactive breakpoints

You can add interactive breakpoints to your Groovy classes. When they trigger, they transfer control to the SSH
interface, where you can inspect their payload and then pass data back to the function which triggered them. The
breakpoints are implemented as a normal function, so you can easily create conditional breakpoints by nesting them in
an `if` statement. See the [Managing breakpoints](repl.md#managing-breakpoints) section to learn about the corresponding
REPL functionality.

> **Function**
>
> `replbreakpoint(name, extra, timeoutMillis)`
>
> Pauses execution of the current thread and transfers the `extra` object to the REPL. When this breakpoint is continued
> from the REPL, it will return the feedback data passed interactively from there.<br/>
 > You do not have to import this function manually. It is always available.
>
> **Positional Parameters**
>
> `String name` *optional* - A descriptive name of this breakpoint instance allowing for easy identification in the
> breakpoint list (including ids or timestamps is a good idea). If omitted, it will be auto-generated from the thread's stack.
>
> `Object extra` *optional* - Any related extra data you would like to make available in the REPL.
> 
> `Long timeoutMillis` *optional* - Limit the amount of time that this breakpoint will wait for. When this much time has
> passed and no one has resumed the breakpoint instance from the REPL, it will continue anyway. The default behavior is
> to wait forever (`timeoutMillis = null`).
>
> **Returns** the feedback value passed from the REPL, or `null` if none was passed.

**Examples:**
```groovy
// here is a conditional breakpoint that also returns a value
package foo
class Example {
    def doSomething(Integer data) {
      // trigger a breakpoint only if input > 100
      if ((data?:0) > 100) {
       def feedback = replbreakpoint("foo.Example ${data}", data)
       // if the breakpoint was continued from the REPL without feedback, continue (":B 0")
       // else (":B 0 not good") abort
       if (feedback) throw new RuntimeException("Abort at breakpoint requested: ${feedback}")
      }
     return (data?:0) * 2
    }
}
```

**Related Classes:** [REPLBreakpoint](apidocs/de/uni_kiel/rz/fdr/repl/REPLBreakpoint.html) provides additional functionality related to breakpoints.

## Updating methods at runtime

Dynamic languages that run in the JVM (like Groovy or JRuby) allow you to change the implementation of your classes at
runtime without reloading your entire application. This feature has until now always be confined to the dynamic parts of
the JVM: Java code interacting with your Groovy objects will not pick up the changes to their definition and continue to
use the original class bytecode. Only calls initiated from Groovy code itself can use the updated implementations.

cau-repl includes a **very** experimental feature which works around this limitation, allowing you to update a method's
implementation and have it take effect everywhere in the JVM - including from Java callsites.

> **Class Annotation**
>
> `@Dynamize`
>
> Classes that have this annotation are transparently augmented with a dynamic method dispatcher, enabling you to update
> a method's code and have the change be visible from the non-dynamic Java world in your JVM without restarting the
> application.<br/>
> You do not have to import this annotation manually. It is always available.
>
> > **Warning**
> >
> > Consider this a proof-of-concept. Expect bugs and problems, also a substantial
> > performance overhead for each method-call into the affected classes. Do not enable in a production environment.
>
> **Optional Named Parameters**
>
> `boolean inherit = true` - Subclasses of dynamized classes will automatically be dynamized as well. If you'd like to
> prevent this, set this to `false` (which is strongly discouraged and will cause all sorts of problems).

You update a method by assigning a `Closure` to its metaclass, like using
[Groovy's ExpandoMetaClass](https://docs.groovy-lang.org/latest/html/api/groovy/lang/ExpandoMetaClass.html). See below
for an example.

Because your updated code runs inside a closure, some of Groovy's language features are not readily available
(especially `super` is missing). cau-repl includes some helper functions that you can use instead:

> **Function**
>
> `dynamicSuper(method, ...parameters)`
> 
> `dynamicSuper(int parents, method, ...parameters)`
>
> These functions provide access to the methods of your class' superclass, which are not available from a closure.
> They are intended to be used from closure that you apply to a `@Dynamize` metaclass.<br/>
> You do not have to import these functions manually. It is always available.
>
> **Positional Parameters**
>
> `int parents = 1` *optional* - You may select a (grand-)parent in the class hierarchy
> (1 = `super`, 2 = `super.super`, ...).
> 
> `String method` - The name of the method you would like to invoke from your class' superclass.
>
> `Object parameters` *optional, repeatable* - The parameters of the function call.
>
> **Returns** the result that invoking `super.method(parameters)` from your class would have returned.

> **Function**
>
> `dynamicPatcheeSuper(method, ...parameters)`
>
> This function is intended to be used from closure that you apply to a `@Patches @Dynamize` metaclass.<br/>
> They provide access to the methods of your class' patch-target superclass, which is shadowed due to the patching
> process.<br/>
> You do not have to import these functions manually. It is always available.
>
> **Positional Parameters**
>
> `String method` - The name of the method you would like to invoke from your patch target's superclass.
>
> `Object parameters` *optional, repeatable* - The parameters of the function call.
>
> **Returns** the result that invoking `super.method(parameters)` from your target would have returned.

**Examples:**

```groovy
package foo

@Dynamize
class Example {
    int data
 
    static String greetMe(def name) {
       return "Hi, ${name}"
    }
 
    Example(int data) {
       this.data = data
    }
 
    int calculate() {
       return data * 2
    } 
}

/*
groovy:000> compile("example.groovy")
===> de.uni_kiel.rz.fdr.repl.groovy.GroovySourceDirectory@d6b75cb
groovy:000> import "foo.Example"
===> de.uni_kiel.rz.fdr.repl.REPL, de.uni_kiel.rz.fdr.repl.REPLLog, de.uni_kiel.rz.fdr.repl.REPLLogEntry, de.uni_kiel.rz.fdr.repl.REPLLog.LOG_TARGETS, de.uni_kiel.rz.fdr.repl.REPLBreakpoint, de.uni_kiel.rz.fdr.repl.REPLJob, de.uni_kiel.rz.fdr.repl.REPLJobCallbackAutoTune, foo.Example
groovy:000> Example.greetMe("Bob")
===> Hi, Bob
groovy:000> e = new Example(3)
===> foo.Example@5dd95ba8
groovy:000> e.calculate()
===> 6

// redefining a static method
groovy:000> Example.metaClass.static.greetMe = { name -> "Hello, ${name}" }
===> groovysh_evaluate$_run_closure1@c60b893
groovy:000> Example.greetMe("Bob")
===> Hello, Bob

// redefining an instance method
groovy:000> e.metaClass.calculate = { return data * 10 }
===> groovysh_evaluate$_run_closure1@3cfb3247
groovy:000> e.calculate()
===> 30
 */
```