// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

println("${de.uni_kiel.rz.fdr.repl.REPL.versionString} initializing...")

:register de.uni_kiel.rz.fdr.repl.groovy.GroovyCmdEditSSH :editssh :E
:register de.uni_kiel.rz.fdr.repl.groovy.GroovyCmdEditFileSSH :editfilessh :EF
:register de.uni_kiel.rz.fdr.repl.groovy.GroovyCmdGrabREPL :grabrepl :G
:register de.uni_kiel.rz.fdr.repl.groovy.GroovyCmdBreakpoint :breakpoint :B
:register de.uni_kiel.rz.fdr.repl.groovy.GroovyCmdJob :job :J

_cauIn = null
_cauEnv = null