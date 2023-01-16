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
        if (version == null || version.equals("")) {
            return null;
        }
        return new Version(version);
    }

    private Version(String version) {
        this.version = version;
        parseVersion(version);
    }

    private void parseVersion(String version) {
        int state = 0;
        StringBuilder buffer = new StringBuilder();
        for (char c : version.toCharArray()) {
            switch (state) {
                case 0:
                    if (isNumber(c)) {
                        buffer.append(c);
                        state = 1;
                    } else {
                        return;
                    }
                    break;
                case 1:
                    if (isNumber(c)) {
                        buffer.append(c);
                    } else {
                        versions.add(Integer.parseInt(buffer.toString()));
                        buffer = new StringBuilder();
                        if (c == '.') {
                            state = 0;
                        } else {
                            return;
                        }
                    }
                    break;
            }
        }
        versions.add(Integer.parseInt(buffer.toString()));
    }

    private boolean isNumber(char c) {
        return c >= '0' && c <= '9';
    }

    @Override
    public int compareTo(@NotNull Version o) {
        int min = Math.min(versions.size(), o.versions.size());
        for (int i = 0; i < min; ++i) {
            if (Objects.equals(versions.get(i), o.versions.get(i))) {
                continue;
            }
            return Integer.compare(versions.get(i), o.versions.get(i));
        }
        return Integer.compare(versions.size(), o.versions.size());
    }
}