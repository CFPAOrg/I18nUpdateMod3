package i18nupdatemod.util;

import org.apache.commons.io.IOUtils;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LocationDetectUtil {
    private static Boolean cached = null;
    
    private static final String[][] GEO_APIS = {
            {"Kugou", "https://mips.kugou.com/check/iscn?&format=json"},
            {"IP.SB", "https://api.ip.sb/geoip"}
    };

    public static boolean isMainlandChina() {
        if (cached != null) {
            return cached;
        }

        for (String[] api : GEO_APIS) {
            Boolean result = tryApi(api[0], api[1]);
            if (result != null) {
                cached = result;
                return cached;
            }
        }
        
        cached = false;
        return false;
    }

    private static Boolean tryApi(String name, String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            if (conn.getResponseCode() == 200) {
                String response = IOUtils.toString(conn.getInputStream(), StandardCharsets.UTF_8);
                boolean isChina = parseResponse(response);
                Log.info("Location Detected (" + name + " API): " + (isChina ? "Inside mainland China" : "Outside mainland China"));
                return isChina;
            }
        } catch (Exception e) {
            Log.debug(name + " API detection failed: " + e.getMessage());
        }
        return null;
    }

    private static boolean parseResponse(String response) {
        response = response.trim();
        // Kugou: {"flag":1/true} | IP.SB: {"country_code":"CN"}
        return response.matches(".*(\"flag\".*[\":] *(1|true)|\"country_code\".*\"CN\").*");
    }
}
