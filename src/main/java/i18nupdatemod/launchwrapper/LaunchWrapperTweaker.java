package i18nupdatemod.launchwrapper;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Reflection;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;

//1.6-1.12.2
public class LaunchWrapperTweaker implements ITweaker {
    private static final Logger LOGGER = Logger.getLogger("I18nUpdateMod");

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
            //1.8.8-1.12.2
            return (String) Reflection.clazz("net.minecraftforge.common.ForgeVersion").get("mcVersion").get();
        } catch (Exception ignored) {
        }

        try {
            //1.6-1.7.10
            //1.6: https://github.com/MinecraftForge/FML/blob/902772ed0cb6c22c4cd7ad9b0ec7a02961b5e016/common/cpw/mods/fml/relauncher/FMLInjectionData.java#L32
            //1.7.10: https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/fml/src/main/java/cpw/mods/fml/relauncher/FMLInjectionData.java#L32
            return (String)
                    Reflection.clazz("cpw.mods.fml.relauncher.FMLInjectionData").get("mccversion").get();
        } catch (Exception ignored) {
        }

        try {
            //1.8
            //https://github.com/MinecraftForge/FML/blob/d4ded9d6e218ac097990e836676bbe22b47e5966/src/main/java/net/minecraftforge/fml/relauncher/FMLInjectionData.java#L32
            return (String)
                    Reflection.clazz("net.minecraftforge.fml.relauncher.FMLInjectionData").get("mccversion").get();
        } catch (Exception ignored) {
        }
        LOGGER.warning("Failed to get minecraft version.");
        return null;
    }
}
