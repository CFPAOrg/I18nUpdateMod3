package i18nupdatemod.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameConfigTest {
    @TempDir
    Path gameDir;

    @Test
    void emptyResourcePacksValueAddsThePackInsteadOfAborting() throws Exception {
        Path options = gameDir.resolve("options.txt");
        Files.write(options, "lang:zh_cn\nresourcePacks:\nfov:70\n".getBytes(StandardCharsets.UTF_8));

        assertEquals(Collections.emptyList(), GameConfig.readResourcePacks(options));

        GameConfig.addResourcePack(options, "Minecraft-Mod-Language-Modpack",
                "file/Minecraft-Mod-Language-Modpack-Converted-1.9.zip");
        assertEquals(Collections.singletonList("file/Minecraft-Mod-Language-Modpack-Converted-1.9.zip"),
                GameConfig.readResourcePacks(options));
        String content = new String(Files.readAllBytes(options), StandardCharsets.UTF_8);
        assertTrue(content.contains("lang:zh_cn"), content);
        assertTrue(content.contains("fov:70"), content);
    }
}
