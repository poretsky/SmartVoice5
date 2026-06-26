package tts.smartvoice;

import static tts.smartvoice.api.Utils.ANY_COUNTRY;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import tts.smartvoice.api.TtsEngine;
import tts.smartvoice.api.Speaker;

import tts.smartvoice.markup.ExpirableHint;
import tts.smartvoice.markup.LanguageGroup;

import tts.synth.PicoTtsEngine;
import tts.synth.RussianVoiceEngine;
import tts.synth.Robot;

import tts.thirdparty.TtsIntegrator;

public class SmartVoiceApp extends Application {

    public static final String LOG_TAG = "SmartVoice";

    private static final String SVOX_PICO_RESOURCE = "pico";
    private static final String RULEX_DB_FILENAME = "rulex.db";
    private static final String RULEX_REVISION_KEY = "rulex_revision";
    private static final int RULEX_REVISION = 29;

    public final Set<String> languages;
    public final Set<String> validVoices;
    public final Set<String> generalVoices;
    public final Map<String, Speaker> voices;
    public final Map<String, String> defaultVoices;
    public final ExpirableHint<String> explicitVoice;

    private Handler handler;
    private Runnable uiNotifier;
    private SharedPreferences preferences;

    private PicoTtsEngine picoEngine;
    private RussianVoiceEngine syntheticRussian;
    private TtsIntegrator ttsIntegrator;


    public SmartVoiceApp() {
        uiNotifier = null;
        picoEngine = null;
        syntheticRussian = null;
        ttsIntegrator = null;
        languages = new ConcurrentSkipListSet<String>();
        validVoices = new ConcurrentSkipListSet<String>();
        generalVoices = new ConcurrentSkipListSet<String>();
        voices = new ConcurrentHashMap<String, Speaker>();
        defaultVoices = new ConcurrentHashMap<String, String>();
        explicitVoice = new ExpirableHint<String>(60000);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(getMainLooper());
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();
        File rulexDB = new File(getFilesDir(), RULEX_DB_FILENAME);
        if (rulexDB.exists() && (preferences.getInt(RULEX_REVISION_KEY, 0) < RULEX_REVISION))
            rulexDB.delete();
        if (unpackAsset(new File(RULEX_DB_FILENAME))) {
            editor.putInt(RULEX_REVISION_KEY, RULEX_REVISION);
            editor.apply();
        }
        try {
            String[] lingware = getAssets().list(SVOX_PICO_RESOURCE);
            for (String dataFile : lingware)
                unpackAsset(new File(SVOX_PICO_RESOURCE, dataFile));
        } catch (Exception ex) {
            Log.e(LOG_TAG, "Cannot unpack SVOX Pico lingware resources", ex);
        }
        refresh();
        String languageDetectionOrderKey = getString(R.string.language_detection_order_key);
        String currentOrder = preferences.getString(languageDetectionOrderKey, null);
        if (currentOrder != null) {
            LanguageDetectionOrder order = new LanguageDetectionOrder(this, currentOrder);
            String newOrder = order.getStringValue();
            if (newOrder == null) {
                editor.remove(languageDetectionOrderKey);
                editor.apply();
            } else if (!currentOrder.equals(newOrder)) {
                editor.putString(languageDetectionOrderKey, newOrder);
                editor.apply();
            }
        }
        checkPostNotificationsPermission();
    }

    public synchronized void refresh() {
        lockTtsService();
        if (ttsIntegrator == null)
            ttsIntegrator = new TtsIntegrator(this, this::deployEngine);
        else ttsIntegrator.unloadEngines();
        ttsIntegrator.loadEngines();
        if (picoEngine == null)
            try {
                picoEngine = new PicoTtsEngine(getFilesDir().getAbsolutePath());
            } catch (Throwable exception) {
                Log.e(LOG_TAG, String.format(Locale.getDefault(), "SVOX Pico TTS shared library failed to load.\n%s", exception.getMessage()));
                picoEngine = null;
            }
        if (syntheticRussian == null)
            try {
                syntheticRussian = new RussianVoiceEngine(this, new File(getFilesDir(), RULEX_DB_FILENAME));
            } catch (Throwable exception) {
                Log.e(LOG_TAG, String.format(Locale.getDefault(), "RU_TTS shared library failed to load.\n%s", exception.getMessage()));
                syntheticRussian = null;
            }
        languages.clear();
        voices.clear();
        defaultVoices.clear();
        generalVoices.clear();
        validVoices.clear();
        if (picoEngine != null)
            registerVoices(picoEngine);
        if (syntheticRussian != null)
            registerVoices(syntheticRussian);
        explicitVoice.clear();
        unlockTtsService();
        scheduleUiNotification();
    }

