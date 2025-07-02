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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class I18nUpdateMod {
    private static final int DOWNLOAD_WAIT_SECONDS = 10;
    public static final String MOD_ID = "i18nupdatemod";
    public static String MOD_VERSION;

    public static final Gson GSON = new Gson();

    public static void init(Path minecraftPath, String minecraftVersion, String loader) {
        try (InputStream is = I18nUpdateMod.class.getResourceAsStream("/i18nMetaData.json")) {
            MOD_VERSION = GSON.fromJson(new InputStreamReader(is), JsonObject.class).get("version").getAsString();
        } catch (Exception e) {
            Log.warning("Error getting version: " + e);
        }
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

        CompletableFuture<DownloadTaskResult> downloadTask = CompletableFuture.supplyAsync(() -> {
            //Get asset
            GameAssetDetail assets = I18nConfig.getAssetDetail(minecraftVersion, loader);
            if (assets.downloads.isEmpty()) {
                Log.info("No resource pack found for " + minecraftVersion + " with loader " + loader);
                return new DownloadTaskResult(true, assets, "", null);
            }

            //Update resource pack
            List<ResourcePack> languagePacks = new ArrayList<>();
            List<CompletableFuture<Void>> downloadTasks = new ArrayList<>();
            boolean convertNotNeed = assets.downloads.size() == 1 && assets.downloads.get(0).targetVersion.equals(minecraftVersion);
            String applyFileName = assets.downloads.get(0).fileName;
            for (GameAssetDetail.AssetDownloadDetail it : assets.downloads) {
                FileUtil.setTemporaryDirPath(Paths.get(localStorage, "." + MOD_ID, it.targetVersion));
                ResourcePack languagePack = new ResourcePack(it.fileName, convertNotNeed);
                downloadTasks.add(CompletableFuture.runAsync(() -> {
                    try {
                        languagePack.checkUpdate(it.fileUrl, it.md5Url);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }));
                languagePacks.add(languagePack);
            }

            // Wait for all download tasks to complete
            CompletableFuture.allOf(downloadTasks.toArray(new CompletableFuture[0])).join();
            return new DownloadTaskResult(convertNotNeed, assets, applyFileName, languagePacks);
        });

        // Convert and apply resource pack
        Consumer<DownloadTaskResult> convertAndApply = (result) -> {
            if (result.assets.downloads.isEmpty()) {
                return;
            }

            try {
                //Convert resourcepack
                if (!result.convertNotNeed) {
                    FileUtil.setTemporaryDirPath(Paths.get(localStorage, "." + MOD_ID, minecraftVersion));
                    result.applyFileName = result.assets.covertFileName;
                    ResourcePackConverter converter = new ResourcePackConverter(result.languagePacks, result.applyFileName);
                    converter.convert(result.assets.covertPackFormat, getResourcePackDescription(result.assets.downloads));
                }

                //Apply resource pack
                GameConfig config = new GameConfig(minecraftPath.resolve("options.txt"));
                config.addResourcePack("Minecraft-Mod-Language-Modpack",
                        (minecraftMajorVersion <= 12 ? "" : "file/") + result.applyFileName);
                config.writeToFile();
            } catch (Exception e) {
                Log.warning(String.format("Failed to convert and apply resource pack: %s", e));
                //e.printStackTrace();
            }
        };

        // Handle the download task result, if it completes within the timeout, apply the resource pack
        try {
            DownloadTaskResult r = downloadTask.get(DOWNLOAD_WAIT_SECONDS, TimeUnit.SECONDS); // Wait for the download task to complete
            convertAndApply.accept(r);
        } catch (TimeoutException ex) {
            downloadTask.thenAccept(convertAndApply);
            Log.info("Resource pack download take too long, will apply it when download finished.");
        } catch (ExecutionException | InterruptedException ex) {
            Log.warning(String.format("Failed to download resource pack: %s", ex.getCause()));
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

    private static class DownloadTaskResult
    {
        public boolean convertNotNeed;
        public GameAssetDetail assets;
        public String applyFileName;
        public List<ResourcePack> languagePacks;

        public DownloadTaskResult(boolean convertNotNeed, GameAssetDetail assets, String applyFileName, List<ResourcePack> languagePacks) {
            this.convertNotNeed = convertNotNeed;
            this.assets = assets;
            this.applyFileName = applyFileName;
            this.languagePacks = languagePacks;
        }
    }
}