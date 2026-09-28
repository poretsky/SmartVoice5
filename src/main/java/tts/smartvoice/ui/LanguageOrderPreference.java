package tts.smartvoice.ui;

import java.util.Set;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;

import androidx.preference.DialogPreference;

import tts.smartvoice.R;

public class LanguageOrderPreference extends DialogPreference implements IStringPreference, IDialogPreference {

    private String stringValue;
    private Set<String> allowedItems;


    public LanguageOrderPreference(Context context, AttributeSet attributes, int defStyleAttr, int defStyleRes) {
        super(context, attributes, defStyleAttr, defStyleRes);
        bootstrap(context);
    }

    public LanguageOrderPreference(Context context, AttributeSet attributes, int defStyleAttr) {
        super(context, attributes, defStyleAttr);
        bootstrap(context);
    }

    public LanguageOrderPreference(Context context, AttributeSet attributes) {
        super(context, attributes);
        bootstrap(context);
    }

    public LanguageOrderPreference(Context context) {
        super(context);
        bootstrap(context);
    }


    @Override
    public void onSetInitialValue(Object defaultValue) {
        setValue(getPersistedString((String) defaultValue));
    }


    @Override
    public String getValue() {
        return stringValue;
    }

    @Override
    public void setValue(String newValue) {
        if (!TextUtils.equals(stringValue, newValue)) {
            stringValue = newValue;
            persistString(stringValue);
            notifyChanged();                                                                                
        }
    }


    @Override
    public ListDialogFragment getDialogFragment() {
        return LanguageOrderPreferenceDialogFragment.newInstance(getKey());
    }


    void setAllowedItems(Set<String> items) {
        allowedItems = items;
        setEnabled(items.size() > 1);
    }

    Set<String> getAllowedItems() {
        return allowedItems;
    }


    private void bootstrap(Context context) {
        StringList order = new StringList();
        for (String item : context.getResources().getStringArray(R.array.languages))
            order.add(item);
        setDefaultValue(order.getStringValue());
    }

}
