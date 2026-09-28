package tts.smartvoice.api;

import java.util.Locale;
import java.util.Set;

import android.speech.tts.Voice;

public class Speaker extends Voice {

    private final TtsEngine engine;
    private final String gender;
    private final String age;

    public Speaker(String name,
                   String gender,
                   String age,
                   TtsEngine engine,
                   Locale locale,
                   int quality,
                   int latency,
                   boolean requiresNetworkConnection,
                   Set<String> features) {
        super(name, locale, quality, latency, requiresNetworkConnection, features);
        this.engine = engine;
        this.gender = gender;
        this.age = age;
    }

    public TtsEngine getEngine() {
        return engine;
    }

    public String getGender() {
        return gender;
    }

    public String getAge() {
        return age;
    }

}
