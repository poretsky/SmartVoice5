package tts.smartvoice.ui;

import android.content.Context;

class VoiceItem implements Comparable<VoiceItem> {

    private final String value;
    private final String name;
    private final String primarySortingKey;

    public VoiceItem(String value, Context context) {
        this.value = value;
        String[] vs = value.split("-");
        name = HumanName.get(vs, context);
        primarySortingKey = HumanName.get(vs[0], vs[1]);
    }

    public VoiceItem(String value) {
        this.value = value;
        if (value.length() > 3) {
            String[] vs = value.split("-");
            name = HumanName.get(vs[0], vs[1]);
        } else name = HumanName.get(value);
        primarySortingKey = name;
    }

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }


    @Override
    public int compareTo(VoiceItem other) {
        int result = primarySortingKey.compareTo(other.primarySortingKey);
        if (result != 0)
            return result;
        return name.compareTo(other.name);
    }

}
