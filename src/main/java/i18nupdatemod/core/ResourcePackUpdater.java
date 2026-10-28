package i18nupdatemod.core;

import com.google.gson.Gson;
import i18nupdatemod.core.v1.ResourcePackV1;
import i18nupdatemod.core.v2.ResourcePackV2;
import i18nupdatemod.entity.GameAssetDetail;
import i18nupdatemod.entity.ModTranslation;
import i18nupdatemod.util.DigestUtil;
import i18nupdatemod.util.Log;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ResourcePackUpdater {
    private static final Gson GSON = new Gson();
    // 资源文件的 mtime 是内容更新时间，不是检查时间，所以检查时间单独存
    private static final String CHECK_STAMP = "check-stamp.json";

    public static Path update(String minecraftVersion, String loader, List<ModTranslation> mods,
                              Path resourcePackDirectory, Path cacheRoot) throws Exception {
        GameAssetDetail plan = I18nConfig.getAssetDetail(minecraftVersion);
        Path convertedPack = resourcePackDirectory.resolve(plan.convertedFileName);
        long ttl = I18nUserConfig.cacheTtlMillis();
        String fingerprint = fingerprint(minecraftVersion, mods);
        if (checkedRecently(cacheRoot, convertedPack, ttl, fingerprint)) {
            Log.debug("Skipping the update check: unchanged inputs, last check within %s hours.",
                    TimeUnit.MILLISECONDS.toHours(ttl));
            return convertedPack;
        }
        try {
            Path updated = ResourcePackV2.update(minecraftVersion, mods, resourcePackDirectory, cacheRoot);
            markChecked(cacheRoot, fingerprint);
            return updated;
        } catch (Exception e) {
            Log.warning("V2 resource pipeline failed; falling back to V1: %s", e);
        }
        Path updated = ResourcePackV1.update(minecraftVersion, loader, mods, resourcePackDirectory, cacheRoot);
        markChecked(cacheRoot, fingerprint);
        return updated;
    }

    static boolean checkedRecently(Path cacheRoot, Path convertedPack, long ttlMillis, String fingerprint) {
        if (ttlMillis <= 0L) {
            return false;
        }
        try {
            if (!Files.isRegularFile(convertedPack) || Files.size(convertedPack) <= 0L) {
                return false;
            }
            Path stampFile = cacheRoot.resolve(CHECK_STAMP);
            if (!Files.isRegularFile(stampFile)) {
                return false;
            }
            CheckStamp stamp = GSON.fromJson(
                    new String(Files.readAllBytes(stampFile), StandardCharsets.UTF_8), CheckStamp.class);
            if (stamp == null) {
                return false;
            }
            long age = System.currentTimeMillis() - stamp.checkedAt;
            return fingerprint.equals(stamp.fingerprint) && age >= 0L && age < ttlMillis;
        } catch (Exception e) {
            return false;
        }
    }

    static void markChecked(Path cacheRoot, String fingerprint) {
        try {
            Files.createDirectories(cacheRoot);
            Path stampFile = cacheRoot.resolve(CHECK_STAMP);
            Path temporary = Files.createTempFile(cacheRoot, ".check-stamp-", ".tmp");
            try {
                CheckStamp stamp = new CheckStamp();
                stamp.checkedAt = System.currentTimeMillis();
                stamp.fingerprint = fingerprint;
                Files.write(temporary, GSON.toJson(stamp).getBytes(StandardCharsets.UTF_8));
                try {
                    Files.move(temporary, stampFile,
                            StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(temporary, stampFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            Log.warning("Cannot record the update check in %s: %s", cacheRoot, e);
        }
    }

    // 指纹覆盖所有影响选包的本地输入：游戏版本、模组文件、排除项
    static String fingerprint(String minecraftVersion, List<ModTranslation> mods)
            throws NoSuchAlgorithmException {
        List<String> excluded = new ArrayList<>(I18nUserConfig.excludedMods());
        Collections.sort(excluded);
        List<String> lines = new ArrayList<>();
        lines.add("version=" + minecraftVersion);
        lines.add("excluded=" + excluded);
        if (mods != null) {
            for (ModTranslation mod : mods) {
                if (mod == null) {
                    continue;
                }
                lines.add(mod.namespace + "|" + mod.source.getFileName()
                        + "|" + sizeOf(mod.source) + "|" + modifiedOf(mod.source));
            }
        }
        Collections.sort(lines);
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return DigestUtil.hexString(
                digest.digest(String.join("\n", lines).getBytes(StandardCharsets.UTF_8)));
    }

    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return -1L;
        }
    }

    private static long modifiedOf(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return -1L;
        }
    }

    private static final class CheckStamp {
        long checkedAt;
        String fingerprint;
    }
}
