package tts.smartvoice.markup;

import android.text.TextUtils;

public class TextElement {

    private String text;
    private String voice;

    TextElement(String text, String voice) {
        this.text = text;
        this.voice = voice;
    }

    TextElement(String text) {
        this(text, null);
    }

    void setVoice(String voice) {
        this.voice = voice;
    }

    boolean setVoice(String voice, LanguageDetector languageDetector) {
        boolean detected = (languageDetector != null) &&
            (voice != null) &&
            languageDetector.detect(text) &&
            !languageDetector.refute(text);
        if (detected)
            this.voice = voice;
        return detected;
    }

    boolean add(TextElement other) {
        if (TextUtils.equals(voice, other.voice)) {
            if ((text != null) && (other.text != null)) {
                text += other.text;
            } else if (other.text != null) {
                text = other.text;
            }
            return true;
        }
        return false;
    }


    public String getText() {
        return text;
    }

    public String getVoice() {
        return voice;
    }

}
