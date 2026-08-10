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
            // Every entry here is now used as a real asset root. raw.githubusercontent.com used to
            // sit in this list as a sentinel that selected the release-index path instead; that
            // branch is gone, and appending a pack filename to the host root would 404.
            assertFalse(root.contains("raw.githubusercontent.com"), root);
        }
    }

    @Test
    void assetRootsKeepTheCfpaRootAsTheLastResort() {
        List<String> roots = AssetUtil.getAssetRoots();

        assertTrue(roots.contains("http://downloader1.meitangdehulu.com:22943/"));
    }
}
