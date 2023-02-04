package i18nupdatemod.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class FileUtil {
    private static Path resourcePackDirPath;
    private static Path temporaryDirPath;

    public static void setResourcePackDirPath(Path path) {
        saveMakeDir(path);
        resourcePackDirPath = path;
    }

    public static void setTemporaryDirPath(Path temporaryDirPath) {
        saveMakeDir(temporaryDirPath);
        FileUtil.temporaryDirPath = temporaryDirPath;
    }

    private static void saveMakeDir(Path path) {
        try {
            if (!Files.isDirectory(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            Log.warning("Cannot create dir: " + e);
        }
    }

    public static Path getResourcePackPath(String filename) {
        return resourcePackDirPath.resolve(filename);
    }

    public static Path getTemporaryPath(String filename) {
        return temporaryDirPath.resolve(filename);
    }

    public static void syncTmpFile(Path filePath, Path tmpFilePath, boolean saveToGame) throws IOException {
        //Both temp and current file not found
        if (!Files.exists(filePath) && !Files.exists(tmpFilePath)) {
            Log.debug("Both temp and current file not found");
            return;
        }

        int cmp = compareTmpFile(filePath, tmpFilePath);
        Path from, to;
        if (cmp == 0) {
            Log.debug("Temp and current file has already been synchronized");
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

        if (!saveToGame && to == filePath) {
            //Don't save to game
            return;
        }

//        Log.info("Synchronizing: %s -> %s", from, to);
        Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        //Ensure same last modified time
        Files.setLastModifiedTime(to, Files.getLastModifiedTime(from));
        Log.info(String.format("Synchronized: %s -> %s", from, to));
    }

    private static int compareTmpFile(Path filePath, Path tmpFilePath) throws IOException {
        if (!Files.exists(filePath)) {
            return 1;
        }
        if (!Files.exists(tmpFilePath)) {
            return -1;
        }
        return Files.getLastModifiedTime(tmpFilePath).compareTo(Files.getLastModifiedTime(filePath));
    }
}
