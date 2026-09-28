package tts.thirdparty;

import android.speech.tts.Voice;

import tts.smartvoice.api.Speaker;
import tts.smartvoice.api.TtsEngine;

class AndroidVoice extends Speaker {

    public AndroidVoice(String name, TtsEngine engine, Voice voice) {
        super(name,
              null,
              null,
              engine,
              voice.getLocale(),
              voice.getQuality(),
              voice.getLatency(),
              voice.isNetworkConnectionRequired(),
              voice.getFeatures());
    }

}
