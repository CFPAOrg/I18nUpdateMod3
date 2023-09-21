package i18nupdatemod.core;

import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.StringBuilderWriter;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class ResourcePack {
    /**
     * Limit update check frequency
     */
    private static final long UPDATE_TIME_GAP = TimeUnit.DAYS.toMillis(1);
    private static final ThreadLocal<char[]> charBuffer = ThreadLocal.withInitial(() -> new char[8192]);
    private static final ThreadLocal<byte[]> byteBuffer = ThreadLocal.withInitial(() -> new byte[8192]);
    static char[] DIGITS_UPPER = { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F' };
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

    public void checkUpdate(String fileUrl, String md5Url) throws IOException {
        if (isUpToDate(md5Url)) {
            Log.debug("Already up to date.");
            return;
        }
        //In this time, we can only download full file
        downloadFull(fileUrl, md5Url);
        //In the future, we will download patch file and merge local file
    }

    private boolean isUpToDate(String md5Url) throws IOException {
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

    private boolean checkMd5(Path localFile, String md5Url) throws IOException {
        String localMd5 = md5Hex(Files.newInputStream(localFile));
        if (remoteMd5 == null) {
            remoteMd5 = getString(md5Url);
        }
        Log.debug("%s md5: %s, remote md5: %s", localFile, localMd5, remoteMd5);
        return localMd5.equalsIgnoreCase(remoteMd5);
    }

    private void downloadFull(String fileUrl, String md5Url) throws IOException {
        try {
            Path downloadTmp = FileUtil.getTemporaryPath(filename + ".tmp");
            File file = downloadTmp.toFile();
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } else {
                if (!file.canWrite()) throw new IllegalStateException("Temp file " + downloadTmp + " can't be write");
            }
            download(fileUrl, downloadTmp);
            if (!checkMd5(downloadTmp, md5Url)) {
                throw new IOException("Download MD5 not match");
            }
            Files.copy(downloadTmp, tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
            // Files.move(downloadTmp, tmpFilePath, StandardCopyOption.REPLACE_EXISTING);
            Log.debug(String.format("Updates temp file: %s", tmpFilePath));
        } catch (Exception e) {
            Log.warning("Error which downloading: ", e);
        }
        Log.debug(tmpFilePath.toString());
        if (!Files.exists(tmpFilePath)) {
            throw new FileNotFoundException("Temp file not found.");
        }
        FileUtil.syncTmpFile(filePath, tmpFilePath, saveToGame);
    }

    public Path getTmpFilePath() {
        return tmpFilePath;
    }

    public String getFilename() {
        return filename;
    }

    private static void download(String url, Path localFile) throws IOException {
        Log.info("Downloading: %s -> %s", url, localFile);

        URLConnection connection = new URL(url).openConnection();
        connection.setConnectTimeout(3000); // 3s
        connection.setReadTimeout(33000); // 33s

        File dest = localFile.toFile();
        if (dest.exists()) {
            if (!dest.canWrite()) throw new IllegalArgumentException("The file " + localFile + " can't be write");
        } else {
            dest.getParentFile().mkdirs();
        }

        OutputStream output = new FileOutputStream(dest, false);
        InputStream input = connection.getInputStream();

        int n;
        long count = 0;
        byte[] buffer = ResourcePack.byteBuffer.get();
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }

        if (count > Integer.MAX_VALUE) throw new IllegalStateException("Data too large: " + count);
    }

    private static String getString(String url_) throws IOException {
        URL url = new URL(url_);
        try (InputStream input = url.openStream()) {
            final StringBuilderWriter sw = new StringBuilderWriter();
            final InputStreamReader isr = new InputStreamReader(input, StandardCharsets.UTF_8);
            int n;
            long count = 0;
            char[] buffer = ResourcePack.charBuffer.get();

            while (-1 != (n = isr.read(buffer))) {
                sw.write(buffer, 0, n);
                count += n;
            }

            if (count > Integer.MAX_VALUE) return "";

            return sw.toString();
        }
    }

    private static String md5Hex(InputStream input) {
        try {
            byte[] data;
            MessageDigest digest = MessageDigest.getInstance("MD5");

            final byte[] buffer = new byte[1024];
            int read = input.read(buffer, 0, 1024);

            while (read > -1) {
                digest.update(buffer, 0, read);
                read = input.read(buffer, 0, 1024);
            }

            data = digest.digest();
            char[] out = new char[data.length << 1];
            for (int i = 0, j = 0; i < data.length; i++) {
                out[j++] = DIGITS_UPPER[(0xF0 & data[i]) >>> 4];
                out[j++] = DIGITS_UPPER[0x0F & data[i]];
            }
            return new String(out);
        } catch (Exception ignored) {}
        return "";
    }
}
