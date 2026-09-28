package tts.synth;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.speech.tts.TextToSpeech;
import android.util.SparseArray;

import androidx.preference.PreferenceManager;

import tts.smartvoice.R;
import tts.smartvoice.api.SoundFormatAdapter;
import tts.smartvoice.api.TtsEngine;
import tts.smartvoice.api.Speaker;

public class RussianVoiceEngine implements TtsEngine, SharedPreferences.OnSharedPreferenceChangeListener {

    public static final String VOICE_NAME = String.format((Locale) null, "rus-RUS-%s", Robot.GENERAL_NAME);
    public static final String MALE_VOICE = VOICE_NAME + "\'" + Robot.MALE;
    public static final String FEMALE_VOICE = VOICE_NAME + "\'" + Robot.FEMALE;

    private static final int SAMPLE_RATE = 10000;
    private static final int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_8BIT;
    private static final int NCHANNELS = 1;

    private static final int MIN_RATE = 40;

    private static final int MIN_PITCH = 50;
    private static final float PITCH_STEP = 0.75f;

    private static final float FILTER_ATTENUATION = -4.5f;
    private static final float FILTER_TRANSITION_FREQUENCY = 3000.0f;
    private static final float FILTER_SLOPE = 1.0f;
    private static final float FILTER_GAIN = 1.5f;

    private final LowShelfFilter audioFilter;
    private final List<Map.Entry<Pattern, String>> textFilters;
    private final List<Map.Entry<Pattern, String>> translitFilters;
    private final List<Map.Entry<Pattern, SparseArray<String>>> abbrevFilters;
    private final Pattern explicitStress;

    private final Context context;
    private final float intonationDefault;
    private final float interclauseGapDefault;
    private final boolean useRulexDefault;
    private final boolean decimalCommaDefault;
    private final boolean decimalPointDefault;
    private final boolean speakQuotesDefault;
    private final boolean speakParenthesisDefault;

    private SoundFormatAdapter synthesisCallback;
    private volatile boolean isSpeaking;

    // Mandatory fields examined by JNI
    private final String rulexPath;
    private int audioBufferSize;

    // Speech parameters
    private int volume;
    private int pitch;
    private int speechRate;
    private int intonation;
    private int generalGapFactor;
    private int commaGapFactor;
    private int dotGapFactor;
    private int semicolonGapFactor;
    private int colonGapFactor;
    private int questionGapFactor;
    private int exclamationGapFactor;
    private int intonationalGapFactor;
    private boolean decimalPoint;
    private boolean decimalComma;
    private boolean altVoice;
    private boolean useRulex;

    // Additional filtering parameters
    private boolean speakQuotes;
    private boolean speakParenthesis;


