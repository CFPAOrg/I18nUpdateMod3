package i18nupdatemod.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AssetUtilTest {
    @Test
    void everyAssetRootIsADirectoryUrl() {
        List<String> roots = AssetUtil.getAssetRoots();

        assertFalse(roots.isEmpty());
        for (String root : roots) {
            // Filenames are appended verbatim, so a root must end with a separator.
            assertTrue(root.endsWith("/"), root);
            // raw.githubusercontent.com is a host root, not an asset root: appending a pack
            // filename to it yields a 404, so it must not be listed as a download source.
            assertFalse(root.contains("raw.githubusercontent.com"), root);
        }
    }

    @Test
    void assetRootsKeepTheCfpaRootAsTheLastResort() {
        List<String> roots = AssetUtil.getAssetRoots();

        assertTrue(roots.contains("http://downloader1.meitangdehulu.com:22943/"));
    }
}
