package i18nupdatemod.entity;

import java.util.List;

public class GameAssetDetail {
    public List<AssetDownloadDetail> downloads;
    public String covertFileName;

    public static class AssetDownloadDetail {
        public String fileName;
        public String targetVersion;
        public List<DownloadSource> sources;

        public static class DownloadSource {
            public final String fileUrl;
            public final String md5Url;

            public DownloadSource(String fileUrl, String md5Url) {
                this.fileUrl = fileUrl;
                this.md5Url = md5Url;
            }
        }
    }
}
