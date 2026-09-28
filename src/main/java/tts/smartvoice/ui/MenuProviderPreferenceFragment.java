package tts.smartvoice.ui;

import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;

import androidx.core.view.MenuProvider;
import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceFragmentCompat;

abstract class MenuProviderPreferenceFragment extends PreferenceFragmentCompat implements MenuProvider {

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        requireActivity().addMenuProvider(this, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    @Override
    public void onCreateMenu(Menu menu, MenuInflater menuInflater) {
    }

    @Override
    public boolean onMenuItemSelected(MenuItem menuItem) {
        return false;
    }

}
