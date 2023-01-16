package i18nupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import i18nupdatemod.I18nUpdateMod;
import i18nupdatemod.util.FileUtil;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path sourcePath;
    private final Path filePath;
    private final Path tmpFilePath;

    public ResourcePackConverter(ResourcePack resourcePack, String filename) {
        this.sourcePath = resourcePack.getTmpFilePath();
        this.filePath = FileUtil.getResourcePackPath(filename);
        this.tmpFilePath = FileUtil.getTemporaryPath(filename);
    }

    public void convert(int packFormat, String description) {
        try (ZipFile zf = new ZipFile(sourcePath.toFile())) {
            ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFilePath));
            for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                ZipEntry ze = e.nextElement();
                String name = ze.getName();
                zos.putNextEntry(new ZipEntry(name));
                InputStream is = zf.getInputStream(ze);
                if (name.equalsIgnoreCase("pack.mcmeta")) {
                    //Convert pack.mcmeta
                    zos.write(convertPackMeta(is, packFormat, description));
                } else {
                    //Copy other file
                    IOUtils.copy(is, zos);
                }
                zos.closeEntry();
            }
            zos.close();
            I18nUpdateMod.LOGGER.info(String.format("Converted to %s", tmpFilePath));
            FileUtil.syncTmpFile(tmpFilePath, filePath);
        } catch (Exception e) {
            I18nUpdateMod.LOGGER.warning(String.format("Error convert %s to %s", sourcePath, tmpFilePath));
            e.printStackTrace();
        }
    }

    private byte[] convertPackMeta(InputStream is, int packFormat, String description) {
        PackMeta meta = GSON.fromJson(new InputStreamReader(is), PackMeta.class);
        meta.pack.pack_format = packFormat;
        meta.pack.description = description + meta.pack.description;
        return GSON.toJson(meta).getBytes(StandardCharsets.UTF_8);
    }

    private static class PackMeta {
        Pack pack;

        private static class Pack {
            int pack_format;
            String description;
        }
    }
}
