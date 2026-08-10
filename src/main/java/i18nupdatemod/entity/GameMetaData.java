package i18nupdatemod.entity;

import com.google.gson.JsonElement;

import java.util.List;

public class GameMetaData {
    public String gameVersions;
    public Integer packFormat;
    /** Supports both pre-26 integer formats and 26.1 structured formats. */
    public JsonElement minFormat, maxFormat;
    public List<String> convertFrom;

    public boolean useNewFormat() {
        return minFormat != null && maxFormat != null
                && !minFormat.isJsonNull() && !maxFormat.isJsonNull();
    }
}
