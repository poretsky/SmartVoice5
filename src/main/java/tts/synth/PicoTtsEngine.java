package tts.synth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.media.AudioFormat;
import android.speech.tts.TextToSpeech;

import tts.smartvoice.api.AudioFilter;
import tts.smartvoice.api.SoundFormatAdapter;
import tts.smartvoice.api.TtsEngine;
import tts.smartvoice.api.Speaker;

public class PicoTtsEngine implements TtsEngine {

    public static final String GENERAL_VOICE_NAME = "PicoVoice";

    public static final String[] SUPPORTED_LANGUAGES = {
        "eng-USA", "eng-GBR", "deu-DEU", "spa-ESP", "fra-FRA", "ita-ITA"
    };

    private static final String VOLUME = "volume";
    private static final String PITCH = "pitch";
    private static final String RATE = "rate";

    private static final int MIN_PITCH = 50;
    private static final int MAX_PITCH = 100;
    private static final int MIN_RATE = 20;
    private static final int MAX_RATE = 100;

    private static final int SAMPLE_RATE = 16000;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    private static final int NCHANNELS = 1;

    private static final float FILTER_ATTENUATION = -18.0f;
    private static final float FILTER_TRANSITION_FREQUENCY = 1100.0f;
    private static final float FILTER_SLOPE = 1.0f;
    private static final float FILTER_GAIN = 5.5f;

    private final AudioFilter audioFilter;

    private long jniData;
    private int audioBufferSize;

    private SoundFormatAdapter synthesisCallback;
    private volatile boolean isSpeaking;


    public PicoTtsEngine(String lingwarePath) {
        jniData = initialize(lingwarePath.endsWith("/") ? lingwarePath : lingwarePath.concat("/"));
        if (jniData == 0)
            throw new RuntimeException("Failed to initialize TTS engine");
        audioFilter = new LowShelfFilter(FILTER_ATTENUATION, FILTER_TRANSITION_FREQUENCY, FILTER_SLOPE, FILTER_GAIN);
        synthesisCallback = null;
        isSpeaking = false;
    }


    @Override
    public List<String> getAvailableVoices() {
        List<String> voices = new ArrayList<String>();
        for (String lang : SUPPORTED_LANGUAGES)
            voices.add(String.format(Locale.ROOT, "%s-%s", lang, Robot.GENERAL_NAME));
        return voices;
    }

    @Override
    public String[] getLanguage() {
        String[] result = getCurrentLanguage();
        result[2] = Robot.GENERAL_NAME;
        return result;
    }

    @Override
    public String getVoice(String lang, String country, String variant) {
        if (loadLanguage(lang, country, variant) < TextToSpeech.LANG_AVAILABLE)
            return null;
        String ls[] = getLanguage();
        String voice = String.format((Locale) null, "%s-%s-%s", ls[0], ls[1], GENERAL_VOICE_NAME);
        for (int i = 0; i < SUPPORTED_LANGUAGES.length; i++)
            if (voice.substring(0, 7).equals(SUPPORTED_LANGUAGES[i])) {
                voice = String.format((Locale) null, "%s%d", voice, i);
                break;
            }
        return voice;
    }

    @Override
    public Speaker getSpeaker(String langVoiceSpec) {
        return new Robot(langVoiceSpec, Robot.FEMALE, this);
    }

    @Override
    public int loadLanguage(String lang, String country, String variant) {
        return setLanguage(lang, country, variant);
    }

    @Override
    public int setAudioStream(int value) {
        return (value < 0) ? TextToSpeech.ERROR : TextToSpeech.SUCCESS;
    }

    @Override
    public int setVolume(int value) {
        return setProperty(VOLUME, String.valueOf(value));
    }

    @Override
    public int setSpeechRate(int value) {
        return setProperty(RATE, String.valueOf(value * (MAX_RATE - MIN_RATE) / 100 + MIN_RATE));
    }

    @Override
    public int setPitch(int value) {
        return setProperty(PITCH, String.valueOf(value * (MAX_PITCH - MIN_PITCH) / 100 + MIN_PITCH));
    }

    @Override
    public int synthesizeText(String text, SoundFormatAdapter callback) {
        if (callback.start(SAMPLE_RATE, AUDIO_FORMAT, NCHANNELS) == TextToSpeech.SUCCESS) {
            synthesisCallback = callback;
            isSpeaking = true;
            callback.setFullGain();
            callback.setAudioFilter(audioFilter);
            audioBufferSize = callback.getMaxBufferSize();
            speak(text.trim());
        }
        isSpeaking = false;
        return callback.done();
    }

    @Override
    public void stop() {
        isSpeaking = false;
        abort();
    }

    @Override
    public void destroy() {
        shutdown();
    }


    private boolean outputAudioStream(byte[] audioBuffer) {
        if (isSpeaking && (synthesisCallback.audioAvailable(audioBuffer, 0, audioBuffer.length) != TextToSpeech.SUCCESS))
                isSpeaking = false;
        return !isSpeaking;
    }

    private native long initialize(String resourcePath);

    private native void shutdown();

    private native int hasVoiceFor(String language, String country, String variant);

    private native int setLanguage(String language, String country, String variant);

    private native String[] getCurrentLanguage();

    private native int setProperty(String name, String value);

    private native int speak(String text);

    private native int abort();

    static {
        System.loadLibrary("svttspico");
    }

}
