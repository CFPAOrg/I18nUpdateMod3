package i18nupdatemod.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public class AssetUtil {
    public static void download(String url, Path localFile) throws IOException, URISyntaxException {
        Log.info("Downloading: %s -> %s", url, localFile);
        FileUtils.copyURLToFile(new URI(url).toURL(), localFile.toFile(),
                (int) TimeUnit.SECONDS.toMillis(3), (int) TimeUnit.SECONDS.toMillis(33));
        Log.debug("Downloaded: %s -> %s", url, localFile);
    }

    public static String getString(String url) throws IOException, URISyntaxException {
        return IOUtils.toString(new URI(url).toURL(), StandardCharsets.UTF_8);
    }
}
