package tts.thirdparty;

import static tts.smartvoice.api.Utils.*;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import android.content.Context;
import android.content.pm.ServiceInfo;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;
import android.text.TextUtils;

import tts.smartvoice.R;
import tts.smartvoice.api.SoundFormatAdapter;
import tts.smartvoice.api.Speaker;
import tts.smartvoice.api.TtsEngine;

class AndroidTtsEngine extends UtteranceProgressListener implements TtsEngine {

    private final Context context;
    private final ServiceInfo serviceInfo;
    private final Map<String, Voice> voices;
    private final TextToSpeech tts;

    private int utteranceIndex;
    private SoundFormatAdapter speechCallback;

    private volatile int volume;
    private volatile int audioStream;

    private volatile boolean isSpeaking;
    private volatile String currentUtteranceId;


    AndroidTtsEngine(TtsIntegrator integrator, ServiceInfo service) {
        context = integrator.getContext();
        serviceInfo = service;
        voices = new HashMap<String, Voice>();
        tts = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS)
                    bootstrap();
                integrator.acceptEngineStartResult(AndroidTtsEngine.this, status);
            }, service.packageName);
        utteranceIndex = 0;
        volume = context.getResources().getInteger(R.integer.volume_value);
        audioStream = AudioManager.STREAM_MUSIC;
        speechCallback = null;
        isSpeaking = false;
        currentUtteranceId = null;
    }

    String getName() {
        return serviceInfo.packageName;
    }

    String getLabel() {
        CharSequence label = serviceInfo.loadLabel(context.getPackageManager());
        return TextUtils.isEmpty(label) ? getName() : label.toString();
    }


    @Override
    public void onAudioAvailable(String utteranceId, byte[] audio) {
        if (!utteranceId.equals(currentUtteranceId))
            return;
        if (speechCallback != null) {
            for (int startOffset = 0; isSpeaking && (startOffset < audio.length); startOffset += speechCallback.getMaxBufferSize())
                if (speechCallback.audioAvailable(audio, startOffset, Math.min(speechCallback.getMaxBufferSize(), audio.length - startOffset)) != TextToSpeech.SUCCESS)
                    cancel();
        } else cancel();
    }

    @Override
    public void onBeginSynthesis(String utteranceId, int sampleRate, int audioFormat, int channelCount) {
        if (!utteranceId.equals(currentUtteranceId))
            return;
        if ((speechCallback == null) ||
            (speechCallback.start(sampleRate, audioFormat, channelCount) != TextToSpeech.SUCCESS))
            cancel();
    }

    @Override
    public void onDone(String utteranceId) {
        if (!utteranceId.equals(currentUtteranceId))
            return;
        cancel();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onError(String utteranceId) {
        onDone(utteranceId);
    }

    @Override
    public void onError(String utteranceId, int errorCode) {
        onDone(utteranceId);
    }

    @Override
    public void onStart(String utteranceId) {
    }

    @Override
    public void onStop(String utteranceId, boolean interrupted) {
        onDone(utteranceId);
    }


    @Override
    public List<String> getAvailableVoices() {
        List<String> voices = new ArrayList<String>();
        for (String voice : this.voices.keySet())
            if (voice.indexOf('\'') > 0)
                voices.add(voice);
        return voices;
    }

    @Override
    public String[] getLanguage() {
        Voice voice = tts.getVoice();
        return (voice != null) ?
            getVoiceLanguage(voice) :
            null;
    }

    @Override
    public String getVoice(String lang, String country, String variant) {
        String voiceName = constructVoiceNameFor(lang, country, variant);
        if ((voiceName != null) && voices.containsKey(voiceName)) {
            String[] voiceLang = getVoiceLanguage(voices.get(voiceName));
            return String.format(Locale.ROOT, "%s-%s-%s", voiceLang[0], TextUtils.isEmpty(voiceLang[1]) ? ANY_COUNTRY : voiceLang[1], voiceLang[2]);
        } else if (ANY_COUNTRY.equals(country)) {
            return null;
        }
        return getVoice(lang, ANY_COUNTRY, variant);
    }

    @Override
    public Speaker getSpeaker(String langVoiceSpec) {
        return new AndroidVoice(langVoiceSpec, this, voices.get(langVoiceSpec));
    }

    @Override
    public int loadLanguage(String lang, String country, String variant) {
        int rc = evaluateVoice(lang, country, variant);
        String voice = constructVoiceNameFor(lang, country, variant, rc);
        if ((voice == null) || !voices.containsKey(voice) || (tts.setVoice(voices.get(voice)) != TextToSpeech.SUCCESS))
            rc = ANY_COUNTRY.equals(country) ?
                TextToSpeech.LANG_NOT_SUPPORTED :
                loadLanguage(lang, ANY_COUNTRY, variant);
        return rc;
    }

    @Override
    public int setAudioStream(int value) {
        if (value < 0)
            return TextToSpeech.ERROR;
        audioStream = value;
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int setVolume(int value) {
        volume = value;
        return TextToSpeech.SUCCESS;
    }

    @Override
    public int setSpeechRate(int value) {
        return tts.setSpeechRate((float) value / 100.0f);
    }

    @Override
    public int setPitch(int value) {
        return tts.setPitch((float) value / 100.0f);
    }

    @Override
    public int synthesizeText(String text, SoundFormatAdapter callback) {
        callback.setGain((float) volume / 80.0f);
        speechCallback = callback;
        Bundle params = new Bundle();
        params.putInt(TextToSpeech.Engine.KEY_PARAM_STREAM, audioStream);
        params.putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f);
        File devNull = new File("/dev/null");
        currentUtteranceId = String.format(Locale.ROOT, "SV-%d", utteranceIndex++);
        isSpeaking = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ParcelFileDescriptor pfd = null;
            try {
                pfd = context.getContentResolver().openFile(Uri.fromFile(devNull), "w", null);
            } catch (Exception ex) {
                cancel();
                return TextToSpeech.ERROR;
            }
            if ((pfd == null) ||
                (tts.synthesizeToFile(text, params, pfd, currentUtteranceId) != TextToSpeech.SUCCESS)) {
                cancel();
                return TextToSpeech.ERROR;
            }
        } else if (tts.synthesizeToFile(text, params, devNull, currentUtteranceId) != TextToSpeech.SUCCESS) {
            cancel();
            return TextToSpeech.ERROR;
        }
        synchronized (this) {
            while (isSpeaking)
                try {
                    wait();
                } catch (InterruptedException ex) {
                }
        }
        return callback.done();
    }

    @Override
    public void stop() {
        cancel();
        tts.stop();
    }

    @Override
    public void destroy() {
        try {
            tts.shutdown();
        } catch (Exception ex) {
        }
    }


    private void bootstrap() {
        for (Voice voice : tts.getVoices()) {
            String[] voiceLang = getVoiceLanguage(voice);
            registerVoice(voiceLang[0], voice);
            if (TextUtils.isEmpty(voiceLang[1]))
                voiceLang[1] = ANY_COUNTRY;
            else registerVoice(String.format(Locale.ROOT, "%s-%s", voiceLang[0], voiceLang[1]), voice);
            registerVoice(String.format(Locale.ROOT, "%s-%s-%s", voiceLang[0], voiceLang[1], voiceLang[2].substring(0, voiceLang[2].indexOf('\''))), voice);
            voices.put(String.format(Locale.ROOT, "%s-%s-%s", voiceLang[0], voiceLang[1], voiceLang[2]), voice);
        }
        tts.setOnUtteranceProgressListener(this);
        setPitch(context.getResources().getInteger(R.integer.pitch_value));
        setSpeechRate(context.getResources().getInteger(R.integer.rate_value));
    }

    private String[] getVoiceLanguage(Voice voice) {
        Locale locale = voice.getLocale();
        String language;
        try {
            language = locale.getISO3Language();
        } catch (Exception ex) {
            language = "";
        }
        String country;
        try {
            country = locale.getISO3Country();
        } catch (Exception ex) {
            country = "";
        }
        String voiceName = getName() + "|" + language;
        if (!TextUtils.isEmpty(country))
            voiceName += "." + country;
        voiceName += "\'" + voice.getName().replace('-', '/');
        String[] result = { language, country, voiceName };
        return result;
    }

    private void registerVoice(String key, Voice voice) {
        Voice v = voices.get(key);
        if ((v == null) || (v.isNetworkConnectionRequired() && !voice.isNetworkConnectionRequired()))
            voices.put(key, voice);
        else if (v.isNetworkConnectionRequired() && voice.isNetworkConnectionRequired() && (voice.getLatency() < v.getLatency()))
            voices.put(key, voice);
        else if (!v.isNetworkConnectionRequired() && !voice.isNetworkConnectionRequired() && (voice.getQuality() > v.getQuality()))
            voices.put(key, voice);
    }

    private synchronized void cancel() {
        isSpeaking = false;
        notifyAll();
    }

}