    public RussianVoiceEngine(Context context, File rulexDB) {
        this.context = context;
        intonationDefault = context.getResources().getInteger(R.integer.intonation_value);
        interclauseGapDefault = context.getResources().getInteger(R.integer.interclause_gap_value);
        useRulexDefault = context.getResources().getBoolean(R.bool.pref_use_rulex_state);
        decimalCommaDefault = context.getResources().getBoolean(R.bool.pref_decimal_comma_state);
        decimalPointDefault = context.getResources().getBoolean(R.bool.pref_decimal_point_state);
        speakQuotesDefault = context.getResources().getBoolean(R.bool.pref_speak_quotes_state);
        speakParenthesisDefault = context.getResources().getBoolean(R.bool.pref_speak_parenthesis_state);
        rulexPath = rulexDB.getAbsolutePath();
        audioFilter = new LowShelfFilter(FILTER_ATTENUATION, FILTER_TRANSITION_FREQUENCY, FILTER_SLOPE, FILTER_GAIN);
        audioFilter.enableContrastEnhancement(true);
        textFilters = new ArrayList<Map.Entry<Pattern, String>>();
        translitFilters = new ArrayList<Map.Entry<Pattern, String>>();
        abbrevFilters = new ArrayList<Map.Entry<Pattern, SparseArray<String>>>();
        synthesisCallback = null;
        isSpeaking = false;
        volume = 80;
        pitch = 100;
        speechRate = 100;
        commaGapFactor = 100;
        dotGapFactor = 100;
        semicolonGapFactor = 100;
        colonGapFactor = 100;
        questionGapFactor = 100;
        exclamationGapFactor = 100;
        intonationalGapFactor = 100;
        altVoice = false;

        // Text filters chain
        addFilter("[+-]?\\d+([\\.,]\\d+)?|_", " $0 ");
        addFilter("\\+-", " плюс минус ");
        addFilter("-(\\d)", "минус $1");
        addFilter("([а-яёА-ЯЁ][аеёиоуыэю]|[яЯ])(\\s+|-)(([бж]|ль)\\b)", "$1$3");

        // Abbrev filter
        SparseArray<String> substitutions = new SparseArray<String>();
        substitutions.put('б', "бэ");
        substitutions.put('в', "вэ");
        substitutions.put('й', "и краткое");
        substitutions.put('к', "ка");
        substitutions.put('с', "эс");
        substitutions.put('ъ', "твёрдый знак");
        substitutions.put('ь', "мягкий знак");
        substitutions.put('b', "бэ");
        substitutions.put('g', "жэ");
        substitutions.put('h', "аш");
        substitutions.put('j', "йот");
        substitutions.put('k', "ка");
        substitutions.put('q', "ку");
        substitutions.put('s', "эс");
        substitutions.put('v', "вэ");
        substitutions.put('w', "дубльвэ");
        substitutions.put('x', "икс");
        substitutions.put('y', "игрек");
        substitutions.put('z', "зэт");
        addFilter("\\b[бвкс]\\.", substitutions);
        addFilter("(\\b|-)([bcdfghj-np-tv-zб-джзй-нп-тф-ъь]{2,}|[bghjkqsv-zбжьъ])(-|\\b)", substitutions);
        addFilter("^[\\Wbcdfghj-np-tv-zб-джзй-нп-тф-ъь]+$", substitutions);

        // Special symbols
        substitutions = new SparseArray<String>();
        substitutions.put('$', "доллар");
        substitutions.put('#', "решётка");
        substitutions.put('@', "собака");
        substitutions.put('!', "восклицательный знак");
        substitutions.put('/', "слэш");
        substitutions.put('%', "процент");
        substitutions.put('^', "домик");
        substitutions.put('&', "амперсанд");
        substitutions.put('*', "звезда");
        substitutions.put('-', "минус");
        substitutions.put('_', "подчерк");
        substitutions.put('+', "плюс");
        substitutions.put('=', "равно");
        substitutions.put('\\', "бэкслэш");
        substitutions.put('|', "вертикальная черта");
        substitutions.put('.', "точка");
        substitutions.put(',', "запятая");
        substitutions.put(';', "точка с запятой");
        substitutions.put(':', "двоеточие");
        substitutions.put('\'', "апостроф");
        substitutions.put('"', "кавычка");
        substitutions.put('?', "вопросительный знак");
        substitutions.put('`', "одинарная кавычка");
        substitutions.put('~', "тильда");
        substitutions.put('(', "круглая скобка открыть");
        substitutions.put(')', "круглая скобка закрыть");
        substitutions.put('[', "квадратная скобка открыть");
        substitutions.put(']', "квадратная скобка закрыть");
        substitutions.put('{', "фигурная скобка открыть");
        substitutions.put('}', "фигурная скобка закрыть");
        substitutions.put('<', "меньше");
        substitutions.put('>', "больше");
        addFilter("^\\W+$", substitutions);
        addFilter("[+=@#$%^&*~<>/\\|]+", substitutions);
        addFilter("://", substitutions);

        // Special transliteration cases
        addTranslit("ä", "я");
        addTranslit("[öœ]", "ё");
        addTranslit("[üû]", "ю");
        addTranslit("ï", "й");
        addTranslit("ß", "сс");
        addTranslit("ç", "с");
        addTranslit("[îі]", "и");
        addTranslit("ô", "о");
        addTranslit("[àá]", "а+");
        addTranslit("[òó]", "о+");
        addTranslit("[ùú]", "у+");
        addTranslit("[ìí]", "и+");
        addTranslit("[èé]", "э+");
        addTranslit("ñ", "нь");
        addTranslit("є", "е");
        addTranslit("ї", "йи");

        // Explicit stress notation pattern
        explicitStress = Pattern.compile("([аеёиоуыэюя])\\u0301");

        // Integration with preferences
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        intonation = Math.round(preferences.getFloat(context.getString(R.string.intonation_key), intonationDefault));
        generalGapFactor = Math.round(preferences.getFloat(context.getString(R.string.interclause_gap_key), interclauseGapDefault));
        decimalPoint = preferences.getBoolean(context.getString(R.string.pref_decimal_point_key), decimalPointDefault);
        decimalComma = preferences.getBoolean(context.getString(R.string.pref_decimal_comma_key), decimalCommaDefault);
        useRulex = preferences.getBoolean(context.getString(R.string.pref_use_rulex_key), useRulexDefault);
        speakQuotes = preferences.getBoolean(context.getString(R.string.pref_speak_quotes_key), speakQuotesDefault);
        speakParenthesis = preferences.getBoolean(context.getString(R.string.pref_speak_parenthesis_key), speakParenthesisDefault);
        preferences.registerOnSharedPreferenceChangeListener(this);
    }


