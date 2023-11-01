// (C) Copyright 2023 Ove Sörensen
// SPDX-License-Identifier: MIT

package de.uni_kiel.rz.fdr.repl;

import de.uni_kiel.rz.fdr.repl.error.ObjectStoreInvalidException;

import java.io.*;
import java.util.Arrays;
import java.util.Iterator;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import static de.uni_kiel.rz.fdr.repl.REPLLog.*;

class AppendableObjectStore implements Iterator<Serializable>, AutoCloseable {

    public static final byte[] MAGIC = new byte[]{(byte) 0xAC, (byte) 0xED, (byte) 0xBE, (byte) 0xEF, (byte) 0xAF, (byte) 0xFE, (byte) 0xFE, (byte) 0xED};
    public static final int MAX_QUEUED_ITEMS = 100;

    private enum QUEUE_CONTROL { END }

    private final File path;
    private FileInputStream infile;
    private GZIPInputStream ingz;
    private DataInputStream in;
    private FileOutputStream outfile;
    private GZIPOutputStream outgz;
    private DataOutputStream out;
    private LinkedBlockingQueue<Serializable> queue;
    private Thread worker;
    private Integer nextLen = null;
    

    public AppendableObjectStore(File path) throws IOException, ObjectStoreInvalidException {
        this.path = path;
        if (this.path.isFile()) {
            infile = new FileInputStream(this.path);
            if (!Arrays.equals(MAGIC, infile.readNBytes(MAGIC.length))) throw new ObjectStoreInvalidException("bad magic");
            ingz = new GZIPInputStream(infile);
            in = new DataInputStream(ingz);
            outfile = null;
            outgz = null;
            out = null;
            queue = null;
            worker = null;
        } else {
            outfile = new FileOutputStream(this.path, false);
            outfile.write(MAGIC);
            outgz = new GZIPOutputStream(outfile, true);
            out = new DataOutputStream(outgz);
            queue = new LinkedBlockingQueue<>(MAX_QUEUED_ITEMS);
            infile = null;
            ingz = null;
            in = null;
            worker = new Thread(this::queueWorker);
            worker.setDaemon(false);
            worker.start();
        }
    }

    private void queueWorker() {
        while (true) {
            try {
                Serializable object = queue.take();
                if (object == QUEUE_CONTROL.END) {
                    if (TRACE || TRACE_JOBS) REPLLog.trace("queueWorker {}: END command received", path);
                    if (!queue.isEmpty()) REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: there were {} remaining items after termination", path, queue.size()), INTERNAL_LOG_TARGETS);
                    return;
                }
                try (ByteArrayOutputStream bytes = new ByteArrayOutputStream(1024)) {
                    try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
                        out.writeObject(object);
                    }
                    out.writeInt(bytes.size());
                    out.write(bytes.toByteArray());
                }
                out.flush();
            } catch (Exception e) {
                REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "Internal error in queueWorker {}: {}", path, e), INTERNAL_LOG_TARGETS);
                try { //noinspection BusyWait
                    Thread.sleep(5000); // throttle
                } catch (InterruptedException e2) {
                    REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.ERROR, "queueWorker {}: terminating, was interrupted {}", path, e2), INTERNAL_LOG_TARGETS);
                    return;
                }
            }
        }
    }

    public synchronized void writeObject(Serializable object) throws InterruptedException {
        if (worker == null) throw new ObjectStoreNotAvailableException("this store is not available for writing");
        queue.put(object);
    }

    @Override
    public synchronized void close() {
        try {
            if (worker != null) {
                try {
                    writeObject(QUEUE_CONTROL.END);
                    worker.join();
                } catch (InterruptedException ex) {
                    REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: interrupted during close", path), INTERNAL_LOG_TARGETS);
                }
            }
            if (out != null) try { out.close(); } catch (IOException e) { REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: error during close: {}", path, e), INTERNAL_LOG_TARGETS); }
            if (outgz != null) try { outgz.close(); } catch (IOException e) { REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: error during close: {}", path, e), INTERNAL_LOG_TARGETS); }
            if (outfile != null) try { outfile.close(); } catch (IOException e) { REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: error during close: {}", path, e), INTERNAL_LOG_TARGETS); }
            if (in != null) try { in.close(); } catch (IOException e) { REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: error during close: {}", path, e), INTERNAL_LOG_TARGETS); }
            if (ingz != null) try { ingz.close(); } catch (IOException e) { REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: error during close: {}", path, e), INTERNAL_LOG_TARGETS); }
            if (infile != null) try { infile.close(); } catch (IOException e) { REPLLog.log(new REPLLogEntry(REPLLogEntry.LOG_LEVEL.WARN, "queueWorker {}: error during close: {}", path, e), INTERNAL_LOG_TARGETS); }
        } finally {
            worker = null;
            queue = null;
            out = null;
            outgz = null;
            outfile = null;
            in = null;
            ingz = null;
            infile = null;
        }
    }

    @Override
    public synchronized boolean hasNext() {
        if (in == null) throw new ObjectStoreNotAvailableException("this store is not available for reading");
        if (nextLen != null) return true;
        try {
            nextLen = in.readInt();
            return true;
        } catch (EOFException e) {
            return false;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized Serializable next() {
        if (in == null) throw new ObjectStoreNotAvailableException("this store is not available for reading");
        try {
            int len = (nextLen == null) ? in.readInt() : nextLen;
            byte[] buffer = new byte[len];
            in.readFully(buffer);
            try (ByteArrayInputStream bytes = new ByteArrayInputStream(buffer); ObjectInputStream in = new ObjectInputStream(bytes)) {
                return (Serializable) in.readObject();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(path + ": class not found", e);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            nextLen = null;
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }

    protected static class ObjectStoreNotAvailableException extends RuntimeException {
        public ObjectStoreNotAvailableException(String message) {
            super(message);
        }
    }
}
