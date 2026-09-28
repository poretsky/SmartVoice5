package tts.smartvoice.ui;

import java.util.SortedSet;
import java.util.TreeSet;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceGroup;

import tts.smartvoice.R;
import tts.smartvoice.SmartVoiceApp;
import tts.synth.RussianVoiceEngine;

public class VoiceSettingsFragment extends MenuProviderPreferenceFragment {

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.voice_preferences);
        String voice = getArguments().getString(getString(R.string.voice_key));
        if (voice != null) {
            SmartVoiceApp app = (SmartVoiceApp)getActivity().getApplication();
            PreferenceGroup preferences = (PreferenceGroup)findPreference(getString(R.string.voice_prefs_key));
            int nPrefs = preferences.getPreferenceCount();
            for (int i = 0; i < nPrefs; i++) {
                Slider preference = (Slider) preferences.getPreference(i);
                String newKey = getString(R.string.voice_preference_key_format, app.getInternalVoiceName(voice), preference.getKey());
                preference.setKey(newKey);
                preference.setValue(preference.getPersistedInt(preference.getValue()));
            }

            if (RussianVoiceEngine.VOICE_NAME.equals(voice))
                addPreferencesFromResource(R.xml.robot_voice_preferences);

            SortedSet<String> models = new TreeSet<String>();
            for (String model : app.validVoices)
                if ((model.length() > voice.length()) && model.startsWith(voice) && (model.charAt(voice.length()) == '\''))
                    models.add(model);
            if (models.size() > 0) {
                ListPreference pref = new ListPreference(preferences.getContext());
                pref.setDialogTitle(R.string.pref_choose_voice_model);
                pref.setDefaultValue(app.defaultVoices.get(voice));
                String entryValues[] = models.toArray(new String[0]);
                String entries[] = new String[entryValues.length];
                for (int i = 0; i < entries.length; i++) {
                    int modelNameStart = entryValues[i].indexOf('\'') + 1;
                    String modelName = (modelNameStart > 0) ? entryValues[i].substring(modelNameStart).replace('/', '-') : "";
                    if (modelName.isEmpty())
                        modelName = String.valueOf(i + 1);
                    else if (RussianVoiceEngine.VOICE_NAME.equals(voice))
                        modelName = HumanName.get(modelName, app);
                    entries[i] = modelName;
                }
                pref.setEntries(entries);
                pref.setEntryValues(entryValues);
                pref.setKey(voice);
                pref.setPersistent(true);
                pref.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
                pref.setTitle(R.string.pref_voice_model_title);
                pref.setEnabled(entries.length > 1);
                preferences.addPreference(pref);
            }
        }
    }

    @Override
    public void onPrepareMenu(Menu menu) {
        boolean showRefreshButton = ((TtsPreferenceActivity) getActivity()).hasHeaders();
        MenuItem refreshButton = menu.findItem(R.id.action_refresh);
        refreshButton.setEnabled(showRefreshButton);
        refreshButton.setVisible(showRefreshButton);
    }

}
