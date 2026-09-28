package tts.smartvoice.markup;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import orestes.bloomfilter.BloomFilter;

class Criterion {

    private final String alphabet;
    private final String distinctionPrefix;
    private final String distinctionSuffix;
    private final Pattern contradictions;
    private final Pattern wordExtractor;
    private final int matchMode;

    private Pattern distinctions;

    protected final Pattern selector;
    protected final List<? extends Criterion> alternatives;

    protected BloomFilter<String> filter;


    protected Criterion(LanguageData language) {
        this(language, null);
    }

    protected Criterion(LanguageData language, List<? extends Criterion> alternatives) {
        this(language.getAlphabet(), alternatives, language.aggregate());
        filter = language.getFilter();
    }

    protected Criterion(String alphabet, String... features) {
        this(alphabet, null, features);
    }

    protected Criterion(String alphabet, List<? extends Criterion> alternatives, String... features) {
        this(alphabet, alternatives, features, null);
    }

    protected Criterion(String alphabet, List<? extends Criterion> alternatives, String[] evidences, String[] contradictions) {
        this.alphabet = alphabet;
        this.alternatives = alternatives;
        matchMode = Pattern.UNICODE_CASE | Pattern.CASE_INSENSITIVE;
        String tolerable = String.format((Locale) null, "[%s\\P{L}]*", alphabet);
        StringBuilder accumulator = new StringBuilder(String.format((Locale) null, "[\\p{L}&&[^%s]]", alphabet));
        if (contradictions != null)
            for (String feature : contradictions)
                accumulator.append('|').append(feature);
        this.contradictions = Pattern.compile(accumulator.toString(), matchMode);
        accumulator.replace(0, accumulator.length(), "]");
        if (evidences != null)
            for (String feature : evidences)
                if (!feature.isEmpty())
                    accumulator.append('|').append(feature);
        distinctionPrefix = String.format((Locale) null, "[%s", alphabet);
        distinctionSuffix = accumulator.toString();
        selector = Pattern.compile(String.format((Locale) null, "(?:[^\\p{L}\\d\\s_]*[%s\\d]%s)?[%s]%s", alphabet, tolerable, alphabet, tolerable), matchMode);
        wordExtractor = Pattern.compile(String.format((Locale) null, "(?:\\A|\\s)[%s]+\\b", alphabet), matchMode);
        distinctions = null;
        filter = null;
    }


    protected Pattern getDistinctions() {
        if (distinctions == null) {
            StringBuilder accumulator = new StringBuilder(distinctionPrefix);
            if (alternatives != null)
                for (Criterion other : alternatives)
                    if (this != other)
                        accumulator.append("&&[^").append(other.alphabet).append(']');
            accumulator.append(distinctionSuffix);
            distinctions = Pattern.compile(accumulator.toString(), matchMode);
        }
        return distinctions;
    }

    protected boolean recognizeWords(String text) {
        if (filter != null) {
            Matcher words = wordExtractor.matcher(text);
            while (words.find()) {
                String word = words.group().trim().toLowerCase(Locale.getDefault());
                if (filter.contains(word)) {
                    if (alternatives != null) {
                        boolean doubt = false;
                        for (Criterion other : alternatives)
                            if ((other != this) && (other.filter != null) && other.filter.contains(word)) {
                                doubt = true;
                                break;
                            }
                        if (doubt)
                            continue;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean detect(String text, Pattern distinctions) {
        return distinctions.matcher(text).find() || recognizeWords(text);
    }


    boolean detect(String text) {
        Pattern distinctions = getDistinctions();
        if (distinctions == null)
            distinctions = selector;
        return detect(text, distinctions);
    }

    boolean refute(String text) {
        return contradictions.matcher(text).find();
    }

    boolean isValid(String text) {
        return selector.matcher(text).find() && !refute(text);
    }

}
