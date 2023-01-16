package i18nupdatemod.util;

public class VersionRange {
    private Version fromVersion;
    private boolean containsLeft;
    private Version toVersion;
    private boolean containsRight;

    public VersionRange(String range) {
        parseVersionRange(range);
    }

    private void parseVersionRange(String range) {
        int state = 0;
        StringBuilder buffer = new StringBuilder();
        for (char c : range.toCharArray()) {
            switch (state) {
                case 0:
                    state = 1;
                    if (c == '[') {
                        containsLeft = true;
                    } else if (c == '(') {
                        containsLeft = false;
                    } else {
                        throw new IllegalArgumentException("Range illegal");
                    }
                    break;
                case 1:
                    if (c == ',') {
                        fromVersion = Version.from(buffer.toString());
                        buffer = new StringBuilder();
                        state = 2;
                    } else {
                        buffer.append(c);
                    }
                    break;
                case 2:
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

    public boolean contains(Version version) {
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
