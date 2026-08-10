package i18nupdatemod.fabricloader;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.Reflection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.Map;

//1.14-latest
public class FabricLoaderMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        Log.setMinecraftLogFile(gameDir);
        String mcVersion = getMcVersion();
        if (mcVersion == null) {
            Log.warning("Minecraft version not found");
            return;
        }
        I18nUpdateMod.init(gameDir, mcVersion, "Fabric", getMods());
    }

    private String getMcVersion() {
        try {
            // Public API used by current Fabric Loader versions.
            return FabricLoader.getInstance().getModContainer("minecraft")
                    .map(container -> container.getMetadata().getVersion().getFriendlyString())
                    .orElse(null);
        } catch (Exception | LinkageError ignored) {
        }
        try {
            // Fabric
            return (String) Reflection.clazz("net.fabricmc.loader.impl.FabricLoaderImpl")
                    .get("INSTANCE")
                    .get("getGameProvider()")
                    .get("getNormalizedGameVersion()").get();
        } catch (Exception | LinkageError ignored) {

        }
        try {
            // Quilt
            return (String) Reflection.clazz("org.quiltmc.loader.impl.QuiltLoaderImpl")
                    .get("INSTANCE")
                    .get("getGameProvider()")
                    .get("getNormalizedGameVersion()").get();
        } catch (Exception ignored) {

        }
        return null;
    }


    private HashSet<String> getMods() {
        HashSet<String> modList = new HashSet<>();
        try {
            // Public API used by current Fabric Loader versions.
            for (ModContainer mod : FabricLoader.getInstance().getAllMods()) {
                modList.add(mod.getMetadata().getId());
            }
            return modList;
        } catch (Exception | LinkageError ignored) {
        }
        try {
            // Fabric
            @SuppressWarnings("unchecked") final Map<String, Object> instance = (Map<String, Object>) Reflection.clazz("net.fabricmc.loader.impl.FabricLoaderImpl")
                    .get("INSTANCE")
                    .get("modMap").get();
            modList = new HashSet<>(instance.keySet());
            return modList;
        } catch (Exception ignored) {

        }
        try {
            // Quilt
            @SuppressWarnings("unchecked") final Map<String, Object> instance = (Map<String, Object>) Reflection.clazz("org.quiltmc.loader.impl.QuiltLoaderImpl")
                    .get("INSTANCE")
                    .get("modMap").get();
            modList = new HashSet<>(instance.keySet());
            return modList;
        } catch (Exception ignored) {

        }
        return modList;
    }
}
