package tts.smartvoice.api;

import java.util.List;

public interface TtsEngine {

    public interface OnLoadListener {
        public void onLoad(TtsEngine engine);
    }

    public List<String> getAvailableVoices();

    public String[] getLanguage();

    public String getVoice(String lang, String country, String variant);

    public Speaker getSpeaker(String langVoiceSpec);

    public int loadLanguage(String lang, String country, String variant);

    public int setAudioStream(int value);

    public int setVolume(int value);

    public int setSpeechRate(int value);

    public int setPitch(int value);

    public int synthesizeText(String text, SoundFormatAdapter callback);

    public void stop();

    public void destroy();

}
