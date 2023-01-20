package i18nupdatemod;

import i18nupdatemod.core.AssetConfig;
import i18nupdatemod.core.GameConfig;
import i18nupdatemod.core.ResourcePack;
import i18nupdatemod.core.ResourcePackConverter;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;

import java.nio.file.Path;
import java.nio.file.Paths;

public class I18nUpdateMod {
    public static final String MOD_ID = "i18nupdatemod";

    public static void init(Path minecraftPath, String minecraftVersion, String loader) {
        Log.info(String.format("I18nUpdate Mod is loaded in %s with %s", minecraftVersion, loader));
        Log.debug(String.format("Minecraft path: %s", minecraftPath));
        String userHome = System.getProperty("user.home");
        if (userHome.equals("null")) {
            userHome = minecraftPath.toString();
        }
        Log.debug(String.format("User home: %s", userHome));

        FileUtil.setResourcePackDirPath(minecraftPath.resolve("resourcepacks"));

        int minecraftMajorVersion = Integer.parseInt(minecraftVersion.split("\\.")[1]);

        try {
            //Get asset
            AssetConfig.AssetInfo assets = AssetConfig.getAsset(minecraftVersion, loader);

            //Update resource pack
            FileUtil.setTemporaryDirPath(Paths.get(userHome, "." + MOD_ID, assets.targetVersion));
            ResourcePack languagePack = new ResourcePack(assets.fileName, assets.covertPackFormat == null);
            languagePack.checkUpdate(assets.fileUrl, assets.md5Url);
            String applyFileName = assets.fileName;

            //Convert resourcepack
            if (assets.covertPackFormat != null) {
                FileUtil.setTemporaryDirPath(Paths.get(userHome, "." + MOD_ID, minecraftVersion));
                applyFileName = assets.covertFileName;
                ResourcePackConverter converter = new ResourcePackConverter(languagePack, applyFileName);
                converter.convert(assets.covertPackFormat, "这是自动转换的版本！不受官方支持！");
            }

            //Apply resource pack
            GameConfig config = new GameConfig(minecraftPath.resolve("options.txt"));
            config.addResourcePack("Minecraft-Mod-Language-Modpack",
                    (minecraftMajorVersion <= 12 ? "" : "file/") + applyFileName);
            config.writeToFile();
        } catch (Exception e) {
            Log.warning(String.format("Failed to update resource pack: %s", e));
//            e.printStackTrace();
        }
    }
}