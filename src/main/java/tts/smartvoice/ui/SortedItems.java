package tts.smartvoice.ui;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
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

    public static SortedSet<VoiceItem> from(Collection<String> langs, Map<String, Set<VoiceItem>> voiceMap) {
        SortedSet<VoiceItem> result = new TreeSet<VoiceItem>();
        for (String lang : langs)
            result.add(new VoiceItem(lang, voiceMap.containsKey(lang) ? voiceMap.get(lang).size() : 0));
        return result;
    }

    public static SortedSet<VoiceItem> from(Map<String, Set<VoiceItem>> voiceMap) {
        return from(voiceMap.keySet(), voiceMap);
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

    public static SortedSet<VoiceItem> fromGeneralVoices(SmartVoiceApp app, Collection<String> assignedVoices) {
        SortedSet<VoiceItem> result = new TreeSet<VoiceItem>();
        for (String voice : app.generalVoices) {
            int priority = assignedVoices.contains(voice) ? 1 : 0;
            result.add(new VoiceItem(voice, priority, app));
        }
        return result;
    }

}
