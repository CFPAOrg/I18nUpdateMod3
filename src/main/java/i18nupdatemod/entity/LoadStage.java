package i18nupdatemod.entity;

public enum LoadStage {
    INIT(0),
    DOWNLOAD_ASSET(1),
    CONVERT_RESOURCE_PACK(2),
    APPLY_RESOURCE_PACK(3),
    FINISH(4);

    private final int value;

    LoadStage(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }

    public static String getDescription(LoadStage stage) {
        switch (stage) {
            case INIT:
                return "初始化";
            case DOWNLOAD_ASSET:
                return "更新资源包";
            case CONVERT_RESOURCE_PACK:
                return "转换资源包";
            case APPLY_RESOURCE_PACK:
                return "应用资源包";
            case FINISH:
                return "完成";
            default:
                return "未知";
        }
    }
}
