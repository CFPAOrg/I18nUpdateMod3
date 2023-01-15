package i18nupdatemod.modlauncher;

import cpw.mods.modlauncher.Launcher;
import cpw.mods.modlauncher.api.IEnvironment;
import cpw.mods.modlauncher.api.ITransformationService;
import cpw.mods.modlauncher.api.ITransformer;
import cpw.mods.modlauncher.api.IncompatibleEnvironmentException;
import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Reflection;
import org.jetbrains.annotations.NotNull;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Logger;

//1.13-latest
public class ModLauncherService implements ITransformationService {
    private static final Logger LOGGER = Logger.getLogger("I18nUpdateMod");

    @Override
    public @NotNull String name() {
        return "I18nUpdateMod";
    }

    @Override
    public void initialize(IEnvironment environment) {
        Optional<Path> minecraftPath = environment.getProperty(IEnvironment.Keys.GAMEDIR.get());
        if (!minecraftPath.isPresent()) {
            LOGGER.warning("Minecraft path not found");
            return;
        }
        String minecraftVersion = getMinecraftVersion();
        if (minecraftVersion == null) {
            LOGGER.warning("Minecraft version not found");
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
            LOGGER.warning(String.format("Error getting minecraft version: %s", e));
        }
        return null;
    }
}
