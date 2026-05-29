package tts.smartvoice;

import java.util.ArrayList;
import java.util.Collections;

public class StringList extends ArrayList<String> {

    private static final long serialVersionUID = 42L;

    public String getStringValue() {
        if (isEmpty())
            return null;
        StringBuilder value = new StringBuilder();
        for (String item : this) {
            if (value.length() > 0)
                value.append(',');
            value.append(item);
        }
        return value.toString();
    }

    public void putStringValue(String value) {
        clear();
        if (value != null)
            Collections.addAll(this, value.split(","));
    }

}
