package i18nupdatemod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import i18nupdatemod.core.GameConfig;
import i18nupdatemod.core.ResourcePackUpdater;
import i18nupdatemod.core.RuntimePackActivation;
import i18nupdatemod.entity.ModTranslation;
import i18nupdatemod.util.Log;
import i18nupdatemod.util.Version;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class I18nUpdateMod {
    public static final String MOD_ID = "i18nupdatemod";
    public static String MOD_VERSION;

    public static final Gson GSON = new Gson();

    public static void init(Path minecraftPath, String minecraftVersion, String loader, @NotNull List<ModTranslation> mods) {
        try (InputStream is = I18nUpdateMod.class.getResourceAsStream("/i18nMetaData.json")) {
            MOD_VERSION = GSON.fromJson(new InputStreamReader(is), JsonObject.class).get("version").getAsString();
        } catch (Exception e) {
            Log.warning("Error getting version: " + e);
        }

        mods.removeIf(mod -> MOD_ID.equals(mod.namespace));

        Log.info(String.format("I18nUpdate Mod %s is loaded in %s with %s", MOD_VERSION, minecraftVersion, loader));
        Log.debug(String.format("Minecraft path: %s", minecraftPath));
        String localStorage = getLocalStoragePos(minecraftPath);
        Log.debug(String.format("Local Storage Pos: %s", localStorage));

        try {
            Class.forName("com.netease.mc.mod.network.common.Library");
            Log.warning("I18nUpdateMod will get resource pack from Internet, whose content is uncontrolled.");
            Log.warning("This behavior contraries to Netease Minecraft developer content review rule: " +
                    "forbidden the content in game not match the content for reviewing.");
            Log.warning("To follow this rule, I18nUpdateMod won't download any thing.");
            Log.warning("I18nUpdateMod会从互联网获取内容不可控的资源包。");
            Log.warning("这一行为违背了网易我的世界「开发者内容审核制度」：禁止上传与提审内容不一致的游戏内容。");
            Log.warning("为了遵循这一制度，I18nUpdateMod不会下载任何内容。");
            return;
        } catch (ClassNotFoundException ignored) {
        }

        try {
            Path resourcePackDirectory = minecraftPath.resolve("resourcepacks");
            Path cacheRoot = Paths.get(localStorage, "." + MOD_ID);
            Path convertedPack = ResourcePackUpdater.update(
                    minecraftVersion, loader, mods, resourcePackDirectory, cacheRoot);

            // NeoForge applies the selection after its resource repository is populated.
            if (RuntimePackActivation.isEnabled()) {
                RuntimePackActivation.prepare(minecraftPath, convertedPack);
                return;
            }

            //Apply resource pack
            GameConfig config = new GameConfig(minecraftPath.resolve("options.txt"));
            config.addResourcePack("Minecraft-Mod-Language-Modpack",
                    (Version.from(minecraftVersion).compareTo(Version.from("1.13")) < 0 ? "" : "file/")
                            + convertedPack.getFileName().toString());
            config.writeToFile();
        } catch (Exception e) {
            Log.warning(String.format("Failed to update resource pack: %s", e));
//            e.printStackTrace();
        }
    }


    public static String getLocalStoragePos(Path minecraftPath) {
        Path userHome = Paths.get(System.getProperty("user.home"));
        Path oldPath = userHome.resolve("." + MOD_ID);
        if (Files.exists(oldPath)) {
            return userHome.toString();
        }

        // https://developer.apple.com/documentation/foundation/url/3988452-applicationsupportdirectory#discussion
        String macAppSupport = System.getProperty("os.name").contains("OS X") ?
                userHome.resolve("Library/Application Support").toString() : null;
        String localAppData = System.getenv("LocalAppData");

        // XDG_DATA_HOME fallbacks to ~/.local/share
        // https://specifications.freedesktop.org/basedir-spec/latest/#variables
        String xdgDataHome = System.getenv("XDG_DATA_HOME");
        if (xdgDataHome == null) {
            xdgDataHome = userHome.resolve(".local/share").toString();
        }

        return Stream.of(localAppData, macAppSupport).filter(
                Objects::nonNull
        ).findFirst().orElse(xdgDataHome);
    }

}