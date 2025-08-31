package i18nupdatemod.core;

import com.google.gson.Gson;
import i18nupdatemod.entity.AssetMetaData;
import i18nupdatemod.entity.GameAssetDetail;
import i18nupdatemod.entity.GameMetaData;
import i18nupdatemod.entity.I18nMetaData;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.Version;
import i18nupdatemod.util.VersionRange;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static i18nupdatemod.util.AssetUtil.getFastestUrl;
import static i18nupdatemod.util.AssetUtil.getGitIndex;

public class I18nConfig {
    /**
     * <a href="https://github.com/CFPAOrg/Minecraft-Mod-Language-Package">CFPAOrg/Minecraft-Mod-Language-Package</a>
     */
    private static final String CFPA_ASSET_ROOT = "http://downloader1.meitangdehulu.com:22943/";
    private static final Gson GSON = new Gson();
    private static I18nMetaData i18nMetaData;

    static {
        init();
    }

    private static void init() {
        try (InputStream is = I18nConfig.class.getResourceAsStream("/i18nMetaData.json")) {
            if (is != null) {
                i18nMetaData = GSON.fromJson(new InputStreamReader(is), I18nMetaData.class);
            } else {
                Log.warning("Error getting index: is is null");
            }
        } catch (Exception e) {
            Log.warning("Error getting index: " + e);
        }
    }

    private static GameMetaData getGameMetaData(String minecraftVersion) {
        Version version = Version.from(minecraftVersion);
        return i18nMetaData.games.stream().filter(it -> {
            VersionRange range = new VersionRange(it.gameVersions);
            return range.contains(version);
        }).findFirst().orElseThrow(() -> new IllegalStateException(String.format("Version %s not found in i18n meta", minecraftVersion)));
    }

    private static AssetMetaData getAssetMetaData(String minecraftVersion, String loader) {
        List<AssetMetaData> current = i18nMetaData.assets.stream()
                .filter(it -> it.targetVersion.equals(minecraftVersion))
                .collect(Collectors.toList());
        return current.stream()
                .filter(it -> it.loader.equalsIgnoreCase(loader)).findFirst().orElseGet(() -> current.get(0));
    }

    public static GameAssetDetail getAssetDetail(String minecraftVersion, String loader) {
        GameMetaData convert = getGameMetaData(minecraftVersion);
        GameAssetDetail ret = new GameAssetDetail();

        String asset_root = getFastestUrl();
        Log.debug("Using asset root: " + asset_root);

        if (asset_root.contains("github")) {
            ret.downloads = createDownloadDetailsFromGit(convert, loader);
        } else {
            ret.downloads = createDownloadDetails(convert, loader, asset_root);
        }

        ret.covertPackFormat = convert.packFormat;
        ret.covertFileName =
                String.format("Minecraft-Mod-Language-Modpack-Converted-%s.zip", minecraftVersion);
        return ret;
    }

    private static List<GameAssetDetail.AssetDownloadDetail> createDownloadDetails(GameMetaData convert, String loader, String asset_root) {
        return convert.convertFrom.stream().map(it -> getAssetMetaData(it, loader)).map(it -> {
            GameAssetDetail.AssetDownloadDetail adi = new GameAssetDetail.AssetDownloadDetail();
            adi.fileName = it.filename;
            adi.fileUrl = asset_root + it.filename;
            adi.md5Url = asset_root + it.md5Filename;
            adi.targetVersion = it.targetVersion;
            return adi;
        }).collect(Collectors.toList());
    }

    private static List<GameAssetDetail.AssetDownloadDetail> createDownloadDetailsFromGit(GameMetaData convert, String loader) {
        try {
            Map<String, String> index = getGitIndex();
            String releaseTag;
            String version = convert.gameVersions.substring(1,5);

            if(loader.toLowerCase().contains("fabric")){
                releaseTag = index.get(version + "-fabric");
            }else{
                releaseTag = index.get(version);
            }
            if (releaseTag == null) {
                Log.debug("Error getting index: " + version + "-" + loader);
                Log.debug(index.toString());
                throw new Exception();
            }
            String asset_root = "https://github.com/CFPAOrg/Minecraft-Mod-Language-Package/releases/download/" + releaseTag + "/";

            return convert.convertFrom.stream().map(it -> getAssetMetaData(it, loader)).map(it -> {
                GameAssetDetail.AssetDownloadDetail adi = new GameAssetDetail.AssetDownloadDetail();
                adi.fileName = it.filename;
                adi.fileUrl = (asset_root + it.filename).replace("Minecraft-Mod-Language-Modpack-1-","Minecraft-Mod-Language-Package-1.");
                adi.md5Url = asset_root + it.md5Filename;
                adi.targetVersion = it.targetVersion;
                return adi;
            }).collect(Collectors.toList());
        } catch (Exception ignore) {
            return createDownloadDetails(convert, loader, CFPA_ASSET_ROOT);
        }
    }
}
