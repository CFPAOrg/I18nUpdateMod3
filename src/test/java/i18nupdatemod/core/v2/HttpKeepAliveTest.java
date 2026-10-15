package i18nupdatemod.core.v2;

import i18nupdatemod.core.net.ResourcePackHttp;
import i18nupdatemod.util.DigestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpKeepAliveTest {
    @TempDir
    Path cache;

    @Test
    void consumedFixedLengthAndChunkedResponsesShareOneConnection() throws Exception {
        try (KeepAliveServer server = new KeepAliveServer()) {
            ResourcePackHttp source = new ResourcePackHttp(server.url(), null);
            manifest(source, "ok");
            manifest(source, "chunked");
            manifest(source, "ok");
            assertEquals(1, server.connections.get());
        }
    }

    @Test
    void smallErrorBodiesReuseConnectionWithoutChangingCacheOrFatalStatusPolicy() throws Exception {
        try (KeepAliveServer server = new KeepAliveServer()) {
            ResourcePackHttp source = new ResourcePackHttp(server.url(), null);
            manifest(source, "ok");
            Path missing = cached("missing");
            assertEquals(Collections.singletonList(missing), download(source, "missing"));
            Path gone = cached("gone");
            assertEquals(Collections.singletonList(gone), download(source, "gone"));
            assertThrows(IOException.class, () -> download(source, "fatal"));
            manifest(source, "ok");
            assertEquals(1, server.connections.get());

            // A malformed 500 body must still fail the pipeline, not become a skippable asset error.
            assertThrows(IOException.class, () -> download(source, "fatal-broken"));
            manifest(source, "ok");
            assertEquals(2, server.connections.get());
        }
    }

    @Test
    void largeErrorIsAbandonedBeforeTheNextRequest() throws Exception {
        try (KeepAliveServer server = new KeepAliveServer()) {
            ResourcePackHttp source = new ResourcePackHttp(server.url(), null);
            assertThrows(IOException.class, () -> ResourcePackDownloader.loadManifest(source, "large-error"));
            manifest(source, "ok");
            assertEquals(2, server.connections.get());
        }
    }

    @Test
    void parsingFailureClosesAnUnreadSuccessfulResponse() throws Exception {
        try (KeepAliveServer server = new KeepAliveServer()) {
            ResourcePackHttp source = new ResourcePackHttp(server.url(), null);
            assertThrows(IOException.class, () -> ResourcePackDownloader.loadManifest(source, "bad-json"));
            manifest(source, "ok");
            assertEquals(2, server.connections.get());
        }
    }

    private Path cached(String namespace) throws Exception {
        Path file = Files.createDirectories(cache.resolve("test/mods")).resolve(namespace + ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("assets/" + namespace + "/lang/zh_cn.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.write(file.resolveSibling(namespace + ".zipmd5"),
                DigestUtil.md5Hex(file).getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private java.util.List<Path> download(ResourcePackHttp source, String namespace) throws Exception {
        return ResourcePackDownloader.download("test", Collections.singletonMap(namespace, namespace),
                Collections.emptyList(), cache, source);
    }

    private static void manifest(ResourcePackHttp source, String version) throws IOException {
        assertEquals(Collections.singletonList("fixture"),
                ResourcePackDownloader.loadManifest(source, version).blackList);
    }

    private static final class KeepAliveServer implements AutoCloseable {
        private static final byte[] MANIFEST = "{\"blackList\":[\"fixture\"],\"rules\":{}}".getBytes(StandardCharsets.UTF_8);
        private final ServerSocket listener;
        private final AtomicInteger connections = new AtomicInteger();
        private final Set<Socket> sockets = Collections.newSetFromMap(new ConcurrentHashMap<Socket, Boolean>());
        private final ExecutorService workers = Executors.newCachedThreadPool(task -> {
            Thread thread = new Thread(task, "keep-alive-test-worker");
            thread.setDaemon(true);
            return thread;
        });
        private final Thread acceptor;
        private volatile boolean closed;

        private KeepAliveServer() throws IOException {
            listener = new ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"));
            acceptor = new Thread(() -> {
                while (!closed) {
                    try {
                        Socket socket = listener.accept();
                        sockets.add(socket);
                        connections.incrementAndGet();
                        workers.execute(() -> serve(socket));
                    } catch (IOException failure) {
                        if (!closed) throw new AssertionError(failure);
                    }
                }
            }, "keep-alive-test-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();
        }

        private String url() {
            return "http://127.0.0.1:" + listener.getLocalPort() + "/";
        }

        private void serve(Socket socket) {
            try (Socket connection = socket;
                 BufferedReader input = new BufferedReader(new InputStreamReader(
                         connection.getInputStream(), StandardCharsets.US_ASCII))) {
                connection.setSoTimeout(10000);
                String request;
                while ((request = input.readLine()) != null) {
                    String path = request.split(" ")[1];
                    String header;
                    while ((header = input.readLine()) != null && !header.isEmpty()) {
                        // Read the complete request before replying on the persistent connection.
                    }
                    respond(connection.getOutputStream(), path);
                }
            } catch (IOException ignored) {
                // The client deliberately disconnects on incomplete or malformed responses.
            } finally {
                sockets.remove(socket);
            }
        }

        private static void respond(OutputStream output, String path) throws IOException {
            int status = 200;
            byte[] body = MANIFEST;
            if (path.endsWith("/missing.md5")) status = 404;
            if (path.endsWith("/gone.md5")) status = 410;
            if (path.endsWith("/fatal.md5")) status = 500;
            if (path.contains("/chunked/") || path.endsWith("/fatal-broken.md5")) {
                boolean broken = path.endsWith("/fatal-broken.md5");
                output.write(("HTTP/1.1 " + (broken ? 500 : 200)
                        + " Fixture\r\nTransfer-Encoding: chunked\r\nConnection: keep-alive\r\n\r\n")
                        .getBytes(StandardCharsets.US_ASCII));
                if (broken) {
                    output.write("not-hex\r\n".getBytes(StandardCharsets.US_ASCII));
                } else {
                    output.write((Integer.toHexString(body.length) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                    output.write(body);
                    output.write("\r\n0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
                }
            } else {
                if (path.contains("/large-error/") || path.contains("/bad-json/")) {
                    status = path.contains("/large-error/") ? 404 : 200;
                    body = new byte[1024 * 1024];
                    body[0] = '}';
                    body[1] = '\n';
                }
                output.write(("HTTP/1.1 " + status + " Fixture\r\nContent-Length: " + body.length
                        + "\r\nConnection: keep-alive\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(body);
            }
            output.flush();
        }

        @Override
        public void close() throws Exception {
            closed = true;
            listener.close();
            acceptor.join(10000);
            workers.shutdownNow();
            for (Socket socket : sockets) socket.close();
            workers.awaitTermination(10, TimeUnit.SECONDS);
        }
    }
}
