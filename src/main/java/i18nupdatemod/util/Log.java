package i18nupdatemod.util;

import java.io.File;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Log {
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    private static Writer fileWriter;

    public static void setMinecraftLogFile(Path minecraftDir) {
        setLogFile(minecraftDir.resolve("logs/I18nUpdateMod.log"));
    }

    public static void setLogFile(Path path) {
        try {
            File file = path.toFile();
            if (!file.exists()) {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } else {
                if (!file.canWrite()) throw new IllegalStateException("Log file " + path + " can't be write");
            }
            fileWriter = Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.printf("Error setting log file: %s%n\r\n", e);
        }
    }

    enum Level {
        DEBUG(Out.FILE_ONLY), INFO(Out.STD_OUT), WARNING(Out.STD_ERR);
        final Out out;

        Level(Out out) {
            this.out = out;
        }
    }

    enum Out {
        FILE_ONLY,
        STD_OUT,
        STD_ERR
    }

    private static void log(Level level, String message) {
        String out = String.format("[%s] [%s]: %s\r\n", DATE_FORMAT.format(new Date()), level.name(), message);
        if (fileWriter != null) {
            try {
                fileWriter.write(out);
                fileWriter.flush();
            } catch (Exception e) {
                System.err.printf("Error writing log: %s%n\r\n", e);
            }
        }
        switch (level.out) {
            case STD_OUT:
                System.out.print(out);
                return;
            case STD_ERR:
                System.err.print(out);
        }
    }

    public static void debug(String message) {
        log(Level.DEBUG, message);
    }

    public static void debug(String format, Object... args) {
        debug(String.format(format, args));
    }

    public static void info(String message) {
        log(Level.INFO, message);
    }

    public static void info(String format, Object... args) {
        info(String.format(format, args));
    }

    public static void warning(String message) {
        log(Level.WARNING, message);
    }

    public static void warning(String format, Object... args) {
        warning(String.format(format, args));
    }

}
