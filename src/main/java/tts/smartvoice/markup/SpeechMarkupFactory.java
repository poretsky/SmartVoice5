package tts.smartvoice.markup;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;

public class SpeechMarkupFactory {

    private final Context context;
    private final Map<String, LanguageDetector> languageDetectors;
    private final Map<String, EmojiTranslator> emojiTranslators;

    private LanguageDetector numberDetector;
    private String lastVoice;
    private Pattern separator;

    final ExpirableHint<LanguageDetector> assumptions;


    public SpeechMarkupFactory(Context context) {
        this.context = context;
        languageDetectors = new HashMap<String, LanguageDetector>();
        emojiTranslators = new HashMap<String, EmojiTranslator>();
        numberDetector = null;
        lastVoice = null;
        separator = null;
        assumptions = new ExpirableHint<LanguageDetector>(2000);
        LanguageGroup.setup(context);
    }

    public SpeechLayout getSpeechLayout(VoiceResolver voiceResolver, String text, int limit) {
        return new SpeechLayout(this, voiceResolver, text, limit);
    }

    @SuppressLint("DiscouragedApi")
    public synchronized void setLanguages(Collection<String> languages) {
        Resources res = context.getResources();
        Map<LanguageGroup, List<LanguageDetector>> alternatives = new HashMap<LanguageGroup, List<LanguageDetector>>();
        for (LanguageGroup group : LanguageGroup.values())
            if (group != LanguageGroup.NONE)
                alternatives.put(group, new ArrayList<LanguageDetector>());
        languageDetectors.clear();
        for (String language : languages) {
            int langResId = res.getIdentifier(language, "raw", context.getPackageName());
            if (langResId != 0) {
                LanguageGroup langGroup = LanguageGroup.find(language);
                try {
                    InputStreamReader dataSource = new InputStreamReader(res.openRawResource(langResId));
                    LanguageData langData = LanguageData.load(dataSource);
                    dataSource.close();
                    LanguageDetector detector = (langGroup != LanguageGroup.NONE) ?
                        new LanguageDetector(this, langGroup, langData, alternatives.get(langGroup)) :
                        new LanguageDetector(langData.getAlphabet());
                    if (langGroup != LanguageGroup.NONE)
                        alternatives.get(langGroup).add(detector);
                    languageDetectors.put(language, detector);
                } catch (Exception e) {
                }
            }
        }
    }


    Pattern getSeparator() {
        if (separator == null)
            separator = Pattern.compile("\\s+");
        return separator;
    }

    synchronized LanguageDetector getLanguageDetector(String language) {
        return languageDetectors.get(language);
    }

    EmojiTranslator getEmojiTranslator(String voice) {
        EmojiTranslator result = null;
        String actualVoice = (voice != null) ? voice : lastVoice;
        if (actualVoice != null) {
            String lang = (actualVoice.length() > 7) ? actualVoice.substring(0, 7) : actualVoice;
            result = emojiTranslators.get(lang);
            if (result == null) {
                result = new EmojiTranslator(context, lang);
                if (!result.getLanguage().equals(lang))
                    emojiTranslators.put(result.getLanguage(), result);
                emojiTranslators.put(lang, result);
            }
        }
        return result;
    }

    LanguageDetector getNumberDetector() {
        if (numberDetector == null)
            numberDetector = new LanguageDetector("0-9");
        return numberDetector;
    }

    void remember(String voice) {
        if (voice != null)
            lastVoice = voice;
    }

}
