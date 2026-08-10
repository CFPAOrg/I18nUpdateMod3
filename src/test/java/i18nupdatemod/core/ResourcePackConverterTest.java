package i18nupdatemod.core;

import com.google.gson.JsonParser;
import i18nupdatemod.entity.GameMetaData;
import i18nupdatemod.util.FileUtil;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourcePackConverterTest {
    @TempDir
    Path tempDir;

    @Test
    void keepsVanillaAndInstalledModDomains() throws Exception {
        FileUtil.setResourcePackDirPath(tempDir.resolve("resourcepacks"));
        FileUtil.setTemporaryDirPath(tempDir.resolve("temporary"));

        Path sourceFile = tempDir.resolve("source.zip");
        try (OutputStream output = Files.newOutputStream(sourceFile);
             ZipOutputStream zip = new ZipOutputStream(output, StandardCharsets.UTF_8)) {
            writeEntry(zip, "pack.mcmeta", "{\"pack\":{\"pack_format\":75}}\n");
            writeEntry(zip, "assets/minecraft/font/default.json", "{}\n");
            writeEntry(zip, "assets/installed_mod/lang/zh_cn.json", "{}\n");
            writeEntry(zip, "assets/uninstalled_mod/lang/zh_cn.json", "{}\n");
        }

        ResourcePack source = new ResourcePack("source.zip");
        Files.copy(sourceFile, source.getTmpFilePath(), StandardCopyOption.REPLACE_EXISTING);

        GameMetaData metadata = new GameMetaData();
        metadata.minFormat = JsonParser.parseString("[84,0]");
        metadata.maxFormat = JsonParser.parseString("[84,0]");

        ResourcePackConverter converter = new ResourcePackConverter(
                Collections.singletonList(source), "converted.zip");
        converter.convert(metadata, "test pack", new HashSet<>(Arrays.asList("minecraft", "installed_mod")));

        try (ZipFile result = new ZipFile(tempDir.resolve("resourcepacks/converted.zip").toFile(), StandardCharsets.UTF_8)) {
            assertNotNull(result.getEntry("assets/minecraft/font/default.json"));
            assertNotNull(result.getEntry("assets/installed_mod/lang/zh_cn.json"));
            assertFalse(result.stream().anyMatch(entry ->
                    entry.getName().equals("assets/uninstalled_mod/lang/zh_cn.json")));

            String packMeta = IOUtils.toString(result.getInputStream(result.getEntry("pack.mcmeta")),
                    StandardCharsets.UTF_8);
            assertEquals(84, JsonParser.parseString(packMeta).getAsJsonObject()
                    .getAsJsonObject("pack").getAsJsonArray("min_format").get(0).getAsInt());
            assertEquals(84, JsonParser.parseString(packMeta).getAsJsonObject()
                    .getAsJsonObject("pack").getAsJsonArray("max_format").get(0).getAsInt());
        }
    }

    private static void writeEntry(ZipOutputStream zip, String name, String content) throws Exception {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }
}
