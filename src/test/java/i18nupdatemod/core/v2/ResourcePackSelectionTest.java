package i18nupdatemod.core.v2;

import i18nupdatemod.entity.ModTranslation;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourcePackSelectionTest {
    @Test
    void excludedNamespacesAreSkippedBySelectionOnly() {
        ResourcePackDownloader.Manifest manifest = new ResourcePackDownloader.Manifest();
        manifest.blackList.add("create");

        Map<String, String> selected = ResourcePackDownloader.selectNamespaces(
                Arrays.asList(mod("jei"), mod("create"), mod("other")), manifest,
                Collections.singletonList("jei"));

        assertEquals(Collections.singletonMap("other", "other"), selected);
    }

    private static ModTranslation mod(String namespace) {
        return new ModTranslation(namespace, null, null, null, null);
    }
}
