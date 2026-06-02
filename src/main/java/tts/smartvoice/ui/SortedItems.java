package tts.smartvoice.ui;

import java.util.Collection;
import java.util.SortedSet;
import java.util.TreeSet;

import android.content.Context;

import tts.smartvoice.SmartVoiceApp;

class SortedItems {

    public static SortedSet<VoiceItem> from(Collection<String> langs) {
        SortedSet<VoiceItem> result = new TreeSet<VoiceItem>();
        for (String lang : langs)
            result.add(new VoiceItem(lang));
        return result;
    }

    public static SortedSet<VoiceItem> from(Collection<String> voices, Context context){
        SortedSet<VoiceItem> result = new TreeSet<VoiceItem>();
        for (String voice : voices)
            result.add(new VoiceItem(voice, context));
        return result;
    }

    public static SortedSet<VoiceItem> fromGeneralVoices(SmartVoiceApp app) {
        return from(app.generalVoices, app);
    }

}
