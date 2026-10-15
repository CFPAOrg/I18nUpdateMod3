package i18nupdatemod.core.v2;

import i18nupdatemod.entity.ModTranslation;
import i18nupdatemod.util.DigestUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Identifies a mod by hashing a named file in its archive.
 *
 * <p>A descriptor can represent a nested archive while retaining the source
 * path of the outermost mod.  This class keeps that version-specific lookup
 * logic out of the shared descriptor.</p>
 */
public final class ModIdentity {
    private ModIdentity() {
    }

    /**
     * Calculate the MD5 of a file inside a mod archive without allowing a
     * caller to leave the archive. Archive paths are resolved through the
     * ordered nested-jar chain and never extracted to the filesystem.
     */
    public static String getFileMd5(ModTranslation mod, String path)
            throws IOException, NoSuchAlgorithmException {
        String resourcePath = normalizeRelativePath(path);
        Path source = mod.source;
        if (source == null) {
            throw new IOException("Mod source is missing");
        }
        if (!Files.isRegularFile(source)) {
            throw new IOException("Mod source does not exist: " + source);
        }
        return digest(openArchiveResource(source, mod.nestedJars, resourcePath));
    }

    private static InputStream openArchiveResource(Path source, List<String> nestedJars,
                                                    String resourcePath) throws IOException {
        InputStream current = Files.newInputStream(source);
        boolean closeCurrent = true;
        try {
            for (String nestedJar : nestedJars) {
                String nestedPath = normalizeRelativePath(nestedJar);
                ZipInputStream archive = new ZipInputStream(current);
                closeCurrent = false;
                try {
                    ZipEntry nestedEntry = findEntry(archive, nestedPath);
                    if (nestedEntry == null || nestedEntry.isDirectory()) {
                        throw new IOException("Nested mod archive is missing: " + nestedPath);
                    }
                    ByteArrayOutputStream nestedBytes = new ByteArrayOutputStream();
                    copy(archive, nestedBytes);
                    archive.close();
                    current = new ByteArrayInputStream(nestedBytes.toByteArray());
                    closeCurrent = true;
                } catch (IOException e) {
                    try {
                        archive.close();
                    } catch (IOException ignored) {
                    }
                    throw e;
                }
            }

            ZipInputStream archive = new ZipInputStream(current);
            closeCurrent = false;
            ZipEntry resourceEntry = findEntry(archive, resourcePath);
            if (resourceEntry == null || resourceEntry.isDirectory()) {
                try {
                    archive.close();
                } catch (IOException ignored) {
                }
                throw new IOException("Mod resource is missing: " + resourcePath);
            }
            // The returned stream owns the archive stream and therefore the
            // current input. digest() closes it after consuming the entry.
            return archive;
        } finally {
            if (closeCurrent) {
                try {
                    current.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    private static ZipEntry findEntry(ZipInputStream archive, String wanted) throws IOException {
        ZipEntry entry;
        while ((entry = archive.getNextEntry()) != null) {
            String entryName;
            try {
                entryName = normalizeRelativePath(entry.getName());
            } catch (IOException ignored) {
                // An unrelated malformed entry must not make a safe lookup
                // read outside the requested archive member.
                continue;
            }
            if (wanted.equals(entryName)) {
                return entry;
            }
        }
        return null;
    }

    private static String digest(InputStream input) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = input) {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] buffer = new byte[8192];
            int read;
            while ((read = is.read(buffer)) != -1) {
                if (read > 0) {
                    digest.update(buffer, 0, read);
                }
            }
            return DigestUtil.hexString(digest.digest());
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
    }

    private static String normalizeRelativePath(String path) throws IOException {
        if (path == null || path.length() == 0 || path.indexOf('\u0000') >= 0) {
            throw new IOException("Mod resource path is missing");
        }

        String normalized = path.replace('\\', '/');
        if (normalized.startsWith("/") || normalized.startsWith("//")
                || (normalized.length() > 1 && normalized.charAt(1) == ':')) {
            throw new IOException("Unsafe mod resource path: " + path);
        }

        String[] components = normalized.split("/", -1);
        StringBuilder result = new StringBuilder(normalized.length());
        for (String component : components) {
            if (component.isEmpty() || ".".equals(component) || "..".equals(component)) {
                throw new IOException("Unsafe mod resource path: " + path);
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(component);
        }
        return result.toString();
    }
}
