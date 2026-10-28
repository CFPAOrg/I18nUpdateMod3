package i18nupdatemod.core.net;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LegacyFallbackTest {
    @Test
    void bmclStatusFailureStillTriesTheI18nMirrorBeforeGithub() throws Exception {
        try (Mirror bmcl = new Mirror(404, "not here");
             Mirror i18n = new Mirror(200, "i18n pack");
             Mirror github = new Mirror(200, "github pack")) {
            ResourcePackHttp source = new ResourcePackHttp(i18n.baseUrl(), bmcl.baseUrl(), github.baseUrl());
            assertEquals("i18n pack", read(source.openLegacy("pack.zip")));
            assertEquals(0, github.hits.get());
        }
    }

    @Test
    void bmclConnectionFailureStillTriesTheI18nMirrorBeforeGithub() throws Exception {
        Mirror bmcl = new Mirror(200, "unreachable");
        bmcl.close();
        try (Mirror i18n = new Mirror(200, "i18n pack");
             Mirror github = new Mirror(200, "github pack")) {
            ResourcePackHttp source = new ResourcePackHttp(i18n.baseUrl(), bmcl.baseUrl(), github.baseUrl());
            assertEquals("i18n pack", read(source.openLegacy("pack.zip")));
            assertEquals(0, github.hits.get());
        }
    }

    @Test
    void githubIsUsedOnlyAfterBothMirrorsFail() throws Exception {
        try (Mirror bmcl = new Mirror(500, "broken");
             Mirror i18n = new Mirror(404, "not here");
             Mirror github = new Mirror(200, "github pack")) {
            ResourcePackHttp source = new ResourcePackHttp(i18n.baseUrl(), bmcl.baseUrl(), github.baseUrl());
            assertEquals("github pack", read(source.openLegacy("pack.zip")));
        }
    }

    private static String read(InputStream input) throws IOException {
        try (InputStream stream = input) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static final class Mirror implements AutoCloseable {
        private final HttpServer server;
        private final int status;
        private final String body;
        private final AtomicInteger hits = new AtomicInteger();

        Mirror(int status, String body) throws IOException {
            this.status = status;
            this.body = body;
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                hits.incrementAndGet();
                byte[] payload = this.body.getBytes(StandardCharsets.UTF_8);
                try {
                    exchange.sendResponseHeaders(this.status, payload.length);
                    exchange.getResponseBody().write(payload);
                } finally {
                    exchange.close();
                }
            });
            server.start();
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
