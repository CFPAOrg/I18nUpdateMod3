package i18nupdatemod.core;

import i18nupdatemod.core.v1.ResourcePackV1;
import i18nupdatemod.core.v2.ResourcePackV2;
import i18nupdatemod.entity.ModTranslation;
import i18nupdatemod.util.Log;

import java.nio.file.Path;
import java.util.List;

public class ResourcePackUpdater {
    public static Path update(String minecraftVersion, String loader, List<ModTranslation> mods,
                              Path resourcePackDirectory, Path cacheRoot) throws Exception {
        try {
            return ResourcePackV2.update(minecraftVersion, mods, resourcePackDirectory, cacheRoot);
        } catch (Exception e) {
            Log.warning("V2 resource pipeline failed; falling back to V1: %s", e);
        }
        return ResourcePackV1.update(minecraftVersion, loader, mods, resourcePackDirectory, cacheRoot);
    }
}
