package i18nupdatemod.util;

import com.google.archivepatcher.applier.bsdiff.BsPatch;

import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.nio.file.Path;

public class BsDiffUtil {
    public static void applyPatch(Path oldFile, Path patchFile, Path newFile) throws Exception {
        BsPatch.applyPatch(new RandomAccessFile(oldFile.toFile(), "r"),
                Files.newOutputStream(newFile),
                Files.newInputStream(patchFile)
        );
    }
}
