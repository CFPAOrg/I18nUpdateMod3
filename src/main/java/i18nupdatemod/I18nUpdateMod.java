package i18nupdatemod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import i18nupdatemod.core.AssetConfig;
import i18nupdatemod.core.GameConfig;
import i18nupdatemod.core.ResourcePack;
import i18nupdatemod.core.ResourcePackConverter;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class I18nUpdateMod {
    public static final String MOD_ID = "i18nupdatemod";
    public static String MOD_VERSION;

    private static final Gson GSON = new Gson();

    public static void init(Path minecraftPath, String minecraftVersion, String loader) {
        try (InputStream is = AssetConfig.class.getResourceAsStream("/fabric.mod.json")) {
            MOD_VERSION = GSON.fromJson(new InputStreamReader(is), JsonObject.class).get("version").getAsString();
        } catch (Exception e) {
            Log.warning("Error getting version: " + e);
        }
        Log.info(String.format("I18nUpdate Mod %s is loaded in %s with %s", MOD_VERSION, minecraftVersion, loader));
        Log.debug(String.format("Minecraft path: %s", minecraftPath));
        String userHome = System.getProperty("user.home");
        if (userHome.equals("null")) {
            userHome = minecraftPath.toString();
        }
        Log.debug(String.format("User home: %s", userHome));

        try {
            Class.forName("com.netease.mc.mod.network.common.Library");
            Log.warning("I18nUpdateMod will get resource pack from Internet, whose content is uncontrolled.");
            Log.warning("This behavior contraries to Netease Minecraft developer content review rule: " +
                    "forbidden the content in game not match the content for reviewing.");
            Log.warning("To follow this rule, I18nUpdateMod won't download any thing.");
            Log.warning("I18nUpdateMod会从互联网获取内容不可控的资源包。");
            Log.warning("这一行为违背了网易我的世界「开发者内容审核制度」：禁止上传与提审内容不一致的游戏内容。");
            Log.warning("为了遵循这一制度，I18nUpdateMod不会下载任何内容。");
            return;
        } catch (ClassNotFoundException ignored) {
        }

        FileUtil.setResourcePackDirPath(minecraftPath.resolve("resourcepacks"));

        int minecraftMajorVersion = Integer.parseInt(minecraftVersion.split("\\.")[1]);

        try {
            //Get asset
            AssetConfig.AssetInfo assets = AssetConfig.getAsset(minecraftVersion, loader);

            //Update resource pack
            List<ResourcePack> languagePacks = new ArrayList<>();
            for (AssetConfig.AssetInfo.AssetDownloadInfo it : assets.downloads) {
                FileUtil.setTemporaryDirPath(Paths.get(userHome, "." + MOD_ID, it.targetVersion));
                ResourcePack languagePack = new ResourcePack(it.fileName, assets.covertPackFormat == null);
                languagePack.checkUpdate(it.fileUrl, it.md5Url);
                languagePacks.add(languagePack);
            }
            String applyFileName = languagePacks.get(0).getFilename();

            //Convert resourcepack
            if (assets.covertPackFormat != null) {
                FileUtil.setTemporaryDirPath(Paths.get(userHome, "." + MOD_ID, minecraftVersion));
                applyFileName = assets.covertFileName;
                ResourcePackConverter converter = new ResourcePackConverter(languagePacks, applyFileName);
                converter.convert(assets.covertPackFormat,
                        languagePacks.size() > 1 ? "该包由两版本合并，若有错误请反馈！" : "这是自动转换的版本！不受官方支持！");
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