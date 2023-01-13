package i18nupdatemod.launchwrapper;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Reflection;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.util.List;

public class LaunchWrapperTweaker implements ITweaker {
    private static final Logger LOGGER = LogManager.getLogger("I18nUpdateMod");

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        String mcVersion = getMcVersion();
        if (mcVersion == null) {
            return;
        }
        I18nUpdateMod.init(gameDir.toPath(), mcVersion, "Forge");
    }

    @Override
    public void injectIntoClassLoader(LaunchClassLoader classLoader) {

    }

    @Override
    public String getLaunchTarget() {
        return "";
    }

    @Override
    public String[] getLaunchArguments() {
        return new String[0];
    }

    private String getMcVersion() {
        try {
            return (String) Reflection.clazz("net.minecraftforge.common.ForgeVersion").get("mcVersion").get();
        } catch (Exception e) {
            LOGGER.warn("Failed to get minecraft version: {}", e.toString());
            return null;
        }
    }
}
