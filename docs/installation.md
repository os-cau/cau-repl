# Installation

cau-repl can be built in one of two flavors

- the standard build is an MIT-licensed universal Java agent that can be used with any Java application
- the GPL build is also a MyCoRe plugin, in addition to the Java agent functionality of the standard build

## Universal Java Agent

### Compiling

Compile cau-repl as a universal Java agent with Maven:
```bash
mvn clean package
# alternatively, if you would also like to run the integration test:
# mvn clean install 
```
This will produce the following two JARs:

- `target/cau-repl-X.Y.Z-fatjar-mit.jar` can be used as a standalone Java agent and contains all of cau-repls dependencies
- `target/cau-repl-agent-X.Y.Z.jar` is an optional lightweight loader-only agent that can be used to load the full `fatjar`
  into a specific classloader. Use this if your application places its classes in a non-default classloader (e.g.
  web applications in a servlet container). See the [Classloader Selection](#classloader-selection) section for details. 

### Loading the Agent

To simply get access to the SSH REPL, load cau-repl as a Java agent. You need to pass a special parameter to your
Java command:
```bash
java -javaagent:/path/to/cau-repl-X.Y.Z-fatjar-mit.jar ...
```
This also allows you to compile your own classes and use them in the REPL.

If you would like to extend your own classes' availability to the entire application (and not just the REPL), they need
to be loaded into the system-default classloader. Then you should configure cau-repl like this: 
```bash
java -javaagent:/path/to/cau-repl-X.Y.Z-fatjar-mit.jar -DCAU.Groovy.UseSystemClassLoader=true ...
```

If you use the system-default classloader and loading your classes fails with an exception, you might also need to
disable the access protection provided by Java's module system (potentially impacting your target's security if it
depends on it). Whether this step is necessary, depends on your target application:
```bash
java -javaagent:/path/to/cau-repl-X.Y.Z-fatjar-mit.jar --add-opens 'java.base/java.lang=ALL-UNNAMED' -DCAU.Groovy.UseSystemClassLoader=true ...
```

The REPL will listen for SSH connections on port 8512 on the local interface. You can use any username to log in. A
per-session password will be printed to STDERR on startup. **Be careful to make sure that it does not end up in a public
logfile**, e.g. Systemd's journal. Any local user who can read this password can connect to the REPL and execute code
with the permissions of your application. To secure your installation permanently, see the [Configuration](configuration.md) section for ways to set your own
static password without producing log output. The configuration section also describes various parameters that you can
use to customize ports, directories, etc.

By default, cau-repl will store its state in the `cau-repl` directory, which it will create in the current working
directory.

If you do not see your application's classes in the REPL, you need to load cau-repl into a specific classloader.

### Classloader Selection

To load cau-repl in a specific classloader of your application, place `cau-repl-X.Y.Z-fatjar-noglp.jar` in your
application's classpath and use the lightweight loader JAR `cau-repl-agent-X.Y.Z.jar` as the agent. You can then
configure the loader agent to place the main JAR into the same classloader that your application uses. Just identify
a package or class that your target application will load and pass its resource path as a parameter to the loader agent:
```bash
# use the classloader that loads the first class from the org.example.* package
java -javagent:/path/to/cau-repl-agent-X.Y.Z.jar -DCAU.JavaAgent.Triggers=org/example/
# use the classloader that loads the class org.example.SomeClass
java -javagent:/path/to/cau-repl-agent-X.Y.Z.jar -DCAU.JavaAgent.Triggers=org/example/SomeClass
```
As before, you might also have to set the `--add-opens 'java.base/java.lang=ALL-UNNAMED'` parameter if you plan on
changing or adding classes in your target. Note that this will have security implications for your target application if it
depends on the separation provided by Java's module system.

_Remark_: it is currently not possible to patch the class that the agent triggers
on (by the time it is seen, it is too late to block it). So make sure to select a trigger that triggers in the correct
classloader, but before the first class you would like to patch.

### An Alternative Method for Loading: the Chainloader

