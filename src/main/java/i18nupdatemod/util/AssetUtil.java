package i18nupdatemod.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class AssetUtil {
    public static void download(String url, Path localFile) throws IOException {
        FileUtils.copyURLToFile(new URL(url), localFile.toFile());
    }

    public static String getString(String url) throws IOException {
        return IOUtils.toString(new URL(url), StandardCharsets.UTF_8);
    }
}
