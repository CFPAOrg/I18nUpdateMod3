package i18nupdatemod.core.net;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;
import java.util.Properties;
import java.util.concurrent.Semaphore;

/**
 * Shared V1/V2 source routing and in-flight HTTP limits, including response bodies.
 */
public final class ResourcePackHttp {
    private static final int BMCL_PARALLELISM = 64;
    private static final int I18N_PARALLELISM = 4;
    private static volatile ResourcePackHttp configured;

    private final String i18nBaseUrl;
    private final String bmclBaseUrl;
    private final Semaphore bmclRequests = new Semaphore(BMCL_PARALLELISM, true);
    private final Semaphore baseRequests = new Semaphore(I18N_PARALLELISM, true);

    public ResourcePackHttp(String i18nBaseUrl, String bmclBaseUrl) {
        this.i18nBaseUrl = root(i18nBaseUrl);
        this.bmclBaseUrl = bmclBaseUrl == null || bmclBaseUrl.isEmpty() ? null : root(bmclBaseUrl);
        try {
            httpUrl(this.i18nBaseUrl);
            if (this.bmclBaseUrl != null) {
                httpUrl(this.bmclBaseUrl);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid resource source URL", e);
        }
    }

    public static ResourcePackHttp configured() {
        ResourcePackHttp result = configured;
        if (result == null) {
            synchronized (ResourcePackHttp.class) {
                result = configured;
                if (result == null) {
                    configured = result = loadConfiguration();
                }
            }
        }
        return result;
    }

    private static ResourcePackHttp loadConfiguration() {
        Properties properties = new Properties();
        try (InputStream input = ResourcePackHttp.class.getResourceAsStream("/i18n-build.properties")) {
            if (input == null) throw new IOException("Missing bundled build configuration");
            properties.load(input);
            String base = properties.getProperty("assetBaseUrl");
            if (base == null || base.isEmpty()) throw new IOException("Missing assetBaseUrl in build configuration");
            return new ResourcePackHttp(base, properties.getProperty("bmclBaseUrl"));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot load bundled build configuration", e);
        }
    }

    public int parallelism() {
        return bmclBaseUrl == null ? I18N_PARALLELISM : BMCL_PARALLELISM;
    }

    public InputStream open(String relativePath) throws IOException {
        Objects.requireNonNull(relativePath, "relativePath");
        if (bmclBaseUrl != null) {
            try {
                return request(bmclBaseUrl + relativePath, bmclRequests);
            } catch (HttpStatusException failure) {
                if (failure.status < 500 || failure.status > 599) throw failure;
            }
        }
        return request(i18nBaseUrl + relativePath, baseRequests);
    }

    private InputStream request(String address, Semaphore limit) throws IOException {
        URL url = httpUrl(address);
        acquire(limit);
        HttpURLConnection connection = null;
        boolean transferred = false;
        try {
            connection = (HttpURLConnection) url.openConnection();
            connection.setConnectTimeout(3000);
            connection.setReadTimeout(33000);
            connection.setInstanceFollowRedirects(true);
            int status = connection.getResponseCode();
            if (status >= 400 && status <= 599) {
                discardBody(connection);
                throw new HttpStatusException(connection.getURL().toExternalForm(), status);
            }
            if (status < 200 || status >= 300) {
                throw new IOException("Unexpected HTTP " + status + ": " + connection.getURL());
            }
            InputStream input = new ResponseStream(connection.getInputStream(), connection, limit);
            transferred = true;
            return input;
        } catch (HttpStatusException failure) {
            // The error body has already been released; the caller decides whether to switch source.
            throw failure;
        } catch (IOException | RuntimeException failure) {
            if (connection != null) connection.disconnect();
            throw failure;
        } finally {
            if (!transferred) limit.release();
        }
    }

    private static URL httpUrl(String value) throws IOException {
        URL url = new URL(value);
        if (!"http".equalsIgnoreCase(url.getProtocol()) && !"https".equalsIgnoreCase(url.getProtocol())) {
            throw new MalformedURLException("Unsupported resource URL protocol: " + url.getProtocol());
        }
        return url;
    }

    private static String root(String value) {
        if (value == null || value.isEmpty()) throw new IllegalArgumentException("Missing resource source URL");
        return value.endsWith("/") ? value : value + "/";
    }

    private static void acquire(Semaphore limit) throws InterruptedIOException {
        try {
            limit.acquire();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException("Interrupted while waiting for resource source");
            interrupted.initCause(e);
            throw interrupted;
        }
    }

    // 据称能够优化底层复用
    private static void discardBody(HttpURLConnection connection) {
        try {
            InputStream body = connection.getErrorStream();
            if (body == null) {
                if (connection.getContentLengthLong() != 0) connection.disconnect();
                return;
            }
            try (InputStream input = new ResponseStream(body, connection, null)) {
                int remaining = 64 * 1024 + 1;
                byte[] buffer = new byte[1024];
                while (remaining > 0 && !Thread.currentThread().isInterrupted()) {
                    int count = input.read(buffer, 0, Math.min(buffer.length, remaining));
                    if (count < 0) return;
                    remaining -= count;
                }
            }
        } catch (IOException | RuntimeException ignored) {
            connection.disconnect();
            // A broken error body must not replace its original HTTP status.
        }
    }

    private static final class ResponseStream extends FilterInputStream {
        private final HttpURLConnection connection;
        private final Semaphore limit;
        private volatile boolean exhausted;
        private boolean closed;

        private ResponseStream(InputStream input, HttpURLConnection connection, Semaphore limit) {
            super(input);
            this.connection = connection;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            try {
                int value = in.read();
                if (value < 0) exhausted = true;
                return value;
            } catch (IOException | RuntimeException failure) {
                connection.disconnect();
                throw failure;
            }
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            try {
                int count = in.read(buffer, offset, length);
                if (count < 0) exhausted = true;
                return count;
            } catch (IOException | RuntimeException failure) {
                connection.disconnect();
                throw failure;
            }
        }

        @Override
        public synchronized void close() throws IOException {
            if (closed) return;
            closed = true;
            try {
                try {
                    if (!exhausted || Thread.currentThread().isInterrupted()) {
                        connection.disconnect();
                    }
                } finally {
                    try {
                        super.close();
                    } catch (IOException | RuntimeException failure) {
                        connection.disconnect();
                        throw failure;
                    }
                }
            } finally {
                if (limit != null) {
                    limit.release();
                }
            }
        }
    }

    public static final class HttpStatusException extends IOException {
        public final int status;

        private HttpStatusException(String url, int status) {
            super("HTTP " + status + ": " + url);
            this.status = status;
        }
    }
}
