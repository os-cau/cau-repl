# Using the REPL

Once the REPL is started, simply connect to it with your SSH client.
The foundation of cau-repl is Groovy's `groovysh`, so for the very basics you can refer to
the [groovysh Documentation](https://groovy-lang.org/groovysh.html).

The general idea behind the REPL is that you can enter Groovy code, which is immediately executed in the same JVM as
your target application. The results of your invocation are displayed to you after every single command and can be
further modified with the next command you enter. This concept is known as a
[Read-Eval-Print Loop](https://en.wikipedia.org/wiki/Read%E2%80%93eval%E2%80%93print_loop) (REPL). All the classes the
application uses internally are available to you, enriching it with full scripting capabilities.

## Basic Operation
Apart from the normal Groovy syntax, the REPL provides a number of built-in commands. See the
[groovysh Command List](https://groovy-lang.org/groovysh.html#GroovyShell-RecognizedCommands). We will only focus on cau-repl specific additions in the following.

## Editing Files
When you enter longer blocks of code - such as functions or loops - it is convenient to use a full-featured editor.
cau-repl provides two commands for this.
As a prerequisite, a suitable terminal-based editor must be installed on the system running cau-repl. By default,
`vim` is configured. **If you configure a different editor, make sure that it supports running without a TTY.** Not all
editor have this capability.

------------------------------
**Edit and evaluate a file**
> **Shell Command**
> 
> `:editssh / :E [filename]`
> 
> Opens an existing or new file named `filename` in an editor and immediately evaluates its content after returning to the REPL.
> 
> **Positional Parameters**
> 
> `String filename` *optional* - The name of the file to open
>
> **Returns** the return value of the last statement in the file.
------------------------------
**Edit a file without evaluating it**
> **Shell Command**
> 
> `:editfilessh / :EF [filename]`
>
> Opens an existing or new file named `filename` in an editor without evaluating it in the REPL.
> 
> **Positional Parameters**
>
> `String filename` *optional* - The name of the file to open
> 
> **Returns** nothing.
------------------------------

*Examples:*
```text
# invocation without a filename parameter
# creates a temporary one-off file that is automatically deleted after evaluation
groovy:000> :E

# most convenient: invocation with a simple name - no path and no extension
# creates / opens a persistent file named e.g. editbuffer-1.groovy in the REPL's workdir
groovy:000> :E 1

# invocation with a full path creates / opens exactly this file
groovy:000> :E /tmp/foo.groovy
```

## Logging
The REPL has built-in logging functions that will make use of Log4J if it is installed, but also work without it.

------------------------------
**Log a message**
> **Function**
> 
> ``trace(...msgs) / debug(...msgs) / warn(...msgs) / error(...msgs)``
> 
> 
> Logs the given message to suitable logging systems with the log-level corresponding to the method name.
> 
> **Positional Parameters**
> - `String msgs` *repeatable* - The objects that will be logged after invoking their `.toString()` method. The first
>   instance of this parameter is special in that it can contain `{}` placeholders. These will be replaced with the
>   text representation of each succeeding further parameter. Excess parameters will be joined with a newline.
>
> **Optional Named Parameters**
> - `ReplLog.LOG_TARGETS[] targets = REPLLog.DEFAULT_LOG_TARGETS` - The list of targets that will receive this message. See the [REPLLog](TODO)
>   documentation for possible values. If omitted, the message will be logged to Log4J if it is availble or stderr if
>   not. In addition, it will be written to the REPL's own logfile in its work directory.
> - `PrintStream[] streams = []` - A list of multiple `PrintStream` that will receive this message. Empty by default.
> 
> **Returns** The [REPLLogEntry](TODO) that was logged
------------------------------
**Retrieve the REPL log file**
> **Function**
> 
> ``repllog()``
> 
> Retrieves the contents of the REPL's internal log file, which is rotated every time
> the target application starts up.
> 
> **Returns** a list of [REPLLogEntry](TODO) in chronological order.
------------------------------
**Retrieve the Tomcat log**
> **Function**
>
> ``tomcatlog()``
>
> Retrieves the contents of Tomcat's main `catalina.out` log file. This will only be successful, if you use Tomcat with
> a standard logging setup.
>
> **Returns** an array of `String`, in chronological order.
------------------------------

*Related Classes:* [REPLLog](TODO), [REPLLogEntry](TODO)

*Examples:*
```text
# log a formatted message to sensible default targets
groovy:000> info("Java version {} on {}/{}", System.getProperty("java.version"), System.getProperty("os.name"), System.getProperty("os.arch"))
===> [2023-09-28 12:33:58] INFO Java version 17.0.8 on Linux/amd64

# log a message to the REPL's log file and print it on all connected SSH sessions
groovy:000> warn("restarting in 5min", targets:[REPLLog.LOG_TARGETS.REPL_FILE, REPLLog.LOG_TARGETS.REPL_ALL_SHELLS])
===> [2023-09-28 12:37:02] WARN restarting in 5min
```

## Job Management

You can start long-running jobs in the REPL that will execute in the background and continue running after you end the
SSH session. Jobs can be associated with a list of inputs to be processed. If you can list your inputs in advance,
cau-repl can automatically parallelize your jobs. This also makes it possible to re-run just the inputs that failed later
on, even after restarting the target application.

------------------------------
**Create a new job**
> **Function**
>
>`job(closure)`
> 
> Run the Groovy `Closure` closure in the background. cau-repl will persist the job progress to a file in its work
> directory. This makes it possible for you to resume aborted or failed executions as well as inspect the job's results
> even after restarting the target application. Jobs instances that throw an exception will be considered failed.
> Instances that do not throw an exception will be considered successful.<br/>
> MyCoRe users should use the [mcrjob()](TODO) function instead,
> which is additionally integrated with MyCoRe's job system.
>
> **Positional Parameters**
> 
> `Closure closure` - The Groovy Closure to execute. If you do not specify the named parameter `inputs`, pass a closure
> that does not require any parameters (`{ -> foo() }`). If you do specify `inputs`, pass a closure that accepts two
> parameters (`{ x, j -> foo(x) }`). cau-repl will pass the current input in the first parameter and the job that it
> belongs to in the second parameter.<br/>
> The return type of your closure must implement the `Serializable` interface, so cau-repl can persist your results. The
> results of each invocation of the closure are kept in memory, so
> design your jobs to return only small data structures. If you need to generate larger structures, you should persist
> them somewhere else yourself and only return a status code here.<br/>
> The `ReplJob` that is created by this call will by default be set as the closure's
> [delegate](https://groovy-lang.org/closures.html#_delegation_strategy), providing variants of the logging methods
> `info()` and so forth. This will cause your log messages to additionally be persisted in the job's state file, so you
> can review them together with its results.<br/>
> To signal a failure, throw any exception.
> 
> **Optional Named Parameters**
> 
> `boolean autostart = true` - Controls whether your job should immediately be started or placed in the master job list
> in a paused state.
> 
> `Integer autotune = null` *experimental* - If set, enables automatic parallelization for the processing of your input items.
> cau-repl will measure the throughput of work items and optimize the degree of parallelism on-the-fly. This works best
> when your work items are homogenous and individual processing time is small. The number you specify here is the
> maximum degree of parallelism you will allow.
> 
> `boolean background = true` - Controls whether this call should return immediately, or only after the job has finished
> running. You might want to disable background processing when you also enable progress messages.
> 
> `boolean becomedelegate = true` - Controls whether cau-repl will set the newly created `ReplJob` as your closure's
> delegate before executing it. This makes the instance methods of your `ReplJob` (especially the versions of the
> logging methods `info()`, etc. that also log to your job's private log) readily available for your closure. 
> More information is available in
> [Groovy's closure documentation](https://groovy-lang.org/closures.html#_delegation_strategy). 
> 
> `int concurrency = 1`
> 
> `boolean errorpause = false`
> 
> `List<Serializable> inputs = null`
> 
> `boolean progress = false`
> 
> `String resume = null`
>
> `boolean retryerror = true`
> 
> `boolean retrysuccess = false`
> 
> `ThreadFactory threadfactory = null`
------------------------------

`:job / :J`


## Loading Maven Artifacts
`:grabrepl / :G`

## Managing Breakpoints
`:breakpoint / :B`

## Compiling
`compile()`

## MyCoRe CLI
`:mcrcli / :M`

## Hints
- Tab-completion and line history is available.
- When you mismatch parentheses or similar syntactic structures, the REPL may enter a state where it's waiting for you
  to properly syntactivally finish your statement. To get out of this state, simply enter a single line consisting of
  just `:c`.
- The return value of your last command is always available in the special `_` variable. This is useful to continue
  using it in the next line, or if you want to save it in a more persistent properly named variable. 
- groovysh's standard `:edit / :e` command is not very useful when accessing it via SSH, because it launches the editor in
  the TTY of the server process. Use the `:editssh / :E` commands instead to edit a file directly in your
  SSH session.
- groovysh's standard `:grab / :g` command does not use the correct classloader of the REPL. Use the `:grabrepl / :G`
  commands instead.