package i18nupdatemod;

import i18nupdatemod.core.AssetConfig;
import i18nupdatemod.core.GameConfig;
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

        int minecraftMajorVersion = Integer.parseInt(minecraftVersion.split("\\.")[1]);

        try {
            //Get asset
            Map<AssetConfig.Type, String> assets = AssetConfig.getAsset(minecraftVersion, loader);

            //Update resource pack
            ResourcePack languagePack =
                    new ResourcePack(assets.get(AssetConfig.Type.FILE_NAME));
            languagePack.checkUpdate(assets.get(AssetConfig.Type.FILE_URL), assets.get(AssetConfig.Type.MD5_URL));

            //Apply resource pack
            GameConfig config = new GameConfig(minecraftPath.resolve("options.txt"));
            config.addResourcePack("Minecraft-Mod-Language-Modpack",
                    (minecraftMajorVersion <= 12 ? "" : "file/") + assets.get(AssetConfig.Type.FILE_NAME));
            config.writeToFile();
        } catch (Exception e) {
            LOGGER.warn("Failed to update resource pack: {}", e.toString());
//            e.printStackTrace();
        }
    }
}