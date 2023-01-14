package i18nupdatemod;

import i18nupdatemod.core.AssetConfig;
import i18nupdatemod.core.ResourcePack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

public class I18nUpdateMod {
    public static final String MOD_ID = "i18nupdatemod";
    public static final Logger LOGGER = LogManager.getLogger(MOD_ID);

    public static void init(Path minecraftPath, String minecraftVersion, String loader) {
        LOGGER.info("I18nUpdate Mod is loaded in {} with {}", minecraftVersion, loader);
        LOGGER.info("Minecraft path: {}", minecraftPath);
        String userHome = System.getProperty("user.home");
        if (userHome.equals("null")) {
            userHome = minecraftPath.toString();
        }
        LOGGER.info("User home: {}", userHome);

        ResourcePack.resourcePackPath = minecraftPath.resolve("resourcepacks");
        ResourcePack.temporaryPath = Paths.get(userHome, "." + MOD_ID, minecraftVersion);
        try {
            Map<AssetConfig.Type, String> assets = AssetConfig.getAsset(minecraftVersion, loader);
            ResourcePack languagePack =
                    new ResourcePack(assets.get(AssetConfig.Type.FILE_NAME));
            languagePack.checkUpdate(assets.get(AssetConfig.Type.FILE_URL), assets.get(AssetConfig.Type.MD5_URL));
        } catch (Exception e) {
            LOGGER.warn("Failed to update resource pack: {}", e.toString());
//            e.printStackTrace();
        }
    }
}