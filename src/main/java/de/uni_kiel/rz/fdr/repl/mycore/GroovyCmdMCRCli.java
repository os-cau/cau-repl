// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: GPL-3.0-only

package de.uni_kiel.rz.fdr.repl.mycore;

import groovy.lang.Binding;
import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;
import org.codehaus.groovy.tools.shell.IO;

import java.util.List;

public class GroovyCmdMCRCli extends CommandSupport {
    protected GroovyCmdMCRCli(Groovysh shell, String name, String shortcut) {
        super(shell, name, shortcut);
    }

    @Override
    public String getDescription() {
        return "Executes a MyCoRe CLI Command";
    }

    @Override
    public String getHelp() {
        return "Executes a MyCoRe CLI Command";
    }

    @Override
    public String getUsage() {
        return "[command]";
    }

    @Override
    public Object execute(List<String> list) {
        Binding binding;
        IO.Verbosity verb = shell.getIo().getVerbosity();
        shell.getIo().setVerbosity(IO.Verbosity.QUIET);
        try {
            binding = (Binding) shell.execute("binding");
        } finally {
            shell.getIo().setVerbosity(verb);
        }
        binding.setVariable("_CAUREPL_MCRCLICMD", String.join(" ", list));
        return shell.execute("mcrcli(_CAUREPL_MCRCLICMD)");
    }
}
