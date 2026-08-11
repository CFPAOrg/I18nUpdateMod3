package i18nupdatemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import i18nupdatemod.entity.GameMetaData;
import i18nupdatemod.util.FileUtil;
import i18nupdatemod.util.Log;
import org.apache.commons.io.IOUtils;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackConverter {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private final List<Path> sourcePath;
    private final Path filePath;
    private final Path tmpFilePath;

    public ResourcePackConverter(List<ResourcePack> resourcePack, String filename) {
        this.sourcePath = resourcePack.stream().map(ResourcePack::getTmpFilePath).collect(Collectors.toList());
        this.filePath = FileUtil.getResourcePackPath(filename);
        this.tmpFilePath = FileUtil.getTemporaryPath(filename);
    }

    public void convert(GameMetaData metaData, String description, HashSet<String> modDomainsSet) throws Exception {
        Set<String> fileList = new HashSet<>();
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmpFilePath), StandardCharsets.UTF_8)) {
//            zos.setMethod(ZipOutputStream.STORED);
            for (Path p : sourcePath) {
                Log.info("Converting: " + p);
                try (ZipFile zf = new ZipFile(p.toFile(), StandardCharsets.UTF_8)) {
                    for (Enumeration<? extends ZipEntry> e = zf.entries(); e.hasMoreElements(); ) {
                        ZipEntry ze = e.nextElement();
                        String name = ze.getName();
                        String[] parts = name.split("/");
                        // 正在筛选的是assets/modDomain/** && 当前的modDomain不需要
                        if (parts.length >= 2 && !modDomainsSet.contains(parts[1])) {
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
                            zos.write(convertPackMeta(is, metaData, description));
                        } else {
                            //Copy other file
                            IOUtils.copy(is, zos);
                        }
                        zos.closeEntry();
                    }
                }
            }
            zos.close();
            Log.info("Converted: %s -> %s", sourcePath, tmpFilePath);
            FileUtil.syncTmpFile(tmpFilePath, filePath);
        } catch (Exception e) {
            throw new Exception(String.format("Error converting %s to %s: %s", sourcePath, tmpFilePath, e));
        }
    }

    private byte[] convertPackMeta(InputStream is, GameMetaData metaData, String description) {
        PackMeta meta = GSON.fromJson(new InputStreamReader(is, StandardCharsets.UTF_8), PackMeta.class);
        meta.pack.pack_format = metaData.useNewFormat() || metaData.packFormat == null
                ? null : new JsonPrimitive(metaData.packFormat);
        meta.pack.min_format = metaData.useNewFormat() ? metaData.minFormat : null;
        meta.pack.max_format = metaData.useNewFormat() ? metaData.maxFormat : null;
        meta.pack.description = new JsonPrimitive(description);
        return GSON.toJson(meta).getBytes(StandardCharsets.UTF_8);
    }

    private static class PackMeta {
        Pack pack;

        private static class Pack {
            JsonElement pack_format;
            JsonElement min_format;
            JsonElement max_format;
            JsonElement description;
        }
    }
}
