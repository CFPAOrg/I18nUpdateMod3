package i18nupdatemod.core.v1;

import i18nupdatemod.core.I18nConfig;
import i18nupdatemod.entity.AssetMetaData;
import i18nupdatemod.entity.GameMetaData;

import java.util.List;
import java.util.stream.Collectors;

public class LegacyConfig {
    /**
     * <a href="https://github.com/CFPAOrg/Minecraft-Mod-Language-Package">CFPAOrg/Minecraft-Mod-Language-Package</a>
     */
    public static List<AssetDownloadDetail> getLegacyDownloads(GameMetaData convert, String loader) {
        return convert.convertFrom.stream()
                .map(version -> getAssetMetaData(version, loader))
                .map(metadata -> {
                    AssetDownloadDetail detail = new AssetDownloadDetail();
                    detail.fileName = metadata.filename;
                    detail.md5FileName = metadata.md5Filename;
                    detail.targetVersion = metadata.targetVersion;
                    return detail;
                })
                .collect(Collectors.toList());
    }

    private static AssetMetaData getAssetMetaData(String minecraftVersion, String loader) {
        List<AssetMetaData> current = I18nConfig.getMetaData().assets.stream()
                .filter(it -> it.targetVersion.equals(minecraftVersion))
                .collect(Collectors.toList());
        return current.stream()
                .filter(it -> it.loader.equalsIgnoreCase(loader))
                .findFirst()
                .orElseGet(() -> current.get(0));
    }
}