    @Override
    public List<String> getAvailableVoices() {
        List<String> voices = new ArrayList<String>();
        voices.add(MALE_VOICE);
        voices.add(FEMALE_VOICE);
        return voices;
    }

    @Override
    public String[] getLanguage() {
        return new String[] {
            VOICE_NAME.substring(0, 3),
            VOICE_NAME.substring(4, 7),
            (altVoice ? FEMALE_VOICE : MALE_VOICE).substring(8)
        };
    }

    @Override
    public String getVoice(String lang, String country, String variant) {
        return (loadLanguage(lang, country, variant) < TextToSpeech.LANG_AVAILABLE) ? null : (altVoice ? FEMALE_VOICE : MALE_VOICE);
    }

    @Override
    public Speaker getSpeaker(String langVoiceSpec) {
        if (langVoiceSpec.indexOf('\'') > 0)
            return new Robot(langVoiceSpec, langVoiceSpec.substring(langVoiceSpec.indexOf('\'') + 1), this);
        return new Robot(langVoiceSpec, Robot.MALE, this);
    }

    @Override
    public int loadLanguage(String lang, String country, String variant) {
        int result = TextToSpeech.LANG_NOT_SUPPORTED;
        if (VOICE_NAME.substring(0, 3).equals(lang)) {
            result = TextToSpeech.LANG_AVAILABLE;
            if (VOICE_NAME.substring(4, 7).equals(country)) {
                result = TextToSpeech.LANG_COUNTRY_AVAILABLE;
                if (VOICE_NAME.substring(8).equals(variant) ||
                    MALE_VOICE.substring(8).equals(variant) ||
                    FEMALE_VOICE.substring(8).equals(variant))
                    result = TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
            }
        }
        if (result > TextToSpeech.LANG_NOT_SUPPORTED)
            altVoice = FEMALE_VOICE.substring(8).equals(variant);
        return result;
    }

    @Override
    public int setAudioStream(int value) {
        return (value < 0) ? TextToSpeech.ERROR : TextToSpeech.SUCCESS;
    }

