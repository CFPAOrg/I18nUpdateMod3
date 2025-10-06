package i18nupdatemod;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import i18nupdatemod.core.GameConfig;
import i18nupdatemod.core.I18nConfig;
import i18nupdatemod.core.ResourcePack;
import i18nupdatemod.core.ResourcePackConverter;
import i18nupdatemod.entity.GameAssetDetail;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class I18nUpdateMod {
    public static final String MOD_ID = "i18nupdatemod";
    public static String MOD_VERSION;

    public static final Gson GSON = new Gson();

    public static void init(Path minecraftPath, String minecraftVersion, String loader, @NotNull HashSet<String> modDomainsSet) {
        long startTime = System.nanoTime();
        try (InputStream is = I18nUpdateMod.class.getResourceAsStream("/i18nMetaData.json")) {
            MOD_VERSION = GSON.fromJson(new InputStreamReader(is), JsonObject.class).get("version").getAsString();
        } catch (Exception e) {
            Log.warning("Error getting version: " + e);
        }

        //Log.debug("modList from loader:" + modDomainsSet);
        if ("Forge".equals(loader)) {
            modDomainsSet = getModDomainsFromModsFolder(minecraftPath, minecraftVersion, loader);
            //Log.debug("modList from mods folder: " + modDomainsSet);

        }
        modDomainsSet.remove("i18nupdatemod");

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

        FileUtil.setResourcePackDirPath(minecraftPath.resolve("resourcepacks"));

        int minecraftMajorVersion = Integer.parseInt(minecraftVersion.split("\\.")[1]);

        try {
            //Get asset
            GameAssetDetail assets = I18nConfig.getAssetDetail(minecraftVersion, loader);

            //Update resource pack
            List<ResourcePack> languagePacks = new ArrayList<>();
            for (GameAssetDetail.AssetDownloadDetail it : assets.downloads) {
                FileUtil.setTemporaryDirPath(Paths.get(localStorage, "." + MOD_ID, it.targetVersion));
                ResourcePack languagePack = new ResourcePack(it.fileName);
                languagePack.checkUpdate(it.fileUrl, it.md5Url);
                languagePacks.add(languagePack);
            }

            //Convert resourcepack
            FileUtil.setTemporaryDirPath(Paths.get(localStorage, "." + MOD_ID, minecraftVersion));
            String applyFileName = assets.covertFileName;
            ResourcePackConverter converter = new ResourcePackConverter(languagePacks, applyFileName);
            converter.convert(assets.covertPackFormat, getResourcePackDescription(assets.downloads), modDomainsSet);

            //Apply resource pack
            GameConfig config = new GameConfig(minecraftPath.resolve("options.txt"));
            config.addResourcePack("Minecraft-Mod-Language-Modpack",
                    (minecraftMajorVersion <= 12 ? "" : "file/") + applyFileName);
            config.writeToFile();
            long endTime = System.nanoTime();
            Log.info(String.format("I18nUpdateMod finished in %.2f ms", (endTime - startTime) / 1000000.0));
        } catch (Exception e) {
            Log.warning(String.format("Failed to update resource pack: %s", e));
//            e.printStackTrace();
        }
    }

    private static String getResourcePackDescription(List<GameAssetDetail.AssetDownloadDetail> downloads) {
        return downloads.size() > 1 ?
                String.format("该包由%s版本合并\n作者：CFPA团队及汉化项目贡献者",
                        downloads.stream().map(it -> it.targetVersion).collect(Collectors.joining("和"))) :
                String.format("该包对应的官方支持版本为%s\n作者：CFPA团队及汉化项目贡献者",
                        downloads.get(0).targetVersion);

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

    private static HashSet<String> getModDomainsFromModsFolder(Path minecraftPath, String minecraftVersion, String loader) {
        HashSet<String> modDomainSet = new HashSet<>();
        Path modsPath = minecraftPath.resolve("mods");
        String[] modsNamesList = modsPath.toFile().list((dir, name) -> name.endsWith(".jar"));
        if (modsNamesList != null) {
            for (String name : modsNamesList) {
                modDomainSet.addAll(getModDomainFromJar(modsPath.resolve(name).toFile()));
            }
        }
        return modDomainSet;
    }

    private static HashSet<String> getModDomainFromJar(File modPath) {
        Log.debug(String.format("Get mod domain from %s", modPath));
        HashSet<String> modList = new HashSet<>();
        try (FileInputStream fis = new FileInputStream(modPath)) {
            modList.addAll(getModDomainFromStream(fis, modPath.getName()));
        } catch (Exception e) {
            Log.warning(String.format("Failed to read jar %s: %s", modPath, e));
        }
        return modList;
    }

    private static HashSet<String> getModDomainFromStream(InputStream input, String sourceName) throws IOException {
        HashSet<String> modList = new HashSet<>();
        try (JarInputStream jis = new JarInputStream(input)) {
            JarEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = jis.getNextJarEntry()) != null) {
                String path = entry.getName();

                // 匹配 assets/<domain>/
                if (path.startsWith("assets/")) {
                    String[] parts = path.split("/");
                    if (parts.length >= 2) {
                        modList.add(parts[1]);
                    }
                } else if (path.endsWith(".jar")) {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int bytesRead;
                        while ((bytesRead = jis.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }

                        byte[] innerJarBytes = baos.toByteArray();
                        try (ByteArrayInputStream innerStream = new ByteArrayInputStream(innerJarBytes)) {
                            modList.addAll(getModDomainFromStream(innerStream, path));
                        }
                    } catch (Exception innerEx) {
                        Log.warning(String.format("Failed to parse nested jar %s inside %s: %s", path, sourceName, innerEx));
                    }
                }
            }
        }
        return modList;
    }


}