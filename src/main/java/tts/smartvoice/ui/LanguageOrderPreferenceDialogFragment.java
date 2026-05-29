package tts.smartvoice.ui;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import android.view.View;
import android.widget.AdapterView;

import tts.smartvoice.StringList;
import tts.smartvoice.api.Utils;

public class LanguageOrderPreferenceDialogFragment extends ListDialogFragment {

    private Map<String, String> entries;
    private StringList order;


    public static LanguageOrderPreferenceDialogFragment newInstance(String key) {
        return equip(new LanguageOrderPreferenceDialogFragment(), key);
    }


    @Override
    protected String getState() {
        return order.getStringValue();
    }

    @Override
    protected void setState(String value) {
        order = new StringList();
        order.putStringValue(value);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        entries = new HashMap<String, String>();
        for (String item : ((LanguageOrderPreference) getPreference()).getAllowedItems())
            if (order.contains(item)) {
                Locale locale = Utils.obtainLocale(item);
                entries.put(locale.getDisplayLanguage(), item);
            }
        content.clear();
        content.addAll(entries.keySet());
        content.sort(this);
        content.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        if ((position > 0) && (position < content.getCount())) {
            String item = content.getItem(position);
            String value = entries.get(item);
            if (order.remove(value)) {
                order.add(0, value);
                content.remove(item);
                content.insert(item, 0);
                content.notifyDataSetChanged();
            }
        }
    }

    @Override
    public int compare(String s1, String s2) {
        return order.indexOf(entries.get(s1)) - order.indexOf(entries.get(s2));
    }

}
