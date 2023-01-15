package i18nupdatemod.core;

import i18nupdatemod.I18nUpdateMod;

import java.util.HashMap;
import java.util.Map;

public class AssetConfig {
    /**
     * <a href="https://github.com/CFPAOrg/Minecraft-Mod-Language-Package">CFPAOrg/Minecraft-Mod-Language-Package</a>
     */
    private static final String CFPA_ASSET_ROOT = "http://downloader1.meitangdehulu.com:22943/";
    /**
     * <a href="https://github.com/zkitefly/TranslationPackConvert">zkitefly/TranslationPackConvert</a>
     */
    private static final String CONVERT_ASSET_ROOT = "https://gitcode.net/chearlai/translationpackconvert/-/raw/main/files/";

    public enum Type {
        FILE_NAME,
        FILE_URL,
        MD5_URL,
    }

    public static Map<Type, String> getAsset(String minecraftVersion, String loader) {
        boolean isFabric = loader.equalsIgnoreCase("Fabric");
        String shortVersion = minecraftVersion;
        //1.19.3->1.19
        if (minecraftVersion.lastIndexOf(".") != minecraftVersion.indexOf(".")) {
            shortVersion = minecraftVersion.substring(0, minecraftVersion.lastIndexOf("."));
        }
        //Cfpa official
        switch (shortVersion) {
            case "1.12":
                return createAsset(CFPA_ASSET_ROOT, "Minecraft-Mod-Language-Modpack.zip", "1.12.2.md5");
            case "1.16":
                if (isFabric) {
                    return createAsset(CFPA_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-16-Fabric.zip", "1.16-fabric.md5");
                } else {
                    return createAsset(CFPA_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-16.zip", "1.16.md5");
                }
            case "1.18":
                if (isFabric) {
                    return createAsset(CFPA_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-18-Fabric.zip", "1.18-fabric.md5");
                } else {
                    return createAsset(CFPA_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-18.zip", "1.18.md5");
                }
        }
        //3-rd party convert
        switch (shortVersion) {
            case "1.15":
                if (isFabric) {
                    return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-16-FabricT1-15-Fabric.zip", "1.16-fabrict1.15-fabric.md5");
                } else {
                    return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-16T1-15.zip", "1.16t1.15.md5");
                }
            case "1.17":
                if (isFabric) {
                    return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-16-FabricT1-17-Fabric.zip", "1.16-fabrict1.17-fabric.md5");
                } else {
                    return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-16T1-17.zip", "1.16t1.17.md5");
                }
            case "1.19":
                if (minecraftVersion.equalsIgnoreCase("1.19.3")) {
                    if (isFabric) {
                        return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-18-FabricT1-19-3-Fabric.zip", "1.18-fabrict1.19.3-fabric.md5");
                    } else {
                        return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-18T1-19-3.zip", "1.18t1.19.3.md5");
                    }
                } else {
                    if (isFabric) {
                        return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-18-FabricT1-19-1o2-Fabric.zip", "1.18-fabrict1.19.1o2-fabric.md5");
                    } else {
                        return createAsset(CONVERT_ASSET_ROOT, "Minecraft-Mod-Language-Modpack-1-18T1-19-1o2.zip", "1.18t1.19.1o2.md5");
                    }
                }
        }
        throw new IllegalStateException("Minecraft version not supported");
    }

    private static Map<Type, String> createAsset(String root, String filename, String md5name) {
        if (!root.equalsIgnoreCase(CFPA_ASSET_ROOT)) {
            I18nUpdateMod.LOGGER.warning("You are using unofficial Minecraft Mod Language Modpack!");
            I18nUpdateMod.LOGGER.warning("Using it with your own risk!");
        }
        Map<Type, String> ret = new HashMap<>();
        ret.put(Type.FILE_NAME, filename);
        ret.put(Type.FILE_URL, root + filename);
        ret.put(Type.MD5_URL, root + md5name);
        return ret;
    }
}
