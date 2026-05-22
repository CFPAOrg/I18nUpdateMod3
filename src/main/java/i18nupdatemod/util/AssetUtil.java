package i18nupdatemod.util;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
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
import java.util.stream.Collectors;

public class AssetUtil {
    private static final String CFPA_ASSET_ROOT = "http://downloader1.meitangdehulu.com:22943/";
    private static final String GITHUB = "https://raw.githubusercontent.com/";
    private static final List<String> MIRRORS;

    static {
        // 镜像地址可以改成服务器下发
        MIRRORS = new ArrayList<>();
        // 此镜像源维护者：502y
        MIRRORS.add("http://8.137.167.65:64684/");
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
        // 海外用户直接用 GitHub，不测速
        if (!LocationDetectUtil.isMainlandChina()) {
            Log.info("Outside mainland China: Using GitHub source");
            return GITHUB;
        }

        // 中国大陆：测速选择最快的国内源
        Log.info("Inside mainland China: Testing mirrors...");
        List<String> urls = new ArrayList<>(MIRRORS);
        urls.add(CFPA_ASSET_ROOT);

        ExecutorService executor = Executors.newCachedThreadPool();
        try {
            String fastest = executor.invokeAny(
                urls.stream().map(url -> (Callable<String>) () -> testUrlConnection(url)).collect(Collectors.toList()),
                10, TimeUnit.SECONDS
            );
            executor.shutdownNow();
            Log.info("Using fastest url: %s", fastest);
            return fastest;
        } catch (Exception e) {
            executor.shutdownNow();
            Log.info("All sources unreachable, using CFPA_ASSET_ROOT as fallback");
            return CFPA_ASSET_ROOT;
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
    public static Map<String, String> getGitIndex() {
        try {
            URL index_url = new URL("https://raw.githubusercontent.com/CFPAOrg/Minecraft-Mod-Language-Package/refs/heads/index/version-index.json");
            HttpURLConnection httpConn = (HttpURLConnection) index_url.openConnection();
            httpConn.setRequestMethod("GET");
            httpConn.setConnectTimeout(5000);
            httpConn.setReadTimeout(5000);

            try (InputStreamReader reader = new InputStreamReader(httpConn.getInputStream())) {
                Type mapType = new TypeToken<Map<String, String>>() {
                }.getType();
                return new Gson().fromJson(reader, mapType);
            } finally {
                httpConn.disconnect();
            }
        } catch (Exception ignore) {
            return new HashMap<>();
        }
    }
}
