// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class StreamRedirector implements Runnable {

    private final InputStream inStream;
    private final OutputStream outStream;
    private final Process process;
    private final boolean closeIn;
    private final boolean closeOut;

    public static ExecutorService asyncRedirect(InputStream instream, OutputStream outstream, boolean closeIn, boolean closeOut) {
        ExecutorService exsvc = Executors.newSingleThreadExecutor();
        exsvc.submit(new StreamRedirector(instream, outstream, closeIn, closeOut));
        exsvc.shutdown();
        return exsvc;
    }

    public static ExecutorService asyncRedirect(Process proc, InputStream stdinSource, OutputStream stdoutTarget, OutputStream stderrTarget) {
        if (proc == null) throw new InvalidParameterException("proc may not be null");
        ExecutorService exsvc = Executors.newFixedThreadPool(4);
        final Future<?> stdinFuture = exsvc.submit(new StreamRedirector(stdinSource, proc.getOutputStream(), proc, false, true));
        exsvc.submit(new StreamRedirector(proc.getInputStream(), stdoutTarget, proc, true, false));
        exsvc.submit(new StreamRedirector(proc.getErrorStream(), stderrTarget, proc, true, false));
        // also spawn a process watchdog that terminates the redirector blocking on stdin
        exsvc.execute(() -> {
            try { proc.waitFor();} catch (InterruptedException ignore) {}
            stdinFuture.cancel(true);
        });
        exsvc.shutdown();
        return exsvc;
    }

    public StreamRedirector(InputStream inStream, OutputStream outStream, boolean closeIn, boolean closeOut) {
        this.inStream = inStream;
        this.outStream = outStream;
        this.process = null;
        this.closeIn = closeIn;
        this.closeOut = closeOut;
    }

    public StreamRedirector(InputStream inStream, OutputStream outStream, Process process, boolean closeIn, boolean closeOut) {
        this.inStream = inStream;
        this.outStream = outStream;
        this.process = process;
        this.closeIn = closeIn;
        this.closeOut = closeOut;
    }

    @Override
    public void run() {
        try {
            Byte peeked = null;
            while (true) {
                boolean procalive = (process == null || process.isAlive());
                byte[] data = null;
                int nread = 0;
                int avail = inStream.available();

                // do we have a stashed byte from last iteration's blocking read? write it now
                if (peeked != null) outStream.write(peeked);

                // read some bytes from input stream if it has some. will not block
                if (avail > 0) {
                    data = new byte[avail];
                    nread = inStream.read(data, 0, avail);
                }

                // did the last read have some data? pass it on now
                if (nread > 0) {
                    outStream.write(data, 0, nread);
                }

                // did we write anything? time to flush the output
                if (nread > 0 || peeked != null) {
                    outStream.flush();
                    peeked = null;
                }

                // nread == -1 -> eof
                if (nread < 0) return;

                // nothing was read and the process is dead? -> eof
                if (nread < 1 && !procalive) return;

                // we're not done yet: block until at least 1 new byte is available
                int i = inStream.read();
                if (i < 0) return; // eof
                peeked = (byte) i; // store the byte for the next iteration
            }
        } catch (IOException ignore) {
        } finally {
            if (closeIn) try { inStream.close(); } catch (IOException ignore) {}
            if (closeOut) try { outStream.close(); } catch (IOException ignore) {}
        }
    }
}
