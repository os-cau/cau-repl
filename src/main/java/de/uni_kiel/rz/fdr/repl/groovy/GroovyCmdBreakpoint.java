// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPLBreakpoint;
import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;

import java.util.List;
import java.util.stream.Collectors;


public class GroovyCmdBreakpoint extends CommandSupport {

    protected GroovyCmdBreakpoint(Groovysh shell, String name, String shortcut) {
        super(shell, name, shortcut);
    }

    @Override
    public String getDescription() {
        return "Lists or resumes breakpoints";
    }

    @Override
    public String getHelp() {
        return "Lists or resumes breakpoints";
    }

    @Override
    public String getUsage() {
        return "[] | [key [feedback]] | disable [pattern] | enable [pattern] | eval [key] [code] | eval [key] | max [limit]";
    }

    @Override
    public Object execute(List<String> list) {
        if (list.isEmpty()) {
            // list command
            String b = REPLBreakpoint.list().stream().map(REPLBreakpoint::toString).collect(Collectors.joining("\n"));
            String d = String.join("\n", REPLBreakpoint.getDisabledPatterns());
            if (!d.isEmpty()) d = "\nDisabled Patterns:\n" + d;
            return("\n" + b + d);
        }

        if (list.get(0).matches("^[0-9]+$")) {
            // resume command
            int i = Integer.parseInt(list.get(0));
            String feedback = list.size() > 1 ? String.join(" ", list.subList(1, list.size())) : null;
            if (!REPLBreakpoint.resume(i, feedback)) fail("no such breakpoint");
            return null;
        }

        switch(list.get(0)) {
            case "max" -> {
                if (list.size() != 2) fail("the max command needs exactly 1 argument");
                REPLBreakpoint.MAX_BREAKPOINTS = Integer.parseInt(list.get(1));
            }
            case "disable" -> REPLBreakpoint.disable(String.join(" ", list.subList(1, list.size())));
            case "enable" -> REPLBreakpoint.enable(String.join(" ", list.subList(1, list.size())));
            case "eval" -> {
                if (list.size() < 2) fail("the eval commands needs at least a breakpoint number");
                if (list.size() == 2) {
                    // get result
                    REPLBreakpoint bp = REPLBreakpoint.get(Long.parseLong(list.get(1)));
                    if (bp == null) fail("no such breakpoint");
                    assert bp != null;
                    return bp.getEvalResult();
                } else {
                    // eval
                    REPLBreakpoint.eval(Long.parseLong(list.get(1)), new REPLBreakpoint.Eval(String.join(" ", list.subList(2, list.size())), null));
                }
            }
            default -> fail("unknown subcommand: " + list.get(0));
        }

        return null;
    }
}
