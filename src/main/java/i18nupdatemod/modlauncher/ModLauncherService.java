package i18nupdatemod.modlauncher;

import com.google.gson.JsonObject;
import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.Reflection;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static i18nupdatemod.I18nUpdateMod.GSON;

//1.13-latest
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
        I18nUpdateMod.init(minecraftPath.get(), minecraftVersion, "Forge");
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
