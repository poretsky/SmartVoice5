package tts.synth;

import java.util.Collections;
import java.util.Locale;

import tts.smartvoice.api.TtsEngine;
import tts.smartvoice.api.Speaker;
import tts.smartvoice.api.Utils;

public class Robot extends Speaker {

    public static final String GENERAL_NAME = "Robot";
    public static final String MALE = "Male";
    public static final String FEMALE = "Female";

    Robot(String name, String gender, TtsEngine engine) {
        super(name,
              gender,
              "Adult",
              engine,
              Utils.obtainLocale(name.substring(0, 3), name.substring(4, 7)),
              QUALITY_LOW,
              LATENCY_VERY_LOW,
              false,
              Collections.emptySet());
    }

}
