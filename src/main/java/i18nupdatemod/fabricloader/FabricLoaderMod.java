package i18nupdatemod.fabricloader;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.Reflection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.ModContainerImpl;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
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
            // Fabric
            return (String) Reflection.clazz("net.fabricmc.loader.impl.FabricLoaderImpl")
                    .get("INSTANCE")
                    .get("getGameProvider()")
                    .get("getNormalizedGameVersion()").get();
        } catch (Exception ignored) {

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


    private List<String> getMods(){
        List<String> modList = null;
         try {
            // Fabric
             @SuppressWarnings("unchecked")
            final Map<String, Object> instance = (Map<String, Object>)Reflection.clazz("net.fabricmc.loader.impl.FabricLoaderImpl")
                    .get("INSTANCE")
                    .get("modMap").get();
            modList = new ArrayList<>(instance.keySet());
         } catch (Exception ignored) {

        }
        try {
            // Quilt
            @SuppressWarnings("unchecked")
            final Map<String, Object> instance = (Map<String, Object>)Reflection.clazz("org.quiltmc.loader.impl.QuiltLoaderImpl")
                    .get("INSTANCE")
                    .get("modMap").get();
            modList = new ArrayList<>(instance.keySet());
        } catch (Exception ignored) {

        }
        return modList;
    }
}
