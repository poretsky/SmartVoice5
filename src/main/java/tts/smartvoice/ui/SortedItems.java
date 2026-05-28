package tts.smartvoice.ui;

import java.util.SortedSet;
import java.util.TreeSet;

import tts.smartvoice.SmartVoiceApp;

class SortedItems {

    public static SortedSet<VoiceItem> getGeneralVoices(SmartVoiceApp app) {
        SortedSet<VoiceItem> result = new TreeSet<VoiceItem>();
        for (String voice : app.generalVoices)
            result.add(new VoiceItem(voice, app));
        return result;
    }

}
