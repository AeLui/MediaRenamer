package com.mediarenamer.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Only one window: a later start (files dropped on the exe or a shortcut, "Send to", the Explorer context menu) hands
 * its files to the window already open and exits. They talk through a Unix domain socket (Windows 10+, see
 * {@link #standard()}): no port, nothing reachable from the network. The files travel as one UTF-8 path per line,
 * after an optional {@code ?profile=<id>} line ({@code ?} cannot appear in a Windows path); the server answers "ok".
 * <p>
 * The context menu starts one process per selected file, all at once: a lock file elects the one that opens the
 * window, and the others keep trying to hand their files over until it listens.
 */
public final class SingleInstance {

    /** What a start asks the window for: files to add and, optionally, the profile to apply to them. */
    public record Request(List<File> files, String profile) {
        public Request {
            files = List.copyOf(files);
        }
    }

    private static final String OK = "ok";
    private static final String PROFILE = "?profile=";
    /** How long a start waits for the window being opened by another one before opening its own. */
    private static final long WAIT_MS = 20_000;

    private final Path socket;
    private final Path lockFile;
    /** Held while this process owns the window (released by the system when it exits). */
    private FileLock lock;

    SingleInstance(Path socket) {
        this.socket = socket;
        this.lockFile = socket.resolveSibling(socket.getFileName() + ".lock");
    }

    /**
     * One per data folder, in the temporary folder: socket paths are limited to ~100 characters, which a data folder
     * under a long profile path could exceed.
     */
    private static SingleInstance standard() {
        String data = AppFolders.data().toAbsolutePath().toString().toLowerCase(java.util.Locale.ROOT);
        String id = Integer.toHexString(data.hashCode());
        return new SingleInstance(Path.of(System.getProperty("java.io.tmpdir"), "MediaRenamer-" + id + ".sock"));
    }

    /** Keeps the lock of this process reachable while it runs. */
    private static SingleInstance current;

    /**
     * Hands the request to the open window (true: this process should exit) or makes this process the one that opens
     * it (false), listening for later starts that are delivered to {@code receiver}.
     */
    public static boolean handOff(Request request, Consumer<Request> receiver) {
        current = standard();
        return current.handOff(request, receiver, WAIT_MS);
    }

    boolean handOff(Request request, Consumer<Request> receiver, long waitMs) {
        long end = System.currentTimeMillis() + waitMs;
        do {
            if (send(request)) {
                return true;
            }
            if (tryLock()) {
                try {
                    start(receiver);
                } catch (Exception e) {
                    // e.g. sockets not supported: this start opens its window, later ones too
                    Logging.warn("Single instance: " + e.getMessage());
                }
                return false;
            }
            // another start owns the lock and is opening the window: wait for it to listen
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        } while (System.currentTimeMillis() < end);
        Logging.warn("Single instance: no answer from the window being opened, opening another one");
        return false;
    }

    boolean tryLock() {
        try {
            Files.createDirectories(lockFile.getParent());
            FileChannel ch = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
            FileLock l = ch.tryLock();
            if (l == null) {
                ch.close();
                return false;
            }
            lock = l;
            return true;
        } catch (OverlappingFileLockException e) {
            // already held in this JVM (tests)
            return false;
        } catch (IOException e) {
            // the lock cannot be used: behave as the only start
            Logging.warn("Single instance: " + e.getMessage());
            return true;
        }
    }

    boolean send(Request request) {
        if (!Files.exists(socket)) {
            return false;
        }
        try (SocketChannel ch = SocketChannel.open(UnixDomainSocketAddress.of(socket))) {
            OutputStream out = Channels.newOutputStream(ch);
            StringBuilder sb = new StringBuilder();
            if (request.profile() != null) {
                sb.append(PROFILE).append(request.profile()).append('\n');
            }
            for (File f : request.files()) {
                sb.append(f.getAbsolutePath()).append('\n');
            }
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            ch.shutdownOutput();
            BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
            return OK.equals(in.readLine());
        } catch (IOException e) {
            // nobody listening: the file is left over from a window that was closed abruptly
            return false;
        }
    }

    /** Binds the socket (replacing one left over by a previous run) and serves it on a daemon thread. */
    void start(Consumer<Request> receiver) throws IOException {
        Files.createDirectories(socket.getParent());
        Files.deleteIfExists(socket);
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(UnixDomainSocketAddress.of(socket));
        Thread t = new Thread(() -> serve(server, receiver), "single-instance");
        t.setDaemon(true);
        t.start();
    }

    private void serve(ServerSocketChannel server, Consumer<Request> receiver) {
        while (server.isOpen()) {
            try (SocketChannel ch = server.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(Channels.newInputStream(ch), StandardCharsets.UTF_8));
                List<File> files = new ArrayList<>();
                String profile = null;
                for (String line; (line = in.readLine()) != null;) {
                    if (line.startsWith(PROFILE)) {
                        profile = line.substring(PROFILE.length());
                    } else if (!line.isBlank()) {
                        files.add(new File(line));
                    }
                }
                OutputStream out = Channels.newOutputStream(ch);
                out.write((OK + "\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                receiver.accept(new Request(files, profile));
            } catch (Exception e) {
                Logging.warn("Single instance: " + e.getMessage());
            }
        }
    }
}
