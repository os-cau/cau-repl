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

- `target/cau-repl-X.Y.Z-fatjar-nogpl.jar` can be used as a standalone Java agent and contains all of cau-repls dependencies
- `target/cau-repl-agent-X.Y.Z.jar` is an optional lightweight loader-only agent that can be used to load the full `fatjar`
  into a specific classloader. Use this if your application places its classes in a non-default classloader (e.g.
  webapplications in a servlet container). See the [Classloader Selection](#classloader-selection) section for details. 

### Loading the Agent

To simply get access to the SSH REPL, load cau-repl as a Java agent. You need to pass a special parameter to your
Java command:
```bash
java -javaagent:/path/to/cau-repl-X.Y.Z-fatjar-nogpl.jar ...
```
This also allows you to compile your own classes and use them in the REPL.

If you would like to extend your own classes' availability to the entire application (and not just the REPL), they need
to be loaded into the system-default classloader. Then you should configure cau-repl like this: 
```bash
java -javaagent:/path/to/cau-repl-X.Y.Z-fatjar-nogpl.jar -DCAU.Groovy.UseSystemClassLoader=true ...
```

If you use the system-default classloader and loading your classes fails with an exception, you might also need to
disable the access protection provided by Java's module system (potentially impacting your target's security if it
depends on it). Whether this step is necessary, depends on your target application:
```bash
java -javaagent:/path/to/cau-repl-X.Y.Z-fatjar-nogpl.jar --add-opens 'java.base/java.lang=ALL-UNNAMED' -DCAU.Groovy.UseSystemClassLoader=true ...
```

The REPL will listen for SSH connections on port 8512 on the local interface. You can use any user name to login. A
per-session password will be printed to STDERR on startup. **Be careful to make sure that it does not end up in a public
logfile**, e.g. Systemd's journal. Any local user who can read this password can connect to the REPL and execute code
with the permissions of your application. To secure your installation permanently, see the [Configuration](configuration.md) section for ways to set your own
static password without producing log output. The confiration section also describes various parameters that you can
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
on (by the time it is seen, it is to late to block it). So make sure to select a trigger that triggers in the correct
classloader, but before the first class you would like to patch.

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
login with the username `administrator` and the corresponding MyCoRe password of the account. See the
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