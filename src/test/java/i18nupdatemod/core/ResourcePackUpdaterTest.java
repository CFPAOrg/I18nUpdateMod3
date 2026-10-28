package i18nupdatemod.core;

import i18nupdatemod.entity.ModTranslation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackUpdaterTest {
    @TempDir
    Path gameDir;

    @Test
    void unchangedInputsWithinWindowSkip() throws Exception {
        Path cache = gameDir.resolve("cache");
        Path pack = pack();
        ResourcePackUpdater.markChecked(cache, "fp");
        assertTrue(ResourcePackUpdater.checkedRecently(cache, pack, hours(24), "fp"));
    }

    @Test
    void changedFingerprintChecksImmediately() throws Exception {
        Path cache = gameDir.resolve("cache");
        Path pack = pack();
        ResourcePackUpdater.markChecked(cache, "before");
        assertFalse(ResourcePackUpdater.checkedRecently(cache, pack, hours(24), "after"));
    }

    @Test
    void expiredWindowChecksAgain() throws Exception {
        Path cache = gameDir.resolve("cache");
        Path pack = pack();
        writeStamp(cache, System.currentTimeMillis() - hours(25), "fp");
        assertFalse(ResourcePackUpdater.checkedRecently(cache, pack, hours(24), "fp"));
    }

    @Test
    void zeroWindowAlwaysChecks() throws Exception {
        Path cache = gameDir.resolve("cache");
        Path pack = pack();
        ResourcePackUpdater.markChecked(cache, "fp");
        assertFalse(ResourcePackUpdater.checkedRecently(cache, pack, 0L, "fp"));
    }

    @Test
    void missingPackChecksAgain() throws Exception {
        Path cache = gameDir.resolve("cache");
        ResourcePackUpdater.markChecked(cache, "fp");
        assertFalse(ResourcePackUpdater.checkedRecently(
                cache, gameDir.resolve("missing.zip"), hours(24), "fp"));
    }

    @Test
    void addingAModChangesTheFingerprint() throws Exception {
        ModTranslation first = mod("jei", "jei.jar");
        ModTranslation second = mod("create", "create.jar");
        String before = ResourcePackUpdater.fingerprint("1.20.1", Collections.singletonList(first));
        String after = ResourcePackUpdater.fingerprint("1.20.1", Arrays.asList(first, second));
        assertNotEquals(before, after);
        assertEquals(after, ResourcePackUpdater.fingerprint("1.20.1", Arrays.asList(second, first)));
    }

    @Test
    void updatingAModFileChangesTheFingerprint() throws Exception {
        ModTranslation mod = mod("jei", "jei.jar");
        String before = ResourcePackUpdater.fingerprint("1.20.1", Collections.singletonList(mod));
        Files.write(mod.source, new byte[]{1, 2, 3});
        assertNotEquals(before,
                ResourcePackUpdater.fingerprint("1.20.1", Collections.singletonList(mod)));
    }

    private ModTranslation mod(String namespace, String fileName) throws Exception {
        Path mods = Files.createDirectories(gameDir.resolve("mods"));
        Path jar = mods.resolve(fileName);
        Files.write(jar, new byte[]{1});
        return new ModTranslation(namespace, null, null, jar, null);
    }

    private Path pack() throws Exception {
        Path pack = gameDir.resolve("pack.zip");
        Files.write(pack, new byte[]{1});
        return pack;
    }

    private static void writeStamp(Path cache, long checkedAt, String fingerprint) throws Exception {
        Files.createDirectories(cache);
        Files.write(cache.resolve("check-stamp.json"),
                ("{\"checkedAt\":" + checkedAt + ",\"fingerprint\":\"" + fingerprint + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static long hours(long value) {
        return TimeUnit.HOURS.toMillis(value);
    }
}
