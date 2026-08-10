package i18nupdatemod.core;

import i18nupdatemod.entity.GameMetaData;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertNull;

class ReleaseTagTest {
    /**
     * {@code getGitIndex()} is declared {@code @NotNull}, but Gson maps an empty or literal-null
     * response body to a null Map. Should that contract ever regress, resolveReleaseTag must be the
     * place it is noticed, because an NPE here aborts the whole resource pack update.
     */
    @Test
    void survivesAnEmptyVersionIndex() {
        GameMetaData metadata = new GameMetaData();
        metadata.convertFrom = Collections.singletonList("26.1");

        assertNull(I18nConfig.resolveReleaseTag(metadata, "NeoForge", new HashMap<>()));
    }

    /**
     * A blank value in the index must be treated as "no release", not turned into a
     * ".../releases/download//" URL.
     */
    @Test
    void treatsABlankIndexEntryAsMissing() {
        GameMetaData metadata = new GameMetaData();
        metadata.convertFrom = Collections.singletonList("26.1");

        assertNull(I18nConfig.resolveReleaseTag(metadata, "NeoForge",
                Collections.singletonMap("26.1", "")));
    }
}
