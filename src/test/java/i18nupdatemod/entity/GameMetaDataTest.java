package i18nupdatemod.entity;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameMetaDataTest {
    private final Gson gson = new Gson();

    @Test
    void acceptsStructuredPackFormatsIntroducedBy261() {
        // 26.1.2 client version.json: resource_major 84, resource_minor 0.
        GameMetaData metadata = gson.fromJson(
                "{\"minFormat\":[84,0],\"maxFormat\":[84,0]}", GameMetaData.class);

        assertTrue(metadata.useNewFormat());
        assertEquals(84, metadata.minFormat.getAsJsonArray().get(0).getAsInt());
        assertEquals(0, metadata.maxFormat.getAsJsonArray().get(1).getAsInt());
    }

    @Test
    void acceptsLegacyNumericPackFormats() {
        GameMetaData metadata = gson.fromJson(
                "{\"packFormat\":75,\"minFormat\":69,\"maxFormat\":75}", GameMetaData.class);

        assertTrue(metadata.useNewFormat());
        assertEquals(69, metadata.minFormat.getAsInt());
        assertFalse(metadata.minFormat.isJsonArray());
    }

    @Test
    void parsesBothMinecraftVersionSchemes() throws Exception {
        Method method = Class.forName("i18nupdatemod.I18nUpdateMod")
                .getDeclaredMethod("getMinecraftMajorVersion", String.class);
        method.setAccessible(true);

        assertEquals(21, method.invoke(null, "1.21.11"));
        assertEquals(26, method.invoke(null, "26.1.2"));
    }
}
