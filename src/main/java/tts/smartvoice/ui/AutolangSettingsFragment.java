package tts.smartvoice.ui;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.preference.MultiSelectListPreference;
import androidx.preference.PreferenceManager;

import net.mm2d.preference.PreferenceActivityCompat;

import tts.smartvoice.LanguageDetectionOrder;
import tts.smartvoice.R;
import tts.smartvoice.SmartVoiceApp;

public class AutolangSettingsFragment extends CustomDialogPreferenceFragment implements SharedPreferences.OnSharedPreferenceChangeListener {

    private String autoLangsKey;
    private String stickyLangsKey;
    private String detectionOrderKey;

    private MultiSelectListPreference autoLangsPreference;
    private MultiSelectListPreference stickyLangsPreference;
    private LanguageOrderPreference detectionOrderPreference;

    private LanguageDetectionOrder languageDetectionOrder;
    private SmartVoiceApp app;


    private void summarize(MultiSelectListPreference preference, boolean reorder) {
        StringBuilder summary = new StringBuilder();
        CharSequence entries[] = preference.getEntries();
        if (reorder) {
            Set<String> orderedValues = new TreeSet<String>(languageDetectionOrder);
            List<CharSequence> orderedEntries = new ArrayList<CharSequence>();
            for (CharSequence value : preference.getEntryValues())
                orderedValues.add(value.toString());
            for (String value : orderedValues)
                orderedEntries.add(entries[preference.findIndexOfValue(value)]);
            entries = orderedEntries.toArray(new CharSequence[0]);
            preference.setEntries(entries);
            preference.setEntryValues(orderedValues.toArray(new String[0]));
        }
        Set<String> values = preference.getValues();
        for (CharSequence v : preference.getEntryValues()) {
            String value = v.toString();
            if (values.contains(value)) {
                if (summary.length() > 0)
                    summary.append(", ");
                summary.append(entries[preference.findIndexOfValue(value)]);
            }
        }
        if (summary.length() > 0) {
            preference.setSummary(summary.toString());
        } else {
            preference.setSummary(R.string.no_selections);
        }
    }

    private void setupLanguageSelectionPreference(MultiSelectListPreference preference, Set<String> langs) {
        String values[] = langs.toArray(new String[0]);
        String entries[] = new String[values.length];
        for (int i = 0; i < values.length; i++)
            entries[i] = HumanName.get(values[i]);
        Set<String> selections = new HashSet<String>(PreferenceManager.getDefaultSharedPreferences(app).getStringSet(preference.getKey(), Collections.<String>emptySet()));
        selections.retainAll(langs);
        preference.setEntries(entries);
        preference.setEntryValues(values);
        preference.setValues(selections);
        summarize(preference, false);
    }


    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getActivity().invalidateOptionsMenu();
        addPreferencesFromResource(R.xml.autolang_preferences);
        autoLangsKey = getString(R.string.pref_languages_auto_key);
        stickyLangsKey = getString(R.string.pref_languages_sticky_key);
        detectionOrderKey = getString(R.string.language_detection_order_key);
        app = (SmartVoiceApp) getActivity().getApplication();
        languageDetectionOrder = new LanguageDetectionOrder(app, PreferenceManager.getDefaultSharedPreferences(app).getString(detectionOrderKey, null));
        Set<String> langs = new TreeSet<String>(languageDetectionOrder);
        Set<String> availableLanguages = new TreeSet<String>();
        List<String> arabics = Arrays.asList(getResources().getStringArray(R.array.arabic_languages));
        for (String l : app.languages)
            if (l.length() == 3) {
                if (arabics.contains(l))
                    availableLanguages.add(arabics.get(0));
                availableLanguages.add(l);
            }
        Collections.addAll(langs, getResources().getStringArray(R.array.languages));
        langs.retainAll(availableLanguages);

        autoLangsPreference = (MultiSelectListPreference)findPreference(autoLangsKey);
        stickyLangsPreference = (MultiSelectListPreference)findPreference(stickyLangsKey);
        detectionOrderPreference = (LanguageOrderPreference)findPreference(detectionOrderKey);

        setupLanguageSelectionPreference(autoLangsPreference, langs);
        setupLanguageSelectionPreference(stickyLangsPreference, availableLanguages);
        detectionOrderPreference.setAllowedItems(autoLangsPreference.getValues());

        PreferenceManager.getDefaultSharedPreferences(app).registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(app).unregisterOnSharedPreferenceChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
        if (autoLangsKey.equals(key)) {
            summarize(autoLangsPreference, false);
            detectionOrderPreference.setAllowedItems(autoLangsPreference.getValues());
        } else if (stickyLangsKey.equals(key)) {
            summarize(stickyLangsPreference, false);
        } else if (detectionOrderKey.equals(key)) {
            languageDetectionOrder.setup(preferences.getString(key, null));
            summarize(autoLangsPreference, true);
        }
    }

}
