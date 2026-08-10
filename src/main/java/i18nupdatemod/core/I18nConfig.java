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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static i18nupdatemod.util.AssetUtil.getAssetRoots;
import static i18nupdatemod.util.AssetUtil.getFastestUrl;
import static i18nupdatemod.util.AssetUtil.getGitIndex;

public class I18nConfig {
    /**
     * <a href="https://github.com/CFPAOrg/Minecraft-Mod-Language-Package">CFPAOrg/Minecraft-Mod-Language-Package</a>
     */
    private static final String GITHUB_RELEASE_ROOT =
            "https://github.com/CFPAOrg/Minecraft-Mod-Language-Package/releases/download/";
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

        String releaseTag = resolveReleaseTag(convert, loader, getGitIndex());
        List<GameAssetDetail.AssetDownloadDetail.DownloadSource> sources =
                createDownloadSources(getFastestUrl(), releaseTag);
        Log.debug("Using asset sources: %s", sources.stream()
                .map(source -> source.fileUrl)
                .collect(Collectors.joining(", ")));

        ret.downloads = createDownloadDetails(convert, loader, sources);

        ret.covertFileName =
                String.format("Minecraft-Mod-Language-Modpack-Converted-%s.zip", minecraftVersion);
        return ret;
    }

    public static GameMetaData getPackFormat(String minecraftVersion) {
        return getGameMetaData(minecraftVersion);
    }

    private static List<GameAssetDetail.AssetDownloadDetail> createDownloadDetails(GameMetaData convert, String loader,
                                                                                   List<GameAssetDetail.AssetDownloadDetail.DownloadSource> sources) {
        return convert.convertFrom.stream().map(it -> getAssetMetaData(it, loader)).map(it -> {
            GameAssetDetail.AssetDownloadDetail adi = new GameAssetDetail.AssetDownloadDetail();
            adi.fileName = it.filename;
            adi.targetVersion = it.targetVersion;
            adi.sources = sources.stream().map(source ->
                    new GameAssetDetail.AssetDownloadDetail.DownloadSource(
                            source.fileUrl + it.filename, source.md5Url + it.md5Filename))
                    .collect(Collectors.toList());
            return adi;
        }).collect(Collectors.toList());
    }

    static List<GameAssetDetail.AssetDownloadDetail.DownloadSource> createDownloadSources(String preferredRoot,
                                                                                             String releaseTag) {
        LinkedHashSet<String> roots = new LinkedHashSet<>();
        if (releaseTag != null && !releaseTag.isEmpty()) {
            roots.add(GITHUB_RELEASE_ROOT + releaseTag + "/");
        }
        if (preferredRoot != null && !preferredRoot.isEmpty()) {
            roots.add(preferredRoot);
        }
        roots.addAll(getAssetRoots());

        List<GameAssetDetail.AssetDownloadDetail.DownloadSource> sources = new ArrayList<>();
        for (String root : roots) {
            if (root == null || root.isEmpty()) {
                continue;
            }
            sources.add(new GameAssetDetail.AssetDownloadDetail.DownloadSource(root, root));
        }
        return sources;
    }

    /**
     * @return CFPA release tag holding this version's packs, or null when the index does not list it
     * (as is the case for 26.1, which is only published to the asset root)
     */
    static String resolveReleaseTag(GameMetaData convert, String loader, Map<String, String> index) {
        String version = convert.convertFrom.get(0);
        String indexedTag = index.get(loader.toLowerCase().contains("fabric") ? version + "-fabric" : version);
        return indexedTag == null || indexedTag.isEmpty() ? null : indexedTag;
    }
}
