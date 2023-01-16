package i18nupdatemod.core;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.AssetUtil;
import i18nupdatemod.util.FileUtil;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class ResourcePack {
    /**
     * Limit update check frequency
     */
    private static final long UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(7);
    private final String filename;
    private final Path filePath;
    private final Path tmpFilePath;

    public ResourcePack(String filename) {
        this.filename = filename;
        this.filePath = FileUtil.getResourcePackPath(filename);
        this.tmpFilePath = FileUtil.getTemporaryPath(filename);
        try {
            FileUtil.syncTmpFile(filePath, tmpFilePath);
        } catch (Exception e) {
            I18nUpdateMod.LOGGER.warning(
                    String.format("Error while sync temp file %s <-> %s: %s", filePath, tmpFilePath, e));
        }
    }

    public void checkUpdate(String fileUrl, String md5Url) throws IOException {
        if (isUpToDate(md5Url)) {
            I18nUpdateMod.LOGGER.info("Already up to date.");
            return;
        }
        //In this time, we can only download full file
        downloadFull(fileUrl);
        //In the future, we will download patch file and merge local file
    }

    private boolean isUpToDate(String md5Url) throws IOException {
        //Not exist -> Update
        if (!Files.exists(tmpFilePath)) {
            I18nUpdateMod.LOGGER.info(String.format("Local file %s not exist.", tmpFilePath));
            return false;
        }
        //Last update time not exceed gap -> Not Update
        if (Files.getLastModifiedTime(tmpFilePath).to(TimeUnit.MILLISECONDS)
                > System.currentTimeMillis() - UPDATE_TIME_GAP) {
            I18nUpdateMod.LOGGER.info(String.format("Local file %s has been updated recently.", tmpFilePath));
            return true;
        }
        //Check Update
        String localMd5 = DigestUtils.md5Hex(Files.newInputStream(tmpFilePath));
        String remoteMd5 = AssetUtil.getString(md5Url);
        I18nUpdateMod.LOGGER.info(String.format("%s md5: %s, remote md5: %s", tmpFilePath, localMd5, remoteMd5));
        return localMd5.equalsIgnoreCase(remoteMd5);
    }

    private void downloadFull(String fileUrl) throws IOException {
        Path downloadTmp = FileUtil.getTemporaryPath(filename + ".tmp");
        AssetUtil.download(fileUrl, downloadTmp);
        Files.move(downloadTmp, tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
        I18nUpdateMod.LOGGER.info(String.format("Updates temp file: %s", tmpFilePath));
        FileUtil.syncTmpFile(filePath, tmpFilePath);
    }

    public Path getTmpFilePath() {
        return tmpFilePath;
    }
}