If you'd rather not add a `-javaagent` to your JVM, there is an alternative way of loading `cau-repl`: the chainloader.
The general idea is to replace the main class of your target application with the chainloader class of `cau-repl`. The
chainloader can then take care of initializing the REPL and start your target application after it's done. You may
optionally make this apply permanently to your target application's `.jar`, but you don't have to.

If at all possible, you should prefer the [Java Agent](#loading-the-agent) instead of the chainloader, because only the
agent has the ability to select a specific classloader. The chainloader can only target the system default classloader.

To start your application with the chainloader, you simply need to arrange for the main `.jar` of `cau-repl` to be in your
class path. Then start your target application the way you usually would, with two important differences:

1. Set the main class to be executed to `de.uni_kiel.rz.fdr.repl.REPLChainloader` instead. This class is contained in
the `.jar` file of `cau-repl`. This is typically achieved by changing your Java command line appropriately.
2. Set the system property `CAU.ChainLoader.Class` to your target application's original main class. The `REPLChainloader`
will start this class' `main` method after it has set up the REPL. The usual way to set this property is to add an argument to
the JVM's command line, e.g. `-DCAU.ChainLoader.Class=org.example.SomeClass`

You may also [add further property definitions](configuration.md#system-properties-for-the-universal-java-agent) to the command line to configure the REPL, as you would with the normal
agent.

```bash
# start an application whose main class is org.example.SomeClass with the chainloader
java -cp /path/to/cau-repl.jar;/other/paths -DCAU.ChainLoader.Class=org.example.SomeClass -DCAU.REPL.SSH.ListenPort=5554 de.uni_kiel.rz.fdr.repl.REPLChainloader
```

If your target application comes packaged in a `.jar`, you have two options for starting it with the chainloader:

To use the chainloader without making any changes to your target's `.jar`, proceed as in the example above and
**do not use Java's `-jar` parameter to start your target**, because this would hardwire Java to use the target application's
main class. Instead, add your target's `.jar` file to the classpath (`-cp`) and start the `REPLChainloader` class instead.
You may determine the main class of your target's `.jar` file by looking at its `META-INF/MANIFEST.MF` file after
unpacking the `.jar`.

```bash
# start example.jar with its main class org.example.SomeClass with the chainloader
java -cp /path/to/cau-repl.jar;/path/to/example.jar -DCAU.ChainLoader.Class=org.example.SomeClass -DCAU.REPL.SSH.ListenPort=5554 de.uni_kiel.rz.fdr.repl.REPLChainloader
```
To install the agent's chainloader (semi-)permanently to you target's jar instead, so the REPL is automatically started when you
launch the target's `.jar`, use the chainloader installer of `cau-repl`:

You start the chainloader installer by running
`cau-repl-agent-X.Y.Z.jar` (i.e. the **-agent** `.jar` file, not the **-fatjar**). The installer will add some required
classes for chainloading to your target's `.jar` and change its manifest so the chainloader gets started when the target's
`.jar` is run. After this, you can start your target's `.jar` without any extra parameters and the REPL will always start
automatically. You just need to make sure that `cau-repl-X.Y.Z-fatjar.jar` is available at the location you passed to the installer.
All of [the usual configuration properties](configuration.md#system-properties-for-the-universal-java-agent) that you have set
before invoking the installer will also be persisted inside the target's `.jar`. You do not need to pass them
again when launching it. The only property that will not be saved is `CAU.REPL.SSH.Password`. Use
`CAU.REPL.SSH.PasswordCommand` instead, or pass the property every time when launching the target `.jar`.

```bash
# install the chainloader into target.jar to automatically start on port 44443
java -DCAU.REPL.SSH.ListenPort=44443 -jar /path/to/cau-repl-agent-X.Y.Z.jar install /path/to/cau-repl-X.Y.Z-fatjar.jar /path/to/target.jar
# you may now start the target as usual and the REPL will be available.
# make sure that the -fatjar.jar file remains at the same relative location to target.jar as during the installation 
java -jar /path/to/target.jar
# update the installation with an additional setting (not clearing previous settings).
# also repeat this every time you install a new version of cau-repl to update the chainloader in the target
java -DCAU.Groovy.SourceDirs=/my/sources -jar /path/to/cau-repl-agent-X.Y.Z.jar install /path/to/cau-repl-X.Y.Z-fatjar.jar /path/to/target.jar
# install the chainloader into target.jar with an absolute reference to the -fatjar.jar file, allowing you to move target.jar
# without moving -fatjar.jar
java -DCAU.REPL.SSH.ListenPort=44443 -jar /path/to/cau-repl-agent-X.Y.Z.jar absinstall /path/to/cau-repl-X.Y.Z-fatjar.jar /path/to/target.jar
# uninstall the chainloader
java -jar /path/to/cau-repl-agent-X.Y.Z.jar uninstall /path/to/target.jar
```

## MyCoRe plugin

### Compiling

Compile cau-repl as a MyCoRe plugin with Maven:
```bash
mvn -P gpl clean package
# alternatively, if you would also like to run the integration test:
# mvn -P gpl clean install 
```
This will produce the following two JARs:

- `target/cau-repl-X.Y.Z-fatjar-gpl.jar` can be used as a MyCoRe plugin (and also as a Java agent)
- `target/cau-repl-agent-X.Y.Z.jar` is an optional lightweight loader-only agent that can be used to load the full `fatjar`
  into a specific classloader. In a MyCoRe context, you would only have to use this if you want to give cau-repl
  priority over MyCoRe's basic classes in the JVM's class load order. This is only necessary if you want to use cau-repl
  to patch basic framework classes, so most MyCoRe users do not have to install this JAR.

### Loading the Plugin

To load the cau-repl MyCoRe plugin, place the main JAR in your MyCoRe `lib/` directory and enable it in your
`.properties` file:
```bash
cp /path/to/cau-repl-X.Y.Z-fatjar-gpl.jar /path/to/mycore/lib
echo "CAU.REPL.Enabled=true" >> /path/to/mycore/mycore.properties
```
Then (re-) start your servlet container. The REPL will listen for SSH connections on port 8512 on the localhost. You can
log in with the username `administrator` and the corresponding MyCoRe password of the account. See the
[Configuration](configuration.md) section for more settings that you can customize from your `.properties`.

By default, cau-repl will store its state in the `cau-repl` subdirectory, which it will create in your MyCoRe
installation's root.

### Loading the Plugin Earlier with the Agent

When you patch libraries or define classes, load order is important. The MyCoRe plugin generally tries to load your
Groovy classes as early as possible, but when you interact with lower layers of the stack, it is sometimes too late to
do anything about that from the plugin. Most users will never have these issues and can skip this step, but if you try
to patch a very basic class and fail because it was already loaded, you can add the agent as described here. Please note: these steps are to be
performed _in addition_ to the plugin installation steps above. Do not delete ot disable the main JAR.

First copy the loader agent into place:
```bash
cp path/to/cau-repl-agent-X.Y.Z.jar /path/to/mycore/lib
```
Then add it to your servlet container's java arguments. E.g. for Tomcat, you would add something like this to your
`bin/setenv.sh` file:
```bash
# adjust paths as needed
export JAVA_OPTS="$JAVA_OPTS -javaagent:/path/to/mycore/lib/cau-repl-agent-X.Y.Z.jar -DCAU.JavaAgent.ClassPath=/path/to/mycore/lib/* -DCAU.JavaAgent.SupportMode=true -DCAU.JavaAgent.Triggers=org/mycore/ -DCAU.Groovy.ClassPath=$CATALINA_HOME/lib/*:$CATALINA_HOME/webapps/ROOT/WEB-INF/lib/* -DCAU.Groovy.SourceDirs=/path/to/your/groovy-sources"
```
From now on, your Groovy sources in the `/path/to/your/groovy-sources` directory will be compiled before other MyCoRe
classes, so they can patch any library that MyCoRe uses and are available from the very beginning of the application's
lifecycle. You can continue to use the plugin as usual when you add the agent like this.