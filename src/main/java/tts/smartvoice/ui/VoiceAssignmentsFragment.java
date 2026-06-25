package tts.smartvoice.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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

    private void addVoice(Map<String, Set<VoiceItem>> voiceMap, VoiceItem voice, int keyLen) {
        String lang = voice.getValue().substring(0, keyLen);
        Set<VoiceItem> voices = voiceMap.get(lang);
        if (voices == null) {
            voices = new TreeSet<VoiceItem>();
            voiceMap.put(lang, voices);
        }
        voices.add(voice);
    }

    private void setupExtraPreference(int keyId, int langListId) {
        Set<String> langs = new HashSet<String>();
        for (String lang : getResources().getStringArray(langListId))
            for (String voice : app.generalVoices)
                if (voice.startsWith(lang))
                    langs.add(voice);
        ListPreference preference = (ListPreference)findPreference(getString(keyId));
        if (langs.isEmpty()) {
            preference.setSummary("");
            preference.setEnabled(false);
        } else {
            Set<VoiceItem> voices = SortedItems.from(langs, app);
            String[] values = new String[voices.size() + 1];
            String[] entries = new String[values.length];
            int i = 0;
            for (VoiceItem voice : voices) {
                entries[i] = voice.getName();
                values[i] = voice.getValue();
                i++;
            }
            entries[voices.size()] = getString(R.string.no_preferences);
            values[voices.size()] = "";
            preference.setEntries(entries);
            preference.setEntryValues(values);
            preference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
            preference.setEnabled(true);
        }
    }

    private void setupExtraPreference(int keyId, Set<VoiceItem> voices) {
        ListPreference preference = (ListPreference)findPreference(getString(keyId));
        String[] values = new String[voices.size() + 2];
        String[] entries = new String[values.length];
        int i = 0;
        for (VoiceItem voice : voices) {
            entries[i] = voice.getName();
            values[i] = voice.getValue();
            i++;
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
        Map<String, Set<VoiceItem>> voiceMap = new HashMap<String, Set<VoiceItem>>();
        for (VoiceItem voice : SortedItems.fromGeneralVoices(app)) {
            addVoice(voiceMap, voice, 3);
            addVoice(voiceMap, voice, 7);
        }

        for (VoiceItem item : SortedItems.from(voiceMap)) {
            String lang = item.getValue();
            Set<VoiceItem> voices = voiceMap.get(lang);
            if (app.languages.contains(lang) && !voices.isEmpty()) {
                String[] entryValues = new String[voices.size()];
                String[] entries = new String[entryValues.length];
                Map<String, Boolean> dialect = new HashMap<String, Boolean>();
                int i = 0;
                for (VoiceItem voice : voices) {
                    entries[i] = voice.getPerson();
                    entryValues[i] = voice.getValue();
                    dialect.put(entries[i], dialect.containsKey(entries[i]));
                    i++;
                }
                for (i = 0; i < entries.length; i++)
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
                preference.setTitle(item.getName());
                preference.setIconSpaceReserved(false);
                preference.setSingleLineTitle(false);
                preference.setEnabled(entries.length > 1);
                languagePreferences.addPreference(preference);
            }
        }

        Set<VoiceItem> emojiVoices = SortedItems.fromGeneralVoices(app);
        Set<String> emojiLangs = new HashSet<String>();
        try {
            for (String emojiData : getActivity().getAssets().list("emoji"))
                emojiLangs.add(emojiData.substring(0, 3));
        } catch (Exception ex) {
        }
        Iterator<VoiceItem> iterator = emojiVoices.iterator();
        while (iterator.hasNext()) {
            VoiceItem voice = iterator.next();
            if (!emojiLangs.contains(voice.getValue().substring(0, 3)))
                iterator.remove();
        }

        setupExtraPreference(R.string.cjk_fallback_key, R.array.cjk_languages);
        setupExtraPreference(R.string.latinic_fallback_key, R.array.latinic_languages);
        setupExtraPreference(R.string.cyrillic_fallback_key, R.array.cyrillic_languages);
        setupExtraPreference(R.string.arabic_fallback_key, R.array.arabic_languages);
        setupExtraPreference(R.string.numeric_language_key, SortedItems.fromGeneralVoices(app));
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
