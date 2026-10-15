package i18nupdatemod.launchwrapper;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.ModUtil;
import i18nupdatemod.util.Reflection;
import net.minecraft.launchwrapper.ITweaker;
import net.minecraft.launchwrapper.LaunchClassLoader;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

//1.6-1.12.2
public class LaunchWrapperTweaker implements ITweaker {

    @Override
    public void acceptOptions(List<String> args, File gameDir, File assetsDir, String profile) {
        if (isDedicatedServer()) {
            Log.info("Dedicated server detected, skipping the client resource-pack update.");
            return;
        }
        Path gameDirPath = (gameDir == null ? new File(".") : gameDir).toPath();
        Log.setMinecraftLogFile(gameDirPath);
        String mcVersion = getMcVersion();
        if (mcVersion == null) {
            Log.warning("Failed to get minecraft version.");
            return;
        }
        I18nUpdateMod.init(gameDirPath, mcVersion, "Forge", ModUtil.getModsFromModsFolder(gameDirPath));
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

    private boolean isDedicatedServer() {
        // 1.6~1.7.10
        if (isServerSide("cpw.mods.fml.relauncher.FMLLaunchHandler")) {
            return true;
        }
        // 1.8~1.12.2
        return isServerSide("net.minecraftforge.fml.relauncher.FMLLaunchHandler");
    }

    private boolean isServerSide(String launchHandler) {
        try {
            return "SERVER".equalsIgnoreCase(
                    String.valueOf(Reflection.clazz(launchHandler).get("side()").get()));
        } catch (Exception ignored) {
            return false;
        }
    }

    private String getMcVersion() {
        try {
            // 1.6~1.7.10
            // 1.6: https://github.com/MinecraftForge/FML/blob/16launch/common/cpw/mods/fml/relauncher/FMLInjectionData.java#L32
            // 1.7.10: https://github.com/MinecraftForge/MinecraftForge/blob/1.7.10/fml/src/main/java/cpw/mods/fml/relauncher/FMLInjectionData.java#L32
            return (String)
                    Reflection.clazz("cpw.mods.fml.relauncher.FMLInjectionData").get("mccversion").get();
        } catch (Exception ignored) {
        }

        try {
            // 1.8
            // https://github.com/MinecraftForge/FML/blob/1.8/src/main/java/net/minecraftforge/fml/relauncher/FMLInjectionData.java#L32
            return (String)
                    Reflection.clazz("net.minecraftforge.fml.relauncher.FMLInjectionData").get("mccversion").get();
        } catch (Exception ignored) {
        }

        try {
            // 1.8.8~1.12.2
            // 1.8.8: https://github.com/MinecraftForge/MinecraftForge/blob/1.8.8/src/main/java/net/minecraftforge/common/ForgeVersion.java#L42
            // 1.12.2: https://github.com/MinecraftForge/MinecraftForge/blob/1.12.x/src/main/java/net/minecraftforge/common/ForgeVersion.java#L64
            return (String) Reflection.clazz("net.minecraftforge.common.ForgeVersion").get("mcVersion").get();
        } catch (Exception ignored) {
        }
        return null;
    }
}
