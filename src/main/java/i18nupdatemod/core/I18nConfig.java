package i18nupdatemod.core;

import com.google.gson.Gson;
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

    public static I18nMetaData getMetaData() {
        return i18nMetaData;
    }

    private static GameMetaData getGameMetaData(String minecraftVersion) {
        Version version = Version.from(minecraftVersion);
        return getMetaData().games.stream().filter(it -> {
            VersionRange range = new VersionRange(it.gameVersions);
            return range.contains(version);
        }).findFirst().orElseThrow(() -> new IllegalStateException(String.format("Version %s not found in i18n meta", minecraftVersion)));
    }


    /**
     * Builds the local resource-pack plan without probing or resolving any legacy source.
     */
    public static GameAssetDetail getAssetDetail(String minecraftVersion) {
        GameMetaData convert = getGameMetaData(minecraftVersion);
        GameAssetDetail ret = new GameAssetDetail();
        ret.targetVersion = convert.convertFrom.get(0);
        ret.packMetaData = convert;
        ret.description = getResourcePackDescription(convert.convertFrom);
        ret.convertedFileName =
                String.format("Minecraft-Mod-Language-Modpack-Converted-%s.zip", minecraftVersion);
        return ret;
    }


    private static String getResourcePackDescription(List<String> sourceVersions) {
        return sourceVersions.size() > 1 ?
                String.format("该包由%s版本合并\n作者：CFPA团队及汉化项目贡献者",
                        sourceVersions.stream().collect(Collectors.joining("和"))) :
                String.format("该包对应的官方支持版本为%s\n作者：CFPA团队及汉化项目贡献者",
                        sourceVersions.get(0));
    }

}
