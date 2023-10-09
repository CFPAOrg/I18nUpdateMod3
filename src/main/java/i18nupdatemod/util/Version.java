package i18nupdatemod.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Version implements Comparable<Version> {
    public final String version;
    private final List<Integer> versions = new ArrayList<>();

    public static @Nullable Version from(String version) {
        if (version == null || version.isEmpty()) {
            return null;
        }
        return new Version(version);
    }

    private Version(@NotNull String version) {
        this.version = version;
        parseVersion(version);
    }

    enum VersionParseState {
        START, READING_NUMBER
    }

    private void parseVersion(@NotNull String version) {
        VersionParseState state = VersionParseState.START;
        StringBuilder buffer = new StringBuilder();
        for (char c : version.toCharArray()) {
            switch (state) {
                case START:
                    if (Character.isDigit(c)) {
                        buffer.append(c);
                        state = VersionParseState.READING_NUMBER;
                    } else {
                        return;
                    }
                    break;
                case READING_NUMBER:
                    if (Character.isDigit(c)) {
                        buffer.append(c);
                    } else {
                        versions.add(Integer.parseInt(buffer.toString()));
                        buffer = new StringBuilder();
                        if (c == '.') {
                            state = VersionParseState.START;
                        } else {
                            return;
                        }
                    }
                    break;
            }
        }
        versions.add(Integer.parseInt(buffer.toString()));
    }

    @Override
    public int compareTo(@NotNull Version o) {
        int min = Math.min(versions.size(), o.versions.size());
        for (int i = 0; i < min; ++i) {
            if (!Objects.equals(versions.get(i), o.versions.get(i))) {
                return Integer.compare(versions.get(i), o.versions.get(i));
            }
        }
        return Integer.compare(versions.size(), o.versions.size());
    }
}