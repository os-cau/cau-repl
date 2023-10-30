package de.uni_kiel.rz.fdr.repl.groovy;

import de.uni_kiel.rz.fdr.repl.REPL;
import de.uni_kiel.rz.fdr.repl.REPLJob;
import org.apache.groovy.groovysh.CommandSupport;
import org.apache.groovy.groovysh.Groovysh;

import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class GroovyCmdPS extends CommandSupport {

    private static final HashMap<Integer, String> speedDial = new HashMap<>();
    private static final HashMap<String, Integer> speedDialInverse = new HashMap<>();
    private static final AtomicInteger speedDialCounter = new AtomicInteger(1);

    private final OutputStream out;

    protected GroovyCmdPS(Groovysh shell, String name, String shortcut) {
        super(shell, name, shortcut);
        out = (OutputStream) shell.execute("_cauOut");
    }

    @Override
    public String getDescription() {
        return "Lists or manages thread";
    }

    @Override
    public String getHelp() {
        return "Lists or manages thread";
    }

    @Override
    public String getUsage() {
        return "[] | all | kill [threadid...] | killforce [threadid...]";
    }

    @Override
    public Object execute(List<String> list) {
        if (list.isEmpty() || list.get(0).equals("all")) {
            // list command
            boolean all = !list.isEmpty() && list.get(0).equals("all");
            LinkedHashMap<Thread, List<StackTraceElement>> traces = Thread.getAllStackTraces().entrySet().stream()
                    .limit(10000)
                    .filter(e -> (all || e.getKey().getName().startsWith(REPL.THREAD_PREFIX)) && e.getValue().length > 0)
                    .limit(1000)
                    .sorted(Comparator.comparing(e -> e.getKey().getId()))
                    .collect(Collectors.toMap(e -> e.getKey(),
                                              e -> Arrays.asList(Arrays.copyOfRange(e.getValue(), 0, Math.min(3, e.getValue().length))),
                                              (v1, v2) -> { throw new RuntimeException("internal error: duplicate threads"); },
                                              LinkedHashMap::new)
                    );
            for (List<StackTraceElement> l : traces.values()) Collections.reverse(l);

            String s= traces.entrySet().stream()
                    .map(e -> "[" + e.getKey().getId() + "]" + (e.getKey().equals(Thread.currentThread()) ? " * " : " ") + e.getKey().getName() + "\n" +
                              e.getValue().stream().map(st -> "  " + st.getClassName() + "." + st.getMethodName()).collect(Collectors.joining("\n"))
                            )
                    .collect(Collectors.joining("\n"));

            return("\n" + s);
        }

        if (list.get(0).equals("kill") || list.get(0).equals("killforce")) {
            // kill command
            boolean force = list.get(0).equals("killforce");
            if (list.size() > 2) fail("the kill and killforce subcommands need at least 1 argument");
            PrintWriter pw = new PrintWriter(out, true, StandardCharsets.UTF_8);
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            for (String arg : list.subList(1, list.size())) {
                try {
                    long tid = Long.valueOf(arg);
                    Thread target = threads.stream().filter(t -> t.getId() == tid).findFirst().orElseThrow();
                    if (force) {
                        for (int i = 0; i < 10000000; i++) {
                            target.interrupt();
                        }
                    } else {
                        target.interrupt();
                    }
                } catch (Exception ex) {
                    pw.println("ERROR: " + arg + ": " + ex.getMessage());
                }
            }
            return null;
        }

        fail("unknown subcommand: " + list.get(0));

        // NOTREACHED
        return null;
    }
}
