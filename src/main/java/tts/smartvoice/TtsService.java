package tts.smartvoice;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.tts.SynthesisCallback;
import android.speech.tts.SynthesisRequest;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeechService;
import android.speech.tts.Voice;
import android.text.TextUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.ServiceCompat;
import androidx.preference.PreferenceManager;

import tts.smartvoice.api.SoundFormatAdapter;
import tts.smartvoice.api.TtsEngine;
import tts.smartvoice.api.Speaker;

import tts.smartvoice.markup.SpeechMarkupFactory;
import tts.smartvoice.markup.TextElement;

import tts.smartvoice.ui.QuickControlActivity;

import tts.synth.Robot;

public class TtsService extends TextToSpeechService implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final int QUICK_CONTROL_WIDGET_ID = 1;
    private static final String NOTIFICATION_CHANNEL_ID = "SmartVoiceChannel";
    private static final String NO_LANGUAGE[] = { "", "", "" };

    private static volatile TtsService instance = null;

    String language;
    String country;
    String variant;

    List<String> autoLangs;
    Set<String> stickyLangs;
    String latinicFallback;
    String cyrillicFallback;
    String arabicFallback;
    String cjkFallback;
    String numericVoice;
    String emojiVoice;
    String systemValue;

    boolean singleVoiceMessages;
    boolean useRequestedVoice;

    SpeechMarkupFactory speechMarkupFactory;
    SmartVoiceApp app;

    private Set<String> detectableLanguages;
    private String defaultLanguage;
    private String defaultCountry;
    private String defaultVariant;

    private String volumeKey;
    private String speechRateKey;
    private String pitchKey;
    private String logTextKey;
    private String useLocaleKey;
    private String fallbackLatinicKey;
    private String fallbackCyrillicKey;
    private String fallbackArabicKey;
    private String fallbackCjkKey;
    private String numericLangKey;
    private String emojiVoiceKey;
    private String detectionOrderKey;
    private String autoLangsKey;
    private String stickyLangsKey;
    private String singleVoiceMessagesKey;
    private String useRequestedVoiceKey;
    private String quickControlWidgetKey;
    private String ignoreEmoticonsKey;

    private float defaultVolume;
    private float defaultSpeechRate;
    private float defaultPitch;

    private boolean logTextDefaultState;
    private boolean useLocaleDefaultState;
    private boolean singleVoiceMessagesDefaultState;
    private boolean useRequestedVoiceDefaultState;
    private boolean quickControlWidgetDefaultState;
    private boolean ignoreEmoticonsDefaultState;

    private Map<String, String> assignedVoices;
    private Map<String, Float> volume;
    private Map<String, Float> speechRate;
    private Map<String, Float> pitch;

    private boolean logTextEnabled;
    private boolean useLocale;
    private boolean ignoreEmoticons;

    private volatile boolean locked;

    private SharedPreferences preferences;
    private SystemSettingsObserver systemSettingsObserver;
    private TtsEngine currentEngine;

    private final LanguageDetectionOrder languageDetectionOrder;
    private final SoundFormatAdapter soundFormatAdapter;
    private final SystemEventsHandler systemEventsHandler;
    private final Pattern emoticons;
    private final TextPreprocessor textPreprocessor;


    public TtsService() {
        languageDetectionOrder = new LanguageDetectionOrder(this);
        soundFormatAdapter = new SoundFormatAdapter();
        systemEventsHandler = new SystemEventsHandler(this);
        autoLangs = new ArrayList<String>();
        detectableLanguages = new HashSet<String>();
        stickyLangs = new HashSet<String>();
        assignedVoices = new HashMap<String, String>();
        volume = new HashMap<String, Float>();
        pitch = new HashMap<String, Float>();
        speechRate = new HashMap<String, Float>();
        emoticons = Pattern.compile("\\p{InEmoticons}+");
        textPreprocessor = new TextPreprocessor(this);
        locked = false;
        currentEngine = null;
    }


    private void sync() {
        while (locked)
            try {
                wait();
            } catch (InterruptedException ex) {
            }
    }

    @SuppressLint("NewApi")
    private void exposeQuickControlWidget(boolean enabled) {
        if (enabled) {
            Intent quickControl = new Intent(this, QuickControlActivity.class);
            quickControl.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
                NotificationChannel notificationChannel = new NotificationChannel(NOTIFICATION_CHANNEL_ID, getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_HIGH);
                notificationChannel.setDescription(getString(R.string.notification_channel_description));
                notificationChannel.setSound(null, null);
                notificationManager.createNotificationChannel(notificationChannel);
            }
            Notification notification = new NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.icon)
                .setContentTitle(null)
                .setContentText(getString(R.string.quick_control))
                .setContentIntent(PendingIntent.getActivity(this, 0, quickControl, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setOngoing(true)
                .setAutoCancel(false)
                .setShowWhen(false)
                .build();
            startForeground(QUICK_CONTROL_WIDGET_ID, notification);
        } else {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE);
        }
    }

    private boolean isFQVN(String voice) {
        return (voice != null) && (app.generalVoices.contains(voice) || app.validVoices.contains(voice));
    }

    private int constructResultCodeFor(String lang, String country, String variant) {
        if (!TextUtils.isEmpty(lang)) {
            if (!TextUtils.isEmpty(country)) {
                if (!TextUtils.isEmpty(variant))
                    return TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE;
                return TextToSpeech.LANG_COUNTRY_AVAILABLE;
            }
            return TextToSpeech.LANG_AVAILABLE;
        }
        return TextToSpeech.LANG_NOT_SUPPORTED;
    }

    private String getUltimateVoiceFor(String lang) {
        String voice = getVoiceAssignmentFor(lang);
        if ((voice != null) && !app.validVoices.contains(voice))
            voice = (assignedVoices.containsKey(voice) ? assignedVoices : app.defaultVoices).get(voice);
        if ((voice != null) && voice.endsWith(Robot.GENERAL_NAME) && app.defaultVoices.containsKey(voice))
            voice = app.defaultVoices.get(voice);
        return voice;
    }

    private String generalName(String voice) {
        int generalNameLength = voice.indexOf('\'');
        return (generalNameLength > 0) ? voice.substring(0, generalNameLength) : voice;
    }

    private int setLanguage(String lang, String country, String variant) {
        int result = TextToSpeech.LANG_NOT_SUPPORTED;
        String vs = getUltimateVoiceFor(constructVoiceNameFor(lang, country, variant));
        if (vs != null) {
            String[] ls = vs.split("-");
            Speaker voice = app.voices.get(ls[2]);
            if (voice != null) {
                TtsEngine voiceEngine = voice.getEngine();
                result = Math.min(voiceEngine.loadLanguage(ls[0], ls[1], ls[2]), constructResultCodeFor(lang, country, variant));
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    currentEngine = voiceEngine;
                    textPreprocessor.remember(lang, country, variant);
                }
            }
        }
        return result;
    }

    private void setUseLocale(boolean enabled) {
        if (useRequestedVoice) {
            useLocale = enabled;
        } else if (enabled) {
            Locale locale = Locale.getDefault();
            useLocale = setLanguage(locale.getISO3Language(), locale.getISO3Country(), locale.getVariant()) >= TextToSpeech.LANG_AVAILABLE;
        } else {
            useLocale = false;
        }
    }

    private void messageSplitSetup() {
        singleVoiceMessages = preferences.getBoolean(singleVoiceMessagesKey, singleVoiceMessagesDefaultState) ||
            (autoLangs.isEmpty() &&
             (latinicFallback == null) &&
             (cyrillicFallback == null) &&
             (arabicFallback == null) &&
             (cjkFallback == null) &&
             (numericVoice == null) &&
             (emojiVoice == null));
    }

    private String validateVoiceFallback(String voice) {
        return ((voice != null) && app.generalVoices.contains(voice)) ? voice : null;
    }

    private String validateVoicePref(String voice) {
        return ((voice != null) && (voice.equals(systemValue) || app.generalVoices.contains(voice))) ? voice : null;
    }

    private String getEngineVoice(TtsEngine engine) {
        String voice = null;
        if (engine != null) {
            String[] ls = engine.getLanguage();
            if (ls != null)
                voice = engine.getVoice(ls[0], ls[1], ls[2]);
            if (voice != null)
                voice = voice.substring(8);
        }
        return voice;
    }

    private String getDefaultVoice(String lang) {
        String voice = getEngineVoice(currentEngine);
        if (voice == null) {
            TtsEngine engine = app.getFallbackEngine(lang);
            voice = getEngineVoice(engine);
            if (voice != null)
                currentEngine = engine;
        }
        return voice;
    }


    String constructVoiceNameFor(String lang, String country, String variant) {
        StringBuilder voiceName = new StringBuilder();
        if (!TextUtils.isEmpty(lang)) {
            voiceName.append(lang);
            if (!TextUtils.isEmpty(country)) {
                voiceName.append('-').append(country);
                if (!TextUtils.isEmpty(variant))
                    voiceName.append('-').append(variant);
            }
        }
        return voiceName.toString();
    }

    String getVoiceAssignmentFor(String lang, String country, String variant) {
        return getVoiceAssignmentFor(constructVoiceNameFor(lang, country, variant));
    }

    String getVoiceAssignmentFor(String voice) {
        return ((voice == null) || app.generalVoices.contains(voice)) ?
            voice :
            (assignedVoices.containsKey(voice) ? assignedVoices : app.defaultVoices).get(voice);
    }

    String getSystemVoiceName() {
        return constructVoiceNameFor(language, country, variant);
    }

    String getSystemVoiceAssignment() {
        return getVoiceAssignmentFor(language, country, variant);
    }


    void refresh() {
        currentEngine = null;
        autoLangs.clear();
        autoLangs.addAll(preferences.getStringSet(autoLangsKey, Collections.<String>emptySet()));
        autoLangs.retainAll(detectableLanguages);
        if (autoLangs.size() > 1)
            Collections.sort(autoLangs, languageDetectionOrder);
        speechMarkupFactory.setLanguages(autoLangs);
        latinicFallback = validateVoiceFallback(preferences.getString(fallbackLatinicKey, null));
        cyrillicFallback = validateVoiceFallback(preferences.getString(fallbackCyrillicKey, null));
        arabicFallback = validateVoiceFallback(preferences.getString(fallbackArabicKey, null));
        cjkFallback = validateVoiceFallback(preferences.getString(fallbackCjkKey, null));
        numericVoice = validateVoicePref(preferences.getString(numericLangKey, null));
        emojiVoice = validateVoicePref(preferences.getString(emojiVoiceKey, null));
        messageSplitSetup();
        useRequestedVoice = preferences.getBoolean(useRequestedVoiceKey, useRequestedVoiceDefaultState);
        stickyLangs = preferences.getStringSet(stickyLangsKey, Collections.<String>emptySet());
        stickyLangs.retainAll(app.languages);
        assignedVoices.clear();
        volume.clear();
        pitch.clear();
        speechRate.clear();
        for (String lang : app.generalVoices) {
            String voice = app.getInternalVoiceName(lang);
            volume.put(voice, preferences.getFloat(getString(R.string.voice_preference_key_format, voice, volumeKey), defaultVolume));
            speechRate.put(voice, preferences.getFloat(getString(R.string.voice_preference_key_format, voice, speechRateKey), defaultSpeechRate));
            pitch.put(voice, preferences.getFloat(getString(R.string.voice_preference_key_format, voice, pitchKey), defaultPitch));
        }
        for (String lang : app.languages) {
            String voice = preferences.getString(lang, null);
            if (isFQVN(voice))
                assignedVoices.put(lang, voice);
        }
        setUseLocale(preferences.getBoolean(useLocaleKey, useLocaleDefaultState));
    }

    synchronized void lock() {
        locked = true;
    }

    synchronized void unlock() {
        locked = false;
        notifyAll();
    }


    synchronized void notifyLocaleChange() {
        if (preferences.getBoolean(useLocaleKey, useLocaleDefaultState))
            setUseLocale(true);
    }


    static TtsService getInstance() {
        return instance;
    }


    @Override
    public void onCreate() {
        app = (SmartVoiceApp)getApplication();
        speechMarkupFactory = new SpeechMarkupFactory(this);
        detectableLanguages.clear();
        Collections.addAll(detectableLanguages, getResources().getStringArray(R.array.languages));
        defaultVolume = getResources().getInteger(R.integer.volume_value);
        defaultSpeechRate = getResources().getInteger(R.integer.rate_value);
        defaultPitch = getResources().getInteger(R.integer.pitch_value);
        logTextDefaultState = getResources().getBoolean(R.bool.log_text_state);
        useLocaleDefaultState = getResources().getBoolean(R.bool.use_default_locale_state);
        singleVoiceMessagesDefaultState = getResources().getBoolean(R.bool.one_voice_per_message_state);
        useRequestedVoiceDefaultState = getResources().getBoolean(R.bool.use_only_default_or_requested_voice_state);
        quickControlWidgetDefaultState = getResources().getBoolean(R.bool.quick_control_widget_option_state);
        ignoreEmoticonsDefaultState = getResources().getBoolean(R.bool.pref_ignore_emoticons_state);
        systemValue = getString(R.string.value_system);
        volumeKey = getString(R.string.volume_key);
        speechRateKey = getString(R.string.rate_key);
        pitchKey = getString(R.string.pitch_key);
        logTextKey = getString(R.string.log_text_key);
        useLocaleKey = getString(R.string.use_default_locale_key);
        singleVoiceMessagesKey = getString(R.string.one_voice_per_message_key);
        useRequestedVoiceKey = getString(R.string.use_only_default_or_requested_voice_key);
        fallbackLatinicKey = getString(R.string.latinic_fallback_key);
        fallbackCyrillicKey = getString(R.string.cyrillic_fallback_key);
        fallbackArabicKey = getString(R.string.arabic_fallback_key);
        fallbackCjkKey = getString(R.string.cjk_fallback_key);
        numericLangKey = getString(R.string.numeric_language_key);
        emojiVoiceKey = getString(R.string.pref_emoji_voice_key);
        quickControlWidgetKey = getString(R.string.quick_control_widget_option_key);
        ignoreEmoticonsKey = getString(R.string.pref_ignore_emoticons_key);
        detectionOrderKey = getString(R.string.language_detection_order_key);
        autoLangsKey = getString(R.string.pref_languages_auto_key);
        stickyLangsKey = getString(R.string.pref_languages_sticky_key);
        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        logTextEnabled = preferences.getBoolean(logTextKey, logTextDefaultState);
        ignoreEmoticons = preferences.getBoolean(ignoreEmoticonsKey, ignoreEmoticonsDefaultState);
        languageDetectionOrder.setup(preferences.getString(detectionOrderKey, null));
        textPreprocessor.start();
        super.onCreate();

        refresh();
        instance = this;
        preferences.registerOnSharedPreferenceChangeListener(this);
        registerReceiver(systemEventsHandler, new IntentFilter(Intent.ACTION_LOCALE_CHANGED));

        systemSettingsObserver = new SystemSettingsObserver(app);
        systemSettingsObserver.onChange(false);
        Uri setting = Settings.Global.getUriFor(Settings.Global.ADB_ENABLED);
        getContentResolver().registerContentObserver(setting, true, systemSettingsObserver);

        if (preferences.getBoolean(quickControlWidgetKey, quickControlWidgetDefaultState))
            exposeQuickControlWidget(true);
    }

    @Override
    public void onDestroy() {
        getContentResolver().unregisterContentObserver(systemSettingsObserver);
        unregisterReceiver(systemEventsHandler);
        instance = null;
        preferences.unregisterOnSharedPreferenceChangeListener(this);
        currentEngine = null;
        textPreprocessor.quit();
        try {
            textPreprocessor.join();
        } catch (InterruptedException ex) {
        }
        super.onDestroy();
    }

    @Override
    public synchronized String onGetDefaultVoiceNameFor(String lang, String country, String variant) {
        sync();
        return getVoiceAssignmentFor(lang, country, variant);
    }

    @Override
    public synchronized List<Voice> onGetVoices() {
        sync();
        List<Voice> result = new ArrayList<Voice>();
        for (String v : app.generalVoices) {
            Voice voice = app.voices.get(app.getInternalVoiceName(v));
            if (voice != null) {
                result.add(voice);
            }
        }
        return result;
    }

    @Override
    public synchronized int onIsValidVoiceName(String voiceName) {
        sync();
        return app.languages.contains(voiceName) ? TextToSpeech.SUCCESS : TextToSpeech.ERROR;
    }

    @Override
    public synchronized int onLoadVoice(String voiceName) {
        sync();
        int result = TextToSpeech.ERROR;
        if (voiceName != null) {
            String ls[] = voiceName.split("-");
            if (onLoadLanguage(ls[0], (ls.length > 1) ? ls[1] : null, (ls.length > 2) ? ls[2] : null) >= TextToSpeech.LANG_AVAILABLE)
                result = TextToSpeech.SUCCESS;
        }
        return result;
    }

    @Override
    protected synchronized String[] onGetLanguage() {
        sync();
        String[] result = (currentEngine != null) ? currentEngine.getLanguage() : NO_LANGUAGE;
        if (result == null)
            result = NO_LANGUAGE;
        result[2] = generalName(result[2]);
        return result;
    }

    @Override
    protected synchronized int onIsLanguageAvailable(String lang, String country, String variant) {
        sync();
        return constructResultCodeFor(app.languages.contains(constructVoiceNameFor(lang, country, variant)) ? lang : null, country, variant);
    }

    @Override
    protected synchronized int onLoadLanguage(String lang, String country, String variant) {
        sync();
        int result = (useLocale && !useRequestedVoice) ? onIsLanguageAvailable(lang, country, variant) : setLanguage(lang, country, variant);
        if (result >= TextToSpeech.LANG_AVAILABLE) {
            defaultLanguage = lang;
            defaultCountry = country;
            defaultVariant = variant;
        }
        return result;
    }

    @Override
    protected void onStop() {
        textPreprocessor.cancel();
        soundFormatAdapter.cancel();
    }

    @Override
    protected synchronized void onSynthesizeText(SynthesisRequest request, SynthesisCallback callback) {
        sync();
        String text = Normalizer.normalize(request.getCharSequenceText(), Normalizer.Form.NFKC);
        Bundle params = request.getParams();
        if (useLocale && !useRequestedVoice) {
            Locale locale = Locale.getDefault();
            language = locale.getISO3Language();
            country = locale.getISO3Country();
            variant = locale.getVariant();
        } else {
            language = request.getLanguage();
            country = request.getCountry();
            variant = request.getVariant();
        }
        if (TextUtils.isEmpty(language)) {
            language = defaultLanguage;
            country = defaultCountry;
            variant = defaultVariant;
        } else if (useRequestedVoice && (language.equals(defaultLanguage))) {
            if (TextUtils.isEmpty(country))
                country = defaultCountry;
            else if (country.equals(defaultCountry) && TextUtils.isEmpty(variant))
                variant = defaultVariant;
        }
        if (logTextEnabled)
            Log.d(SmartVoiceApp.LOG_TAG, text);
        if (ignoreEmoticons)
            text = emoticons.matcher(text).replaceAll(" ");

        text = text.trim();
        if (TextUtils.isEmpty(text)) {
            callback.done();
            return;
        }

        int streamType = params.getInt(TextToSpeech.Engine.KEY_PARAM_STREAM, -1);
        float volumeFactor = params.getFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        float pitchFactor = request.getPitch();
        float speechRateFactor = request.getSpeechRate();
        textPreprocessor.put(text, request.getVoiceName());

        int state = soundFormatAdapter.init(callback);
        for (TextElement element = textPreprocessor.get(); element != null; element = textPreprocessor.get()) {
            if (state != TextToSpeech.SUCCESS)
                break;
            String textChunk = element.getText().trim();
            if (textChunk.isEmpty())
                continue;

            String voice = null;
            synchronized (textPreprocessor) {
                voice = getUltimateVoiceFor(element.getVoice());
                if (!TextUtils.isEmpty(voice)) {
                    String[] ls = voice.split("-");
                    voice = ls[2];
                    if (app.voices.containsKey(voice)) {
                        currentEngine = app.voices.get(voice).getEngine();
                        if (currentEngine.loadLanguage(ls[0], ls[1], ls[2]) < TextToSpeech.LANG_AVAILABLE) {
                            voice = getDefaultVoice(ls[0]);
                        }
                    } else {
                        voice = getDefaultVoice(ls[0]);
                    }
                } else {
                    voice = getDefaultVoice(null);
                }
            }

            if (voice == null) {
                state = TextToSpeech.ERROR;
                break;
            }
            voice = generalName(voice);

            Float volume = this.volume.get(voice);
            Float speechRate = this.speechRate.get(voice);
            Float pitch = this.pitch.get(voice);
            if (volume == null)
                volume = defaultVolume;
            if (speechRate == null)
                speechRate = defaultSpeechRate;
            if (pitch == null)
                pitch = defaultPitch;

            state = (streamType < 0) ? TextToSpeech.SUCCESS : currentEngine.setAudioStream(streamType);
            if (state != TextToSpeech.SUCCESS)
                break;
            state = currentEngine.setVolume(Math.round(volume * volumeFactor));
            if (state != TextToSpeech.SUCCESS)
                break;
            state = currentEngine.setSpeechRate(Math.round(speechRateFactor * speechRate / 100F));
            if (state != TextToSpeech.SUCCESS)
                break;
            state = currentEngine.setPitch(Math.round(pitchFactor * pitch / 100F));
            if (state != TextToSpeech.SUCCESS)
                break;
            state = soundFormatAdapter.serve(currentEngine, textChunk);
        }

        soundFormatAdapter.finish(state);
        app.explicitVoice.refresh();
    }


    @Override
    public synchronized void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        sync();
        if (logTextKey.equals(key)) {
            logTextEnabled = preferences.getBoolean(key, logTextDefaultState);
        } else if (singleVoiceMessagesKey.equals(key)) {
            messageSplitSetup();
        }else if (useRequestedVoiceKey.equals(key)) {
            useRequestedVoice = preferences.getBoolean(key, useRequestedVoiceDefaultState);
            setUseLocale(useLocale);
        } else if (useLocaleKey.equals(key)) {
            setUseLocale(preferences.getBoolean(key, useLocaleDefaultState));
        }else if (quickControlWidgetKey.equals(key)) {
            exposeQuickControlWidget(preferences.getBoolean(key, quickControlWidgetDefaultState));
        } else if (fallbackLatinicKey.equals(key)) {
            latinicFallback = validateVoiceFallback(preferences.getString(key, null));
            messageSplitSetup();
        } else if (fallbackCyrillicKey.equals(key)) {
            cyrillicFallback = validateVoiceFallback(preferences.getString(key, null));
            messageSplitSetup();
        } else if (fallbackArabicKey.equals(key)) {
            arabicFallback = validateVoiceFallback(preferences.getString(key, null));
            messageSplitSetup();
        } else if (fallbackCjkKey.equals(key)) {
            cjkFallback = validateVoiceFallback(preferences.getString(key, null));
            messageSplitSetup();
        } else if (numericLangKey.equals(key)) {
            numericVoice = validateVoicePref(preferences.getString(key, null));
            messageSplitSetup();
        } else if (emojiVoiceKey.equals(key)) {
            emojiVoice = validateVoicePref(preferences.getString(key, null));
            messageSplitSetup();
        } else if (detectionOrderKey.equals(key)) {
            languageDetectionOrder.setup(preferences.getString(key, null));
            if (autoLangs.size() > 1)
                Collections.sort(autoLangs, languageDetectionOrder);
        } else if (app.defaultVoices.containsKey(key)) {
            String voice = preferences.getString(key, null);
            if (isFQVN(voice))
                assignedVoices.put(key, voice);
            else assignedVoices.remove(key);
        } else if (autoLangsKey.equals(key)) {
            autoLangs.clear();
            autoLangs.addAll(preferences.getStringSet(key, Collections.<String>emptySet()));
            autoLangs.retainAll(detectableLanguages);
            if (autoLangs.size() > 1)
                Collections.sort(autoLangs, languageDetectionOrder);
            speechMarkupFactory.setLanguages(autoLangs);
            messageSplitSetup();
        } else if (stickyLangsKey.equals(key)) {
            stickyLangs = preferences.getStringSet(key, Collections.<String>emptySet());
            stickyLangs.retainAll(app.languages);
        } else if (ignoreEmoticonsKey.equals(key)) {
            ignoreEmoticons = preferences.getBoolean(key, ignoreEmoticonsDefaultState);
        } else {
            for (String voice : app.generalVoices) {
                String person = app.getInternalVoiceName(voice);
                if (getString(R.string.voice_preference_key_format, person, volumeKey).equals(key)) {
                    volume.put(person, preferences.getFloat(key, defaultVolume));
                    break;
                } else if (getString(R.string.voice_preference_key_format, person, speechRateKey).equals(key)) {
                    speechRate.put(person, preferences.getFloat(key, defaultSpeechRate));
                    break;
                } else if (getString(R.string.voice_preference_key_format, person, pitchKey).equals(key)) {
                    pitch.put(person, preferences.getFloat(key, defaultPitch));
                    break;
                }
            }
        }
    }

}
