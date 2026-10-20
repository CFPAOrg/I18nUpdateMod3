package i18nupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
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

/** User-editable options read once per launch from {@code config/i18nupdatemod.json}; never throws, always falls back to defaults. */
public class I18nUserConfig {
    private static final Gson GSON = new Gson();
    private static final String FILE_NAME = "i18nupdatemod.json";
    private static final long DEFAULT_CACHE_TTL_HOURS = 24;
    private static final long MAX_CACHE_TTL_HOURS = TimeUnit.DAYS.toHours(365);
    private static final String TEMPLATE = "{\n"
            + "  \"_comment_cacheTtlHours\": \"更新检查间隔（小时）：多久之内不再向服务器校验汉化是否有新版本，0 表示每次启动都校验。默认 24。\",\n"
            + "  \"cacheTtlHours\": 24,\n"
            + "  \"_comment_excludeMods\": \"不参与汉化的模组命名空间（例如 jei、create）：跳过下载，也不会进入汉化包；已有缓存不动。\",\n"
            + "  \"excludeMods\": [],\n"
            + "  \"_comment_onlyWhenChineseSystem\": \"为 true 时，只在系统语言（操作系统区域设置，不是游戏内语言）为中文时才启动：非中文系统下模组完全不动作，不联网、不下载、不写任何文件。整合包作者可设为 true 后随包分发。默认 false。\",\n"
            + "  \"onlyWhenChineseSystem\": false\n"
            + "}\n";

    private static volatile long cacheTtlMillis = TimeUnit.HOURS.toMillis(DEFAULT_CACHE_TTL_HOURS);
    private static volatile List<String> excludedMods = Collections.emptyList();
    private static volatile boolean onlyWhenChineseSystem;
    private static final List<String> pendingDebug = new ArrayList<>();
    private static final List<String> pendingWarnings = new ArrayList<>();

    public static void load(Path gameDirectory) {
        Path file = gameDirectory.resolve("config").resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            writeTemplate(file);
            return;
        }
        JsonObject data = read(file);
        if (data == null) {
            return;
        }
        // 逐字段读取：某个字段写错不能让 onlyWhenChineseSystem 跟着回默认，否则门禁会静默失效
        cacheTtlMillis = TimeUnit.HOURS.toMillis(validTtlHours(longValue(data, "cacheTtlHours", file), file));
        excludedMods = normalize(stringList(data, "excludeMods", file), file);
        onlyWhenChineseSystem = booleanValue(data, "onlyWhenChineseSystem", file);
        debug("User config: cacheTtlHours=%s excludeMods=%s onlyWhenChineseSystem=%s",
                TimeUnit.MILLISECONDS.toHours(cacheTtlMillis), excludedMods, onlyWhenChineseSystem);
    }

    /** 读配置早于日志文件打开，攒下的消息由 prepare() 在开完日志后输出。 */
    public static void flushLogMessages() {
        List<String> debugs;
        List<String> warnings;
        synchronized (pendingDebug) {
            debugs = new ArrayList<>(pendingDebug);
            warnings = new ArrayList<>(pendingWarnings);
            pendingDebug.clear();
            pendingWarnings.clear();
        }
        for (String line : debugs) {
            Log.debug(line);
        }
        for (String line : warnings) {
            Log.warning(line);
        }
    }

    public static long cacheTtlMillis() {
        return cacheTtlMillis;
    }

    public static List<String> excludedMods() {
        return excludedMods;
    }

    public static boolean isExcluded(String namespace) {
        return namespace != null && excludedMods.contains(namespace.toLowerCase(Locale.ROOT));
    }

    public static boolean onlyWhenChineseSystem() {
        return onlyWhenChineseSystem;
    }

    private static long validTtlHours(long hours, Path file) {
        if (hours < 0 || hours > MAX_CACHE_TTL_HOURS) {
            warning("cacheTtlHours %s out of range in %s, using %s", hours, file, DEFAULT_CACHE_TTL_HOURS);
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
                warning("Ignoring empty excludeMods entry in %s", file);
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
            debug("Created default configuration: %s", file);
        } catch (FileAlreadyExistsException e) {
            Log.debug("Configuration already created by another thread: %s", file);
        } catch (IOException e) {
            Log.warning("Failed to create %s: %s", file, e);
        }
    }

    private static JsonObject read(Path file) {
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonReader json = new JsonReader(reader);
            // 宽松模式：容忍手写配置常见的不带引号键名
            json.setLenient(true);
            JsonObject data = GSON.fromJson(json, JsonObject.class);
            if (data == null) {
                throw new IOException("configuration is empty");
            }
            return data;
        } catch (Exception e) {
            warning("Failed to read %s, keeping defaults: %s", file, e);
            return null;
        }
    }

    private static long longValue(JsonObject data, String key, Path file) {
        JsonElement value = data.get(key);
        if (value == null || value.isJsonNull()) {
            return DEFAULT_CACHE_TTL_HOURS;
        }
        try {
            return value.getAsLong();
        } catch (RuntimeException e) {
            warning("%s in %s must be a whole number, using %s", key, file, DEFAULT_CACHE_TTL_HOURS);
            return DEFAULT_CACHE_TTL_HOURS;
        }
    }

    private static List<String> stringList(JsonObject data, String key, Path file) {
        JsonElement value = data.get(key);
        if (value == null || value.isJsonNull()) {
            return Collections.emptyList();
        }
        try {
            List<String> names = new ArrayList<>();
            for (JsonElement name : value.getAsJsonArray()) {
                names.add(name == null || name.isJsonNull() ? null : name.getAsString());
            }
            return names;
        } catch (RuntimeException e) {
            warning("%s in %s must be an array of strings, ignoring it", key, file);
            return Collections.emptyList();
        }
    }

    private static boolean booleanValue(JsonObject data, String key, Path file) {
        JsonElement value = data.get(key);
        if (value == null || value.isJsonNull()) {
            return false;
        }
        try {
            return value.getAsBoolean();
        } catch (RuntimeException e) {
            warning("%s in %s must be true or false, keeping false", key, file);
            return false;
        }
    }

    private static void debug(String format, Object... args) {
        synchronized (pendingDebug) {
            pendingDebug.add(String.format(format, args));
        }
    }

    private static void warning(String format, Object... args) {
        synchronized (pendingDebug) {
            pendingWarnings.add(String.format(format, args));
        }
    }
}
