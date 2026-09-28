package tts.smartvoice.markup;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;

import tts.smartvoice.R;

public class SpeechLayout {

    private final SpeechMarkupFactory factory;
    private final String text;
    private final int limit;

    private VoiceResolver voiceResolver;
    private List<TextElement> content;
    private Matcher breakPoint;
    private int startPosition;


    SpeechLayout(SpeechMarkupFactory factory, VoiceResolver voiceResolver, String text, int limit) {
        this.factory = factory;
        this.voiceResolver = voiceResolver;
        this.text = text;
        this.limit = limit;
        content = null;
        breakPoint = null;
        startPosition = 0;
    }


    public boolean pullText() throws InterruptedException {
        content = null;
        int textLength = text.length() - startPosition;
        if (textLength > limit) {
            int prevBreakStart = -1;
            int prevBreakEnd = -1;
            getContent();
            if (breakPoint != null) {
                prevBreakStart = breakPoint.start();
                prevBreakEnd = breakPoint.end();
            } else {
                breakPoint = factory.getSeparator().matcher(text);
            }
            while (breakPoint.find()) {
                if (Thread.interrupted())
                    throw new InterruptedException();
                if ((breakPoint.start() - startPosition) <= limit) {
                    if ((text.length() - breakPoint.end()) <= limit) {
                        content.add(new TextElement(text.substring(startPosition, breakPoint.start())));
                        startPosition = breakPoint.end();
                        return true;
                    }
                    prevBreakStart = breakPoint.start();
                    prevBreakEnd = breakPoint.end();
                } else if (prevBreakStart > startPosition) {
                    content.add(new TextElement(text.substring(startPosition, prevBreakStart)));
                    startPosition = prevBreakEnd;
                    return true;
                } else {
                    content.add(new TextElement(text.substring(startPosition, breakPoint.start())));
                    startPosition = breakPoint.end();
                    return true;
                }
            }
            if (prevBreakStart > startPosition) {
                content.add(new TextElement(text.substring(startPosition, prevBreakStart)));
                breakPoint.find(prevBreakStart);
                startPosition = prevBreakEnd;
            } else {
                content.add(new TextElement(text.substring(startPosition)));
                startPosition = text.length();
            }
        } else if (textLength > 0) {
            getContent().add(new TextElement((startPosition > 0) ? text.substring(startPosition) : text));
            startPosition = text.length();
        }
        return (content != null) && !content.isEmpty();
    }

    public void setVoice(String voice) throws InterruptedException {
        for (TextElement element : content) {
            if (Thread.interrupted())
                throw new InterruptedException();
            element.setVoice(voice);
        }
        VoiceResolver saved = voiceResolver;
        voiceResolver = null;
        try {
            verbalizeEmoji();
            normalize();
        } catch (InterruptedException ex) {
            voiceResolver = saved;
            throw ex;
        }
        voiceResolver = saved;
    }

    public void chooseLanguage(List<String> languages) throws InterruptedException {
        for (TextElement element : content) {
            if (Thread.interrupted())
                throw new InterruptedException();
            if ((voiceResolver != null) && element.setVoice(voiceResolver.getVoicePreference(R.string.numeric_language_key), factory.getNumberDetector()))
                continue;
            String text = element.getText();
            String assumption = null;
            for (String language : languages) {
                if (Thread.interrupted())
                    throw new InterruptedException();
                LanguageDetector languageDetector = factory.getLanguageDetector(language);
                if (languageDetector != null) {
                    if (languageDetector.detect(text)) {
                        if (languageDetector.isUltimateResult()) {
                            element.setVoice(language);
                            assumption = null;
                            break;
                        } else if ((assumption == null) && !languageDetector.refute(text)) {
                            assumption = language;
                        }
                    }
                }
            }
            if (assumption != null)
                element.setVoice(assumption);
            if (element.getVoice() != null)
                continue;
            for (LanguageGroup group : LanguageGroup.values()) {
                if (Thread.interrupted())
                    throw new InterruptedException();
                String fallback = getFallbackFor(group, languages);
                if ((fallback != null) && element.setVoice(fallback, group.getDetector()))
                    break;
            }
        }

        normalize();
        validate(languages);
        verbalizeEmoji();
        normalize();
    }

    public void markup(List<String> languages) throws InterruptedException {
        boolean[] guessed = new boolean[LanguageGroup.values().length];
        Arrays.fill(guessed, false);
        for (String language : languages) {
            if (Thread.interrupted())
                throw new InterruptedException();
            LanguageDetector languageDetector = factory.getLanguageDetector(language);
            if ((languageDetector != null) && (languageDetector.group != LanguageGroup.NONE)) {
                int groupIndex = languageDetector.group.ordinal();
                if (!guessed[groupIndex]) {
                    for (TextElement element : content) {
                        if (Thread.interrupted())
                            throw new InterruptedException();
                        if (languageDetector.detect(element.getText()) && languageDetector.isUltimateResult()) {
                            guessed[groupIndex] = true;
                            break;
                        }
                    }
                }
            }
        }

        boolean done = (voiceResolver != null) &&
            markup(factory.getNumberDetector(), voiceResolver.getVoicePreference(R.string.numeric_language_key));

        for (String language : languages) {
            if (Thread.interrupted())
                throw new InterruptedException();
            if (done)
                break;
            done = markup(factory.getLanguageDetector(language), language);
        }

        for (LanguageGroup group : LanguageGroup.values()) {
            if (Thread.interrupted())
                throw new InterruptedException();
            if (done)
                break;
            String fallback = getFallbackFor(group, languages);
            if (fallback != null)
                done = markup(group.getDetector(), fallback);
        }

        normalize();
        validate(languages);
        verbalizeEmoji();
        normalize();
    }

