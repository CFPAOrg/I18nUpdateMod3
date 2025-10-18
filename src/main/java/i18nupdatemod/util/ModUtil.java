package i18nupdatemod.util;

import java.io.*;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;

public class ModUtil {
    public static HashSet<String> getModDomainsFromModsFolder(Path minecraftPath, String minecraftVersion, String loader) {
        HashSet<String> modDomainSet = new HashSet<>();
        Path modsPath = minecraftPath.resolve("mods");
        String[] modsNamesList = modsPath.toFile().list((dir, name) -> name.endsWith(".jar"));
        if (modsNamesList != null) {
            for (String name : modsNamesList) {
                modDomainSet.addAll(getModDomainFromJar(modsPath.resolve(name).toFile()));
            }
        }
        return modDomainSet;
    }

    private static HashSet<String> getModDomainFromJar(File modPath) {
        Log.debug(String.format("Get mod domain from %s", modPath));
        HashSet<String> modList = new HashSet<>();
        try (FileInputStream fis = new FileInputStream(modPath)) {
            modList.addAll(getModDomainFromStream(fis, modPath.getName()));
        } catch (Exception e) {
            Log.warning(String.format("Failed to read jar %s: %s", modPath, e));
        }
        return modList;
    }

    private static HashSet<String> getModDomainFromStream(InputStream input, String sourceName) throws IOException {
        HashSet<String> modList = new HashSet<>();
        try (JarInputStream jis = new JarInputStream(input)) {
            JarEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = jis.getNextJarEntry()) != null) {
                String path = entry.getName();

                // 匹配 assets/<domain>/
                if (path.startsWith("assets/")) {
                    String[] parts = path.split("/");
                    if (parts.length >= 2) {
                        modList.add(parts[1]);
                    }
                } else if (path.endsWith(".jar")) {
                    try {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        int bytesRead;
                        while ((bytesRead = jis.read(buffer)) != -1) {
                            baos.write(buffer, 0, bytesRead);
                        }

                        byte[] innerJarBytes = baos.toByteArray();
                        try (ByteArrayInputStream innerStream = new ByteArrayInputStream(innerJarBytes)) {
                            modList.addAll(getModDomainFromStream(innerStream, path));
                        }
                    } catch (Exception innerEx) {
                        Log.warning(String.format("Failed to parse nested jar %s inside %s: %s", path, sourceName, innerEx));
                    }
                }
            }
        }
        return modList;
    }
}
