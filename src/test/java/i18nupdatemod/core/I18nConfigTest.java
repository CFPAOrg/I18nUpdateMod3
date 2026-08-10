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

    /**
     * Forge keeps booting through ModLauncher on 26.1, so it reaches this code with loader="Forge"
     * and must land on the Forge pack. NeoForge arrives via the javafml entrypoint instead, and
     * Fabric must pick the separate Fabric pack.
     */
    @Test
    void picksTheLoaderSpecificPackFor261() {
        for (String version : new String[]{"26.1", "26.1.1", "26.1.2"}) {
            for (String loader : new String[]{"Forge", "NeoForge"}) {
                assertEquals("Minecraft-Mod-Language-Modpack-26-1.zip",
                        firstDownload(version, loader).fileName, loader + " on " + version);
            }
            assertEquals("Minecraft-Mod-Language-Modpack-26-1-Fabric.zip",
                    firstDownload(version, "Fabric").fileName, "Fabric on " + version);
        }
    }

    /**
     * 26.1 has no pack of its own for every fallback generation, so the chain must keep walking back
     * to the 1.21/1.20/1.19 packs rather than stopping at the first miss.
     */
    @Test
    void keepsTheOlderPacksAsFallbacksOn261() {
        List<GameAssetDetail.AssetDownloadDetail> downloads =
                I18nConfig.getAssetDetail("26.1.2", "Forge").downloads;

        assertEquals(4, downloads.size());
        assertEquals("26.1", downloads.get(0).targetVersion);
        assertEquals("1.21", downloads.get(1).targetVersion);
        assertEquals("1.20", downloads.get(2).targetVersion);
        assertEquals("1.19", downloads.get(3).targetVersion);
    }

    private static GameAssetDetail.AssetDownloadDetail firstDownload(String version, String loader) {
        return I18nConfig.getAssetDetail(version, loader).downloads.get(0);
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

    /**
     * The fastest mirror must stay first: the GitHub release is only a last resort, since most users
     * of this mod reach the mirrors much more reliably than they reach GitHub.
     */
    @Test
    void triesTheFastestMirrorBeforeTheGithubRelease() {
        List<GameAssetDetail.AssetDownloadDetail.DownloadSource> sources = I18nConfig.createDownloadSources(
                "http://8.137.167.65:64684/", "Snapshot-2026010508081767600531");

        assertEquals("http://8.137.167.65:64684/", sources.get(0).fileUrl);
        assertEquals("https://github.com/CFPAOrg/Minecraft-Mod-Language-Package/releases/download/"
                + "Snapshot-2026010508081767600531/", sources.get(sources.size() - 1).fileUrl);
        // Every configured source stays available as a fallback.
        assertTrue(sources.stream().anyMatch(
                it -> it.fileUrl.equals("http://downloader1.meitangdehulu.com:22943/")));
    }
}
