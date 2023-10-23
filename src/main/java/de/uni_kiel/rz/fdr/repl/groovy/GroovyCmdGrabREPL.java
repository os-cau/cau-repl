// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import groovy.grape.Grape;
import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;
import org.codehaus.groovy.tools.GrapeUtil;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class GroovyCmdGrabREPL extends CommandSupport {

    private final OutputStream err;


    protected GroovyCmdGrabREPL(Groovysh shell, String name, String shortcut) {
        super(shell, name, shortcut);
        err = (OutputStream) shell.execute("_cauErr");
    }

    @Override
    public String getDescription() {
        return "Loads java packages into the REPL classloader";
    }

    @Override
    public String getHelp() {
        return "Loads java packages into the REPL classloader - this will use the REPL classloader unlike the :grab command";
    }

    @Override
    public String getUsage() {
        return "[group[:module[:version|*[:classifier]]]][@ext]";
    }

    @Override
    public Object execute(List<String> list) {
        try {
            for (String dep : list) {
                Grape.grab(new HashMap<>(Map.of("classLoader", this.getClassLoader())), GrapeUtil.getIvyParts(dep));
            }
        } catch (Exception e) {
            e.printStackTrace(new PrintStream(err));
            throw e;
        }

        return null;
    }
}
