package tts.smartvoice.ui;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

abstract class CustomDialogPreferenceFragment extends PreferenceFragmentCompat {

    private static final String DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG";

    @SuppressWarnings("deprecation")
    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        if (preference instanceof IDialogPreference) {
            boolean handled = false;
            if (getContext() instanceof OnPreferenceDisplayDialogCallback)
                handled = ((OnPreferenceDisplayDialogCallback) getContext())
                    .onPreferenceDisplayDialog(this, preference);
            if (!handled && getActivity() instanceof OnPreferenceDisplayDialogCallback)
                handled = ((OnPreferenceDisplayDialogCallback) getActivity())
                    .onPreferenceDisplayDialog(this, preference);
            if (!(handled || (getParentFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null))) {
                ListDialogFragment fragment = ((IDialogPreference) preference).getDialogFragment();
                fragment.setTargetFragment(this, 0);
                fragment.show(getParentFragmentManager(), DIALOG_FRAGMENT_TAG);
            }
        } else super.onDisplayPreferenceDialog(preference);
    }

}
