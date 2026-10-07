package i18nupdatemod.core.v1;

import i18nupdatemod.util.Log;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public class LegacyFileUtil {
    public static void syncIfNewer(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            return;
        }
        if (Files.exists(target)
                && Files.getLastModifiedTime(target).compareTo(Files.getLastModifiedTime(source)) >= 0) {
            Log.debug("Temp and current file has already been synchronized");
            return;
        }
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        Files.setLastModifiedTime(target, Files.getLastModifiedTime(source));
        Log.info(String.format("Synchronized: %s -> %s", source, target));
    }
}
