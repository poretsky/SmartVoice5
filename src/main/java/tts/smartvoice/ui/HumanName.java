package tts.smartvoice.ui;

import java.util.Locale;

import android.content.Context;

import tts.smartvoice.R;
import tts.smartvoice.SmartVoiceApp;
import tts.smartvoice.api.Utils;
import tts.synth.PicoTtsEngine;
import tts.thirdparty.TtsIntegrator;

enum HumanName {

    Katya(R.string.voice_name_katya),
    Milena(R.string.voice_name_milena),
    Yuri(R.string.voice_name_yuri),
    Robot(R.string.voice_name_robot),
    Male(R.string.voice_male),
    Female(R.string.voice_female),
    Carmit(R.string.voice_name_carmit);

    private int resId;

    HumanName(int resId) {
        this.resId = resId;
    }

    static String get(String language) {
        Locale locale = Utils.obtainLocale(language);
        return locale.getDisplayLanguage();
    }

    static String get(String language, String country) {
        Locale locale = ((country != null) && !country.equals(Utils.ANY_COUNTRY)) ?
            Utils.obtainLocale(language, country) :
            Utils.obtainLocale(language);
        return locale.getDisplayName();
    }

    static String get(String internalName, Context context) {
        String externalName = internalName;
        try {
            externalName = internalName.startsWith(PicoTtsEngine.GENERAL_VOICE_NAME) ?
                context.getString(R.string.voice_name_robot) :
                valueOf(internalName).localize(context);
        } catch (Exception ex) {
            int nameLength = internalName.indexOf('/');
            if (nameLength > 0)
                externalName = internalName.substring(0, nameLength);
            else {
                TtsIntegrator ttsIntegrator = ((SmartVoiceApp) context.getApplicationContext()).getTtsIntegrator();
                nameLength = (ttsIntegrator != null) ?
                    internalName.indexOf('|') :
                    -1;
                if (nameLength > 0) {
                    externalName = ttsIntegrator.getEngineLabelFor(internalName.substring(0, nameLength));
                }
            }
        }
        return externalName;
    }

    static String get(String lang, String country, String variant, Context context) {
        return String.format(Locale.getDefault(), "%s - %s", get(variant, context), get(lang, country));
    }

    static String get(String[] vs, Context context) {
        return get(vs[0], vs[1], vs[2], context);
    }

    private String localize(Context context) {
        return context.getString(resId);
    }

}
