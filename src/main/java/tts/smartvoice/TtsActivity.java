package tts.smartvoice;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

import androidx.preference.PreferenceManager;

public class TtsActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        PreferenceManager.setDefaultValues(this, R.xml.general_preferences, false);
        if (TextToSpeech.Engine.ACTION_CHECK_TTS_DATA.equals(getIntent().getAction())) {
            Intent intent = new Intent();
            ArrayList<String> voices = new ArrayList<String>();
            for (String lang : ((SmartVoiceApp) getApplication()).languages)
                if (lang.length() < 8)
                    voices.add(lang);
            intent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES, voices);
            intent.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_UNAVAILABLE_VOICES, new ArrayList<String>());
            setResult(voices.isEmpty() ? TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL : TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, intent);
        } else if (TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT.equals(getIntent().getAction())) {
            Intent intent = new Intent();
            intent.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, "1, 2, 3, 4, 5");
            setResult(TextToSpeech.SUCCESS, intent);
        }
        finish();
    }

}
