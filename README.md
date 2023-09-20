# cau-repl

Extend Java programs with Groovy at runtime: add an interactive SSH-based REPL to any JVM and tweak it with your own custom Groovy
classes.

The cau-repl comes bundled with an additional [MyCoRe](https://mycore.de) module for easy integration with your document repository.


## 1. Disclaimer

**This project is in a very early stage: expect bugs.**

As of today, testing was exclusively on Linux. While the code is intended to be OS agnostic, there will certainly be
issues if you run it on Windows (it might run fine on the WSL, though). Pull requests for compatibility are welcome!

Testing of the MyCoRe plugin was until now conducted only in a Tomcat environment.


## 2. Requirements

- Java 17 (newer versions may or may not work)
- Maven
- vim or any other console text editor that can run without a tty (optional: only if you want to edit files within the SSH session)
- MyCoRe 2022.06 (optional: only if you would like to use the MyCoRe plugin; other versions may or may not work)


## 3. License

cau-repl is MIT licensed. Designated portions of it were imported from other projects and are Apache 2.0 licensed.

The optional MyCoRe support module is GPL 3.0 licensed. It is not included in the build by default. The documentation contains
instructions on building a version with the GPL-licensed MyCoRe-specific helpers enabled.

See the bundled LICENSE.txt file and the SPDX identifier of each source file for details.


## 4. Quickstart

### 4.1. For use in MyCoRe, built with GPL code
    mvn -P gpl clean package
    cp target/cau-repl-X.Y.Z-fatjar-gpl.jar /path/to/mycore/lib
    echo "CAU.REPL.Enabled=true" >> /path/to/mycore/mycore.properties
    # now (re-)start your servlet container
    # a message like "REPLLog: REPL listening on 127.0.0.1:8512" should be logged
    # you may now login with the MyCoRe administrator password
    ssh "ssh://administrator@localhost:8512"

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

### 4.2. For universal use, built without GPL code
    mvn clean package
    java -javaagent:target/cau-repl-X.Y.Z-fatjar-nogpl.jar -jar /path/to/your/application.jar
    # a message like "REPL: Session Password auto-generated: XXXXXXXXXX" should be printed to the terminal
    # you may now login with any username and the password that was just printed
    ssh "ssh://localhost:8512"

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
