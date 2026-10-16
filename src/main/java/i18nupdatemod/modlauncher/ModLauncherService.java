package i18nupdatemod.modlauncher;

import com.google.gson.JsonObject;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.core.RuntimePackActivation;
import i18nupdatemod.util.ModUtil;
import i18nupdatemod.util.Reflection;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.*;

import static i18nupdatemod.I18nUpdateMod.GSON;

//MinecraftForge: 1.13-latest
//NeoForge: 1.20.1-1.21.8
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
        if (isDedicatedServer(environment)) {
            Log.info("Dedicated server detected, skipping the client resource-pack update.");
            return;
        }
        if (!I18nUpdateMod.prepare(minecraftPath.get())) {
            return;
        }
        String minecraftVersion = getMinecraftVersion();
        if (minecraftVersion == null) {
            Log.warning("Minecraft version not found");
            return;
        }
        boolean neoForge;
        try {
            Class.forName("net.neoforged.fml.loading.FMLLoader", false, getClass().getClassLoader());
            neoForge = true;
            RuntimePackActivation.enable();
        } catch (ClassNotFoundException ignored) {
            neoForge = false;
        }
        I18nUpdateMod.init(minecraftPath.get(), minecraftVersion, neoForge ? "NeoForge" : "Forge",
                ModUtil.getModsFromModsFolder(minecraftPath.get()));
    }

    @Override
    public void beginScanning(IEnvironment environment) {

    }

    @Override
    public void onLoad(IEnvironment env, Set<String> otherServices) throws IncompatibleEnvironmentException {

    }

    @Override
    public @NotNull List<ITransformer> transformers() {
        return Collections.singletonList(ModLauncherPackTransformer.create());
    }

    private static boolean isDedicatedServer(IEnvironment environment) {
        // Forge and NeoForge name every dedicated-server launch target "*server".
        return environment.getProperty(IEnvironment.Keys.LAUNCHTARGET.get())
                .map(target -> target.toLowerCase(Locale.ROOT).contains("server"))
                .orElse(false);
    }

    private String getMinecraftVersion() {
        // MinecraftForge 1.13~1.20.2
        // NeoForge 1.20.1~1.21.8
        try {
            String[] args = (String[]) Reflection.clazz(Launcher.INSTANCE).get("argumentHandler").get("args").get();
            for (int i = 0; i < args.length - 1; ++i) {
                if (args[i].equalsIgnoreCase("--fml.mcversion")) {
                    return args[i + 1];
                }
            }
        } catch (Exception e) {
            Log.warning("Error getting minecraft version: %s", e);
        }

        // MinecraftForge 1.20.3~
        // 1.20.3: https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/fmlloader/src/main/java/net/minecraftforge/fml/loading/VersionInfo.java
        try {
            Class<?> clazz = Class.forName("net.minecraftforge.fml.loading.FMLLoader");
            try (InputStream is = clazz.getResourceAsStream("/forge_version.json")) {
                return GSON.fromJson(new InputStreamReader(is), JsonObject.class).get("mc").getAsString();
            }
        } catch (Exception e) {
            Log.warning("Error getting minecraft version: %s", e);
        }
        return null;
    }
}
