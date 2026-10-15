package i18nupdatemod.neoforgeloader;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.core.RuntimePackActivation;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.ModUtil;
import i18nupdatemod.util.Reflection;
import net.neoforged.neoforgespi.earlywindow.GraphicsBootstrapper;

import java.nio.file.Path;

//NeoForge: 1.21.9-latest
public class NeoForgeBootstrap implements GraphicsBootstrapper {
    @Override
    public String name() {
        return "I18nUpdateMod";
    }

    @Override
    public void bootstrap(String[] arguments) {
        try {
            Reflection loader;
            try {
                loader = Reflection.clazz("net.neoforged.fml.loading.FMLLoader").get("getCurrent()");
            } catch (NoSuchMethodException ignored) {
                // Older FML also discovers this SPI, but ModLauncher already runs our update there.
                return;
            }
            if (!"CLIENT".equals(loader.get("getDist()").get().toString())) {
                return;
            }
            Path gameDir = (Path) loader.get("getGameDir()").get();
            Log.setMinecraftLogFile(gameDir);
            // FML consumes --fml.mcVersion before calling bootstrappers; do not parse arguments.
            String version = (String) loader.get("getVersionInfo()").get("mcVersion()").get();
            RuntimePackActivation.enable();
            I18nUpdateMod.init(gameDir, version, "Forge", ModUtil.getModsFromModsFolder(gameDir));
        } catch (Exception e) {
            Log.warning("Failed to initialize NeoForge resource pack update: %s", e);
        }
    }
}
