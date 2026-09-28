package tts.smartvoice.markup;

class Selector {

    private static final String LEFT_ANCHOR = "(?:\\A|\\s)";
    private static final String RIGHT_ANCHOR = "\\b";
    private static final String EXTENSION = "\\B";

    private String prefix;
    private String suffix;
    private String[] patterns;
    private Selector[] nested;

    public String aggregate() {
        int patternUnits = (patterns != null) ? patterns.length : 0;
        int nestedUnits = (nested != null) ? nested.length : 0;
        int totalUnits = patternUnits + nestedUnits;
        StringBuilder result = new StringBuilder();
        if (prefix != null) {
            if (prefix.equals("[]"))
                result.append(LEFT_ANCHOR);
            else if (prefix.equals("()"))
                result.append(EXTENSION);
            else result.append(prefix);
            if (totalUnits > 1)
                result.append("(?:");
        } else if ((totalUnits > 1) && (suffix != null))
            result.append("(?:");
        for (int i = 0; i < patternUnits; i++) {
            if (i > 0)
                result.append('|');
            result.append(patterns[i]);
        }
        for (int i = 0; i < nestedUnits; i++) {
            if ((patternUnits > 0) || (i > 0))
                result.append('|');
            result.append(nested[i].aggregate());
        }
        if (suffix != null) {
            if (totalUnits > 1)
                result.append(')');
            if (suffix.equals("[]"))
                result.append(RIGHT_ANCHOR);
            else if (suffix.equals("()"))
                result.append(EXTENSION);
            else result.append(suffix);
        } else if ((totalUnits > 1) && (prefix != null))
            result.append(')');
        return result.toString();
    }

}