    public List<TextElement> getContent() {
        if (content == null)
            content = new ArrayList<TextElement>();
        return content;
    }

    public void done() {
        factory.assumptions.refresh();
    }

    public void recycle() {
        content = null;
        if (breakPoint != null)
            breakPoint.reset(text);
        startPosition = 0;
    }


    private String getFallbackFor(LanguageGroup group, List<String> languages) {
        if (group == LanguageGroup.NONE)
            return null;
        String fallback = (voiceResolver != null) ? voiceResolver.getVoicePreference(group.key) : null;
        if (fallback == null)
            for (String lang : languages)
                if (group.contains(lang)) {
                    fallback = lang;
                    break;
                }
        return fallback;
    }

    private boolean markup(LanguageDetector languageDetector, String voice) throws InterruptedException {
        if ((languageDetector != null) && (voice != null)) {
            List<TextElement> newContent = new ArrayList<TextElement>();
            int hits = 0;
            for (TextElement element : content) {
                if (Thread.interrupted())
                    throw new InterruptedException();
                if (element.getVoice() != null) {
                    newContent.add(element);
                    hits++;
                } else {
                    newContent.addAll(languageDetector.markup(element.getText(), voice));
                    hits += languageDetector.getHitCount();
                }
            }
            content = newContent;
            return content.size() == hits;
        }
        return false;
    }

    private void verbalizeEmoji() throws InterruptedException {
        List<TextElement> verbalized = new ArrayList<TextElement>();
        String emojiVoice = (voiceResolver != null) ? voiceResolver.getVoicePreference(R.string.pref_emoji_voice_key) : null;
        for (TextElement element : content) {
            if (Thread.interrupted())
                throw new InterruptedException();
            String voice = (emojiVoice != null) ? emojiVoice : element.getVoice();
            EmojiTranslator emojiTranslator = factory.getEmojiTranslator(voice);
            if (emojiTranslator != null) {
                final String text = element.getText();
                final int textLength = text.length();
                int position = 0;
                for (int i = 0; i < textLength; i++) {
                    if (Thread.interrupted())
                        throw new InterruptedException();
                    for (int l : emojiTranslator.getEmojiLengths()) {
                        final int k = i + l;
                        if (k <= textLength) {
                            String emojiText = emojiTranslator.translate(text.substring(i, k));
                            if (emojiText != null) {
                                if (i > position)
                                    verbalized.add(new TextElement(text.substring(position, i), element.getVoice()));
                                verbalized.add(new TextElement(String.format(Locale.getDefault(), " %s ", emojiText), voice));
                                position = k;
                                i = k - 1;
                                break;
                            }
                        }
                    }
                }
                if (textLength > position)
                    verbalized.add(new TextElement(text.substring(position), element.getVoice()));
            } else {
                verbalized.add(element);
            }
            factory.remember(element.getVoice());
        }
        content = verbalized;
    }

    private void normalize() throws InterruptedException {
        Iterator<TextElement> iterator = content.iterator();
        TextElement accumulator = null;
        while (iterator.hasNext()) {
            if (Thread.interrupted())
                throw new InterruptedException();
            TextElement element = iterator.next();
            if (voiceResolver != null)
                element.setVoice(voiceResolver.resolveVoice(element.getVoice()));
            if (accumulator != null) {
                if (accumulator.add(element)) {
                    iterator.remove();
                } else {
                    accumulator = element;
                }
            } else {
                accumulator = element;
            }
        }
    }

    private void validate(List<String> languages) throws InterruptedException {
        for (TextElement element : content) {
            if (Thread.interrupted())
                throw new InterruptedException();
            String voice = element.getVoice();
            if (voice != null) {
                LanguageDetector languageDetector = factory.getLanguageDetector(voice.substring(0, 3));
                String text = element.getText();
                if ((languageDetector != null) && (languageDetector.group != LanguageGroup.NONE) && (text != null) && !languageDetector.isValid(text)) {
                    for (String language : languages) {
                        if (Thread.interrupted())
                            throw new InterruptedException();
                        languageDetector = factory.getLanguageDetector(language);
                        if ((languageDetector != null) && (languageDetector.group != LanguageGroup.NONE) && languageDetector.isValid(text)) {
                            element.setVoice(language);
                            factory.assumptions.set(languageDetector.group, languageDetector);
                            break;
                        }
                    }
                }
            }
        }
    }

}
