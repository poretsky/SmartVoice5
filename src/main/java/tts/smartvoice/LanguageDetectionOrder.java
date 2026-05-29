package tts.smartvoice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import android.content.Context;

public class LanguageDetectionOrder implements Comparator<String> {

    private final Context context;
    private final StringList languages;

    public LanguageDetectionOrder(Context context) {
        this.context = context;
        languages = new StringList();
    }

    public LanguageDetectionOrder(Context context, String order) {
        this(context);
        setup(order);
    }

    public void setup(String order) {
        if (order != null) {
            List<String> availableLanguages = new ArrayList<String>();
            Collections.addAll(availableLanguages, context.getResources().getStringArray(R.array.languages));
            languages.putStringValue(order);
            for (String lang : availableLanguages)
                if (!languages.contains(lang))
                    languages.add(lang);
            languages.retainAll(availableLanguages);
        } else {
            languages.clear();
            Collections.addAll(languages, context.getResources().getStringArray(R.array.languages));
        }
    }

    public String getStringValue() {
        return languages.getStringValue();
    }


    @Override
    public int compare(String s1, String s2) {
        return languages.indexOf(s1) - languages.indexOf(s2);
    }

}
