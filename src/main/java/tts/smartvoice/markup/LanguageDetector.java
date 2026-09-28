package tts.smartvoice.markup;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class LanguageDetector extends Criterion {

    private SpeechMarkupFactory factory;
    private boolean isUltimate;
    private int hitCount;

    LanguageGroup group;


    LanguageDetector(String alphabet) {
        this(null, LanguageGroup.NONE, alphabet, null);
    }

    LanguageDetector(SpeechMarkupFactory factory, LanguageGroup group, LanguageData language, List<? extends Criterion> alternatives) {
        super(language, alternatives);
        setup(factory, group);
    }

    LanguageDetector(SpeechMarkupFactory factory, LanguageGroup group, String alphabet, List<? extends Criterion> alternatives, String... features) {
        this(factory, group, alphabet, alternatives, features, null);
    }

    LanguageDetector(SpeechMarkupFactory factory, LanguageGroup group, String alphabet, List<? extends Criterion> alternatives, String[] evidences, String[] contradictions) {
        super(alphabet, alternatives, evidences, contradictions);
        setup(factory, group);
    }

    List<TextElement> markup(String text, String voice) throws InterruptedException {
        List<TextElement> result = new ArrayList<TextElement>();
        Matcher occurrence = selector.matcher(text);
        int position = 0;
        hitCount = 0;
        while (occurrence.find()) {
            if (Thread.interrupted())
                throw new InterruptedException();
            Pattern distinctions = getDistinctions();
            if ((distinctions == null) || detect(occurrence.group(), distinctions)) {
                if (position != occurrence.start())
                    result.add(new TextElement(text.substring(position, occurrence.start())));
                result.add(new TextElement(text.substring(occurrence.start(), occurrence.end()), voice));
                position = occurrence.end();
                hitCount++;
            }
        }
        if (position < text.length())
            result.add(new TextElement(text.substring(position)));
        return result;
    }

    boolean isUltimateResult() {
        return (alternatives == null) ||(alternatives.size() < 2) || isUltimate;
    }

    int getHitCount() {
        return hitCount;
    }


    @Override
    protected boolean detect(String text, Pattern distinctions) {
        boolean detected = super.detect(text, distinctions);
        if (detected && isUltimate && (factory != null) && (group != LanguageGroup.NONE))
            factory.assumptions.set(group, this);
        return detected;
    }

    @Override
    protected Pattern getDistinctions() {
        isUltimate = (factory != null) &&
            (group != LanguageGroup.NONE) &&
            (alternatives != null) &&
            (alternatives.size() > 1) &&
            (factory.assumptions.get(group) != this);
        return isUltimate ? super.getDistinctions() : null;
    }

    @Override
    protected boolean recognizeWords(String text) {
        return isUltimate && super.recognizeWords(text);
    }


    private void setup(SpeechMarkupFactory factory, LanguageGroup group) {
        this.factory = factory;
        this.group = group;
        isUltimate = false;
        hitCount = 0;
    }

}
