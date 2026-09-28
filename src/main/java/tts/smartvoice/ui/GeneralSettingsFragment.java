package tts.smartvoice.ui;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import tts.smartvoice.R;
import tts.smartvoice.SmartVoiceApp;

public class GeneralSettingsFragment extends PreferenceFragmentCompat {

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private CheckBoxPreference quickControlWidgetEnabled;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getActivity().invalidateOptionsMenu();
        addPreferencesFromResource(R.xml.general_preferences);
        findPreference(getString(R.string.log_text_key)).setVisible(((SmartVoiceApp) getActivity().getApplication()).isDebugEnabled());
        quickControlWidgetEnabled = (CheckBoxPreference) findPreference(getString(R.string.quick_control_widget_option_key));
        requestPermissionLauncher = registerForActivityResult(new RequestPermission(), isGranted -> {
                if (isGranted) {
                    quickControlWidgetEnabled.setChecked(true);
                }
            });
        quickControlWidgetEnabled.setOnPreferenceChangeListener(this::checkPostNotificationsPermission);
    }

    @SuppressLint("InlinedApi")
    private boolean checkPostNotificationsPermission(Preference preference, Object newValue) {
        Activity activity = getActivity();
        String permission = Manifest.permission.POST_NOTIFICATIONS;
        if (!((Boolean) newValue) ||
            (ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED))
            return true;
        if (!ActivityCompat.shouldShowRequestPermissionRationale(activity, permission))
            requestPermissionLauncher.launch(permission);
        return false;
    }

}
