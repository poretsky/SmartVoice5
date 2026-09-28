package tts.smartvoice.ui;

import java.util.Comparator;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.preference.PreferenceDialogFragmentCompat;

import tts.smartvoice.R;

abstract class ListDialogFragment extends PreferenceDialogFragmentCompat implements AdapterView.OnItemClickListener, Comparator<String> {

    private static final String SAVE_CURRENT_STATE = "CustomListDialog.state";

    protected TextView message;
    protected ArrayAdapter<String> content;
    protected ListView listView;


    protected static <T extends ListDialogFragment> T equip(T fragment, String key) {
        Bundle args = new Bundle();
        args.putString(ARG_KEY, key);
        fragment.setArguments(args);
        return fragment;
    }


    protected abstract String getState();

    protected abstract void setState(String value);


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String currentValue = (savedInstanceState != null) ?
            savedInstanceState.getString(SAVE_CURRENT_STATE) :
            ((IStringPreference) getPreference()).getValue();
        setState(currentValue);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(SAVE_CURRENT_STATE, getState());
    }

    @Override
    public void onDialogClosed(boolean positiveResult) {
        if (positiveResult)
            ((IStringPreference) getPreference()).setValue(getState());
    }

    @Override
    protected View onCreateDialogView(Context context) {
        View layout = super.onCreateDialogView(context);
        message = (TextView) layout.findViewById(android.R.id.message);
        listView = (ListView) layout.findViewById(R.id.content);
        content = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1);
        content.setNotifyOnChange(false);
        listView.setAdapter(content);
        listView.setOnItemClickListener(this);
        return layout;
    }

}
