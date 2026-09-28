package tts.smartvoice;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;

import tts.smartvoice.api.Utils;
import tts.smartvoice.markup.LanguageGroup;
import tts.smartvoice.markup.SpeechLayout;
import tts.smartvoice.markup.TextElement;
import tts.smartvoice.markup.VoiceResolver;

class TextPreprocessor extends HandlerThread implements VoiceResolver, Handler.Callback {

    private static final int TEXT_SIZE_LIMIT = 200;

    private final TtsService service;
    private final ConcurrentLinkedQueue<TextElement> queue;
    private final AtomicReference<SpeechLayout> speechLayout;

    private String language;
    private String country;
    private String variant;
    private String voiceName;

    private volatile boolean processing;
    private Handler handler;


    TextPreprocessor(TtsService service) {
        super("TextPreprocessor");
        this.service = service;
        queue = new ConcurrentLinkedQueue<TextElement>();
        speechLayout = new AtomicReference<SpeechLayout>();
        language = null;
        country = null;
        variant = null;
        processing = false;
    }

    public synchronized void put(String text, String requestedVoice) {
        voiceName = requestedVoice;
        speechLayout.set(service.speechMarkupFactory.getSpeechLayout(this, text, TEXT_SIZE_LIMIT));
        processing = true;
        handler.sendEmptyMessage(0);
    }

    public TextElement get() {
        sync();
        TextElement result = queue.poll();
        if (result == null)
            complete();
        return result;
    }

    public synchronized void cancel() {
        handler.removeMessages(0);
        queue.clear();
        complete();
        if (processing) {
            interrupt();
            conclude();
        }
    }

    public synchronized void remember(String language, String country, String variant) {
        this.language = language;
        this.country = country;
        this.variant = variant;
    }


    @Override
    protected void onLooperPrepared() {
        handler = new Handler(Looper.myLooper(), this);
    }


    @Override
    public synchronized String resolveVoice(String lang) {
        String voice = null;
        if (lang != null) {
            voice = service.app.explicitVoice.get(lang);
            if (voice == null)
                voice = service.getVoiceAssignmentFor(lang);
        }
        if (voice != null) {
            String[] ls = voice.split("-");
            remember(ls[0], ls[1], ls[2]);
        } else {
            if ((language == null) || !service.stickyLangs.contains(language))
                remember(service.language, service.country, service.variant);
            voice = service.getVoiceAssignmentFor(language, country, variant);
        }
        return voice;
    }

    @Override
    public synchronized String getVoicePreference(int key) {
        String voice;
        if (key == R.array.latinic_languages) {
            voice = service.app.explicitVoice.get(LanguageGroup.LATINIC);
            return (voice != null) ? voice : service.latinicFallback;
        } else if (key == R.array.cyrillic_languages) {
            voice = service.app.explicitVoice.get(LanguageGroup.CYRILLIC);
            return (voice != null) ? voice : service.cyrillicFallback;
        } else if (key == R.array.cjk_languages) {
            voice = service.app.explicitVoice.get(LanguageGroup.CJK);
            return (voice != null) ? voice : service.cjkFallback;
        } else if (key == R.string.numeric_language_key) {
            return ((service.numericVoice != null) && service.numericVoice.equals(service.systemValue)) ?
                service.getSystemVoiceName() :
                service.numericVoice;
        } else if (key == R.string.pref_emoji_voice_key) {
            return service.singleVoiceMessages ?
                null :
                (((service.emojiVoice != null) && service.emojiVoice.equals(service.systemValue)) ?
                 service.getSystemVoiceName() :
                 service.emojiVoice);
        }
        return null;
    }


    @Override
    public boolean handleMessage(Message msg) {
        SpeechLayout speechLayout = this.speechLayout.get();
        if (speechLayout != null) {
            try {
                String explicitVoice = service.app.explicitVoice.check() ? service.app.explicitVoice.get() : null;
                if (explicitVoice != null) {
                    while (speechLayout.pullText()) {
                        speechLayout.setVoice(explicitVoice);
                        publish(speechLayout.getContent());
                    }
                } else if (service.useRequestedVoice) {
                    String requestedVoice = TextUtils.isEmpty(voiceName) ?
                        service.getSystemVoiceName() :
                        voiceName;
                    while (speechLayout.pullText()) {
                        speechLayout.setVoice(requestedVoice);
                        publish(speechLayout.getContent());
                    }
                } else if (service.singleVoiceMessages) {
                    while (speechLayout.pullText()) {
                        speechLayout.chooseLanguage(service.autoLangs);
                        publish(speechLayout.getContent());
                    }
                } else {
                    while (speechLayout.pullText()) {
                        speechLayout.markup(service.autoLangs);
                        publish(speechLayout.getContent());
                    }
                }
            } catch (InterruptedException ex) {
            }
        }
        if (!handler.hasMessages(0))
            conclude();
        return true;
    }


    private synchronized void conclude() {
        if (Utils.getThreadId(this) == Utils.getThreadId(currentThread()))
            interrupted();
        processing = false;
        notify();
    }

    private synchronized void publish(List<TextElement> content) {
        if ((speechLayout.get() != null) && (content != null) && !content.isEmpty() && !handler.hasMessages(0)) {
            queue.addAll(content);
            notify();
        }
    }

    private void complete() {
        SpeechLayout speechLayout = this.speechLayout.getAndSet(null);
        if (speechLayout != null)
            speechLayout.done();
    }

    private synchronized void sync() {
        while (processing && queue.isEmpty())
            try {
                wait();
            } catch (InterruptedException ex) {
            }
    }

}
