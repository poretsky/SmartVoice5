package tts.smartvoice.markup;

public interface VoiceResolver {

    public String resolveVoice(String lang);

    public String getVoicePreference(int key);

}
