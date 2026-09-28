package tts.smartvoice.markup;

import java.lang.reflect.Type;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

import android.content.Context;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

final class EmojiTranslator {

    private Map<String, String> emojiMap;
    private NavigableSet<Integer> emojiLengths;
    private String lang;


    public EmojiTranslator(Context context, String language) {
        emojiMap = new HashMap<String, String>();
        emojiLengths = new TreeSet<Integer>();
        lang = language;
        if (loadData(context) && (lang.length() > 3)) {
            lang = language.substring(0, 3);
            loadData(context);
        }
    }

    public String translate(String emoji) {
        return emojiMap.get(emoji);
    }

    public Set<Integer> getEmojiLengths() {
        return emojiLengths.descendingSet();
    }

    public String getLanguage() {
        return lang;
    }


    private boolean loadData(Context context) {
        try {
            Reader source = new InputStreamReader(context.getAssets().open(String.format((Locale) null, "emoji/%s.json", lang)));
            Type collectionType = new TypeToken<Collection<EmojiItem>>(){}.getType();
            Collection<EmojiItem> emoji = new GsonBuilder()
                .registerTypeAdapter(EmojiItem.class, new EmojiItemDeserializer())
                .create()
                .fromJson(source, collectionType);
            for (EmojiItem item : emoji) {
                emojiMap.put(item.cp, item.tts);
                emojiLengths.add(item.cp.length());
            }
        } catch (Exception fail) {
            return true;
        }
        return false;
    }

}
