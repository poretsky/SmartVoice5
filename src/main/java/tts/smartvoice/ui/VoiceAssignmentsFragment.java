package tts.smartvoice.ui;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import android.content.Context;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.preference.ListPreference;
import androidx.preference.PreferenceCategory;

import tts.smartvoice.R;
import tts.smartvoice.SmartVoiceApp;

public class VoiceAssignmentsFragment extends MenuProviderPreferenceFragment {

    private SmartVoiceApp app;

    private void addVoice(Map<String, SortedSet<String>> voiceMap, String voice, int keyLen) {
        String lang = voice.substring(0, keyLen);
        addVoice(voiceMap, voice, lang);
        if (keyLen == 3) {
            List<String> arabics = Arrays.asList(getResources().getStringArray(R.array.arabic_languages));
            if (arabics.contains(lang))
                addVoice(voiceMap, voice, arabics.get(0));
        }
    }

    private void addVoice(Map<String, SortedSet<String>> voiceMap, String voice, String lang) {
        SortedSet<String> voices = voiceMap.get(lang);
        if (voices == null) {
            voices = new TreeSet<String>();
            voiceMap.put(lang, voices);
        }
        voices.add(voice);
    }

    private void setupExtraPreference(int keyId, int langListId) {
        Set<String> langs = new TreeSet<String>();
        for (String lang : getResources().getStringArray(langListId))
            for (String voice : app.generalVoices)
                if (voice.startsWith(lang))
                    langs.add(voice);
        ListPreference preference = (ListPreference)findPreference(getString(keyId));
        if (langs.isEmpty()) {
            preference.setSummary("");
            preference.setEnabled(false);
        } else {
            String values[] = langs.toArray(new String[langs.size() + 1]);
            String entries[] = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                if (values[i] != null) {
                    entries[i] = HumanName.get(values[i].split("-"), app);
                } else {
                    entries[i] = getString(R.string.no_preferences);
                    values[i] = "";
                }
            }
            preference.setEntries(entries);
            preference.setEntryValues(values);
            preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            preference.setEnabled(true);
        }
    }

    private void setupExtraPreference(int keyId, Set<String> voices) {
        ListPreference preference = (ListPreference)findPreference(getString(keyId));
        String values[] = voices.toArray(new String[voices.size() + 2]);
        String entries[] = new String[values.length];
        for (int i = 0; i < voices.size(); i++) {
            String[] ls = values[i].split("-");
            entries[i] = HumanName.get(values[i].split("-"), app);
        }
        entries[voices.size()] = getString(R.string.system_choice);
        values[voices.size()] = getString(R.string.value_system);
        entries[voices.size() + 1] = getString(R.string.no_preferences);
        values[voices.size() + 1] = "";
        preference.setEntries(entries);
        preference.setEntryValues(values);
        preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        app = (SmartVoiceApp)getActivity().getApplication();
        addPreferencesFromResource(R.xml.voice_assignment_preferences);
        PreferenceCategory languagePreferences = (PreferenceCategory)findPreference(getString(R.string.pref_voices_key));
        Context context = languagePreferences.getContext();
        Map<String, SortedSet<String>> voiceMap = new HashMap<String, SortedSet<String>>();
        for (String voice : app.generalVoices) {
            addVoice(voiceMap, voice, 3);
            addVoice(voiceMap, voice, 7);
        }
        Set<String> langs = new TreeSet<String>((l1, l2) -> {
                int i1 = voiceMap.get(l1).size();
                int i2 = voiceMap.get(l2).size();
                if (i1 > i2)
                    return -1;
                else if (i1 < i2)
                    return 1;
                return l1.compareToIgnoreCase(l2);
            });
        langs.addAll(voiceMap.keySet());
        for (String lang : langs) {
            SortedSet<String> voices = voiceMap.get(lang);
            if (app.languages.contains(lang) && !voices.isEmpty()) {
                String entryValues[] = voices.toArray(new String[0]);
                String entries[] = new String[entryValues.length];
                Map<String, Boolean> dialect = new HashMap<String, Boolean>();
                for (int i = 0; i < entries.length; i++) {
                    entries[i] = HumanName.get(entryValues[i].substring(8), app);
                    dialect.put(entries[i], dialect.containsKey(entries[i]));
                }
                for (int i = 0; i < entries.length; i++)
                    if (dialect.get(entries[i]))
                        entries[i] = String.format(Locale.getDefault(), "%s (%s)", entries[i], HumanName.get("", entryValues[i].substring(4, 7)));
                ListPreference preference = new ListPreference(context);
                preference.setDialogTitle(R.string.pref_language_this);
                preference.setDefaultValue(app.defaultVoices.get(lang));
                preference.setEntries(entries);
                preference.setEntryValues(entryValues);
                preference.setKey(lang);
                preference.setPersistent(true);
                preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
                preference.setTitle(HumanName.get(lang.substring(0, 3), (lang.length() > 3) ? lang.substring(4) : null));
                preference.setIconSpaceReserved(false);
                preference.setSingleLineTitle(false);
                preference.setEnabled(entries.length > 1);
                languagePreferences.addPreference(preference);
            }
        }

        Set<String> emojiVoices = new TreeSet<String>();
        langs = new TreeSet<String>();
        try {
            for (String emojiData : getActivity().getAssets().list("emoji"))
                langs.add(emojiData.substring(0, 3));
        } catch (Exception ex) {
        }
        for (String voice : app.generalVoices)
            if (langs.contains(voice.substring(0, 3)))
                emojiVoices.add(voice);

        setupExtraPreference(R.string.cjk_fallback_key, R.array.cjk_languages);
        setupExtraPreference(R.string.latinic_fallback_key, R.array.latinic_languages);
        setupExtraPreference(R.string.cyrillic_fallback_key, R.array.cyrillic_languages);
        setupExtraPreference(R.string.numeric_language_key, app.generalVoices);
        setupExtraPreference(R.string.pref_emoji_voice_key, emojiVoices);
    }

    @Override
    public void onPrepareMenu(Menu menu) {
        boolean showRefreshButton = !((TtsPreferenceActivity) getActivity()).hasHeaders();
        MenuItem refreshButton = menu.findItem(R.id.action_refresh);
        refreshButton.setEnabled(showRefreshButton);
        refreshButton.setVisible(showRefreshButton);
    }

}
