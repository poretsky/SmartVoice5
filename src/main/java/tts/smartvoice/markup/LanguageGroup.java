package tts.smartvoice.markup;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import android.content.Context;
import android.content.res.Resources;

import tts.smartvoice.R;

public enum LanguageGroup {

    LATINIC("a-z", R.array.latinic_languages),
    CYRILLIC("\\p{InCyrillic}", R.array.cyrillic_languages),
    CJK("\\p{InCJK_Unified_Ideographs}", R.array.cjk_languages),
    NONE(null, 0);

    private LanguageDetector languageDetector;

    private final String alphabet;
    private final Set<String> languages;

    final int key;

    LanguageGroup(String alphabet, int resId) {
        this.alphabet = alphabet;
        languages = (alphabet != null) ? new HashSet<String>() : null;
        key = resId;
        languageDetector = null;
    }

    LanguageDetector getDetector() {
        if ((alphabet != null) && (languageDetector == null))
            languageDetector = new LanguageDetector(alphabet);
        return languageDetector;
    }

    boolean contains(String lang) {
        return (languages != null) && languages.contains(lang);
    }

    static void setup(Context context) {
        Resources resources = context.getResources();
        for (LanguageGroup group : values())
            group.setup(resources);
    }

    public static LanguageGroup find(String lang) {
        for (LanguageGroup group : values())
            if (group.contains(lang))
                return group;
        return NONE;
    }

    private void setup(Resources resources) {
        if (languages != null) {
            languages.clear();
            Collections.addAll(languages, resources.getStringArray(key));
        }
    }

}
