package i18nupdatemod.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public class CfpaAssetUtil {
    private static final String CFPA_ASSET_ROOT = "http://downloader1.meitangdehulu.com:22943/";

    private static URL getUrl(String filename) throws MalformedURLException {
        return new URL(CFPA_ASSET_ROOT + filename);
    }

    public static void download(String filename, Path localFile) throws IOException {
        FileUtils.copyURLToFile(getUrl(filename), localFile.toFile());
    }

    public static String getString(String filename) throws IOException {
        return IOUtils.toString(getUrl(filename), StandardCharsets.UTF_8);
    }

    public enum Type {
        RESOURCE_PACK,
        RESOURCE_PACK_MD5,
    }

    public static String getFileName(String minecraftVersion, String loader, Type type) {
        String shortVersion = minecraftVersion.substring(0, minecraftVersion.lastIndexOf("."));
        switch (type) {
            case RESOURCE_PACK:
                if (minecraftVersion.equals("1.12.2")) {
                    return "Minecraft-Mod-Language-Modpack.zip";
                }
                return String.format("Minecraft-Mod-Language-Modpack-%s%s.zip",
                        shortVersion.replace('.', '-'),
                        loader.equalsIgnoreCase("Fabric") ? "-Fabric" : ""
                );
            case RESOURCE_PACK_MD5:
                if (minecraftVersion.equals("1.12.2")) {
                    return "1.12.2.md5";
                }
                return String.format("%s%s.md5",
                        shortVersion,
                        loader.equalsIgnoreCase("Fabric") ? "-fabric" : ""
                );
        }
        throw new IllegalStateException("File not found");
    }
}
