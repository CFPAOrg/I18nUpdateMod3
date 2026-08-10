package i18nupdatemod.neoforge;

import org.junit.jupiter.api.Test;

import java.io.DataInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Checks the reflective FMLLoader lookups in {@link NeoForgeMod} against real FancyModLoader jars.
 * <p>
 * The build has no NeoForge dependency (the API is compiled against a shim and excluded from the
 * jar), so nothing else catches a renamed method. Skipped when the jars are not present.
 */
class FmlApiShapeTest {
    /**
     * Maven-layout directory holding {@code <version>/loader-<version>.jar}, e.g. the
     * {@code net/neoforged/fancymodloader/loader} folder of any launcher's library cache. Set
     * {@code -Dfml.loader.libs=...} or the {@code FML_LOADER_LIBS} environment variable to run these
     * checks; without it there is nothing to verify against and both tests skip.
     */
    private static final String LIB_PROPERTY = "fml.loader.libs";

    private static Path libraryDir() {
        String configured = System.getProperty(LIB_PROPERTY, System.getenv("FML_LOADER_LIBS"));
        return configured == null || configured.isEmpty() ? null : Paths.get(configured);
    }

    @Test
    void fml11ExposesTheInstanceApiTheEntrypointUses() throws Exception {
        Set<String> methods = methodsOf("11.0.15");
        assumeTrue(!methods.isEmpty(), "set -D" + LIB_PROPERTY + " to a library dir containing FML 11");

        assertTrue(methods.contains("getCurrent"), methods.toString());
        assertTrue(methods.contains("getGameDir"), methods.toString());
        assertTrue(methods.contains("getVersionInfo"), methods.toString());
        // The pre-26.1 statics are gone here, which is why the fallback exists.
        assertTrue(!methods.contains("getGamePath"));
        assertTrue(!methods.contains("versionInfo"));
    }

    @Test
    void fml4ExposesTheStaticApiTheFallbackUses() throws Exception {
        Set<String> methods = methodsOf("4.0.39");
        assumeTrue(!methods.isEmpty(), "set -D" + LIB_PROPERTY + " to a library dir containing FML 4");

        assertTrue(methods.contains("getGamePath"), methods.toString());
        assertTrue(methods.contains("versionInfo"), methods.toString());
        // getCurrent() does not exist yet, so the primary lookup must be allowed to fail.
        assertTrue(!methods.contains("getCurrent"));
    }

    /**
     * @return declared method names of FMLLoader in the given loader version, empty if absent
     */
    private static Set<String> methodsOf(String version) throws Exception {
        Path lib = libraryDir();
        if (lib == null) {
            return new HashSet<>();
        }
        Path jar = lib.resolve(version).resolve("loader-" + version + ".jar");
        if (!Files.exists(jar)) {
            return new HashSet<>();
        }
        Set<String> methods = new HashSet<>();
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            java.util.zip.ZipEntry entry = zf.getEntry("net/neoforged/fml/loading/FMLLoader.class");
            if (entry == null) {
                return methods;
            }
            try (DataInputStream input = new DataInputStream(zf.getInputStream(entry))) {
                methods.addAll(readMethodNames(input));
            }
        }
        return methods;
    }

    /**
     * Minimal class file reader. ASM 9.7 rejects the class file version FML 11 is built with, so the
     * constant pool and method table are walked by hand.
     *
     * @see <a href="https://docs.oracle.com/javase/specs/jvms/se21/html/jvms-4.html">JVMS §4</a>
     */
    private static Set<String> readMethodNames(DataInputStream in) throws Exception {
        in.readInt();   // magic
        in.readShort(); // minor version
        in.readShort(); // major version

        int constantPoolCount = in.readUnsignedShort();
        String[] utf8 = new String[constantPoolCount];
        for (int i = 1; i < constantPoolCount; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1: // Utf8
                    utf8[i] = in.readUTF();
                    break;
                case 7:   // Class
                case 8:   // String
                case 16:  // MethodType
                case 19:  // Module
                case 20:  // Package
                    in.skipBytes(2);
                    break;
                case 15: // MethodHandle
                    in.skipBytes(3);
                    break;
                case 5:  // Long
                case 6:  // Double
                    in.skipBytes(8);
                    i++; // these take two constant pool slots
                    break;
                default: // Integer, Float, refs, InvokeDynamic, ...
                    in.skipBytes(4);
            }
        }

        in.readShort(); // access flags
        in.readShort(); // this class
        in.readShort(); // super class
        in.skipBytes(in.readUnsignedShort() * 2); // interfaces
        skipMembers(in); // fields

        Set<String> methods = new HashSet<>();
        int methodCount = in.readUnsignedShort();
        for (int i = 0; i < methodCount; i++) {
            in.readShort(); // access flags
            methods.add(utf8[in.readUnsignedShort()]);
            in.readShort(); // descriptor
            skipAttributes(in);
        }
        return methods;
    }

    private static void skipMembers(DataInputStream in) throws Exception {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.skipBytes(6); // access flags, name, descriptor
            skipAttributes(in);
        }
    }

    private static void skipAttributes(DataInputStream in) throws Exception {
        int count = in.readUnsignedShort();
        for (int i = 0; i < count; i++) {
            in.readShort(); // name index
            int length = in.readInt();
            in.skipBytes(length);
        }
    }
}
