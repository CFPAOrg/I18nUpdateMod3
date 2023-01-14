package i18nupdatemod.core;

import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.AssetUtil;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.TimeUnit;

public class ResourcePack {
    /**
     * Limit update check frequency
     */
    private static final long UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(7);
    public static Path resourcePackPath;
    public static Path temporaryPath;
    private final String filename;
    private final Path filePath;
    private final Path tmpFilePath;

    public ResourcePack(String filename) {
        this.filename = filename;
        this.filePath = resourcePackPath.resolve(filename);
        this.tmpFilePath = temporaryPath.resolve(filename);
        try {
            syncTmpFile();
        } catch (Exception e) {
            I18nUpdateMod.LOGGER.warn("Error while sync temp file {} <-> {}: {}", filePath, tmpFilePath, e);
        }
    }

    private void syncTmpFile() throws IOException {
        //Make tmp dir
        if (!Files.isDirectory(temporaryPath)) {
            Files.createDirectories(temporaryPath);
        }

        //Both temp and current file not found
        if (!Files.exists(filePath) && !Files.exists(tmpFilePath)) {
            I18nUpdateMod.LOGGER.info("Both temp and current file not found");
            return;
        }

        int cmp = compareTmpFile();
        Path from, to;
        if (cmp == 0) {
            I18nUpdateMod.LOGGER.info("Temp and current file has already been synchronized");
            return;
        } else if (cmp < 0) {
            //Current file is newer
            from = filePath;
            to = tmpFilePath;
        } else {
            //Temp file is newer
            from = tmpFilePath;
            to = filePath;
        }

//        I18nUpdateMod.LOGGER.info("Synchronizing: {} -> {}", from, to);
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        //Ensure same last modified time
        Files.setLastModifiedTime(to, Files.getLastModifiedTime(from));
        I18nUpdateMod.LOGGER.info("Synchronized: {} -> {}", from, to);
    }

    private int compareTmpFile() throws IOException {
        if (!Files.exists(filePath)) {
            return 1;
        }
        if (!Files.exists(tmpFilePath)) {
            return -1;
        }
        return Files.getLastModifiedTime(tmpFilePath).compareTo(Files.getLastModifiedTime(filePath));
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
            I18nUpdateMod.LOGGER.info("Local file {} not exist.", tmpFilePath);
            return false;
        }
        //Last update time not exceed gap -> Not Update
        if (Files.getLastModifiedTime(tmpFilePath).to(TimeUnit.MILLISECONDS)
                > System.currentTimeMillis() - UPDATE_TIME_GAP) {
            I18nUpdateMod.LOGGER.info("Local file {} has been updated recently.", tmpFilePath);
            return true;
        }
        //Check Update
        String localMd5 = DigestUtils.md5Hex(Files.newInputStream(tmpFilePath));
        String remoteMd5 = AssetUtil.getString(md5Url);
        I18nUpdateMod.LOGGER.info("{} md5: {}, remote md5: {}", tmpFilePath, localMd5, remoteMd5);
        return localMd5.equalsIgnoreCase(remoteMd5);
    }

    private void downloadFull(String fileUrl) throws IOException {
        Path downloadTmp = temporaryPath.resolve(filename + ".tmp");
        AssetUtil.download(fileUrl, downloadTmp);
        Files.move(downloadTmp, tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
        I18nUpdateMod.LOGGER.info("Updates temp file: {}", tmpFilePath);
        syncTmpFile();
    }
}
