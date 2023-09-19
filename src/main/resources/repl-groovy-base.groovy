//file:noinspection UnnecessaryQualifiedReference

// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

import de.uni_kiel.rz.fdr.repl.REPL
import de.uni_kiel.rz.fdr.repl.REPLLog
import de.uni_kiel.rz.fdr.repl.REPLLogEntry
import de.uni_kiel.rz.fdr.repl.REPLLog.LOG_TARGETS
import de.uni_kiel.rz.fdr.repl.REPLBreakpoint
import de.uni_kiel.rz.fdr.repl.REPLJob
import de.uni_kiel.rz.fdr.repl.REPLJobCallbackAutoTune

def static _CAUREPL_setRo(x) {
    if (x == null) return x
    x.metaClass._CAUREPL_RO = true
    if (x instanceof Collection) for (def y : x) y.metaClass._CAUREPL_RO = true  // don't recurse: we would have to detect cycles
    return x
}

def static _CAUREPL_isRo(x) {
    return (x != null) ? x.hasProperty("_CAUREPL_RO") as boolean : false
}

def compile(Map params=[:], path) {
    def classLoader = params["classLoader"] ?: de.uni_kiel.rz.fdr.repl.REPLAgentStartup.classLoader
    def classPath = params["classPath"] ?: de.uni_kiel.rz.fdr.repl.REPLAgentStartup.classPath
    path = path instanceof String ? java.nio.file.Path.of(path) : path
    path = path instanceof File ? path.toPath() : path
    return new de.uni_kiel.rz.fdr.repl.groovy.GroovySourceDirectory(path, classLoader, classPath)
}

@SuppressWarnings('GrMethodMayBeStatic')
def log(Map args=[:], ...msgs) {
    def level = args.getOrDefault("level", "INFO")
    def entry = new de.uni_kiel.rz.fdr.repl.REPLLogEntry(level as REPLLogEntry.LOG_LEVEL, msgs)

    def targets = new HashSet<LOG_TARGETS>()
    if (args.containsKey("targets")) {
        targets = new HashSet<LOG_TARGETS>((args.get("targets") ?: []).findAll({ it instanceof LOG_TARGETS }))
    } else {
        targets = de.uni_kiel.rz.fdr.repl.REPLLog.DEFAULT_LOG_TARGETS
    }

    def streams = new ArrayList<java.io.PrintStream>()
    if (args.containsKey("streams")) {
        streams = (args.get("streams") ?: []).findAll({ it instanceof java.io.PrintStream })
    }

    de.uni_kiel.rz.fdr.repl.REPLLog.log(entry, targets as Set<LOG_TARGETS>, streams.toArray() as java.io.PrintStream[])
    return entry
}

def trace(Map args=[:], ...msgs) {
    def a = args.clone()
    a["level"] = "TRACE"
    return log(a, msgs)
}

def debug(Map args=[:], ...msgs) {
    def a = args.clone()
    a["level"] = "DEBUG"
    return log(a, msgs)
}

def info(Map args=[:], ...msgs) {
    def a = args.clone()
    a["level"] = "INFO"
    return log(a, msgs)
}

def warn(Map args=[:], ...msgs) {
    def a = args.clone()
    a["level"] = "WARN"
    return log(a, msgs)
}

def error(Map args=[:], ...msgs) {
    def a = args.clone()
    a["level"] = "ERROR"
    return log(a, msgs)
}

static def repllog() {
    return de.uni_kiel.rz.fdr.repl.REPLLog.getLog()
}

static def tomcatlog() {
    if (!System.getProperty("catalina.base")) return null
    def ds = java.nio.file.Files.newDirectoryStream(java.nio.file.Path.of(System.getProperty("catalina.base"), "logs"), "catalina.*")
    def li = new ArrayList<java.nio.file.Path>()
    ds.forEach(li::add)
    if (!li) return null
    return li.sort()[-1].toFile() as String[]
}

static def workdir() {
    return de.uni_kiel.rz.fdr.repl.REPL.getWorkDir()
}

static def job(Map args=[:], Closure<Serializable> closure) {
    String name = args.containsKey("name") ? args["name"] : null
    int concurrency = args.containsKey("concurrency") ? args["concurrency"] : 1 as int
    List<Serializable> inputs = args.containsKey("inputs") ? args["inputs"] : null as List<Serializable>
    String resume = args.containsKey("resume") ? args["resume"] : null
    boolean autostart = args.containsKey("autostart") ? args["autostart"] : true
    Integer autotune = args.containsKey("autotune") ? args["autotune"] : null as Integer
    boolean errorpause = args.containsKey("errorpause") ? args["errorpause"] : false
    boolean progress = args.containsKey("progress") ? args["progress"] : false
    boolean background = args.containsKey("background") ? args["background"] : true
    boolean retrysuccess = args.containsKey("retrysuccess") ? args["retrysuccess"] : false
    boolean retryerror = args.containsKey("retryerror") ? args["retryerror"] : true
    boolean becomedelegate = args.containsKey("becomedelegate") ? args["becomedelegate"] : true
    java.util.concurrent.ThreadFactory threadfactory = args.containsKey("threadfactory") ? args["threadfactory"] : null as java.util.concurrent.ThreadFactory
    java.util.function.Consumer<REPLJob.JobEvent> internalcallback = args.containsKey("internalcallback") ? args["internalcallback"] : null as java.util.function.Consumer<REPLJob.JobEvent>

    REPLJob job
    if (resume == null) job = REPLJob.repljob(closure, inputs, concurrency, name, becomedelegate)
    else job = REPLJob.resume(resume, closure, retrysuccess, retryerror, becomedelegate)
    if (internalcallback) {
        job.setInternalCallback(job.getInternalCallback() ? job.getInternalCallback().andThen(internalcallback) : internalcallback)
    }
    if (autostart) {
        java.util.function.Consumer<REPLJob.JobEvent> callback = null
        if (progress) callback = callback == null ? REPLJob.CALLBACK_LOG_TO_SHELL : callback.andThen(REPLJob.CALLBACK_LOG_TO_SHELL)
        if (errorpause) callback = callback == null ? REPLJob.CALLBACK_PAUSE_ON_ERROR : callback.andThen(REPLJob.CALLBACK_PAUSE_ON_ERROR)
        if (autotune != null) callback = callback == null ? new REPLJobCallbackAutoTune(1, autotune) : callback.andThen(new REPLJobCallbackAutoTune(1, autotune))
        java.util.concurrent.Future<REPLJob.JobProgress> future = job.start(threadfactory, callback)
        if (!background) future.get()
    }
    return job
}