package i18nupdatemod.core.v1;

import i18nupdatemod.core.net.ResourcePackHttp;
import i18nupdatemod.util.DigestUtil;
import i18nupdatemod.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
    private final Path filePath;
    private final Path tmpFilePath;
    private String remoteMd5;

    public ResourcePack(Path filePath, Path tmpFilePath) {
        this.filePath = filePath;
        this.tmpFilePath = tmpFilePath;
        try {
            LegacyFileUtil.syncIfNewer(filePath, tmpFilePath);
        } catch (Exception e) {
            Log.warning(
                    String.format("Error while sync temp file %s <-> %s: %s", filePath, tmpFilePath, e));
        }
    }

    public void checkUpdate(ResourcePackHttp source, String fileName, String md5FileName)
            throws IOException, NoSuchAlgorithmException {
        if (isUpToDate(source, md5FileName)) {
            Log.debug("Already up to date.");
            return;
        }
        //In this time, we can only download full file
        downloadFull(source, fileName, md5FileName);
        //In the future, we will download patch file and merge local file
    }

    private boolean isUpToDate(ResourcePackHttp source, String md5FileName)
            throws IOException, NoSuchAlgorithmException {
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
        return checkMd5(source, tmpFilePath, md5FileName);
    }

    private boolean checkMd5(ResourcePackHttp source, Path localFile, String md5FileName)
            throws IOException, NoSuchAlgorithmException {
        String localMd5 = DigestUtil.md5Hex(localFile);
        if (remoteMd5 == null) {
            try (InputStream input = source.open(md5FileName)) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    if (count > 0) {
                        output.write(buffer, 0, count);
                    }
                }
                remoteMd5 = new String(output.toByteArray(), StandardCharsets.UTF_8).trim();
            }
        }
        Log.debug("%s md5: %s, remote md5: %s", localFile, localMd5, remoteMd5);
        return localMd5.equalsIgnoreCase(remoteMd5);
    }

    private void downloadFull(ResourcePackHttp source, String fileName, String md5FileName)
            throws IOException {
        try {
            Path downloadTmp = tmpFilePath.resolveSibling(tmpFilePath.getFileName().toString() + ".tmp");
            try (InputStream input = source.open(fileName)) {
                Files.copy(input, downloadTmp, StandardCopyOption.REPLACE_EXISTING);
            }
            if (!checkMd5(source, downloadTmp, md5FileName)) {
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
        LegacyFileUtil.syncIfNewer(filePath, tmpFilePath);
    }

    public Path getTmpFilePath() {
        return tmpFilePath;
    }

}
