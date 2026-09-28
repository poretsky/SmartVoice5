package tts.smartvoice.markup;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class ExpirableHint<T> {

    private final long expiration;
    private final T[] groupValue;
    private final Map<String, T> langValue;

    private volatile long deadline;

    @SuppressWarnings("unchecked")
    public ExpirableHint(long expiration) {
        this.expiration = expiration;
        groupValue = (T[]) new Object[LanguageGroup.values().length];
        langValue = new HashMap<String, T>();
        deadline = 0;
        Arrays.fill(groupValue, null);
    }

    public synchronized void clear() {
        Arrays.fill(groupValue, null);
        langValue.clear();
    }

    public void refresh() {
        deadline = System.currentTimeMillis() + expiration;
    }

    public synchronized void set(LanguageGroup group, T value) {
        groupValue[group.ordinal()] = value;
        refresh();
    }

    public synchronized void set(String lang, T value) {
        langValue.put(lang, value);
        refresh();
    }

    public void set(T value) {
        set(LanguageGroup.NONE, value);
    }

    public synchronized T get(LanguageGroup group) {
        int index = group.ordinal();
        if ((groupValue[index] != null) && (System.currentTimeMillis() > deadline))
            groupValue[index] = null;
        return groupValue[index];
    }

    public synchronized T get(String lang) {
        T value = langValue.get(lang);
        if ((value != null) && (System.currentTimeMillis() > deadline)) {
            langValue.remove(lang);
            value = null;
        }
        return value;
    }

    public T get() {
        return get(LanguageGroup.NONE);
    }

    public synchronized boolean check() {
        if (System.currentTimeMillis() > deadline) {
            clear();
            return false;
        }
        for (T value : groupValue)
            if (value != null)
                return true;
        return !langValue.isEmpty();
    }

}
