package i18nupdatemod.fabricloader;

import i18nupdatemod.I18nUpdateMod;
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
        FabricLoader loader = FabricLoader.getInstance();
        if (!(loader instanceof FabricLoaderImpl)) {
            LOGGER.warn("FabricLoader is not instanceof FabricLoaderImpl, is it Quilt?");
            return;
        }
        FabricLoaderImpl impl = (FabricLoaderImpl) loader;
        Path gameDir = impl.getGameDir();
        String mcVersion = impl.getGameProvider().getNormalizedGameVersion();
        I18nUpdateMod.init(gameDir, mcVersion, "Fabric");
    }
}
