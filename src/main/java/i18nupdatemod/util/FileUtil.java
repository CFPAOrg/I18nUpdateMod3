package i18nupdatemod.util;

import java.nio.file.Files;
import java.nio.file.Path;

public class FileUtil {
    public static void safeCreateDir(Path path) {
        try {
            if (!Files.isDirectory(path)) {
                Files.createDirectories(path);
            }
        } catch (Exception e) {
            Log.warning("Cannot create dir: " + e);
        }
    }

}
