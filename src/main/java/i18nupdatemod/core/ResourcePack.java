package i18nupdatemod.core;

import i18nupdatemod.entity.GameAssetDetail;
import i18nupdatemod.util.AssetUtil;
import i18nupdatemod.util.DigestUtil;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ResourcePack {
    /**
     * Limit update check frequency
     */
    private static final long UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(1);
    private final String filename;
    private final Path filePath;
    private final Path tmpFilePath;

    public ResourcePack(String filename) {
        //If target version is not current version, not save
        this.filename = filename;
        this.filePath = FileUtil.getResourcePackPath(filename);
        this.tmpFilePath = FileUtil.getTemporaryPath(filename);
        try {
            FileUtil.syncTmpFile(filePath, tmpFilePath);
        } catch (Exception e) {
            Log.warning(
                    String.format("Error while sync temp file %s <-> %s: %s", filePath, tmpFilePath, e));
        }
    }

    public void checkUpdate(List<GameAssetDetail.AssetDownloadDetail.DownloadSource> sources)
            throws IOException, URISyntaxException, NoSuchAlgorithmException {
        if (sources == null || sources.isEmpty()) {
            throw new IOException("No download sources configured for " + filename);
        }
        if (isRecentlyUpdated()) {
            Log.debug("Already up to date.");
            return;
        }

        Exception failure = null;
        for (GameAssetDetail.AssetDownloadDetail.DownloadSource source : sources) {
            try {
                if (Files.exists(tmpFilePath) && checkMd5(tmpFilePath, source.md5Url)) {
                    Log.debug("Already up to date.");
                    return;
                }
                downloadFull(source.fileUrl, source.md5Url);
                return;
            } catch (Exception e) {
                failure = e;
                Log.warning("Failed resource source %s: %s", source.fileUrl, e);
            }
        }

        if (Files.exists(tmpFilePath)) {
            Log.warning("All resource sources failed; using cached file %s", tmpFilePath);
            return;
        }
        throw new IOException("Failed to download resource pack from all configured sources", failure);
    }

    private boolean isRecentlyUpdated() throws IOException {
        return Files.exists(tmpFilePath)
                && Files.getLastModifiedTime(tmpFilePath).to(TimeUnit.MILLISECONDS)
                > System.currentTimeMillis() - UPDATE_TIME_GAP;
    }

    private boolean checkMd5(Path localFile, String md5Url) throws IOException, URISyntaxException, NoSuchAlgorithmException {
        String localMd5 = DigestUtil.md5Hex(localFile);
        String remoteMd5 = AssetUtil.getString(md5Url).trim();
        Log.debug("%s md5: %s, remote md5: %s", localFile, localMd5, remoteMd5);
        return localMd5.equalsIgnoreCase(remoteMd5);
    }

    private void downloadFull(String fileUrl, String md5Url) throws IOException {
        Path downloadTmp = FileUtil.getTemporaryPath(filename + ".tmp");
        try {
            AssetUtil.download(fileUrl, downloadTmp);
            if (!checkMd5(downloadTmp, md5Url)) {
                throw new IOException("Download MD5 not match");
            }
            Files.move(downloadTmp, tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
            Log.debug(String.format("Updates temp file: %s", tmpFilePath));
        } catch (Exception e) {
            try {
                Files.deleteIfExists(downloadTmp);
            } catch (IOException ignored) {
            }
            throw new IOException("Failed to download resource pack from " + fileUrl, e);
        }
        FileUtil.syncTmpFile(filePath, tmpFilePath);
    }

    public Path getTmpFilePath() {
        return tmpFilePath;
    }

    public String getFilename() {
        return filename;
    }
}
