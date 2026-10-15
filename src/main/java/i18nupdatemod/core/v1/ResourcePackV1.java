package i18nupdatemod.core.v1;

import i18nupdatemod.core.I18nConfig;
import i18nupdatemod.core.ResourcePackConverter;
import i18nupdatemod.entity.GameAssetDetail;
import i18nupdatemod.entity.ModTranslation;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;

public class ResourcePackV1 {
    public static Path update(String minecraftVersion, String loader, List<ModTranslation> mods,
                              Path resourcePackDirectory, Path cacheRoot) throws Exception {
        GameAssetDetail plan = I18nConfig.getAssetDetail(minecraftVersion);
        HashSet<String> modDomains = new HashSet<>();
        for (ModTranslation mod : mods) {
            modDomains.add(mod.namespace);
        }
        List<Path> sources = LegacyResourcePackDownloader.download(
                plan.packMetaData, loader, resourcePackDirectory, cacheRoot);
        Path convertedCache = cacheRoot.resolve(minecraftVersion).resolve(plan.convertedFileName);
        Path convertedOutput = resourcePackDirectory.resolve(plan.convertedFileName);
        new ResourcePackConverter(sources, convertedCache, true)
                .convert(plan.packMetaData, plan.description, modDomains, null);
        LegacyFileUtil.syncIfNewer(convertedCache, convertedOutput);
        return convertedOutput;
    }
}
