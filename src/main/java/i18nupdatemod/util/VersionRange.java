package i18nupdatemod.util;

import org.jetbrains.annotations.NotNull;

public class VersionRange {
    private Version fromVersion;
    private boolean containsLeft;
    private Version toVersion;
    private boolean containsRight;

    public VersionRange(String range) {
        parseVersionRange(range);
    }

    enum RangeParseState {
        START, READING_FIRST_VERSION, READING_SECOND_VERSION
    }

    private void parseVersionRange(@NotNull String range) {
        RangeParseState state = RangeParseState.START;
        StringBuilder buffer = new StringBuilder();
        for (char c : range.toCharArray()) {
            switch (state) {
                case START:
                    state = RangeParseState.READING_FIRST_VERSION;
                    if (c == '[') {
                        containsLeft = true;
                    } else if (c == '(') {
                        containsLeft = false;
                    } else {
                        throw new IllegalArgumentException("Range illegal");
                    }
                    break;
                case READING_FIRST_VERSION:
                    if (c == ',') {
                        fromVersion = Version.from(buffer.toString());
                        buffer = new StringBuilder();
                        state = RangeParseState.READING_SECOND_VERSION;
                    } else {
                        buffer.append(c);
                    }
                    break;
                case READING_SECOND_VERSION:
                    if (c == ']') {
                        toVersion = Version.from(buffer.toString());
                        containsRight = true;
                        return;
                    } else if (c == ')') {
                        toVersion = Version.from(buffer.toString());
                        containsRight = false;
                        return;
                    } else {
                        buffer.append(c);
                    }
            }
        }
        throw new IllegalArgumentException("Range illegal");
    }

    public boolean contains(@NotNull Version version) {
        if (fromVersion != null) {
            int cmp = version.compareTo(fromVersion);
            if (cmp < 0 || (!containsLeft && cmp == 0)) {
                return false;
            }
        }
        if (toVersion != null) {
            int cmp = version.compareTo(toVersion);
            return cmp <= 0 && (containsRight || cmp != 0);
        }
        return true;
    }

}
