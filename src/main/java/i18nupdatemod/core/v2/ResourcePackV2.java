package i18nupdatemod.core.v2;

import i18nupdatemod.core.I18nConfig;
import i18nupdatemod.core.ResourcePackConverter;
import i18nupdatemod.core.net.ResourcePackHttp;
import i18nupdatemod.entity.GameAssetDetail;
import i18nupdatemod.entity.ModTranslation;
import i18nupdatemod.util.Log;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class ResourcePackV2 {
    public static Path update(String minecraftVersion, List<ModTranslation> mods,

                              Path resourcePackDirectory, Path cacheRoot) throws Exception {
        GameAssetDetail plan = I18nConfig.getAssetDetail(minecraftVersion);
        ResourcePackHttp source = ResourcePackHttp.configured();

        ResourcePackDownloader.Manifest manifest = ResourcePackDownloader.loadManifest(source, plan.targetVersion);
        Map<String, String> namespaces = ResourcePackDownloader.selectNamespaces(mods, manifest);
        // Capture language eligibility before adding Minecraft's baseline fixes.
        HashSet<String> modDomains = new HashSet<>(namespaces.values());
        namespaces.put("minecraft", "minecraft");

        Files.createDirectories(resourcePackDirectory);
        List<Path> sources = ResourcePackDownloader.download(
                plan.targetVersion, namespaces, manifest.blackList, cacheRoot, source);
        Path icon = ResourcePackDownloader.downloadIcon(source, plan.targetVersion, cacheRoot);

        Path convertedOutput = resourcePackDirectory.resolve(plan.convertedFileName);
        Path temporary = Files.createTempFile(resourcePackDirectory, plan.convertedFileName + ".", ".tmp");
        try {
            new ResourcePackConverter(sources, temporary, false)
                    .convert(plan.packMetaData, plan.description, modDomains, icon);
            try {
                Files.move(temporary, convertedOutput,
                        StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, convertedOutput, StandardCopyOption.REPLACE_EXISTING);
            }
            Log.info("Published resource pack: %s", convertedOutput);
            return convertedOutput;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
