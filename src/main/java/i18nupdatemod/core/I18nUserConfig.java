package i18nupdatemod.core;

import com.google.gson.Gson;
import i18nupdatemod.util.Log;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * User-editable options read once per launch from {@code config/i18nupdatemod.json}; never throws, always falls back to defaults.
 */
public class I18nUserConfig {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "i18nupdatemod.json";
    private static final long DEFAULT_CACHE_TTL_HOURS = 24;
    private static final long MAX_CACHE_TTL_HOURS = TimeUnit.DAYS.toHours(365);
    private static final String TEMPLATE = "{\n"
            + "  \"_comment_cacheTtlHours\": \"更新检查间隔（小时）：多久之内不再向服务器校验汉化是否有新版本，0 表示每次启动都校验。默认 24。\",\n"
            + "  \"cacheTtlHours\": 24,\n"
            + "  \"_comment_excludeMods\": \"不参与汉化的模组命名空间（例如 jei、create）：跳过下载，其已缓存的汉化也会被删除。\",\n"
            + "  \"excludeMods\": []\n"
            + "}\n";

    private static volatile long cacheTtlMillis = TimeUnit.HOURS.toMillis(DEFAULT_CACHE_TTL_HOURS);
    private static volatile List<String> excludedMods = Collections.emptyList();

    public static void load(Path gameDirectory) {
        Path file = gameDirectory.resolve("config").resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            writeTemplate(file);
            return;
        }
        Data data;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            data = GSON.fromJson(reader, Data.class);
            if (data == null) {
                throw new IOException("configuration is empty");
            }
        } catch (Exception e) {
            Log.warning("Failed to read %s, keeping defaults: %s", file, e);
            return;
        }
        cacheTtlMillis = TimeUnit.HOURS.toMillis(validTtlHours(data.cacheTtlHours, file));
        excludedMods = normalize(data.excludeMods, file);
        Log.debug("User config: cacheTtlHours=%s excludeMods=%s", TimeUnit.MILLISECONDS.toHours(cacheTtlMillis), excludedMods);
    }

    public static long cacheTtlMillis() {
        return cacheTtlMillis;
    }

    public static List<String> excludedMods() {
        return excludedMods;
    }

    private static long validTtlHours(long hours, Path file) {
        if (hours < 0 || hours > MAX_CACHE_TTL_HOURS) {
            Log.warning("cacheTtlHours %s out of range in %s, using %s", hours, file, DEFAULT_CACHE_TTL_HOURS);
            return DEFAULT_CACHE_TTL_HOURS;
        }
        return hours;
    }

    private static List<String> normalize(List<String> names, Path file) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>(names.size());
        for (String name : names) {
            if (name == null || name.trim().isEmpty()) {
                Log.warning("Ignoring empty excludeMods entry in %s", file);
                continue;
            }
            result.add(name.trim().toLowerCase(Locale.ROOT));
        }
        return Collections.unmodifiableList(result);
    }

    private static void writeTemplate(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, TEMPLATE.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
            Log.info("Created default configuration: %s", file);
        } catch (FileAlreadyExistsException e) {
            Log.debug("Configuration already created by another thread: %s", file);
        } catch (IOException e) {
            Log.warning("Failed to create %s: %s", file, e);
        }
    }

    private static class Data {
        long cacheTtlHours = DEFAULT_CACHE_TTL_HOURS;
        List<String> excludeMods;
    }
}
