package i18nupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import i18nupdatemod.entity.GameMetaData;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;
import org.apache.commons.io.IOUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Path> sourcePath;
    private final Path filePath;
    private final Boolean enableLog;

    public ResourcePackConverter(List<Path> sourcePaths, Path filePath, Boolean enableLog) {
        this.sourcePath = sourcePaths;
        this.filePath = filePath;
        this.enableLog = enableLog;
    }

    public void convert(GameMetaData metaData, String description, HashSet<String> modDomainsSet, Path iconPath) throws Exception {
        FileUtil.safeCreateDir(filePath.getParent());
        Set<String> fileList = new HashSet<>();
        try {
            try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(filePath), StandardCharsets.UTF_8)) {
                //            zos.setMethod(ZipOutputStream.STORED);
                for (Path p : sourcePath) {
                    if (enableLog) {
                        Log.info("Converting: " + p);
                    }
                    try (ZipFile zf = new ZipFile(p.toFile(), StandardCharsets.UTF_8)) {
                        for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                            ZipEntry ze = e.nextElement();
                            String name = ze.getName();
                            String[] parts = name.split("/");
                            // Keep Minecraft's baseline fixes, but do not implicitly select its language files.
                            boolean minecraftFix = parts.length >= 2
                                    && "assets".equals(parts[0]) && "minecraft".equals(parts[1])
                                    && (parts.length < 3 || !"lang".equals(parts[2]));
                            if (parts.length >= 2 && !minecraftFix && !modDomainsSet.contains(parts[1])) {
                                continue;
                            }

                            // Don't put same file
                            if (fileList.contains(name)) {
                                //                            Log.debug(name + ": DUPLICATE");
                                continue;
                            }
                            fileList.add(name);

                            // Put file into new zip
                            zos.putNextEntry(new ZipEntry(name));
                            InputStream is = zf.getInputStream(ze);
                            if (name.equalsIgnoreCase("pack.mcmeta")) {
                                //Convert pack.mcmeta
                                PackMeta meta = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), PackMeta.class);
                                zos.write(convertPackMeta(meta, metaData, description));
                            } else {
                                //Copy other file
                                IOUtils.copy(is, zos);
                            }
                            zos.closeEntry();
                        }
                    }
                }
                if (!fileList.contains("pack.mcmeta")) {
                    zos.putNextEntry(new ZipEntry("pack.mcmeta"));
                    PackMeta meta = new PackMeta();
                    meta.pack = new PackMeta.Pack();
                    zos.write(convertPackMeta(meta, metaData, description));
                    zos.closeEntry();
                }
                if (iconPath != null && Files.isRegularFile(iconPath) && !fileList.contains("pack.png")) {
                    zos.putNextEntry(new ZipEntry("pack.png"));
                    Files.copy(iconPath, zos);
                    zos.closeEntry();
                }
            }

            Log.info("Converted: %s -> %s", sourcePath, filePath);
        } catch (Exception e) {
            throw new Exception(String.format("Error converting %s to %s: %s", sourcePath, filePath, e));
        }
    }

    private byte[] convertPackMeta(PackMeta meta, GameMetaData metaData, String description) {
        meta.pack.pack_format = metaData.useNewFormat() ? null : metaData.packFormat;
        meta.pack.min_format = metaData.useNewFormat() ? metaData.minFormat : null;
        meta.pack.max_format = metaData.useNewFormat() ? metaData.maxFormat : null;
        meta.pack.description = description;
        // Older clients read pack.mcmeta using the default charset, not UTF-8.
        // ASCII JSON escapes also work on snapshots without version-specific checks.
        String json = GSON.toJson(meta);
        ByteArrayOutputStream output = new ByteArrayOutputStream(json.length());
        for (int i = 0; i < json.length(); i++) {
            char character = json.charAt(i);
            if (character <= 0x7F) {
                output.write(character);
            } else {
                output.write('\\');
                output.write('u');
                for (int shift = 12; shift >= 0; shift -= 4) {
                    output.write(Character.forDigit((character >>> shift) & 0xF, 16));
                }
            }
        }
        return output.toByteArray();
    }

    private static class PackMeta {
        Pack pack;

        private static class Pack {
            Integer pack_format;  // 改为 Integer，支持 null
            Integer min_format;
            Integer max_format;
            String description;
        }
    }
}
