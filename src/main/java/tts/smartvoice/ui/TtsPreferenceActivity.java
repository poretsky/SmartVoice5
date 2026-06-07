package tts.smartvoice.ui;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.activity.OnBackPressedCallback;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import net.mm2d.preference.Header;
import net.mm2d.preference.PreferenceActivityCompat;

import tts.smartvoice.R;
import tts.smartvoice.SmartVoiceApp;

public class TtsPreferenceActivity extends PreferenceActivityCompat {

    public static final String EXTRA_SCREEN_TITLE = "SmartVoice_preference_screen_title";

    private SmartVoiceApp app;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        app = (SmartVoiceApp)getApplication();
        String screenTitle = getIntent().getStringExtra(EXTRA_SCREEN_TITLE);
        if (screenTitle != null)
            setTitle(screenTitle);
        else if (getString(R.string.action_list_voices).equals(getIntent().getAction()))
            setTitle(R.string.voice_settings);
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    setEnabled(false);
                    invalidateOptionsMenu();
                    getOnBackPressedDispatcher().onBackPressed();
                    setEnabled(true);
                }
            });
    }

    @Override
    public boolean isValidFragment(String fragmentName) {
        return GeneralSettingsFragment.class.getName().equals(fragmentName) ||
            AutolangSettingsFragment.class.getName().equals(fragmentName) ||
            VoiceAssignmentsFragment.class.getName().equals(fragmentName) ||
            VoiceSettingsFragment.class.getName().equals(fragmentName);
    }

    @Override
    public void onBuildHeaders(List<Header> target) {
        if (getString(R.string.action_list_voices).equals(getIntent().getAction())) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            Set<String> assignedVoices = new HashSet<String>();
            Set<String> langs = new HashSet<String>();
            Set<String> autoLangs = new HashSet<String>(preferences.getStringSet(getString(R.string.pref_languages_auto_key), Collections.<String>emptySet()));
            langs.add(getString(R.string.cjk_fallback_key));
            langs.add(getString(R.string.latinic_fallback_key));
            langs.add(getString(R.string.cyrillic_fallback_key));
            langs.add(getString(R.string.numeric_language_key));
            langs.add(getString(R.string.pref_emoji_voice_key));
            Map<String, Set<VoiceItem>> voiceMap = new HashMap<String, Set<VoiceItem>>();
            List<String> arabics = Arrays.asList(getResources().getStringArray(R.array.arabic_languages));
            for (VoiceItem voice : SortedItems.fromGeneralVoices(app)) {
                String lang = voice.getValue().substring(0, 3);
                addVoice(voiceMap, lang, voice);
                if (arabics.contains(lang))
                    addVoice(voiceMap, arabics.get(0), voice);
                lang = voice.getValue().substring(0, 7);
                addVoice(voiceMap, lang, voice);
            }
            for (String lang : voiceMap.keySet()) {
                if (voiceMap.get(lang).size() > 1) {
                    String voice = preferences.getString(lang, null);
                    String defaultVoice = app.defaultVoices.get(lang);
                    if ((voice != null) && (defaultVoice != null) && !voice.equals(defaultVoice)) {
                        assignedVoices.add(voice);
                    }
                }
                if (autoLangs.contains(lang)) {
                    langs.add(lang);
                }
            }
            for (String lang : langs) {
                String voice = preferences.getString(lang, null);
                if (voice == null) {
                    voice = app.defaultVoices.get(lang);
                }
                if ((voice != null) && !voice.isEmpty()) {
                    assignedVoices.add(voice);
                }
            }
            Set<VoiceItem> voices = SortedItems.fromGeneralVoices(app, assignedVoices);
            int index = 0;
            for (VoiceItem voice : voices) {
                Header header = new Header();
                header.setId(index++);
                header.setTitle(voice.getPerson());
                header.setSummary(voice.getLanguage());
                header.setBreadCrumbTitle(voice.getName());
                header.setFragment(VoiceSettingsFragment.class.getName());
                Bundle args = new Bundle();
                args.putString(getString(R.string.voice_key), voice.getValue());
                header.setFragmentArguments(args);
                target.add(header);
            }
        } else {
            loadHeadersFromResource(R.xml.preference_headers, target);
        }
    }

    @Override
    public void onResume() {
        if (getString(R.string.action_list_voices).equals(getIntent().getAction()))
            invalidateHeaders();
        super.onResume();
        app.setUiNotifier(new Runnable() {
                @Override
                public void run() {
                    if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                        try {
                            recreate();
                        } catch (Exception ex) {
                            finish();
                        }
                }
            });
    }

    @Override
    public void onPause() {
        app.setUiNotifier(null);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.preferences_actions, menu);
        if (app.getTtsIntegrator() != null) {
            Map<String, Intent> engineActions = app.getTtsIntegrator().getMenuActions();
            for (String engine : engineActions.keySet()) {
                MenuItem item = menu.add(R.id.extra_items, Menu.NONE, Menu.NONE, app.getTtsIntegrator().getEngineLabelFor(engine));
                item.setIntent(engineActions.get(engine));;
                item.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
            }
        }
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean showRefreshButton = getString(R.string.action_list_voices).equals(getIntent().getAction());
        MenuItem refreshButton = menu.findItem(R.id.action_refresh);
        refreshButton.setEnabled(showRefreshButton);
        refreshButton.setVisible(showRefreshButton);
        boolean showExtraItems = hasHeaders() && !getString(R.string.action_list_voices).equals(getIntent().getAction());
        menu.setGroupEnabled(R.id.extra_items, showExtraItems);
        menu.setGroupVisible(R.id.extra_items, showExtraItems);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_refresh)
            app.refresh();
        else if (itemId == android.R.id.home)
            getOnBackPressedDispatcher().onBackPressed();
        else return super.onOptionsItemSelected(item);
        return true;
    }


    private void addVoice(Map<String, Set<VoiceItem>> voiceMap, String lang, VoiceItem voice) {
        Set<VoiceItem> voices = voiceMap.get(lang);
        if (voices == null) {
            voices = new HashSet<VoiceItem>();
            voiceMap.put(lang, voices);
        }
        voices.add(voice);
    }

}
