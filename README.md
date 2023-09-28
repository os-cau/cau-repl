# cau-repl

Extend Java programs with Groovy at runtime. Add an interactive SSH-based REPL to any JVM and tweak it with your own custom Groovy
classes: no sourcecode required!

The cau-repl comes bundled with an additional [MyCoRe](https://mycore.de) module for easy integration with your document repository.


## Disclaimer

> **This project is in a very early stage: expect bugs.**

As of today, testing was exclusively on Linux. While the code is intended to be OS agnostic, there will certainly be
issues if you run it on Windows (it might run fine on the WSL, though). Pull requests for compatibility are welcome!

Testing of the MyCoRe plugin was until now conducted only in a Tomcat environment.

## Features

**Without access to an application's sourcecode** and without making any changes to it, cau-repl enables you to:

- Connect to any Java application via SSH and run Groovy commands in its JVM. You can use all the application's classes
  interactively.
- Load any Maven artifact into the REPL at runtime and use it just in your session
- Start long-running batch jobs from the SSH console that persist after you disconnect, monitor their status and easily
  retry failed steps. Job inputs can be processed in parallel, with automatic tuning to determine the number of workers
  that maximize throughput.
- Extend any Java program with your own Groovy classes, e.g. for dependency injection.
- Automatically run your own Groovy code each time the application is started.
- Patch classes of any Java program replacing their methods with your own Groovy code, even if the methods are private.
  All changes are applied ad-hoc each time the application starts without altering its installation.
- Use interactive breakpoints that transfer control to the SSH console under certain circumstances.
- (experimental) change the code of methods at runtime, even if those methods are called from Java code.

cau-repl can be built as a MyCoRe plugin that enables better integration:

- Simple installation: just put one `.jar` in your `lib/` directory and enable the REPL in your `.properties`
- Extend your repository with your own EventHandlers, CronJobs, etc. - no need to create a plugin, set up a full
  development environment or (re-)compile anything: just drop a single `.groovy` file in the right directory and the class will be available in MyCoRe.
- Numerous helper functions that make interacting with your repository from the REPL easy:
    - SOLR searches
    - Retrieving documents and their XML via XPath
    - Displaying, changing (by XSLT or manually), saving XML
    - Generating diffs between updated XML and the original before saving
    - Easy MyCoRe session management: run any command as any user, with automatic transactions
- The REPL's Job system is integrated with MyCoRe's, so jobs started from the REPL can also be managed via the webinterface.
- MyCoRe's own CLI commands are also available and can be used from the REPL.

## Requirements

- Java 17 (newer versions may or may not work)
- Maven
- vim or any other console text editor that can run without a tty (optional: only if you want to edit files within the SSH session)
- MyCoRe 2022.06 (optional: only if you would like to use the MyCoRe plugin; other versions may or may not work)


## License

cau-repl is MIT licensed. Designated portions of it were imported from other projects and are Apache 2.0 licensed.

The optional MyCoRe support module is GPL 3.0 licensed. It is not included in the build by default. The documentation contains
instructions on building a version with the MyCoRe-specific helpers enabled (the `-gpl` build). You can use this build under the terms of
the GPL 3.0. In this sense, cau-repl is dual-licensed under either the MIT or GPL 3.0 license.

See the bundled LICENSE.txt file and the SPDX identifier of each source file for details.


## Quickstart

cau-repl can be built in two flavors: as a MyCoRe plugin, or as a generic Java agent from all kinds of java
applications. Choose the quickstart instructions that apply to your use-case.

### For universal use, built without GPL code
    mvn clean package
    java -javaagent:target/cau-repl-X.Y.Z-fatjar-nogpl.jar -jar /path/to/your/application.jar
    # a message like "REPL: Session Password auto-generated: XXXXXXXXXX" should be printed to the terminal
    # you may now login with any username and the password that was just printed
    ssh "ssh://localhost:8512"

By default, cau-repl will store its state in the `cau-repl` directory, which it will create in the current working
directory.

#### Things to try

Your commands run in the target application's JVM. Try listing all threads:

    groovy:000> Thread.allStackTraces.entrySet().collect{ "${it.key} -> ${it.value ? it.value[0] : ''}" }
    ===> [Thread[HikariPool-1 housekeeper,5,main] -> java.base@17.0.8/jdk.internal.misc.Unsafe.park(Native Method),
    ...

Now import a class of your target and start interacting

    groovy:000> import foo.bar.SomeClass
    groovy:000> SomeClass.someStaticMethod()
    groovy:000> x = new SomeClass()
    ...

Can't find the classes you are looking for? Then you should configure cau-repl to use the same ClassLoader as your
target. Consult the documentation for details.

### For use in MyCoRe, built with GPL code
    mvn -P gpl clean package
    cp target/cau-repl-X.Y.Z-fatjar-gpl.jar /path/to/mycore/lib
    echo "CAU.REPL.Enabled=true" >> /path/to/mycore/mycore.properties
    # now (re-)start your servlet container
    # a message like "REPLLog: REPL listening on 127.0.0.1:8512" should be logged
    # you may now login with the MyCoRe administrator password
    ssh "ssh://administrator@localhost:8512"

By default, cau-repl will store its state in the `cau-repl` subdirectory, which it will create in your MyCoRe
installation's root.

#### Things to try

Your commands run directly in MyCoRe's JVM. Try pinging a Solr core:

    groovy:000> MCRSolrClientFactory.mainSolrClient.ping()
    ===> {responseHeader={zkConnected=null,status=0,QTime=13,params={q={!lucene}*:*,distrib=false,df=allMeta,facet.field=mods.genre,echoParams=all,fl=*,score,sort=score desc, mods.dateIssued desc,facet.mincount=1,rows=20,wt=javabin,version=2,facet=true,rid=-26}},status=OK}

Numerous helper functions are avaliable. Go retrieve a document and inspect it:

    groovy:000> doc = mcrxml("mods", filter=".[//mods:title='SOMETITLEHERE']")[0]
    ===> [Document:  No DOCTYPE declaration, Root is [Element: <mycoreobject/>]]
    groovy:000> doc()
    ===>
    <mycoreobject xmlns:xlink="http://www.w3.org/1999/xlink" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:noNamespaceSchema
    Location="datamodel-mods.xsd" ID="fdr_mods_00000299" version="2022.06.3-SNAPSHOT" label="fdr_mods_00000299">
    ...

## Documentation

There is [complete documentation TODO URL](https://127.0.0.1) available covering all of cau-repl's features.
