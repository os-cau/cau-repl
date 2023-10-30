// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPL;
import de.uni_kiel.rz.fdr.repl.StreamRedirector;
import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;
import org.codehaus.groovy.tools.shell.util.Preferences;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GroovyCmdEditSSH extends CommandSupport {
    private static final Pattern ARGS_RE = Pattern.compile("(\"[^\"]+\"|[^\\s\"]+)");
    private final InputStream in;
    private final OutputStream out;
    private final OutputStream err;
    private final  String[] env;


    @SuppressWarnings({"unchecked"})
    protected GroovyCmdEditSSH(Groovysh shell, String name, String shortcut) {
        super(shell, name, shortcut);
        in = (InputStream) shell.execute("_cauIn");
        out = (OutputStream) shell.execute("_cauOut");
        err = (OutputStream) shell.execute("_cauErr");
        Map<String, String> e = (Map<String, String>) shell.execute("_cauEnv");
        env = e.keySet().stream().map(k -> k + "=" + e.get(k)).toArray(String[]::new);
    }

    @Override
    public String getDescription() {
        return "Edit the current buffer or a supplied file in the SSH terminal and evaluate it";
    }

    @Override
    public String getHelp() {
        return "Edit the current buffer or a file and evaluate it - this will use the SSH terminal and not the local terminal like the :edit command";
    }

    @Override
    public String getUsage() {
        return "[] | [filename]";
    }

    @Override
    public Object execute(List<String> list) {
        return doExecute(list, true);
    }

    protected Object doExecute(List<String> list, boolean evaluate) {
        if (list.size() > 1) fail("the :editssh command does not accept more than 1 parameter");
        String editorCmd = Preferences.get("editorssh");
        if (editorCmd == null || editorCmd.isBlank()) fail("the editor preference is currently unset");

        String fileName = null;
        if (!list.isEmpty()) {
            Path p = Path.of(list.get(0));
            if (p.toString().contains("/") || p.toString().contains(".")) {
                // we preserve complex paths unchanged. resolve also works for absolute paths
                fileName = REPL.getWorkDir().toPath().resolve(p).toString();
            } else {
                fileName = REPL.getWorkDir().toPath().resolve("editbuffer-" + p + ".groovy").toString();
            }
        }

        File tmp = null;
        try {
            if (fileName == null) {
                // no file name was given: create a temporary file
                tmp = File.createTempFile("groovysh-buffer", ".groovy");
                tmp.deleteOnExit();
                fileName = tmp.getAbsolutePath();
                try (FileWriter fw = new FileWriter(tmp, StandardCharsets.UTF_8, true)) {
                    for (String cmd : getBuffer()) {
                        fw.write(cmd);
                        fw.write(System.lineSeparator());
                    }
                }
            }

            List<String> args = new ArrayList<>();
            assert editorCmd != null;
            // this is a very crude way of splitting quoted command-line parameters and should never be used on input
            // that is not under your control
            for (Matcher matcher = ARGS_RE.matcher(editorCmd); matcher.find(); ) {
                String g = matcher.group();
                if (g.startsWith("\"") && g.endsWith("\"")) g = g.substring(1, g.length() - 1);
                args.add(g);
            }
            args.add(fileName);
            Process proc = Runtime.getRuntime().exec(args.toArray(new String[0]), env);
            ExecutorService exsvc = StreamRedirector.asyncRedirect(proc, in, out, err);
            try { exsvc.awaitTermination(999, TimeUnit.DAYS); } catch (InterruptedException ignore) {}

            if (evaluate) {
                // feed the edited buffer to the repl
                File result = new File(fileName);
                if (result.isFile() && result.canRead()) {
                    shell.getBuffers().clearSelected();
                    try (BufferedReader br = new BufferedReader(new FileReader(fileName, StandardCharsets.UTF_8))) {
                        String line = br.readLine();
                        int linenum = 0;
                        while (line != null) {
                            linenum++;
                            try {
                                shell.execute(line);
                            } catch (Exception e) {
                                if (line.length() > 100) line = line.substring(0, 97) + "...";
                                PrintWriter pw = new PrintWriter(err, true, StandardCharsets.UTF_8);
                                e.printStackTrace(pw);
                                fail("Line " + linenum + ": " +  line + "\nError: " + e, e);
                            }
                            line = br.readLine();
                        }
                    }
                } else {
                    fail("Could not read editor buffer");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            if (tmp != null) tmp.delete();
        }

        return null;
    }
}
