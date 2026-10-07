package i18nupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import i18nupdatemod.util.Log;
import org.apache.commons.io.FileUtils;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class GameConfig {
    private static final Gson GSON = new Gson();
    private static final Type STRING_LIST_TYPE = new TypeToken<List<String>>() {
    }.getType();
    private static final byte[] RESOURCE_PACKS_KEY =
            "resourcePacks".getBytes(StandardCharsets.US_ASCII);
    protected Map<String, String> configs = new LinkedHashMap<>();
    private final Path configFile;

    public GameConfig(Path configFile) throws Exception {
        this.configFile = configFile;
        if (!Files.exists(configFile)) {
            return;
        }
        this.configs = FileUtils.readLines(configFile.toFile(), StandardCharsets.UTF_8).stream()
                .map(it -> it.split(":", 2))
                .filter(it -> it.length == 2)
                .collect(Collectors.toMap(it -> it[0], it -> it[1], (a, b) -> a, LinkedHashMap::new));
    }

    public void writeToFile() throws Exception {
        FileUtils.writeLines(configFile.toFile(), "UTF-8", configs.entrySet().stream()
                .map(it -> it.getKey() + ":" + it.getValue()).collect(Collectors.toList()));
    }

    /**
     * 避免NeoForge修改资源包顺序导致的按键配置丢失问题。
     */
    static void updateResourcePacks(Path configFile, List<String> resourcePacks)
            throws Exception {
        Path absolute = configFile.toAbsolutePath().normalize();
        byte[] originalBytes;
        try {
            originalBytes = Files.readAllBytes(absolute);
        } catch (NoSuchFileException missing) {
            originalBytes = new byte[0];
        }

        byte[] encodedLine = ("resourcePacks:" + GSON.toJson(resourcePacks))
                .getBytes(StandardCharsets.UTF_8);
        byte[] replacement = replaceResourcePacksLines(originalBytes, encodedLine);
        if (Arrays.equals(originalBytes, replacement)) {
            return;
        }

        Path parent = absolute.getParent();
        if (parent == null) {
            throw new IllegalStateException("options file has no parent: " + absolute);
        }
        Path temporary = Files.createTempFile(parent, ".options-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, replacement);
            try {
                Files.move(temporary, absolute, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, absolute, StandardCopyOption.REPLACE_EXISTING);
            }
            moved = true;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    private static byte[] replaceResourcePacksLines(byte[] original, byte[] encodedLine) {
        ByteArrayOutputStream result =
                new ByteArrayOutputStream(original.length + encodedLine.length);
        boolean replaced = false;
        int offset = 0;
        while (offset < original.length) {
            int newline = indexOf(original, (byte) '\n', offset, original.length);
            int lineEnd = newline < 0 ? original.length : newline + 1;
            int contentEnd = newline < 0 ? original.length : newline;
            if (contentEnd > offset && original[contentEnd - 1] == '\r') {
                contentEnd--;
            }

            int colon = indexOf(original, (byte) ':', offset, contentEnd);
            if (colon >= 0 && isResourcePacksKey(original, offset, colon)) {
                int keyStart = resourcePacksKeyStart(original, offset, colon);
                result.write(original, offset, keyStart - offset);
                result.write(encodedLine, 0, encodedLine.length);
                result.write(original, contentEnd, lineEnd - contentEnd);
                replaced = true;
            } else {
                result.write(original, offset, lineEnd - offset);
            }
            offset = lineEnd;
        }

        if (!replaced) {
            boolean crlf = containsCrlf(original);
            if (original.length > 0 && original[original.length - 1] != '\n') {
                if (crlf) {
                    result.write('\r');
                }
                result.write('\n');
            }
            result.write(encodedLine, 0, encodedLine.length);
            if (crlf) {
                result.write('\r');
            }
            result.write('\n');
        }
        return result.toByteArray();
    }

    private static boolean isResourcePacksKey(byte[] bytes, int lineStart, int colon) {
        int keyStart = resourcePacksKeyStart(bytes, lineStart, colon);
        if (colon - keyStart != RESOURCE_PACKS_KEY.length) {
            return false;
        }
        for (int i = 0; i < RESOURCE_PACKS_KEY.length; i++) {
            if (bytes[keyStart + i] != RESOURCE_PACKS_KEY[i]) {
                return false;
            }
        }
        return true;
    }

    private static int resourcePacksKeyStart(byte[] bytes, int lineStart, int colon) {
        // UTF-8 BOM 魔法值 EF BB BF
        if (lineStart == 0 && colon >= 3
                && (bytes[0] & 0xFF) == 0xEF
                && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF) {
            return 3;
        }
        return lineStart;
    }

    private static int indexOf(byte[] bytes, byte value, int start, int end) {
        for (int i = start; i < end; i++) {
            if (bytes[i] == value) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsCrlf(byte[] bytes) {
        for (int i = 1; i < bytes.length; i++) {
            if (bytes[i - 1] == '\r' && bytes[i] == '\n') {
                return true;
            }
        }
        return false;
    }

    public void addResourcePack(String baseName, String resourcePack) {
        List<String> resourcePacks = GSON.fromJson(
                configs.computeIfAbsent("resourcePacks", it -> "[]"), STRING_LIST_TYPE);
        //If resource packs already contains target resource pack, nothing to do
        if (resourcePacks.contains(resourcePack)) {
            return;
        }
        //Remove other Minecraft Mod Language Pack
        resourcePacks = resourcePacks.stream().filter(it -> !it.contains(baseName)).collect(Collectors.toList());
        resourcePacks.add(resourcePack);
        configs.put("resourcePacks", GSON.toJson(resourcePacks));
        Log.info(String.format("Resource Packs: %s", configs.get("resourcePacks")));
//        configs.put("lang", "zh_cn");
    }

}

