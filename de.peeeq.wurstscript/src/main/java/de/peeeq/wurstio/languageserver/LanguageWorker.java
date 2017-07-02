package de.peeeq.wurstio.languageserver;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import de.peeeq.wurstio.languageserver.requests.UserRequest;
import de.peeeq.wurstscript.WLogger;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.services.LanguageClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 *
 */
public class LanguageWorker implements Runnable {

    private final Map<WFile, PendingChange> changes = new LinkedHashMap<>();
    private final AtomicLong currentTime = new AtomicLong();
    private final Queue<UserRequest<?>> userRequests = new LinkedList<>();
    private final List<String> defaultArgs = ImmutableList.of("-runcompiletimefunctions", "-injectobjects",
            "-stacktraces");
    private final Thread thread;

    private ModelManager modelManager;

    public void setRootPath(WFile rootPath) {
        this.rootPath = rootPath;
    }

    private WFile rootPath;

    private final Object lock = new Object();
    private int initRequestSequenceNr = -1;
    private BufferManager bufferManager = new BufferManager();
    private LanguageClient languageClient;

    public LanguageWorker() {
        // start working
        thread = new Thread(this);
        thread.setName("Wurst LanguageWorker");
        thread.start();
    }


    private List<String> getCompileArgs() {
        try {
            Path configFile = Paths.get(rootPath.toString(), "wurst_run.args");
            if (Files.exists(configFile)) {
                return Files.lines(configFile).collect(Collectors.toList());
            } else {

                String cfg = String.join("\n", defaultArgs) + "\n";
                Files.write(configFile, cfg.getBytes(Charsets.UTF_8));
                return defaultArgs;
            }
        } catch (IOException e) {
            throw new RuntimeException("Could not access wurst run config file", e);
        }
    }

    public BufferManager getBufferManager() {
        return bufferManager;
    }

    public void setLanguageClient(LanguageClient languageClient) {
        this.languageClient = languageClient;
    }

    public void stop() {
        thread.interrupt();
    }


//    public void handleRuntests(int sequenceNr, String filename, int line, int column) {
//        synchronized (lock) {
//            userRequests.add(new RunTests(sequenceNr, server, filename, line, column));
//            lock.notifyAll();
//        }
//
//    }

    abstract class PendingChange {
        private long time;
        private WFile filename;

        public PendingChange(WFile filename) {
            time = currentTime.incrementAndGet();
            this.filename = filename;
        }

        public long getTime() {
            return time;
        }

        public WFile getFilename() {
            return filename;
        }
    }

    class FileUpdated extends PendingChange {

        public FileUpdated(WFile filename) {
            super(filename);
        }
    }

    class FileDeleted extends PendingChange {

        public FileDeleted(WFile filename) {
            super(filename);
        }
    }

    class FileReconcile extends PendingChange {

        private String contents;

        public FileReconcile(WFile filename, String contents) {
            super(filename);
            this.contents = contents;
        }

        public String getContents() {
            return contents;
        }
    }

    @Override
    public void run() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Runnable work;
                synchronized (lock) {
                    work = getNextWorkItem();
                    if (work == null) {
                        lock.wait(10000);
                        work = getNextWorkItem();
                    }
                }
                if (work != null) {
                    // actual work is not synchronized, so that requests can
                    // come in while the work is done
                    try {
                        work.run();
                    } catch (Exception e) {
                        WLogger.severe(e);
                        System.err.println("Error in request " + work + " (see log for details): " + e.getMessage());
                    }
                }
            }
        } catch (InterruptedException e) {
            // ignore
        }
        WLogger.info("Language Worker interrupted");
    }

    private Runnable getNextWorkItem() {
        if (modelManager == null) {
            if (rootPath != null) {
                WLogger.info("LanguageWorker start init");
                return () -> doInit(rootPath);
            } else {
                // cannot do anything useful at the moment
                WLogger.info("LanguageWorker is waiting for init ... ");
            }
        } else if (!userRequests.isEmpty()) {
            return () -> {
                UserRequest<?> req = userRequests.remove();
                req.run(modelManager);
            };
        } else if (!changes.isEmpty()) {
            // TODO this can be done more efficiently than doing one at a time
            PendingChange change = removeFirst(changes);
            return () -> {
                if (change.getFilename().getFile().getName().endsWith("wurst.dependencies")) {
                    if (!(change instanceof FileReconcile)) {
                        modelManager.clean();
                    }
                } else if (change instanceof FileDeleted) {
                    modelManager.removeCompilationUnit(change.getFilename());
                } else if (change instanceof FileUpdated) {
                    modelManager.syncCompilationUnit(change.getFilename());
                } else if (change instanceof FileReconcile) {
                    FileReconcile fr = (FileReconcile) change;
                    modelManager.syncCompilationUnitContent(fr.getFilename(), fr.getContents());
                } else {
                    WLogger.info("unhandled change request: " + change);
                }
            };
        }
        return null;
    }

    private PendingChange removeFirst(Map<WFile, PendingChange> changes) {
        Iterator<Map.Entry<WFile, PendingChange>> it = changes.entrySet().iterator();
        Map.Entry<WFile, PendingChange> e = it.next();
        it.remove();
        return e.getValue();
    }

    private void doInit(WFile rootPath) {
        try {
            log("Handle init " + rootPath);
            modelManager = new ModelManagerImpl(rootPath.getFile(), bufferManager);
            modelManager.onCompilationResult(this::onCompilationResult);

            log("Start building " + rootPath);
            modelManager.buildProject();

            log("Finished building " + rootPath);
            // TODO
//            server.reply(initRequestSequenceNr, "done");
        } catch (Exception e) {
            WLogger.severe(e);
        }
    }


    private void onCompilationResult(PublishDiagnosticsParams compilationResult) {
        languageClient.publishDiagnostics(compilationResult);
    }

    private void log(String s) {
        WLogger.info(s);
    }


    public void handleFileChanged(DidChangeWatchedFilesParams params) {
        synchronized (lock) {
            for (FileEvent fileEvent : params.getChanges()) {
                bufferManager.handleFileChange(fileEvent);

                WFile file = WFile.create(fileEvent.getUri());
                if (fileEvent.getType() == FileChangeType.Deleted) {
                    changes.put(file, new FileDeleted(file));
                } else {
                    changes.put(file, new FileUpdated(file));
                }
            }
            lock.notifyAll();
        }
    }

    public void handleChange(DidChangeTextDocumentParams params) {
        synchronized (lock) {
            bufferManager.handleChange(params);
            WFile file = WFile.create(params.getTextDocument().getUri());

            changes.put(file, new FileReconcile(file, bufferManager.getBuffer(params.getTextDocument())));
            lock.notifyAll();
        }
    }

    public <Res> CompletableFuture<Res> handle(UserRequest<Res> request) {
        synchronized (lock) {
            if (!request.keepDuplicateRequests()) {
                Iterator<UserRequest<?>> it = userRequests.iterator();
                while (it.hasNext()) {
                    UserRequest<?> o = it.next();
                    if (it.getClass().equals(request.getClass())) {
                        o.cancel();
                        it.remove();
                    }
                }
            }
            userRequests.add(request);
            lock.notifyAll();
            return request.getFuture();
        }
    }
}
