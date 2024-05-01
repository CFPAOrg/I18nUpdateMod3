package i18nupdatemod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import i18nupdatemod.core.GameConfig;
import i18nupdatemod.core.I18nConfig;
import i18nupdatemod.core.ResourcePack;
import i18nupdatemod.core.ResourcePackConverter;
import i18nupdatemod.entity.GameAssetDetail;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class I18nUpdateMod {
    public static final String MOD_ID = "i18nupdatemod";
    public static String MOD_VERSION;

    public static final Gson GSON = new Gson();

    public static void init(Path minecraftPath, String minecraftVersion, String loader) {
        try (InputStream is = I18nConfig.class.getResourceAsStream("/i18nMetaData.json")) {
            MOD_VERSION = GSON.fromJson(new InputStreamReader(is), JsonObject.class).get("version").getAsString();
        } catch (Exception e) {
            Log.warning("Error getting version: " + e);
        }
        Log.info(String.format("I18nUpdate Mod %s is loaded in %s with %s", MOD_VERSION, minecraftVersion, loader));
        Log.debug(String.format("Minecraft path: %s", minecraftPath));
        String localStorage = getLocalStoragePos(minecraftPath);
        Log.debug(String.format("Local Storage Pos: %s", localStorage));

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
            GameAssetDetail assets = I18nConfig.getAssetDetail(minecraftVersion, loader);

            //Update resource pack
            List<ResourcePack> languagePacks = new ArrayList<>();
            boolean convertNotNeed = assets.downloads.size() == 1 && assets.downloads.get(0).targetVersion.equals(minecraftVersion);
            String applyFileName = assets.downloads.get(0).fileName;
            for (GameAssetDetail.AssetDownloadDetail it : assets.downloads) {
                FileUtil.setTemporaryDirPath(Paths.get(localStorage, "." + MOD_ID, it.targetVersion));
                ResourcePack languagePack = new ResourcePack(it.fileName, convertNotNeed);
                languagePack.checkUpdate(it.fileUrl, it.md5Url);
                languagePacks.add(languagePack);
            }

            //Convert resourcepack
            if (!convertNotNeed) {
                FileUtil.setTemporaryDirPath(Paths.get(localStorage, "." + MOD_ID, minecraftVersion));
                applyFileName = assets.covertFileName;
                ResourcePackConverter converter = new ResourcePackConverter(languagePacks, applyFileName);
                converter.convert(assets.covertPackFormat, getResourcePackDescription(assets.downloads));
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

    private static String getResourcePackDescription(List<GameAssetDetail.AssetDownloadDetail> downloads) {
        return downloads.size() > 1 ?
                String.format("该包由%s版本合并\n作者：CFPA团队及汉化项目贡献者",
                        downloads.stream().map(it -> it.targetVersion).collect(Collectors.joining("和"))) :
                String.format("该包对应的官方支持版本为%s\n作者：CFPA团队及汉化项目贡献者",
                        downloads.get(0).targetVersion);

    }

    public static String getLocalStoragePos(Path minecraftPath) {
        String userHome = System.getProperty("user.home");
        Path oldPath = Paths.get(userHome, "." + MOD_ID);
        if (Files.exists(oldPath)) {
            return userHome;
        }

        String localAppData = System.getenv("LocalAppData");
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (localAppData != null) {
            return localAppData;
        } else if (xdgDataHome != null) {
            return xdgDataHome;
        } else {
            return minecraftPath.toString();
        }
    }
}