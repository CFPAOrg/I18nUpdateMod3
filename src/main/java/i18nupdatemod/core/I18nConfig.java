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
import java.util.List;
import java.util.stream.Collectors;

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
        }).findFirst().orElseThrow(IllegalStateException::new);
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

        ret.downloads = convert.convertFrom.stream().map(it->getAssetMetaData(it,loader)).map(it -> {
            GameAssetDetail.AssetDownloadDetail adi = new GameAssetDetail.AssetDownloadDetail();
            adi.fileName = it.filename;
            adi.fileUrl = CFPA_ASSET_ROOT + it.filename;
            adi.md5Url = CFPA_ASSET_ROOT + it.md5Filename;
            adi.targetVersion = it.targetVersion;
            return adi;
        }).collect(Collectors.toList());
        ret.covertPackFormat = convert.packFormat;
        ret.covertFileName =
                String.format("Minecraft-Mod-Language-Modpack-Converted-%s.zip", minecraftVersion);
        return ret;
    }
}
