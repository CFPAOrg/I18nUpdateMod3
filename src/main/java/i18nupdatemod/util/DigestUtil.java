package i18nupdatemod.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DigestUtil {
    public static String md5Hex(Path file) throws IOException, NoSuchAlgorithmException {
        try (InputStream is = Files.newInputStream(file)) {
            MessageDigest dig = MessageDigest.getInstance("MD5");

            final byte[] buf = new byte[114514];

            for (int read = is.read(buf); read != -1; read = is.read(buf)) {
                dig.update(buf, 0, read);
            }

            byte[] data = dig.digest();
            StringBuilder sb = new StringBuilder();
            for (byte d : data) {
                sb.append(Integer.toHexString(d));
            }
            return sb.toString();
        }
    }
}
