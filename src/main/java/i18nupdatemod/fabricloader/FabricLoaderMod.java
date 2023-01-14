package i18nupdatemod.fabricloader;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Reflection;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.impl.FabricLoaderImpl;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.file.Path;

public class FabricLoaderMod implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("I18nUpdateMod");

    @Override
    public void onInitializeClient() {
        Path gameDir = FabricLoader.getInstance().getGameDir();
        String mcVersion = getMcVersion();
        if(mcVersion==null){
            LOGGER.warn("Minecraft version not found");
            return;
        }
        I18nUpdateMod.init(gameDir, mcVersion, "Fabric");
    }

    private String getMcVersion(){
        try {
            //Fabric
            return  (String) Reflection.clazz("net.fabricmc.loader.impl.FabricLoaderImpl")
                    .get("INSTANCE")
                    .get("getGameProvider()")
                    .get("getNormalizedGameVersion()").get();
        } catch (Exception ignored) {

        }
        try {
            //Fabric
            return  (String) Reflection.clazz("org.quiltmc.loader.impl.QuiltLoaderImpl")
                    .get("INSTANCE")
                    .get("getGameProvider()")
                    .get("getNormalizedGameVersion()").get();
        } catch (Exception ignored) {

        }
        return null;
    }
}
