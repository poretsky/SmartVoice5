package tts.smartvoice.api;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;

public class Utils {

    private static final Map<String, String> languages = new HashMap<String, String>();
    private static final Map<String, String> countries = new HashMap<String, String>();

    public static final String ANY_COUNTRY = "___";

    public static String getLanguageCode(String lang) {
        return languages.containsKey(lang) ? languages.get(lang) : lang;
    }

    public static String getCountryCode(String country) {
        return countries.containsKey(country) ? countries.get(country) : country;
    }

    public static Locale obtainLocale(String lang) {
        Locale.Builder localeBuilder = new Locale.Builder();
        return localeBuilder
            .setLanguage(getLanguageCode(lang))
            .build();
    }

    public static Locale obtainLocale(String lang, String country) {
        Locale.Builder localeBuilder = new Locale.Builder();
        return localeBuilder
            .setLanguage(getLanguageCode(lang))
            .setRegion(getCountryCode(country))
            .build();
    }

    @SuppressWarnings("deprecation")
    public static long getThreadId(Thread thread) {
        return (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) ? thread.getId() : thread.threadId();
    }

    public static int evaluateVoice(String lang, String country, String variant) {
        if (TextUtils.isEmpty(lang))
            return TextToSpeech.LANG_NOT_SUPPORTED;
        else if (TextUtils.isEmpty(country))
            return TextUtils.isEmpty(variant) ? TextToSpeech.LANG_AVAILABLE : TextToSpeech.LANG_NOT_SUPPORTED;
        else if (TextUtils.isEmpty(variant))
            return TextToSpeech.LANG_COUNTRY_AVAILABLE;
        return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
    }

    public static String constructVoiceNameFor(String lang, String country, String variant, int status) {
        String result;
        switch (status) {
        case TextToSpeech.LANG_AVAILABLE:
            result = lang;
            break;
        case TextToSpeech.LANG_COUNTRY_AVAILABLE:
            result = String.format(Locale.ROOT, "%s-%s", lang, country);
            break;
        case TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE:
            result = String.format(Locale.ROOT, "%s-%s-%s", lang, country, variant);
            break;
        default:
            result = null;
            break;
        }
        return result;
    }

    public static String constructVoiceNameFor(String lang, String country, String variant) {
        return constructVoiceNameFor(lang, country, variant, evaluateVoice(lang, country, variant));
    }

    private Utils() {
    }

    static {
        for (String lang : Locale.getISOLanguages())
            try {
                Locale.Builder localeBuilder = new Locale.Builder();
                languages.put(localeBuilder.setLanguage(lang).build().getISO3Language(), lang);
            } catch (Exception ex) {
                continue;
            }
        for (String country : Locale.getISOCountries())
            try {
                Locale.Builder localeBuilder = new Locale.Builder();
                countries.put(localeBuilder.setRegion(country).build().getISO3Country(), country);
            } catch (Exception ex) {
                continue;
            }
    }

}
