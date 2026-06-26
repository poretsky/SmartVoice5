package tts.smartvoice.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.PopupMenu;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.ListFragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceManager;

import net.mm2d.preference.PreferenceActivityCompat;

import tts.smartvoice.R;
import tts.smartvoice.SmartVoiceApp;

import tts.smartvoice.markup.LanguageGroup;

public class QuickControlActivity extends AppCompatActivity implements AdapterView.OnItemClickListener, AdapterView.OnItemLongClickListener, PopupMenu.OnMenuItemClickListener {

    private SmartVoiceApp app;
    private List<String> voices;
    private List<LanguageGroup> languageGroups;
    private String selection;
    private String autoLangsKey;
    private String useRequestedVoiceKey;
    private boolean useRequestedVoiceDefaultState;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.quick_control_activity);
        getSupportActionBar().setSubtitle(R.string.quick_control);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        app = (SmartVoiceApp)getApplication();
        voices = new ArrayList<String>();
        languageGroups = new ArrayList<LanguageGroup>();
        selection = null;
        autoLangsKey = getString(R.string.pref_languages_auto_key);
        useRequestedVoiceKey = getString(R.string.use_only_default_or_requested_voice_key);
        useRequestedVoiceDefaultState = getResources().getBoolean(R.bool.use_only_default_or_requested_voice_state);
        preferences = PreferenceManager.getDefaultSharedPreferences(this);
        getOnBackPressedDispatcher().addCallback(new OnBackPressedCallback(true) {
                @Override
                public void handleOnBackPressed() {
                    finish();
                }
            });
    }

    @Override
    protected void onStart() {
        super.onStart();
        getListFragment().getListView().setOnItemClickListener(this);
        getListFragment().getListView().setOnItemLongClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        redisplay();
        app.setUiNotifier(new Runnable() {
                @Override
                public void run() {
                    if (getLifecycle().getCurrentState().isAtLeast(Lifecycle.State.RESUMED))
                        redisplay();
                }
            });
    }

    @Override
    protected void onPause() {
        app.setUiNotifier(null);
        super.onPause();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.quick_control_actions, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        if (itemId == R.id.action_refresh)
            app.refresh();
        else if (itemId == R.id.action_settings)
            startActivity(new Intent(this, TtsPreferenceActivity.class));
        else if (itemId == android.R.id.home)
            finish();
        else return super.onOptionsItemSelected(item);
        return true;
    }


    @Override
    public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
        if (position < voices.size()) {
            selection = voices.get(position);
            String lang = selection.substring(0, 3);
            boolean useForLanguage = false;
            PopupMenu popup = new PopupMenu(this, v);
            languageGroups.clear();
            if (!preferences.getBoolean(useRequestedVoiceKey, false)) {
                final int itemOrder = getResources().getInteger(R.integer.middle_item);
                popup.inflate(R.menu.voice_selection);
                Menu menu = popup.getMenu();
                for (LanguageGroup group : LanguageGroup.values())
                    if (group.contains(lang)) {
                        languageGroups.add(group);
                        switch (group) {
                        case LATINIC:
                            menu.add(R.id.language_group_only, languageGroups.size(), itemOrder, R.string.latinics);
                            break;
                        case CYRILLIC:
                            menu.add(R.id.language_group_only, languageGroups.size(), itemOrder, R.string.cyrillics);
                            break;
                        case ARABIC:
                            menu.add(R.id.language_group_only, languageGroups.size(), itemOrder, R.string.arabics);
                            break;
                        case CJK:
                            menu.add(R.id.language_group_only, languageGroups.size(), itemOrder, R.string.cjk);
                            break;
                        default:
                            break;
                        }
                    }
                useForLanguage = preferences.getStringSet(autoLangsKey, Collections.<String>emptySet()).contains(lang);;
                if (useForLanguage) {
                    MenuItem item = menu.findItem(R.id.native_language_only);
                    if (item != null)
                        item.setTitle(HumanName.get(lang));
                } else {
                    menu.removeItem(R.id.native_language_only);
                }
            }
            if (useForLanguage || !languageGroups.isEmpty()) {
                popup.setOnMenuItemClickListener(this);
                popup.show();
            } else {
                app.explicitVoice.set(selection);
                finish();
            }
        } else {
            app.explicitVoice.clear();
            finish();
        }
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        Intent intent = new Intent(this, TtsPreferenceActivity.class);
        intent.putExtra(PreferenceActivityCompat.EXTRA_NO_HEADERS, true);
        if (position < voices.size()) {
            Bundle fragmentArguments = new Bundle();
            fragmentArguments.putString(getString(R.string.voice_key), voices.get(position));
            intent.putExtra(PreferenceActivityCompat.EXTRA_SHOW_FRAGMENT, VoiceSettingsFragment.class.getName());
            intent.putExtra(PreferenceActivityCompat.EXTRA_SHOW_FRAGMENT_ARGUMENTS, fragmentArguments);
            intent.putExtra(TtsPreferenceActivity.EXTRA_SCREEN_TITLE, (String) parent.getAdapter().getItem(position));
        } else {
            intent.putExtra(PreferenceActivityCompat.EXTRA_SHOW_FRAGMENT, AutolangSettingsFragment.class.getName());
            intent.putExtra(TtsPreferenceActivity.EXTRA_SCREEN_TITLE, getString(R.string.autolang_settings));
        }
        startActivity(intent);
        return true;
    }


    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (selection != null) {
            final int itemId = item.getItemId();
            if (itemId == R.id.native_language_only) {
                app.explicitVoice.set(null);
                app.explicitVoice.set(selection.substring(0, 3), selection);
            } else if (item.getGroupId() == R.id.language_group_only) {
                app.explicitVoice.set(null);
                app.explicitVoice.set(languageGroups.get(itemId - 1), selection);
            } else {
                app.explicitVoice.set(selection);
            }
            finish();
        }
        return true;
    }


    private ListFragment getListFragment() {
        return (ListFragment) getSupportFragmentManager().findFragmentById(R.id.list_fragment);
    }

    private void redisplay() {
        voices.clear();
        List<String> items = new ArrayList<String>();
        for (VoiceItem voice : SortedItems.fromGeneralVoices(app)) {
            voices.add(voice.getValue());
            items.add(voice.getName());
        }
        if (app.explicitVoice.check())
            items.add(getString(R.string.according_to_settings));
        getListFragment().setListAdapter(new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, items));
    }

}
