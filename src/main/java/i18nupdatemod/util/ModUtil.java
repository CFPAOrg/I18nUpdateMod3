package i18nupdatemod.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import i18nupdatemod.entity.ModTranslation;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ModUtil {
    private static final Gson GSON = new Gson();

    /**
     * Discover local mod JARs for all loaders, including their nested JARs.
     * This deliberately scans installed candidates rather than a loader's
     * resolved mod list; development directories are not included.
     */
    public static List<ModTranslation> getModsFromModsFolder(Path minecraftPath) {
        List<ModTranslation> result = new ArrayList<>();
        if (minecraftPath == null) {
            return result;
        }

        Path modsPath = minecraftPath.resolve("mods");
        if (!Files.isDirectory(modsPath)) {
            return result;
        }

        List<Path> entries;
        try (Stream<Path> stream = Files.list(modsPath)) {
            entries = stream
                    .filter(path -> Files.isRegularFile(path)
                            && path.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            Log.warning("Failed to list mods directory %s: %s", modsPath, e);
            return result;
        }

        for (Path entry : entries) {
            try {
                scanArchive(entry, Collections.emptyList(), result);
            } catch (Exception e) {
                Log.warning("Failed to read mod %s: %s", entry, e);
            }
        }
        printMods(result);
        return result;
    }

    private static void printMods(List<ModTranslation> result) {
        StringBuilder mods = new StringBuilder("Found resource namespaces: [");
        for (int i = 0; i < result.size(); i++) {
            mods.append(result.get(i).namespace);
            if (i != result.size() - 1) {
                mods.append(", ");
            }
        }
        mods.append("]");
        Log.info(mods.toString());
    }

    private static void scanArchive(Path source, List<String> nestedJars,
                                    List<ModTranslation> output) throws IOException {
        try (InputStream input = Files.newInputStream(source)) {
            scanArchive(input, source, nestedJars, output);
        }
    }

    private static void scanArchive(InputStream input, Path source, List<String> nestedJars,
                                    List<ModTranslation> output) throws IOException {
        ParsedMod parsed = new ParsedMod(source, nestedJars);
        List<NestedArchive> nestedArchives = new ArrayList<>();
        try (JarInputStream jar = new JarInputStream(input)) {
            JarEntry entry;
            while ((entry = jar.getNextJarEntry()) != null) {
                String path = safeArchivePath(entry.getName());
                if (path == null) {
                    continue;
                }
                collectNamespace(parsed.namespaces, path);

                String metadataKind = metadataKind(path);
                boolean nestedArchive = !entry.isDirectory()
                        && path.toLowerCase().endsWith(".jar");
                if (metadataKind != null && !entry.isDirectory()) {
                    boolean rootMetadata = path.indexOf('/') < 0;
                    if (parsed.metadataPath == null
                            || (rootMetadata && parsed.metadataPath.indexOf('/') >= 0)) {
                        parsed.metadataPath = path;
                        parsed.metadataBytes = readCurrentEntry(jar);
                    }
                } else if (nestedArchive) {
                    byte[] bytes = readCurrentEntry(jar);
                    List<String> chain = new ArrayList<>(nestedJars);
                    chain.add(path);
                    nestedArchives.add(new NestedArchive(bytes, chain, path));
                }
            }
        }

        addTranslations(parsed, output);
        for (NestedArchive nestedArchive : nestedArchives) {
            try (InputStream nestedInput = new ByteArrayInputStream(nestedArchive.bytes)) {
                scanArchive(nestedInput, source, nestedArchive.chain, output);
            } catch (Exception e) {
                Log.warning("Failed to parse nested jar %s inside %s: %s", nestedArchive.name, source, e);
            }
        }
    }

    private static void addTranslations(ParsedMod parsed, List<ModTranslation> output) {
        List<MetadataRecord> metadata = Collections.emptyList();
        if (parsed.metadataPath != null) {
            try {
                metadata = parseMetadata(metadataKind(parsed.metadataPath), parsed.metadataBytes);
            } catch (Exception e) {
                Log.warning("Failed to parse metadata %s in %s: %s", parsed.metadataPath, parsed.source, e);
            }
        }
        List<String> namespaces = new ArrayList<>(parsed.namespaces);
        Collections.sort(namespaces);
        for (String namespace : namespaces) {
            MetadataRecord owner = metadataForNamespace(metadata, namespace);
            output.add(new ModTranslation(
                    namespace,
                    owner == null ? null : owner.author,
                    owner == null ? null : owner.displayName,
                    parsed.source,
                    parsed.nestedJars));
        }
    }

    private static List<MetadataRecord> parseMetadata(String kind, byte[] bytes) {
        if ("json".equals(kind)) {
            return parseJsonMetadata(GSON.fromJson(
                    new String(bytes, StandardCharsets.UTF_8), JsonElement.class));
        }
        try {
            return TomlMetadata.parse(bytes);
        } catch (NoClassDefFoundError e) {
            if (!isMissingNightConfig(e)) {
                throw e;
            }
            // NightConfig is supplied by Forge/NeoForge, but is intentionally
            // optional for Fabric and legacy JSON-only scans.
            Log.warning("NightConfig is unavailable; retaining namespaces without TOML metadata");
            return Collections.emptyList();
        }
    }

    private static boolean isMissingNightConfig(NoClassDefFoundError error) {
        Throwable current = error;
        while (current != null) {
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.replace('/', '.');
                if (normalized.contains("com.electronwill.nightconfig.")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    private static List<MetadataRecord> parseJsonMetadata(JsonElement element) {
        List<MetadataRecord> records = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return records;
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                records.addAll(parseJsonMetadata(child));
            }
            return records;
        }
        if (!element.isJsonObject()) {
            return records;
        }
        JsonObject object = element.getAsJsonObject();
        JsonElement modList = object.get("modList");
        if (modList != null && modList.isJsonArray()) {
            return parseJsonMetadata(modList);
        }
        MetadataRecord record = new MetadataRecord();
        record.modId = firstString(object, "modid", "id");
        record.displayName = firstString(object, "displayName", "name");
        record.author = firstAuthor(object, "authors", "authorList");
        records.add(record);
        return records;
    }

    private static MetadataRecord metadataForNamespace(List<MetadataRecord> records,
                                                        String namespace) {
        if (records == null || records.isEmpty()) {
            return null;
        }
        if (records.size() == 1) {
            return records.get(0);
        }
        MetadataRecord owner = null;
        for (MetadataRecord record : records) {
            if (record.owns(namespace)) {
                if (owner != null) {
                    // Duplicate IDs or namespace aliases are ambiguous; never
                    // assign a record merely because it appeared first.
                    return null;
                }
                owner = record;
            }
        }
        return owner;
    }

    private static String firstString(JsonObject object, String... names) {
        for (String name : names) {
            String value = stringValue(object.get(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String firstAuthor(JsonObject object, String first, String second) {
        JsonElement value = object.get(first);
        if (value != null) {
            return authorJson(value);
        }
        return authorJson(object.get(second));
    }

    private static String authorJson(JsonElement value) {
        if (value == null || value.isJsonNull()) {
            return null;
        }
        String selected = null;
        if (value.isJsonArray()) {
            for (JsonElement author : value.getAsJsonArray()) {
                String name = author != null && author.isJsonObject()
                        ? stringValue(author.getAsJsonObject().get("name")) : stringValue(author);
                selected = minAuthor(selected, name);
            }
        } else {
            // A scalar author is already the complete value (for example
            // Forge's "Alice, Bob"), not a comma-separated author list.
            selected = minAuthor(null, stringValue(value));
        }
        return selected;
    }

    // Loaded only for TOML metadata. Keep every NightConfig type reference here
    // so old Forge and Fabric can scan JSON without a TOML library present.
    private static final class TomlMetadata {
        private static List<MetadataRecord> parse(byte[] bytes) {
            Object rawMods = new TomlParser().parse(new ByteArrayInputStream(bytes)).get("mods");
            if (!(rawMods instanceof Iterable)) {
                return Collections.emptyList();
            }
            List<MetadataRecord> records = new ArrayList<>();
            for (Object rawMod : (Iterable<?>) rawMods) {
                Map<String, Object> values = valuesOf(rawMod);
                if (values == null) {
                    continue;
                }
                MetadataRecord record = new MetadataRecord();
                record.modId = firstValueString(values, "modId");
                record.namespaceAlias = valueString(values.get("namespace"));
                record.displayName = firstValueString(values, "displayName", "name");
                record.author = authorValue(values.get("authors"));
                records.add(record);
            }
            return records;
        }

        @SuppressWarnings("unchecked")
        private static Map<String, Object> valuesOf(Object rawMod) {
            if (rawMod instanceof UnmodifiableConfig) {
                return ((UnmodifiableConfig) rawMod).valueMap();
            }
            if (rawMod instanceof Map) {
                return (Map<String, Object>) rawMod;
            }
            return null;
        }

        private static String authorValue(Object value) {
            if (value == null) {
                return null;
            }
            String selected = null;
            if (value instanceof Iterable) {
                for (Object author : (Iterable<?>) value) {
                    String name;
                    if (author instanceof UnmodifiableConfig) {
                        name = valueString(((UnmodifiableConfig) author).get("name"));
                    } else if (author instanceof Map) {
                        name = valueString(((Map<?, ?>) author).get("name"));
                    } else {
                        name = valueString(author);
                    }
                    selected = minAuthor(selected, name);
                }
            } else {
                // Keep scalar authors intact; only arrays select the
                // lexicographically smallest author.
                selected = minAuthor(null, valueString(value));
            }
            return selected;
        }
    }

    private static String minAuthor(String selected, String candidate) {
        if (candidate == null || candidate.isEmpty()) {
            return selected;
        }
        return selected == null || candidate.compareTo(selected) < 0 ? candidate : selected;
    }


    private static String stringValue(JsonElement element) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()
                || !element.getAsJsonPrimitive().isString()) {
            return null;
        }
        return element.getAsString();
    }

    private static String valueString(Object value) {
        return value instanceof String ? (String) value : null;
    }

    private static String firstValueString(Map<String, Object> values, String... names) {
        for (String name : names) {
            String value = valueString(values.get(name));
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static String safeArchivePath(String path) {
        if (path == null || path.isEmpty() || path.indexOf('\u0000') >= 0) {
            return null;
        }
        String normalized = path.replace('\\', '/');
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.isEmpty() || normalized.startsWith("/")) {
            return null;
        }
        String[] components = normalized.split("/", -1);
        StringBuilder result = new StringBuilder(normalized.length());
        for (String component : components) {
            if (component.isEmpty() || ".".equals(component) || "..".equals(component)) {
                return null;
            }
            if (result.length() > 0) {
                result.append('/');
            }
            result.append(component);
        }
        return result.toString();
    }

    private static void collectNamespace(Set<String> namespaces, String path) {
        if (path == null || !path.startsWith("assets/")) {
            return;
        }
        String remainder = path.substring("assets/".length());
        int separator = remainder.indexOf('/');
        String namespace = separator < 0 ? remainder : remainder.substring(0, separator);
        if (!namespace.isEmpty()) {
            namespaces.add(namespace);
        }
    }

    private static String metadataKind(String path) {
        String normalized = path == null ? "" : path.toLowerCase();
        if (normalized.startsWith("meta-inf/")) {
            normalized = normalized.substring("meta-inf/".length());
        }
        switch (normalized) {
            case "mcmod.info":
            case "fabric.mod.json":
                return "json";
            case "mods.toml":
            case "neoforge.mods.toml":
                return "toml";
        }
        return null;
    }

    private static byte[] readCurrentEntry(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            if (read > 0) {
                output.write(buffer, 0, read);
            }
        }
        return output.toByteArray();
    }

    private static class ParsedMod {
        final Path source;
        final List<String> nestedJars;
        final Set<String> namespaces = new LinkedHashSet<>();
        String metadataPath;
        byte[] metadataBytes;

        ParsedMod(Path source, List<String> nestedJars) {
            this.source = source;
            this.nestedJars = new ArrayList<>(nestedJars);
        }
    }

    private static class NestedArchive {
        final byte[] bytes;
        final List<String> chain;
        final String name;

        NestedArchive(byte[] bytes, List<String> chain, String name) {
            this.bytes = bytes;
            this.chain = chain;
            this.name = name;
        }
    }

    private static class MetadataRecord {
        String modId;
        String namespaceAlias;
        String author;
        String displayName;

        boolean owns(String namespace) {
            return namespace != null
                    && (namespace.equals(modId) || namespace.equals(namespaceAlias));
        }
    }
}
