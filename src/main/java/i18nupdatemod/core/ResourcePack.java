package i18nupdatemod.core;

import i18nupdatemod.util.AssetUtil;
import i18nupdatemod.util.DigestUtil;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeUnit;

public class ResourcePack {
    /**
     * Limit update check frequency
     */
    private static final long UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(1);
    private final String filename;
    private final Path filePath;
    private final Path tmpFilePath;
    private final boolean saveToGame;
    private String remoteMd5;

    public ResourcePack(String filename, boolean saveToGame) {
        //If target version is not current version, not save
        this.saveToGame = saveToGame;
        this.filename = filename;
        this.filePath = FileUtil.getResourcePackPath(filename);
        this.tmpFilePath = FileUtil.getTemporaryPath(filename);
        try {
            FileUtil.syncTmpFile(filePath, tmpFilePath, saveToGame);
        } catch (Exception e) {
            Log.warning(
                    String.format("Error while sync temp file %s <-> %s: %s", filePath, tmpFilePath, e));
        }
    }

    public void checkUpdate(String fileUrl, String md5Url) throws IOException, NoSuchAlgorithmException, URISyntaxException {
        if (isUpToDate(md5Url)) {
            Log.debug("Already up to date.");
            return;
        }
        int retryCount = 3;
        while (retryCount > 0) {
            try {
                //In this time, we can only download full file
                downloadFull(fileUrl, md5Url);
                //In the future, we will download patch file and merge local file
                return;
            } catch (Exception e) {
                retryCount--;
                if (retryCount == 0) {
                    Log.debug(String.format("Failed to download resource pack at %s after 3 attempts: %s", fileUrl, e.getMessage()));
                    Log.warning("Failed to download resource pack.");
                    return;
                }
            }
        }
    }

    private boolean isUpToDate(String md5Url) throws IOException, URISyntaxException, NoSuchAlgorithmException {
        //Not exist -> Update
        if (!Files.exists(tmpFilePath)) {
            Log.debug("Local file %s not exist.", tmpFilePath);
            return false;
        }
        //Last update time not exceed gap -> Not Update
        if (Files.getLastModifiedTime(tmpFilePath).to(TimeUnit.MILLISECONDS)
                > System.currentTimeMillis() - UPDATE_TIME_GAP) {
            Log.debug("Local file %s has been updated recently.", tmpFilePath);
            return true;
        }
        //Check Update
        return checkMd5(tmpFilePath, md5Url);
    }

    private boolean checkMd5(Path localFile, String md5Url) throws IOException, URISyntaxException, NoSuchAlgorithmException {
        String localMd5 = DigestUtil.md5Hex(localFile);
        if (remoteMd5 == null) {
            remoteMd5 = AssetUtil.getString(md5Url);
        }
        Log.debug("%s md5: %s, remote md5: %s", localFile, localMd5, remoteMd5);
        return localMd5.equalsIgnoreCase(remoteMd5);
    }

    private void downloadFull(String fileUrl, String md5Url) throws IOException {
        try {
            Path downloadTmp = FileUtil.getTemporaryPath(filename + ".tmp");
            AssetUtil.download(fileUrl, downloadTmp);
            if (!checkMd5(downloadTmp, md5Url)) {
                throw new IOException("Download MD5 not match");
            }
            Files.move(downloadTmp, tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
            Log.debug(String.format("Updates temp file: %s", tmpFilePath));
        } catch (Exception e) {
            Log.warning("Error while downloading: %s", e);
        }
        if (!Files.exists(tmpFilePath)) {
            throw new FileNotFoundException("Tmp file not found.");
        }
        FileUtil.syncTmpFile(filePath, tmpFilePath, saveToGame);
    }

    public Path getTmpFilePath() {
        return tmpFilePath;
    }

    public String getFilename() {
        return filename;
    }
}
