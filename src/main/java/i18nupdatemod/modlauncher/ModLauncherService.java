package i18nupdatemod.modlauncher;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Reflection;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ModLauncherService implements ITransformationService {
    private static final Logger LOGGER = LogManager.getLogger("I18nUpdateMod");

    @Override
    public @NotNull String name() {
        return "I18nUpdateMod";
    }

    @Override
    public void initialize(IEnvironment environment) {
        Optional<Path> minecraftPath = environment.getProperty(IEnvironment.Keys.GAMEDIR.get());
        if (!minecraftPath.isPresent()) {
            LOGGER.warn("Minecraft path not found");
            return;
        }
        String minecraftVersion = getMinecraftVersion();
        if (minecraftVersion == null) {
            LOGGER.warn("Minecraft version not found");
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
        try {
            String[] args = (String[]) Reflection.clazz(Launcher.INSTANCE).get("argumentHandler").get("args").get();
            for (int i = 0; i < args.length - 1; ++i) {
                if (args[i].equalsIgnoreCase("--fml.mcversion")) {
                    return args[i + 1];
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error getting minecraft version: {}", e.toString());
        }
        return null;
    }
}
