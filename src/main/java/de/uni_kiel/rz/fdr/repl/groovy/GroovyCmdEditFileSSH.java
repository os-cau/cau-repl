// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;

import java.util.List;

public class GroovyCmdEditFileSSH extends CommandSupport {

    protected GroovyCmdEditFileSSH(Groovysh shell, String name, String shortcut) {
        super(shell, name, shortcut);
    }

    @Override
    public String getDescription() {
        return "Edit a supplied file in the SSH terminal without evaluating it";
    }

    @Override
    public String getHelp() {
        return "Edit a file without evaluating it - this will use the SSH terminal and not the local terminal like the :edit command";
    }

    @Override
    public Object execute(List<String> list) {
        if (list.size() != 1) fail("the :editfilessh command requires exactly 1 parameter");

        GroovyCmdEditSSH editssh = (GroovyCmdEditSSH) shell.findCommand(":editssh");
        if (editssh == null) fail("Internal error: could find :editssh instance");
        assert editssh != null;
        return editssh.doExecute(list, false);
    }
}
