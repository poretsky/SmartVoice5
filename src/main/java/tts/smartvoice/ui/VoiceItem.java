package tts.smartvoice.ui;

import android.content.Context;

class VoiceItem implements Comparable<VoiceItem> {

    private final String value;
    private final String name;
    private final String person;
    private final String language;
    private final int priority;

    public VoiceItem(String value, Context context) {
        this.value = value;
        String[] vs = value.split("-");
        person = HumanName.get(vs[2], context);
        language = HumanName.get(vs[0], vs[1]);
        name = HumanName.compose(language, person);
        priority = 0;
    }

    public VoiceItem(String value, int priority) {
        this.value = value;
        this.priority = priority;
        person = "";
        if (value.length() > 3) {
            String[] vs = value.split("-");
            name = HumanName.get(vs[0], vs[1]);
        } else name = HumanName.get(value);
        language = name;
    }

    public VoiceItem(String value) {
        this(value, 0);
    }

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public String getPerson() {
        return person;
    }


    @Override
    public int compareTo(VoiceItem other) {
        if (priority < other.priority)
            return 1;
        if (priority > other.priority)
            return -1;
        int result = language.compareTo(other.language);
        if (result != 0)
            return result;
        return name.compareTo(other.name);
    }

}
