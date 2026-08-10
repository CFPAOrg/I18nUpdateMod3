package i18nupdatemod.modlauncher;

import com.google.gson.JsonObject;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.ModUtil;
import i18nupdatemod.util.Reflection;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

import static i18nupdatemod.I18nUpdateMod.GSON;

/**
 * Forge 1.13+ (including Minecraft 26.1) and NeoForge up to Minecraft 1.21.x.
 * <p>
 * Forge still boots through ModLauncher on 26.1 (bootstrap 2.1.8 + modlauncher 10.2.6), so this
 * service remains the Forge entrypoint there. NeoForge's FML 11 dropped ModLauncher entirely, so on
 * NeoForge 26.1+ this service is never loaded and {@link i18nupdatemod.neoforge.NeoForgeMod} takes
 * over instead.
 */
public class ModLauncherService implements ITransformationService {
    @Override
    public @NotNull String name() {
        return "I18nUpdateMod";
    }

    @Override
    public void initialize(IEnvironment environment) {
        Optional<Path> minecraftPath = environment.getProperty(IEnvironment.Keys.GAMEDIR.get());
        if (!minecraftPath.isPresent()) {
            Log.warning("Minecraft path not found");
            return;
        }
        Log.setMinecraftLogFile(minecraftPath.get());
        String minecraftVersion = getMinecraftVersion();
        if (minecraftVersion == null) {
            Log.warning("Minecraft version not found");
            return;
        }
        String loader = isNeoForge() ? "NeoForge" : "Forge";
        I18nUpdateMod.init(minecraftPath.get(), minecraftVersion, loader,
                ModUtil.getModDomainsFromModsFolder(minecraftPath.get(), minecraftVersion, loader));
    }

    @Override
    public void beginScanning(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {

    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return Collections.emptyList();
    }

    private String getMinecraftVersion() {
        // MinecraftForge 1.13~1.20.2
        // NeoForge 1.20.1~
        try {
            String[] args = (String[]) Reflection.clazz(Launcher.INSTANCE).get("argumentHandler").get("args").get();
            for (int i = 0; i < args.length - 1; ++i) {
                if (args[i].equalsIgnoreCase("--fml.mcversion")) {
                    return args[i + 1];
                }
            }
        } catch (Exception | LinkageError e) {
            Log.warning("Error getting minecraft version: %s", e);
        }

        // MinecraftForge 1.20.3~, and the only source on Forge 26.1 where --fml.mcversion is gone
        // 1.20.3: https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/VersionInfo.java
        try {
            // Resolved without running static initializers: only the class's resource root is needed,
            // and initializing it can fail with an Error that would escape into the loader.
            Class<?> clazz = loadClass("net.minecraftforge.fml.loading.FMLLoader");
            if (clazz == null) {
                return null;
            }
            try (InputStream is = clazz.getResourceAsStream("/forge_version.json")) {
                if (is == null) {
                    Log.warning("forge_version.json not found");
                    return null;
                }
                return GSON.fromJson(new InputStreamReader(is), JsonObject.class).get("mc").getAsString();
            }
        } catch (Exception | LinkageError e) {
            Log.warning("Error getting minecraft version: %s", e);
        }
        return null;
    }

    private boolean isNeoForge() {
        return loadClass("net.neoforged.fml.loading.FMLLoader") != null;
    }

    /**
     * @return the class, or null when it is absent or cannot be loaded
     */
    private static Class<?> loadClass(String name) {
        try {
            return Class.forName(name, false, ModLauncherService.class.getClassLoader());
        } catch (ClassNotFoundException ignored) {
            return null;
        } catch (LinkageError e) {
            Log.warning("Error loading %s: %s", name, e);
            return null;
        }
    }
}
