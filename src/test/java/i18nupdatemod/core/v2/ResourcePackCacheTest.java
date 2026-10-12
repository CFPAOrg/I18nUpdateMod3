package i18nupdatemod.core.v2;

import com.sun.net.httpserver.HttpServer;
import i18nupdatemod.core.net.ResourcePackHttp;
import i18nupdatemod.util.DigestUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackCacheTest {
    // Real LZIP/TAR containing lang/zh_cn.json with the UTF-8 JSON below.
    private static final byte[] ARCHIVE = Base64.getDecoder().decode(
            "TFpJUAENADYYSiE1kjXv2/mEk2uKJ9rkqtHOQzPq+Na3yDSrY8DhjbxHTLnfMQpxgaJIpGuxufcopiOcQkALuDct+1I+jL4bzKaIOdZ0m+5+zrYXFnOJCFN1uaGrFFMEbQk3zXF+4Bax7j8VmooviJy2aFceDqGb8qacwbjeffnj/cVg+NPPw///hePgANXfj/8AKAAAAAAAAKIAAAAAAAAA");
    private static final byte[] TRANSLATION = "{\"message\":\"fresh translation\"}".getBytes(StandardCharsets.UTF_8);
    @TempDir
    Path cache;

    @Test
    void repairsChangedBytesEvenWithUnchangedSizeAndFreshTimestamp() throws Exception {
        try (Server server = new Server()) {
            Path zip = fetch(server);
            FileTime timestamp = Files.getLastModifiedTime(zip);
            byte[] corrupt = Files.readAllBytes(zip);
            corrupt[0] ^= 1;
            Files.write(zip, corrupt);
            Files.setLastModifiedTime(zip, timestamp);
            assertEquals(zip, fetch(server));
            assertTranslation(zip);
            assertEquals(2, server.archives.get());
            assertEquals(2, server.checksums.get());
            fetch(server);
            assertEquals(2, server.checksums.get(), "Verified fresh cache retains its network TTL");
        }
    }

    @Test
    void expiredMatchingSourceDigestCannotHideCorruptDecodedZip() throws Exception {
        try (Server server = new Server()) {
            Path zip = fetch(server);
            expire(zip);
            fetch(server);
            assertEquals(1, server.archives.get(), "A valid expired ZIP may still match the source digest");
            Files.write(zip, new byte[]{1, 2, 3});
            expire(zip);
            fetch(server);
            assertTranslation(zip);
            assertEquals(2, server.archives.get());
            assertEquals(3, server.checksums.get());
        }
    }

    @Test
    void missingDecodedDigestRedownloadsRatherThanSigningUnknownBytes() throws Exception {
        try (Server server = new Server()) {
            Path zip = fetch(server);
            Files.delete(zip.resolveSibling("fixture.zipmd5"));
            Files.write(zip, new byte[]{1, 2, 3});
            fetch(server);
            assertTranslation(zip);
            assertEquals(2, server.archives.get());
            fetch(server);
            assertEquals(2, server.checksums.get(), "Only the first migration requires redownloading");
        }
    }

    @Test
    void unavailableAssetRetainsVerifiedCacheButNeverReturnsCorruptCache() throws Exception {
        try (Server server = new Server()) {
            Path zip = fetch(server);
            expire(zip);
            server.status = 404;
            assertEquals(Collections.singletonList(zip), download(server, "fixture", Collections.emptyList()));
            assertTranslation(zip);
            Files.write(zip, new byte[]{1, 2, 3});
            assertTrue(download(server, "fixture", Collections.emptyList()).isEmpty());
            assertFalse(Files.exists(zip));
            assertFalse(Files.exists(zip.resolveSibling("fixture.zipmd5")));
        }
    }

    @Test
    void failedArchiveChecksumDoesNotReplacePreviouslyVerifiedCache() throws Exception {
        try (Server server = new Server()) {
            Path zip = fetch(server);
            byte[] original = Files.readAllBytes(zip);
            byte[] originalDigest = Files.readAllBytes(zip.resolveSibling("fixture.zipmd5"));
            expire(zip);
            server.badChecksum = true;
            assertEquals(zip, fetch(server));
            assertEquals(2, server.archives.get());
            assertArrayEquals(original, Files.readAllBytes(zip));
            assertArrayEquals(originalDigest, Files.readAllBytes(zip.resolveSibling("fixture.zipmd5")));
        }
    }

    @Test
    void blacklistDeletesBothDigestsAlongsideIdentityVariantZip() throws Exception {
        try (Server server = new Server()) {
            Path zip = download(server, "fixture-CFPA-Alice", Collections.emptyList()).get(0);
            Path decodedDigest = zip.resolveSibling("fixture-CFPA-Alice.zipmd5");
            Path sourceDigest = zip.resolveSibling("fixture-CFPA-Alice.md5");
            assertTrue(Files.isRegularFile(decodedDigest));
            assertTrue(download(server, "fixture-CFPA-Alice", Collections.singletonList("fixture")).isEmpty());
            assertFalse(Files.exists(zip));
            assertFalse(Files.exists(decodedDigest));
            assertFalse(Files.exists(sourceDigest));
            assertEquals(1, server.archives.get());
        }
    }

    @Test
    void namespaceEndingInZipDoesNotOverwriteAnotherCachesDigest() throws Exception {
        try (Server server = new Server()) {
            fetch(server);
            ResourcePackDownloader.download("test", Collections.singletonMap("fixture.zip", "second"),
                    Collections.emptyList(), cache, server.source);
            fetch(server);
            ResourcePackDownloader.download("test", Collections.singletonMap("fixture.zip", "second"),
                    Collections.emptyList(), cache, server.source);
            assertEquals(2, server.checksums.get(), "Both distinct namespaces must retain valid fresh caches");
            assertEquals(2, server.archives.get());
        }
    }

    private Path fetch(Server server) throws Exception {
        List<Path> paths = download(server, "fixture", Collections.emptyList());
        assertEquals(1, paths.size());
        return paths.get(0);
    }

    private List<Path> download(Server server, String namespace, List<String> blacklist) throws Exception {
        return ResourcePackDownloader.download("test", Collections.singletonMap(namespace, "fixture"),
                blacklist, cache, server.source);
    }

    private static void expire(Path file) throws Exception {
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - 172800000L));
    }

    private static void assertTranslation(Path file) throws Exception {
        try (ZipFile zip = new ZipFile(file.toFile());
             InputStream input = zip.getInputStream(zip.getEntry("assets/fixture/lang/zh_cn.json"))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            assertArrayEquals(TRANSLATION, output.toByteArray());
        }
    }

    private static final class Server implements AutoCloseable {
        final HttpServer server;
        final ResourcePackHttp source;
        final AtomicInteger archives = new AtomicInteger();
        final AtomicInteger checksums = new AtomicInteger();
        volatile int status = 200;
        volatile boolean badChecksum;

        Server() throws Exception {
            byte[] md5 = DigestUtil.hexString(MessageDigest.getInstance("MD5").digest(ARCHIVE))
                    .getBytes(StandardCharsets.US_ASCII);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 16);
            server.createContext("/", exchange -> {
                boolean checksum = exchange.getRequestURI().getPath().endsWith(".md5");
                byte[] body;
                if (checksum) {
                    checksums.incrementAndGet();
                    body = badChecksum ? "00000000000000000000000000000000".getBytes(StandardCharsets.US_ASCII) : md5;
                } else {
                    archives.incrementAndGet();
                    body = ARCHIVE;
                }
                try {
                    exchange.sendResponseHeaders(status, body.length);
                    exchange.getResponseBody().write(body);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            source = new ResourcePackHttp("http://127.0.0.1:" + server.getAddress().getPort() + "/", null);
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
