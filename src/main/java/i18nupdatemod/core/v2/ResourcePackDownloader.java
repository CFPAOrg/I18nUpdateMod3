package i18nupdatemod.core.v2;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import i18nupdatemod.core.net.ResourcePackHttp;
import i18nupdatemod.entity.ModTranslation;
import i18nupdatemod.util.DigestUtil;
import i18nupdatemod.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionService;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

public class ResourcePackDownloader {
    private static final Gson GSON = new Gson();
    private static final long UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(1);
    private static final long ICON_UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(30);

    public static Manifest loadManifest(ResourcePackHttp source, String version) throws IOException {
        requireSource(source);
        String path = encode(version) + "/Manifest.json";
        try (InputStream input = source.open(path)) {
            return parseManifest(input);
        }
    }

    private static Manifest parseManifest(InputStream input) throws IOException {
        final JsonObject json;
        try {
            JsonElement root = GSON.fromJson(new InputStreamReader(input, StandardCharsets.UTF_8), JsonElement.class);
            if (root == null || !root.isJsonObject()) {
                throw new IOException("Manifest root must be an object");
            }
            json = root.getAsJsonObject();
        } catch (JsonParseException | IllegalStateException e) {
            throw new IOException("Invalid manifest JSON", e);
        }

        Manifest result = new Manifest();
        if (!json.has("blackList") || !json.has("rules")) {
            throw new IOException("Manifest must contain blackList and rules");
        }
        JsonElement blackList = json.get("blackList");
        if (blackList != null) {
            if (!blackList.isJsonArray()) {
                throw new IOException("Manifest blackList must be an array");
            }
            for (JsonElement item : blackList.getAsJsonArray()) {
                if (!item.isJsonPrimitive() || !item.getAsJsonPrimitive().isString()) {
                    throw new IOException("Manifest blackList must contain strings");
                }
                result.blackList.add(item.getAsString());
            }
        }

        JsonElement rules = json.get("rules");
        if (rules != null) {
            if (!rules.isJsonObject()) {
                throw new IOException("Manifest rules must be an object");
            }
            for (Map.Entry<String, JsonElement> entry : rules.getAsJsonObject().entrySet()) {
                JsonElement value = entry.getValue();
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                    throw new IOException("Manifest rules must map strings to strings");
                }
                result.rules.put(entry.getKey(), value.getAsString());
            }
        }
        return result;
    }

    /**
     * 解析Manifest，根据规则排除模组、生成NS
     */
    public static Map<String, String> selectNamespaces(List<ModTranslation> mods, Manifest manifest) {
        if (mods == null) {
            return new LinkedHashMap<>();
        }
        if (manifest == null) {
            throw new NullPointerException("manifest");
        }

        List<String> blackList = manifest.blackList == null
                ? Collections.emptyList() : manifest.blackList;
        Map<String, String> rules = manifest.rules == null
                ? Collections.emptyMap() : manifest.rules;
        Map<String, String> selected = new LinkedHashMap<>();
        for (ModTranslation mod : mods) {
            if (mod == null) {
                continue;
            }
            String rawNamespace = mod.namespace;
            if (rawNamespace == null || !rawNamespace.matches("[a-z0-9_.-]+")) {
                Log.warning("Invalid translation namespace: %s", rawNamespace);
                continue;
            }
            if (blackList.contains(rawNamespace)) {
                continue;
            }

            String namespace = resolveNamespace(mod, rules.get(rawNamespace));
            if (selected.containsKey(namespace)) {
                // 太多了，没事别看
                //Log.debug("Duplicate namespace %s, rawNamespace %s", namespace, rawNamespace);
                continue;
            }
            selected.put(namespace, rawNamespace);
        }
        return selected;
    }

    /**
     * 下载新流程的资源
     *
     * <p>NS不存在、下载失败、MD5不匹配、解压失败时跳过更新，已有缓存仍参与组包。其他错误回滚到旧流程。</p>
     */
    public static List<Path> download(String version, Map<String, String> namespaces,
                                      List<String> blackList, Path cacheRoot,
                                      ResourcePackHttp source) throws IOException, NoSuchAlgorithmException {
        requireSource(source);
        if (namespaces == null) {
            throw new NullPointerException("namespaces");
        }
        if (cacheRoot == null) {
            throw new NullPointerException("cacheRoot");
        }

        List<String> blocked = blackList == null
                ? Collections.emptyList() : blackList;
        // Minecraft's baseline fixes are not optional mod translations.
        if (blocked.contains("minecraft")) {
            blocked = new ArrayList<>(blocked);
            blocked.removeIf("minecraft"::equals);
        }
        Path modCache = cacheRoot.resolve(version).resolve("mods");
        Files.createDirectories(modCache);
        deleteBlacklisted(modCache, blocked);

        String versionPath = encode(version) + "/";
        List<DownloadRequest> requests = new ArrayList<>();
        for (Map.Entry<String, String> entry : namespaces.entrySet()) {
            String namespace = entry.getKey();
            String rawNamespace = entry.getValue();
            if (namespace == null || rawNamespace == null
                    || blocked.contains(namespace) || blocked.contains(rawNamespace)) {
                continue;
            }

            Path cached = modCache.resolve(encode(namespace) + ".zip");
            Path md5File = modCache.resolve(encode(namespace) + ".md5");
            String assetPath = versionPath + "assets/" + encode(namespace);
            requests.add(new DownloadRequest(
                    version, namespace, rawNamespace, cached, md5File, assetPath, source));
        }
        if (requests.isEmpty()) {
            return new ArrayList<>();
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(source.parallelism(), requests.size()),
                downloadThreadFactory());
        CompletionService<Path> completions = new ExecutorCompletionService<>(executor);
        List<Future<Path>> futures = new ArrayList<>(requests.size());
        List<Path> sourcePaths = new ArrayList<>(requests.size());
        try {
            for (DownloadRequest request : requests) {
                futures.add(completions.submit(() -> downloadOne(request)));
            }
            executor.shutdown();

            for (int completed = 0; completed < requests.size(); completed++) {
                Path cachedSource = completions.take().get();
                if (cachedSource != null) {
                    sourcePaths.add(cachedSource);
                }
            }
            awaitTermination(executor);

            return sourcePaths;
        } catch (InterruptedException e) {
            cancelAndJoin(executor, futures);
            Thread.currentThread().interrupt();
            InterruptedIOException interrupted = new InterruptedIOException(
                    "Interrupted while downloading translations");
            interrupted.initCause(e);
            throw interrupted;
        } catch (ExecutionException e) {
            cancelAndJoin(executor, futures);
            return rethrowTaskFailure(e.getCause());
        } catch (RuntimeException e) {
            cancelAndJoin(executor, futures);
            throw e;
        } catch (Error e) {
            cancelAndJoin(executor, futures);
            throw e;
        }
    }

    private static Path downloadOne(DownloadRequest request)
            throws IOException, NoSuchAlgorithmException {
        ensureWorkerNotInterrupted();
        try {
            updateMod(request.source, request.assetPath, request.rawNamespace,
                    request.cached, request.md5File);
        } catch (ResourcePackHttp.HttpStatusException e) {
            if (e.status == 404 || e.status == 410) {
                // 太多了，没事别看（
                //Log.debug("No exact translation asset for %s/%s; keeping local cache if present", version, namespace);
            } else {
                Log.warning("Translation asset %s/%s returned HTTP %s; aborting new pipeline",
                        request.version, request.namespace, e.status);
                throw e;
            }
        } catch (AssetFailure e) {
            Log.warning("Failed to update translation %s; keeping local cache if present: %s",
                    request.namespace, e.getMessage());
        }
        ensureWorkerNotInterrupted();
        return Files.isRegularFile(request.cached) ? request.cached : null;
    }

    private static void ensureWorkerNotInterrupted() throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted()) {
            throw new InterruptedIOException("Translation download interrupted");
        }
    }

    private static ThreadFactory downloadThreadFactory() {
        return new ThreadFactory() {
            private int nextId;

            @Override
            public synchronized Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable,
                        "i18nupdatemod-v2-download-" + (++nextId));
                thread.setDaemon(false);
                return thread;
            }
        };
    }

    private static void awaitTermination(ExecutorService executor) throws InterruptedException {
        while (!executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS)) {
            // A fixed executor with a finite submission set eventually terminates.
        }
    }

    private static void cancelAndJoin(ExecutorService executor,
                                      List<? extends Future<?>> futures) {
        for (Future<?> future : futures) {
            future.cancel(true);
        }
        executor.shutdownNow();
        boolean interrupted = false;
        while (!executor.isTerminated()) {
            try {
                executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static List<Path> rethrowTaskFailure(Throwable failure)
            throws IOException, NoSuchAlgorithmException {
        if (failure instanceof IOException) {
            throw (IOException) failure;
        }
        if (failure instanceof NoSuchAlgorithmException) {
            throw (NoSuchAlgorithmException) failure;
        }
        if (failure instanceof RuntimeException) {
            throw (RuntimeException) failure;
        }
        if (failure instanceof Error) {
            throw (Error) failure;
        }
        throw new IOException("Translation download failed", failure);
    }

    private static final class DownloadRequest {
        final String version;
        final String namespace;
        final String rawNamespace;
        final Path cached;
        final Path md5File;
        final String assetPath;
        final ResourcePackHttp source;

        DownloadRequest(String version, String namespace, String rawNamespace, Path cached,
                        Path md5File, String assetPath, ResourcePackHttp source) {
            this.version = version;
            this.namespace = namespace;
            this.rawNamespace = rawNamespace;
            this.cached = cached;
            this.md5File = md5File;
            this.assetPath = assetPath;
            this.source = source;
        }
    }
    public static Path downloadIcon(ResourcePackHttp source, String version, Path cacheRoot) {
        Path cached = cacheRoot.resolve("shared").resolve("pack.png");
        Path temporary = null;
        try {
            if (Files.isRegularFile(cached)
                    && Files.getLastModifiedTime(cached).toMillis() > System.currentTimeMillis() - ICON_UPDATE_TIME_GAP) {
                return cached;
            }
            requireSource(source);
            Files.createDirectories(cached.getParent());
            temporary = Files.createTempFile(cached.getParent(), "pack-icon-", ".tmp");
            try (InputStream input = source.open(encode(version) + "/pack.png")) {
                Files.copy(input, temporary, StandardCopyOption.REPLACE_EXISTING);
            }
            byte[] signature = new byte[]{(byte) 137, 80, 78, 71, 13, 10, 26, 10};
            try (InputStream input = Files.newInputStream(temporary)) {
                for (byte expected : signature) {
                    if (input.read() != (expected & 255)) throw new IOException("Invalid pack.png signature");
                }
            }
            Files.move(temporary, cached, StandardCopyOption.REPLACE_EXISTING);
            Log.info("Updated shared resource pack icon: %s", cached);
        } catch (Exception e) {
            Log.warning("Failed to update resource pack icon; retaining cached icon if present: %s", e);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException e) {
                    Log.warning("Failed to remove temporary icon %s: %s", temporary, e);
                }
            }
        }
        return Files.isRegularFile(cached) ? cached : null;
    }

    private static String resolveNamespace(ModTranslation mod, String identifier) {
        if (identifier == null) {
            return mod.namespace;
        }
        try {
            String value;
            if ("author".equals(identifier)) {
                value = mod.author;
            } else if ("displayName".equals(identifier)) {
                value = mod.displayName;
            } else {
                value = ModIdentity.getFileMd5(mod, identifier);
            }
            if (value != null && !value.trim().isEmpty()) {
                return mod.namespace + "-CFPA-" + value;
            }
        } catch (Exception e) {
            Log.warning("Cannot identify translation %s using %s; using raw namespace: %s",
                    mod.namespace, identifier, e);
        }
        return mod.namespace;
    }

    private static void updateMod(ResourcePackHttp source, String assetPath, String rawNamespace,
                                  Path cached, Path md5File)
            throws IOException, AssetFailure, NoSuchAlgorithmException {
        if (Files.isRegularFile(cached) && Files.isRegularFile(md5File)
                && Files.getLastModifiedTime(cached).toMillis()
                > System.currentTimeMillis() - UPDATE_TIME_GAP) {
            return;
        }

        String remoteMd5 = readRemoteText(source, assetPath + ".md5").trim();
        if (!remoteMd5.matches("[0-9a-fA-F]{32}")) {
            throw new AssetFailure("Invalid asset MD5: " + assetPath);
        }
        if (Files.isRegularFile(cached) && Files.isRegularFile(md5File)
                && remoteMd5.equalsIgnoreCase(
                new String(Files.readAllBytes(md5File), StandardCharsets.UTF_8).trim())) {
            return;
        }

        Path archive = Files.createTempFile(cached.getParent(), "translation-", ".tar.lzma");
        Path decoded = Files.createTempFile(cached.getParent(), "translation-", ".zip.tmp");
        try {
            downloadRemote(source, assetPath + ".tar.lzma", archive);
            if (!remoteMd5.equalsIgnoreCase(DigestUtil.md5Hex(archive))) {
                throw new AssetFailure("Download MD5 not match: " + assetPath);
            }
            try {
                TranslationArchive.unpack(archive, decoded, rawNamespace);
            } catch (TranslationArchive.LocalIoException e) {
                throw e;
            } catch (IOException | RuntimeException e) {
                throw new AssetFailure("Failed to decode translation archive: " + assetPath, e);
            }

            Files.move(decoded, cached, StandardCopyOption.REPLACE_EXISTING);
            Files.write(md5File, remoteMd5.getBytes(StandardCharsets.UTF_8));
        } finally {
            Files.deleteIfExists(archive);
            Files.deleteIfExists(decoded);
        }
    }
    private static String readRemoteText(ResourcePackHttp source, String path)
            throws IOException, AssetFailure {
        InputStream input = fetchAsset(source, path);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            while (true) {
                int count;
                try {
                    count = input.read(buffer);
                } catch (IOException e) {
                    throw new AssetFailure("Failed to read " + path, e);
                }
                if (count < 0) {
                    break;
                }
                if (count > 0) {
                    output.write(buffer, 0, count);
                }
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                throw new AssetFailure("Failed to close " + path, e);
            }
        }
    }

    private static void downloadRemote(ResourcePackHttp source, String path, Path destination)
            throws IOException, AssetFailure {
        InputStream input = fetchAsset(source, path);
        OutputStream output = null;
        IOException localFailure = null;
        AssetFailure assetFailure = null;
        try {
            try {
                output = Files.newOutputStream(destination);
            } catch (IOException e) {
                localFailure = e;
            }
            if (output != null) {
                try {
                    byte[] buffer = new byte[32 * 1024];
                    while (true) {
                        int count;
                        try {
                            count = input.read(buffer);
                        } catch (IOException e) {
                            throw new AssetFailure("Failed to read " + path, e);
                        }
                        if (count < 0) {
                            break;
                        }
                        if (count > 0) {
                            output.write(buffer, 0, count);
                        }
                    }
                } catch (AssetFailure e) {
                    assetFailure = e;
                } catch (IOException e) {
                    localFailure = e;
                }
                try {
                    output.close();
                } catch (IOException e) {
                    if (localFailure == null) {
                        localFailure = e;
                    } else {
                        localFailure.addSuppressed(e);
                    }
                }
            }
        } finally {
            try {
                input.close();
            } catch (IOException e) {
                AssetFailure closeFailure = new AssetFailure("Failed to close " + path, e);
                if (localFailure != null) {
                    localFailure.addSuppressed(closeFailure);
                } else if (assetFailure != null) {
                    assetFailure.addSuppressed(closeFailure);
                } else {
                    assetFailure = closeFailure;
                }
            }
        }
        if (localFailure != null) {
            if (assetFailure != null) {
                localFailure.addSuppressed(assetFailure);
            }
            throw localFailure;
        }
        if (assetFailure != null) {
            throw assetFailure;
        }
    }

    private static InputStream fetchAsset(ResourcePackHttp source, String path)
            throws IOException, AssetFailure {
        try {
            return source.open(path);
        } catch (ResourcePackHttp.HttpStatusException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new AssetFailure("Failed to fetch " + path, e);
        }
    }

    private static void deleteBlacklisted(Path cache, List<String> blackList) throws IOException {
        if (blackList.isEmpty()) {
            return;
        }
        try (DirectoryStream<Path> files = Files.newDirectoryStream(cache)) {
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".zip") && !name.endsWith(".md5")) {
                    continue;
                }
                String encoded = name.substring(0, name.lastIndexOf('.'));
                final String namespace;
                try {
                    namespace = URLDecoder.decode(encoded, "UTF-8");
                } catch (IllegalArgumentException e) {
                    continue;
                }
                int marker = namespace.indexOf("-CFPA-");
                String rawNamespace = marker < 0 ? namespace : namespace.substring(0, marker);
                if (blackList.contains(namespace) || blackList.contains(rawNamespace)) {
                    Files.deleteIfExists(file);
                    Log.info("Deleted blacklisted translation cache: %s", file);
                }
            }
        }
    }

    private static String encode(String segment) throws IOException {
        return URLEncoder.encode(segment, "UTF-8").replace("+", "%20");
    }

    private static void requireSource(ResourcePackHttp source) {
        if (source == null) {
            throw new NullPointerException("source");
        }
    }


    public static class Manifest {
        public List<String> blackList = new ArrayList<>();
        public Map<String, String> rules = new LinkedHashMap<>();
    }

    private static class AssetFailure extends Exception {
        AssetFailure(String message) {
            super(message);
        }

        AssetFailure(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

