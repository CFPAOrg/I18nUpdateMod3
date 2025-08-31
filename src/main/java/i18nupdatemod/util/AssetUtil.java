package i18nupdatemod.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class AssetUtil {
    private static final String CFPA_ASSET_ROOT = "http://downloader1.meitangdehulu.com:22943/";
    private static final List<String> MIRRORS;

    static {
        // 镜像地址可以改成服务器下发
        MIRRORS = new ArrayList<>();
        // MIRRORS.add("http://localhost:8080/");
        MIRRORS.add("https://raw.githubusercontent.com/");
    }

    public static void download(String url, Path localFile) throws IOException, URISyntaxException {
        Log.info("Downloading: %s -> %s", url, localFile);
        FileUtils.copyURLToFile(new URI(url).toURL(), localFile.toFile(),
                (int) TimeUnit.SECONDS.toMillis(3), (int) TimeUnit.SECONDS.toMillis(33));
        Log.debug("Downloaded: %s -> %s", url, localFile);
    }

    public static String getString(String url) throws IOException, URISyntaxException {
        return IOUtils.toString(new URI(url).toURL(), StandardCharsets.UTF_8);
    }

    public static String getFastestUrl() {
        List<String> urls = new ArrayList<>(MIRRORS);
        urls.add(CFPA_ASSET_ROOT);

        ExecutorService executor = Executors.newFixedThreadPool(Math.max(urls.size(), 10));
        try {
            List<CompletableFuture<String>> futures = new ArrayList<>();
            for (String url : urls) {
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        return testUrlConnection(url);
                    } catch (IOException e) {
                        return null; // 表示失败
                    }
                }, executor);
                futures.add(future);
            }

            // 阻塞等待最快完成且成功的任务
            String fastest = null;
            while (!futures.isEmpty()) {
                CompletableFuture<Object> first = CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0]));
                fastest = (String) first.join();

                // 移除已完成的 future
                futures.removeIf(CompletableFuture::isDone);

                if (fastest != null) {
                    // 成功，取消其他任务
                    for (CompletableFuture<String> f : futures) {
                        f.cancel(true);
                    }
                    Log.info("Using fastest url: %s", fastest);
                    return fastest;
                }
            }

            // 全部失败，返回默认 URL
            Log.info("All urls are unreachable, using CFPA_ASSET_ROOT");
            return CFPA_ASSET_ROOT;

        } finally {
            executor.shutdownNow();
        }
    }

    private static String testUrlConnection(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("HEAD");
        conn.setConnectTimeout(3000);
        conn.setReadTimeout(5000);
        conn.connect();
        int code = conn.getResponseCode();
        if (code >= 200 && code < 300) {
            return url;
        }
        Log.debug("URL unreachable: %s, code: %d", url, code);
        throw new IOException("URL unreachable: " + url);
    }

    @NotNull
    public static Map<String,String> getGitIndex(){
        try{
            URL index_url = new URL("https://raw.githubusercontent.com/CFPAOrg/Minecraft-Mod-Language-Package/refs/heads/main/version-index.json");
            HttpURLConnection httpConn = (HttpURLConnection) index_url.openConnection();
            httpConn.setRequestMethod("GET");
            httpConn.setConnectTimeout(5000);
            httpConn.setReadTimeout(5000);

            try (InputStreamReader reader = new InputStreamReader(httpConn.getInputStream())) {
                Type mapType = new TypeToken<Map<String, String>>(){}.getType();
                return new Gson().fromJson(reader, mapType);
            } finally {
                httpConn.disconnect();
            }
        } catch (Exception ignore) {
            return new HashMap<>();
        }
    }
}
