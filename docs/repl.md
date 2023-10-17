# Using the REPL

Once the REPL is started, simply connect to it with your SSH client.
The foundation of cau-repl is Groovy's `groovysh`, so for the very basics you can refer to
the [groovysh Documentation](https://groovy-lang.org/groovysh.html).

The general idea behind the REPL is that you can enter Groovy code, which is immediately executed in the same JVM as
your target application. The results of your invocation are displayed to you after every single command and can be
further modified with the next command you enter. This concept is known as a
[Read-Eval-Print Loop](https://en.wikipedia.org/wiki/Read%E2%80%93eval%E2%80%93print_loop) (REPL). All the classes the
application uses internally are available to you, enriching it with full scripting capabilities.

------------------------------

## Basic Operation
Apart from the normal Groovy syntax, the REPL provides a number of built-in commands. See the
[groovysh Command List](https://groovy-lang.org/groovysh.html#GroovyShell-RecognizedCommands). We will only focus on cau-repl specific additions in the following.

------------------------------

## Editing Files
When you enter longer blocks of code - such as functions or loops - it is convenient to use a full-featured editor.
cau-repl provides two commands for this.
As a prerequisite, a suitable terminal-based editor must be installed on the system running cau-repl. By default,
`vim` is configured. **If you configure a different editor, make sure that it supports running without a TTY.** Not all
editor have this capability.

**Edit and evaluate a file**
> **Shell Command**
> 
> `:editssh [filename]`<br/>
> `:E [filename]`
> 
> Opens an existing or new file named `filename` in an editor and immediately evaluates its content after returning to the REPL.
> 
> **Positional Parameters**
> 
> `String filename` *optional* - The name of the file to open
>
> **Returns** the return value of the last statement in the file.

**Edit a file without evaluating it**
> **Shell Command**
> 
> `:editfilessh [filename]`<br/>
> `:EF [filename]`
>
> Opens an existing or new file named `filename` in an editor without evaluating it in the REPL.
> 
> **Positional Parameters**
>
> `String filename` *optional* - The name of the file to open
> 
> **Returns** nothing.

**Examples:**
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

------------------------------

## Logging
The REPL has built-in logging functions that will make use of Log4J if it is installed, but also work without it.


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

**Retrieve the REPL log file**
> **Function**
> 
> ``repllog()``
> 
> Retrieves the contents of the REPL's internal log file, which is rotated every time
> the target application starts up.
> 
> **Returns** a list of [REPLLogEntry](TODO) in chronological order.

**Retrieve the Tomcat log**
> **Function**
>
> ``tomcatlog()``
>
> Retrieves the contents of Tomcat's main `catalina.out` log file. This will only be successful, if you use Tomcat with
> a standard logging setup.
>
> **Returns** an array of `String`, in chronological order.


**Related Classes:** [REPLLog](TODO), [REPLLogEntry](TODO) provide additional functionality related to logging.

**Examples:**
```text
// log a formatted message to sensible default targets
groovy:000> info("Java version {} on {}/{}", System.getProperty("java.version"), System.getProperty("os.name"),
System.getProperty("os.arch"))
===> [2023-09-28 12:33:58] INFO Java version 17.0.8 on Linux/amd64

// log a message to the REPL's log file and print it on all connected SSH sessions
groovy:000> warn("restarting in 5min", targets:[REPLLog.LOG_TARGETS.REPL_FILE, REPLLog.LOG_TARGETS.REPL_ALL_SHELLS])
===> [2023-09-28 12:37:02] WARN restarting in 5min
```

------------------------------

## Job Management

You can start long-running jobs in the REPL that will execute in the background and continue running after you end the
SSH session. Jobs can be associated with a list of inputs to be processed. If you can list your inputs in advance,
cau-repl can automatically parallelize your jobs. This also makes it possible to re-run just the inputs that failed later
on, even after restarting the target application.

<a name="job"></a>**Create a new job**
> **Function**
>
>`job(closure)`
> 
> Run the Groovy `Closure` closure in the background. You can optionally pass a list of input work items, which is then
> subsequently processed, tracking the job's progress in a file in the REPL's work
> directory. This makes it possible for you to resume aborted or failed executions as well as inspect the job's results
> even after restarting the target application. Jobs instances that throw an exception will be considered failed.
> Instances that do not throw an exception will be considered successful.<br/>
> MyCoRe users should use the [mcrjob()](mycore.md#mcrjob) function instead,
> which is additionally integrated with MyCoRe's job system.
>
> **Positional Parameters**
> 
> `Closure closure` - The Groovy Closure to execute. If you do not specify the named parameter `inputs`, pass a closure
> that does not require any parameters (`{ -> foo() }`). If you do specify `inputs`, pass a closure that accepts two
> parameters (`{ x, j -> foo(x) }`). cau-repl will pass the current input in the first parameter and the job that it
> belongs to in the second parameter.<br/>
> The return value of the closure is considered the result of the job for the current input item.
> Return values' class must implement the `Serializable` interface, so cau-repl can persist your output. The
> results of each invocation of the closure are kept in memory, so
> design your jobs to return only small data structures. If you need to generate larger structures, you should persist
> them somewhere else yourself and only return a status code here.<br/>
> The `ReplJob` that is created by this call will by default be set as the closure's
> [delegate](https://groovy-lang.org/closures.html#_delegation_strategy), providing variants of the logging methods
> `info()` and so forth. This will cause your log messages to additionally be persisted in the job's state file, so you
> can review them via the `ReplJob.jobLog()` method.<br/>
> To signal a failure, throw any exception.
> 
> **Optional Named Parameters**
> 
> `Boolean autostart = true` - Controls whether your job should immediately be started or placed in the master job list
> in a paused state.
> 
> `Integer autotune` *experimental* - If set, enables automatic parallelization for the processing of your input items.
> cau-repl will measure the throughput of work items and optimize the degree of parallelism on-the-fly. This works best
> when your work items are homogenous and individual processing time is small. The number you specify here is the
> maximum degree of parallelism you will allow. *Note:* to use a fixed number of parallel worker threads, just specify
> the `concurrency` parameter (see below) and don't set `autotune`.
> 
> `Boolean background = true` - Controls whether this call should return immediately, or only after the job has finished
> running. You might want to disable background processing when you also enable progress messages.
> 
> `Boolean becomedelegate = true` - Controls whether cau-repl will set the newly created `ReplJob` as your closure's
> delegate before executing it. This makes the instance methods of your `ReplJob` (especially the versions of the
> logging methods `info()`, etc. that also log to your job's private log) readily available for your closure. 
> More information is available in
> [Groovy's closure documentation](https://groovy-lang.org/closures.html#_delegation_strategy). 
> 
> `int concurrency = 1` - Spawn this many worker threads and process inputs in parallel. You can also change the concurrency
> level of a job while it is running. If you also pass the `autotune` parameter,
> the value of `concurrency` will be used as the initial concurrency level to start the tuning-process from.
> 
> `Boolean errorpause = false` - If set, the job will be paused on the first error (i.e. when your closure throws an
> exception). If there are other parallel workers, the work items they are currently processing will not be aborted and
> your job will be fully paused after they too have finished their current work items.
> 
> `Collection<Serializable> inputs` - The work items that will be sequentially - in the order you specified them -
> passed as the first parameter to subsequent executions of your closure. The entire list will be kept in memory, so
> only use small objects here. To process larger objects, pass their ids or addresses instead and fetch them yourself
> from your closure. If you omit this parameter, your closure will simply be called once without input arguments.
> 
> `String name = <job's key>` - Set a descriptive name for your job to make identifying it easier. If omitted, your job's
> auto-generated key will also be used as its name.
> 
> `Boolean progress = false` - Log job progress to the REPL periodically. Best combined with `background = false`.
> 
> `String resume` - Pass the `key` of a finished ReplJob here to resume it. When you resume a job, your
> new job's state will be initialized from the persistent state file of the old job in the REPL's work directory.
> This includes inputs, results as well as its internal log.
> By default, only inputs that were not successfully processed (i.e. inputs that have failed, never started or started and
> did not finish at all) will be processed when you start the new job. Do not pass the `inputs` parameter when you
> resume - input values will automatically be read from the old job's state file.
>
> `Boolean retryerror = true` - When resuming a job, disable to not retry inputs that threw an exception.
> 
> `Boolean retrysuccess = false` - When resudimg a job, enable to also retry inputs that were successful.
> 
> `ThreadFactory threadfactory` - Use a custom ThreadFactory to spawn the worker threads. If unspecified, the
> system default is used.
> 
> **Returns** the [ReplJob](TODO) that was created.

**List current jobs**
> **Shell Command**
>
> `:job`<br/>
> `:J`
>
> Prints a list of all jobs that were created in this session as well as their status.
>
> **Returns** The list in text format.

**Retrieve a current job and print its progress**
> **Shell Command**
>
> `:job [index]` / `:job [key]`<br/>
> `:J [index]` / `:J [key]`
>
> Retrieves the ReplJob that has the given `index` number in the `:J` listing, or whose `key` matches the argument.
> Print its JobProgress to the console and return the job object.
>
> **Returns** The [ReplJob](TODO) that was requested.
**Pause or unpause a job**
> **Shell Command**
>
> `:job pause [key]` / `:job unpause [key]`<br/>
> `:J pause [key]` / `:J unpause [key]`<br/>
>
> Pauses the job with the given `key`. When paused, workers will finish their currently assigned work item, but will not
> receive any new work items. While residual items are still being processed, the job's state will be given as
> "pausing", after which it will transition to "paused".
>
> Unpausing a paused job will cause unprocessed work items to be submitted to the workers again.
>
> **Returns** when pausing: a boolean indicating if the job transitioned to the "pausing" state (could e.g. be false for already
> paused jobs); when unpausing: the number of milliseconds that the jobs has in the paused state.
>

**Cancel a job**
> **Shell Command**
>
> `:job cancel [key]` / `:job cancelforce [key]`<br/>
> `:J cancel [key]` / `:J cancelforce [key]`<br/>
>
> Cancels the job with the given `key`. No further input items will be passed to the worker threads. When invoked as
> `cancel`, previously active threads will be allowed to continue indefinitely until they have finished their previously
> assigned inputs. During this phase, the job state will be "cancelling" after which it will transition to "cancelled".
> The job is then ready for archiving or resuming it.
>
> When invoked as `cancelforce`, previously active threads will be forcefully terminated after a grace period of 10s.
>
> **Returns** a boolean indicating if the job transitioned to the "cancelling" or "cancelled" state (could e.g. be
> false for jobs that finished in the meantime).

**Archive a job**
> **Shell Command**
>
> `:job archive [key]`<br/>
> `:J archive [key]`
>
> Archives the finished job with the given `key`, removing it from the job list and freeing its memory.
>
> **Returns** a boolean indicating if the job was archived or not.

**List all job keys, including archived jobs**
> **Shell Command**
>
> `:job archived`<br/>
> `:J archived`
>
> Prints a list of all jobs' keys for which a state file in the REPL's work directory still exists.
> This includes all current jobs as well as archived jobs from previous sessions.
>
> **Returns** The list in text format.

**Prune all successfully completed jobs, including archived jobs**
> **Shell Command**
>
> `:job prune`<br/>
> `:J prune`
>
> Removes the state file of all jobs that have successfully completed (i.e. all inputs were processed without error)
> from the REPL's work directory. This includes both current and archived jobs.
>
> **Returns** The list of pruned keys in text format.

**Related Classes:** [REPLJob](TODO), [REPLJobCallbackAutoTune](TODO) provide additional functionality related to job
control.

**Example: Simple Job**
```text
// copy a file in a background job without parameters (please adjust filenames)...
groovy:000> job({ return java.nio.file.Files.copy(java.nio.file.Path.of("/path/to/source"),
java.nio.file.Path.of("/path/to/destination")) as String })
===> 20231002-115717-998860232 (Job 20231002-115717-998860232)
// ...now view the job's progress
groovy:000> :J
===> 
[1] 20231002-115717-998860232 - Job 20231002-115717-998860232 - completed successfully (0/1 threads active), 100%
```
**Example: Parallel Job with Inputs**
```text
// calculate primes around the 8th mersenne prime using a naive algorithm, 4 workers in parallel 
groovy:000> j = job({BigInteger x, j -> x > 1G && (x <= 3G || !(2G..x.sqrt()).find{ x.mod(it) == 0G })},
inputs:(2147000000G..2148000000G), concurrency:4)
// monitor progress with the :J command (or run the job with "progress: true" to watch it in realtime)
groovy:000> :J
===> 
[1] 20231002-143021-171027612 - Job 20231002-143021-171027612 - running (4/4 threads active), 7%, eta 2m
// after the job is done, view the results
groovy:000> j.results.findAll{ it.result }.collect{ j.inputs[it.index] }
===> [2147000041, 2147000081, 2147000111, 2147000117, 2147000173, 2147000189, 2147000243, 2147000281, 2147000293,
[...]
// now archive the job to free its memory
groovy:000> :J archive 20231002-143021-17102761
groovy:000> j = null
```
**Example: Resuming**
```text
// start the calculation again, cancelling and resuming it. disable status updates this time
groovy:000> j = job({BigInteger x, j -> x > 1G && (x <= 3G || !(2G..x.sqrt()).find{ x.mod(it) == 0G })},
inputs:(2147000000G..2148000000G), concurrency:4)
===> 20231002-151111-065054552 (Job 20231002-151111-065054552)
// wait a bit until the job is about half done, and then cancel it
groovy:000> :J cancel 20231002-151111-065054552
===> true
// verify that the job's state is no longer "cancelling", but "cancelled"
groovy:000> :J
===> 
[2] 20231002-151111-065054552 - Job 20231002-151111-065054552 - cancelled (0/4 threads active), 48%
// you may now restart the target application (but you don't have to)
[...]
// let's resume our cancelled calculation, disabling autostart so we can inspect it's initial state
groovy:000> j = job({BigInteger x, j -> x > 1G && (x <= 3G || !(2G..x.sqrt()).find{ x.mod(it) == 0G })},
resume:"20231002-151111-065054552", concurrency:4, autostart:false)
===> 20231002-151645-393465402 (Resume: Job 20231002-151111-065054552)
// you can see that the "success" counter is at about 50%. results of the previous run were loaded
groovy:000> j.progress
===> JobProgress[state=not yet started, nextInput=0, totalInputs=1000001, remainingInputs=1000001, success=480790,
skippedSuccess=0, errors=0, skippedErrors=0, percentDone=0, pausedSince=null, cancelledSince=null, startTimestamp=null,
doneTimestamp=null, eta=null, etaSeconds=null, activeThreads=0, future=null]
groovy:000> j.results[0]
===> InputResult[key=20231002-151111-065054552, index=0, epochMicrosFrom=1696252271190601,
epochMicrosTo=1696252271200158, result=false, error=null]
// the job's private log - that you can write to with the info(),... methods from your closure - was also restored
groovy:000> j.jobLog[0]
===> [2023-10-02 15:11:11] INFO Job 20231002-151111-065054552: Starting job...
// resuming from where we cancelled
groovy:000> j.start()
===> de.uni_kiel.rz.fdr.repl.REPLJob$1@4a183138[Not completed]
// the first 50% of progress will pass very quickly, as the job skips all already completed inputs
// now simply wait for the rest of the calculation to finish
```

------------------------------

## Loading Maven Artifacts
You can load maven artifacts into the repl at runtime, without altering the installation of your target application.

**Load a maven artifact into the REPL**
> **Shell Command**
>
> `:grabrepl [group[:module[:version|*[:classifier]]]][@ext]`<br/>
> `:G [group[:module[:version|*[:classifier]]]][@ext]`
>
> Fetches the maven artifact described by the parameters as well as its dependencies and loads it into the REPL for this
> session. Downloaded artifacts will be cached in the REPL's work directory to speed up the process in subsequent
> sessions. Specifying `group`, `module` and `version` is usually sufficient.
>
> **Returns** nothing.

**Examples:**
```text
// fetch the apache-commons numbers module for primes and load it into the repl
groovy:000> :G org.apache.commons:commons-numbers-primes
// now import one of its classes
groovy:000> import org.apache.commons.numbers.primes.Primes
===> [...], org.apache.commons.numbers.primes.Primes
// and use it to find the prime factors of a number
groovy:000> Primes.primeFactors(34592523)
===> [3, 7, 773, 2131]
```

------------------------------

## Compiling
The REPL will normally compile your Groovy sources when the target application starts. If you want to compile code
dynamically at runtime, there is a support function available for you.

<a name="compile"></a>**Compile Groovy sources**
> **Function**
> 
> `compile(path)`
> 
> Compile the Groovy sources at the specified path and load them into a suitable classloader. MyCoRe user should use the
> [mcrcompile()](mycore.md#mcrcompile) function instead, which will use default parameter values suitable for MyCoRe.
>
> **Positional Parameters**
>
> `Path | File | String path` - The location of the sources to compile. If you pass a directory, all `.groovy` files
> below it will be compiled as one coherent unit.
> 
> **Optional Named Parameters**
>
> `ClassLoader classloader = <REPL's classloader>` - The ClassLoader in which the generated classed should be put. Will
> default to the REPL's ClassLoader, which is subject to the settings described in the [Installation](TODO) section.
> 
> `String classpath = <REPL's classpath>` - The class path to use when locating targets of the @Patches annotation.
> Defaults to the REPL's classpath.
> 
> **Returns** the [GroovySourceDirectory](TODO) with the results of the compilation.

**Related Classes:** [GroovySourceDirectory](TODO) provides additional functionality related to compilation.

**Examples:**
```text
// compile a single groovy class
groovy:000> compile("Dummy.groovy")
===> de.uni_kiel.rz.fdr.repl.groovy.GroovySourceDirectory@130807a5
// now import it to the REPL
groovy:000> import foo.Dummy
===> [...], foo.Dummy
groovy:000> x = new Dummy()
===> foo.Dummy@4cc38db
```

------------------------------
## Managing Breakpoints
You can add breakpoints to your Groovy code. When triggered, they pause its execution and transfer control to the REPL.
See the [Programming](TODO) section to start using this feature. Once you have augmented your code with breakpoints, you
can manage them in the REPL:

**List currently triggered breakpoints and disabled patterns**
> **Shell Command**
>
> `:breakpoint`<br/>
> `:B`
>
> Lists all currently triggered breakpoints: their numeric key, function and name.
> Will output a list of all currently disabled breakpoint patterns
>
> **Returns** the list in text format.

**Resume a currently triggered breakpoint**
> **Shell Command**
>
> `:breakpoint [key [feedback]]`<br/>
> `:B [key [feedback]]`
>
> Resumes the currently triggered breakpoint with the specified `key`. You can optionally pass a feedback result, which
> will be returned as the result of the `replbreakpoint` function call that triggered this breakpoint.<br/>
> *Please Note:* for technical reasons, repeated whitespace characters in your feedback string will be merged into a
> single space - even within quotes (which will be considered part of your feedback). If this is a problem, you should
> use the [REPLBreakpoint.resume()](TODO) function instead.
>
> **Returns** nothing.

**Disable or enable breakpoints**
> **Shell Command**
>
> `:breakpoint disable [pattern]` / `:breakpoint enable [pattern]`<br/>
> `:B disable [pattern]` / `:B enable [pattern]`
>
> Use this to disable or re-enable certain breakpoint matching a regular-expression pattern in Java RE syntax. A
> triggered breakpoint with a [signature](TODO) that matches one of your disabled patterns will be silently ignored.
> Your breakpoints' signatures are displayed in the `:B` breakpoint list after the numeric key (e.g.
> `org.example.Dummy::foo - My Breakpoint`).<br/>
> *Please Note:* for technical reasons, repeated whitespace characters in your pattern string will be merged into a
> single space - even within quotes (which will be considered part of your pattern). If this is a problem, you should
> use the [REPLBreakpoint.disable()](TODO) / [REPLBreakpoint.enable()](TODO) functions instead.
> 
> **Returns** nothing.

**Execute code in the thread of a triggered breakpoint**
> **Shell Command**
>
> `:breakpoint eval [key] [code]` / `:breakpoint eval [key]`<br/>
> `:B eval [key] [code]` / `:B eval [key]`
>
> You can run code in the thread of a triggered breakpoint. Just pass the `key` of the target breakpoint and the
> Groovy `code` to run end it will execute. The environment executing your snippet will have the special variable `this`
> and also `x` set to the REPLBreakpoint instance that triggered.
> 
> You know that your code has finished executed, when your breakpoint has a `*` marker in the `:B` list. You may now
> retrieve the result of you code using the `eval [key]` subcommand (i.e. without a code argument).
> 
> *Please Note:* for technical reasons, repeated whitespace characters in your code string will be merged into a
> single space - even within quotes (which will be considered part of your code). If this is a problem, you should
> use the [REPLBreakpoint.eval()](TODO) function instead.
>
> **Returns** nothing when you submit code to run, or the results of your code wrapped in a
> [REPLBreakpoint.EvalResult](TODO) when you retrieve the result.

**Set the maximum number of concurrent breakpoints**
> **Shell Command**
>
> `:breakpoint max [limit]`<br/>
> `:B max [limit]`
>
> Sets the global limit for the number of unresumed triggered breakpoint instances. The first time more than this many
> instances are triggered, a warning is logged and all further breakpoints are ignored (i.e. executed without
> interruption) until the total number on unresumed instances drops below this threshold.<br/>
> Set this to `0` to temporarily disable all breakpoints globally.
>
> **Returns** nothing.

**Related Classes:** [REPLBreakpoint](TODO) provides additional functionality related to breakpoints.

**Examples:**
```text
// you need a triggered breakpoint for this example. see the "writing code" section of the docs for further info.

// list all triggered breakpoints
groovy:000> :B
===> 
   0 org.example.Dummy::foo - My Breakpoint
   
// let's evaluate some code in the breakpoint's context that returns its thread
groovy:000> :B eval 0 Thread.currentThread()
// note the asterisk "*", which signals that our code has returned a result
groovy:000> :B
===> 
   0 org.example.Dummy::foo - My Breakpoint *
// retrieve the result...
groovy:000> :B eval 0
===> EvalResult[result=Thread[GroovySh Client Thread: /127.0.0.1:57352,5,main], exception=null]
// ...and save it to a variable
groovy:000> t = _

// resume the breakpoint, passing a feedback string
groovy:000> :B 0 some string data
```
------------------------------


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