    @Override
    public int setVolume(int value) {
        volume = (value < 0) ? 0 : Math.min(value, 100);
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int setSpeechRate(int value) {
        speechRate = (value + MIN_RATE) >> 1;
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int setPitch(int value) {
        pitch = Math.round(value * PITCH_STEP) + MIN_PITCH;
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int synthesizeText(String text, SoundFormatAdapter callback) {
        if (callback.start(SAMPLE_RATE, AUDIO_FORMAT, NCHANNELS) == TextToSpeech.SUCCESS) {
            synthesisCallback = callback;
            isSpeaking = true;
            callback.setGain(((float)volume) / 100.0f);
            callback.setAudioFilter(audioFilter);
            audioBufferSize = callback.getMaxBufferSize();
            if (text.matches(".*\\w.*")) {
                if (!speakQuotes)
                    text = text.replace('\"', ' ');
                if (!speakParenthesis) {
                    text = text.replace('(', ' ');
                    text = text.replace(')', ' ');
                }
            }
            for (Map.Entry<Pattern, String> filter : textFilters)
                text = filter.getKey().matcher(text).replaceAll(filter.getValue());
            text = text.toLowerCase(Locale.getDefault());
            StringBuilder preparedText = new StringBuilder();
            for (Map.Entry<Pattern, SparseArray<String>> filter : abbrevFilters) {
                int position = 0;
                preparedText.setLength(0);
                for (Matcher abbrev = filter.getKey().matcher(text); abbrev.find(); position = abbrev.end()) {
                    if (position < abbrev.start())
                        preparedText.append(text.substring(position, abbrev.start()));
                    for (int i = abbrev.start(); i < abbrev.end(); i++) {
                        char originalLetter = text.charAt(i);
                        String replacement = filter.getValue().get(originalLetter);
                        if (replacement != null)
                            preparedText.append(' ').append(replacement);
                        else if (originalLetter < 'A')
                            preparedText.append(originalLetter);
                        else preparedText.append(' ').append(originalLetter);
                    }
                    if ((abbrev.end() < text.length()) && (text.charAt(abbrev.end()) >= 'A'))
                        preparedText.append(' ');
                }
                if (position < text.length())
                    preparedText.append(text.substring(position));
                text = preparedText.toString();
            }
            for (Map.Entry<Pattern, String> filter : translitFilters)
                text = filter.getKey().matcher(text).replaceAll(filter.getValue());
            text = explicitStress.matcher(text).replaceAll("$1+");
            speak(text.trim());
        }
        isSpeaking = false;
        return callback.done();
    }

    @Override
    public void stop() {
        isSpeaking = false;
    }

    @Override
    public void destroy() {
        PreferenceManager.getDefaultSharedPreferences(context).unregisterOnSharedPreferenceChangeListener(this);
    }


    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (context.getString(R.string.pref_use_rulex_key).equals(key))
            useRulex = preferences.getBoolean(key, useRulexDefault);
        else if (context.getString(R.string.pref_speak_quotes_key).equals(key))
            speakQuotes = preferences.getBoolean(key, speakQuotesDefault);
        else if (context.getString(R.string.pref_speak_parenthesis_key).equals(key))
            speakParenthesis = preferences.getBoolean(key, speakParenthesisDefault);
        else if (context.getString(R.string.pref_decimal_point_key).equals(key))
            decimalPoint = preferences.getBoolean(key, decimalPointDefault);
        else if (context.getString(R.string.pref_decimal_comma_key).equals(key))
            decimalComma = preferences.getBoolean(key, decimalCommaDefault);
        else if (context.getString(R.string.intonation_key).equals(key))
            intonation = Math.round(preferences.getFloat(key, intonationDefault));
        else if (context.getString(R.string.interclause_gap_key).equals(key))
            generalGapFactor = Math.round(preferences.getFloat(key, interclauseGapDefault));
    }


    private native void speak(String text);

    private boolean speechCallback(byte[] audioBuffer) {
        if (isSpeaking) {
            for (int i = 0; i < audioBuffer.length; i++)
                audioBuffer[i] += 128;
            if (synthesisCallback.audioAvailable(audioBuffer, 0, audioBuffer.length) != TextToSpeech.SUCCESS)
                isSpeaking = false;
        }
        return !isSpeaking;
    }

    private void addFilter(String match, String replacement, List<Map.Entry<Pattern, String>> filters) {
        filters.add(new AbstractMap.SimpleImmutableEntry<Pattern, String>(Pattern.compile(match, Pattern.UNICODE_CASE), replacement));
    }

    private void addFilter(String match, SparseArray<String> substitutions) {
        abbrevFilters.add(new AbstractMap.SimpleImmutableEntry<Pattern, SparseArray<String>>(Pattern.compile(match, Pattern.UNICODE_CASE), substitutions));
    }

    private void addFilter(String match, String replacement) {
        addFilter(match, replacement, textFilters);
    }

    private void addTranslit(String match, String replacement) {
        addFilter(match, replacement, translitFilters);
    }

    static {
        System.loadLibrary("ruvoicesynth");
    }

}
