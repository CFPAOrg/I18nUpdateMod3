package i18nupdatemod.neoforge;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.ModUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLLoader;

import java.nio.file.Path;

@Mod(value = I18nUpdateMod.MOD_ID, dist = Dist.CLIENT)
public class NeoForgeMod {
    private static final String LOADER_NAME = "NeoForge";

    public NeoForgeMod() {
        FMLLoader loader = FMLLoader.getCurrent();
        if (loader == null) {
            Log.warning("NeoForge loader not found");
            return;
        }

        Path minecraftPath = loader.getGameDir();
        if (minecraftPath == null) {
            Log.warning("Minecraft path not found");
            return;
        }
        Log.setMinecraftLogFile(minecraftPath);

        String minecraftVersion = getMinecraftVersion(loader);
        if (minecraftVersion == null) {
            Log.warning("Minecraft version not found");
            return;
        }

        I18nUpdateMod.init(minecraftPath, minecraftVersion, LOADER_NAME,
                ModUtil.getModDomainsFromModsFolder(minecraftPath, minecraftVersion, LOADER_NAME));
    }

    private String getMinecraftVersion(FMLLoader loader) {
        String version = loader.getVersionInfo() == null ? null : loader.getVersionInfo().mcVersion();
        if (version != null && !version.isEmpty()) {
            return version;
        }

        return getMinecraftVersion(loader.getProgramArgs().getArguments());
    }

    private String getMinecraftVersion(String[] args) {
        for (int i = 0; i < args.length - 1; ++i) {
            if (args[i].equalsIgnoreCase("--fml.mcversion")) {
                return args[i + 1];
            }
        }
        return null;
    }
}
