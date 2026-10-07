package i18nupdatemod.entity;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Metadata for one resource namespace supplied by a mod.
 *
 * <p>The source is the original mod archive. For nested Fabric or Forge
 * jars, {@code nestedJars} contains the entry names from the outer archive to
 * the innermost archive.</p>
 */
public class ModTranslation {
    public String namespace;
    public String author;
    public String displayName;

    public final Path source;
    public final List<String> nestedJars;

    public ModTranslation(String namespace, String author, String displayName,
                          Path source, List<String> nestedJars) {
        this.namespace = namespace;
        this.author = author;
        this.displayName = displayName;
        this.source = source;
        this.nestedJars = nestedJars == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(nestedJars));
    }
}
