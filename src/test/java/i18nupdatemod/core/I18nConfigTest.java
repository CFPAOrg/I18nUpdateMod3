package i18nupdatemod.core;

import i18nupdatemod.entity.GameMetaData;
import i18nupdatemod.entity.GameAssetDetail;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class I18nConfigTest {
    @Test
    void supportsEveryMinecraft261Patch() {
        for (String version : new String[]{"26.1", "26.1.1", "26.1.2"}) {
            GameMetaData metadata = I18nConfig.getPackFormat(version);

            assertEquals(84, metadata.minFormat.getAsJsonArray().get(0).getAsInt());
            assertEquals(0, metadata.minFormat.getAsJsonArray().get(1).getAsInt());
            assertEquals(metadata.minFormat, metadata.maxFormat);
            assertEquals("26.1", metadata.convertFrom.get(0));
        }
    }

    @Test
    void fallsBackToTheAssetRootWhenTheIndexDoesNotList261() {
        GameMetaData metadata = new GameMetaData();
        metadata.convertFrom = Collections.singletonList("26.1");

        // The CFPA version index has no 26.1 entry, so there is no release to download from.
        assertNull(I18nConfig.resolveReleaseTag(metadata, "NeoForge", Collections.emptyMap()));
        assertNull(I18nConfig.resolveReleaseTag(metadata, "Fabric", Collections.emptyMap()));

        List<GameAssetDetail.AssetDownloadDetail.DownloadSource> sources =
                I18nConfig.createDownloadSources("http://downloader1.meitangdehulu.com:22943/", null);
        assertEquals("http://downloader1.meitangdehulu.com:22943/", sources.get(0).fileUrl);
    }

    @Test
    void prefersAnIndexReleaseWhenItBecomesAvailable() {
        GameMetaData metadata = new GameMetaData();
        metadata.convertFrom = Collections.singletonList("26.1");

        assertEquals("newer-release", I18nConfig.resolveReleaseTag(metadata, "NeoForge",
                Collections.singletonMap("26.1", "newer-release")));
    }

    @Test
    void usesTheIndexForExistingVersions() {
        GameMetaData metadata = new GameMetaData();
        metadata.convertFrom = Collections.singletonList("1.21");

        assertEquals("release-forge", I18nConfig.resolveReleaseTag(metadata, "NeoForge",
                Collections.singletonMap("1.21", "release-forge")));
        assertEquals("release-fabric", I18nConfig.resolveReleaseTag(metadata, "Fabric",
                Collections.singletonMap("1.21-fabric", "release-fabric")));
    }

    @Test
    void prefersTheReleaseThenFallsBackToMirrors() {
        List<GameAssetDetail.AssetDownloadDetail.DownloadSource> sources = I18nConfig.createDownloadSources(
                "http://8.137.167.65:64684/", "Snapshot-2026010508081767600531");

        assertEquals("https://github.com/CFPAOrg/Minecraft-Mod-Language-Package/releases/download/"
                + "Snapshot-2026010508081767600531/", sources.get(0).fileUrl);
        assertEquals("http://8.137.167.65:64684/", sources.get(1).fileUrl);
        // Every configured source stays available as a fallback.
        assertTrue(sources.stream().anyMatch(
                it -> it.fileUrl.equals("http://downloader1.meitangdehulu.com:22943/")));
    }
}