    public synchronized void setUiNotifier(Runnable action) {
        if (uiNotifier != null)
            handler.removeCallbacks(uiNotifier);
        uiNotifier = action;
    }

    public boolean isDebugEnabled() {
        return Settings.Global.getInt(getContentResolver(), Settings.Global.ADB_ENABLED, 0) != 0;
    }

    public String getInternalVoiceName(String voice) {
        String voiceName = voice.substring(8);
        if ((!RussianVoiceEngine.VOICE_NAME.equals(voice)) && Robot.GENERAL_NAME.equals(voiceName) && defaultVoices.containsKey(voice))
            voiceName = defaultVoices.get(voice).substring(8);
        return voiceName;
    }

    public TtsIntegrator getTtsIntegrator() {
        return ttsIntegrator;
    }


    TtsEngine getFallbackEngine(String lang) {
        return ((lang != null) && (LanguageGroup.find(lang) == LanguageGroup.CYRILLIC)) ?
            syntheticRussian :
            picoEngine;
    }


    private void registerVoices(TtsEngine engine) {
        for (String v : engine.getAvailableVoices()) {
            String voice = v;
            int voiceNameLength = v.indexOf('\'');
            if (voiceNameLength > 0) {
                voices.put(v.substring(8), engine.getSpeaker(v));
                validVoices.add(v);
                voice = v.substring(0, voiceNameLength);
            }
            String generalLanguage = voice.substring(0, 3);
            String countryLanguage = voice.substring(0, 7);
            String country = voice.substring(4, 7);
            String voiceName = voice.substring(8);
            languages.add(voice);
            languages.add(generalLanguage);
            generalVoices.add(voice);
            if (!(engine instanceof PicoTtsEngine))
                voices.put(voiceName, engine.getSpeaker(voice));
            registerDefaultVoice(generalLanguage, voice, engine);
            if (!ANY_COUNTRY.equals(country)) {
                languages.add(countryLanguage);
                registerDefaultVoice(countryLanguage, voice, engine);
            }
            String engineVoice = engine.getVoice(generalLanguage, country, voiceName);
            if (engineVoice != null) {
                if (engineVoice.equals(voice)) {
                    validVoices.add(voice);
                } else {
                    if (engine instanceof PicoTtsEngine) {
                        validVoices.add(engineVoice);
                        voices.put(engineVoice.substring(8), engine.getSpeaker(voice));
                    }
                    registerDefaultVoice(voice, engineVoice, engine);
                }
            }
        }
    }

    private void registerDefaultVoice(String name, String voice, TtsEngine engine) {
        if (!defaultVoices.containsKey(name))
            defaultVoices.put(name, voice);
    }

    @SuppressLint("InlinedApi")
    private void checkPostNotificationsPermission() {
        String optionKey = getString(R.string.quick_control_widget_option_key);
        if (preferences.getBoolean(optionKey, false) &&
            (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)) {
            SharedPreferences.Editor editor = preferences.edit();
            editor.putBoolean(optionKey, false);
            editor.apply();
        }
    }

    private boolean unpackAsset(File dataFile) {
        File destFile = new File(getFilesDir(), dataFile.getName());
        if (!destFile.exists()) {
            try {
                FileOutputStream dest = openFileOutput(dataFile.getName(), MODE_PRIVATE);
                InputStream src = getAssets().open(dataFile.getPath());
                byte[] buffer = new byte[1024];
                int read;
                while ((read = src.read(buffer)) > 0)
                    dest.write(buffer, 0, read);
                src.close();
                dest.close();
            } catch (Exception ex) {
                Log.w(LOG_TAG, String.format(Locale.getDefault(), "Unpacking asset file %s failed", dataFile.getPath()), ex);
                return false;
            }
        }
        return true;
    }

    private synchronized void deployEngine(TtsEngine engine) {
        handler.post(new EngineRegistrator(engine));
        scheduleUiNotification();
    }

    private void scheduleUiNotification() {
        if (uiNotifier != null) {
            handler.removeCallbacks(uiNotifier);
            handler.postDelayed(uiNotifier, 100);
        }
    }

    private synchronized void lockTtsService() {
        TtsService service = TtsService.getInstance();
        if (service != null)
            service.lock();
    }

    private synchronized void unlockTtsService() {
        TtsService service = TtsService.getInstance();
        if (service != null) {
            service.refresh();
            service.unlock();
        }
    }


    private class EngineRegistrator implements Runnable {

        private final TtsEngine engine;

        EngineRegistrator(TtsEngine engine) {
            this.engine = engine;
        }

        @Override
        public void run() {
            lockTtsService();
            registerVoices(engine);
            unlockTtsService();
        }

    }

}
