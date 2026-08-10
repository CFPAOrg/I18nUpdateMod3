package i18nupdatemod.neoforge;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.ModUtil;
import i18nupdatemod.util.Reflection;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;

import java.nio.file.Path;

/**
 * NeoForge javafml entrypoint.
 * <p>
 * Required from FML 11 (Minecraft 26.1) onwards, where ModLauncher was removed and
 * {@link i18nupdatemod.modlauncher.ModLauncherService} is therefore never invoked. Older NeoForge
 * still loads this class from the same universal jar, and there FMLLoader exposes static
 * {@code getGamePath()}/{@code versionInfo()} instead of the current instance methods, so both
 * shapes are resolved reflectively.
 */
@Mod(value = I18nUpdateMod.MOD_ID, dist = Dist.CLIENT)
public final class NeoForgeMod {
    private static final String FML_LOADER = "net.neoforged.fml.loading.FMLLoader";

    public NeoForgeMod() {
        try {
            if (I18nUpdateMod.isInitialized()) {
                // ModLauncherService already did the work on an older NeoForge.
                Log.debug("Already initialized, skipping NeoForge entrypoint");
                return;
            }

            Path gameDir = getGameDir();
            if (gameDir == null) {
                Log.warning("Minecraft path not found");
                return;
            }
            Log.setMinecraftLogFile(gameDir);
            String mcVersion = getMcVersion();
            if (mcVersion == null) {
                Log.warning("Minecraft version not found");
                return;
            }

            I18nUpdateMod.init(gameDir, mcVersion, "NeoForge",
                    ModUtil.getModDomainsFromModsFolder(gameDir, mcVersion, "NeoForge"));
        } catch (Exception e) {
            Log.warning("Failed to initialize NeoForge entrypoint: " + e);
        }
    }

    /**
     * @return game directory, or null if neither API shape is present
     */
    private static Path getGameDir() {
        // FML 11+ (Minecraft 26.1+)
        try {
            return (Path) Reflection.clazz(FML_LOADER)
                    .get("getCurrent()")
                    .get("getGameDir()")
                    .get();
        } catch (Exception ignored) {
        }
        // FML 1~4 (Minecraft 1.20.1~1.21.x)
        try {
            return (Path) Reflection.clazz(FML_LOADER).get("getGamePath()").get();
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * @return Minecraft version, or null if neither API shape is present
     */
    private static String getMcVersion() {
        // FML 11+ (Minecraft 26.1+)
        try {
            return (String) Reflection.clazz(FML_LOADER)
                    .get("getCurrent()")
                    .get("getVersionInfo()")
                    .get("mcVersion()")
                    .get();
        } catch (Exception ignored) {
        }
        // FML 1~4 (Minecraft 1.20.1~1.21.x)
        try {
            return (String) Reflection.clazz(FML_LOADER)
                    .get("versionInfo()")
                    .get("mcVersion()")
                    .get();
        } catch (Exception ignored) {
        }
        return null;
    }
}
