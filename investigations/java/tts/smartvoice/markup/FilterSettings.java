package tts.smartvoice.markup;

import java.io.FileReader;
import java.io.IOException;

import com.google.gson.GsonBuilder;

import orestes.bloomfilter.HashProvider;

class FilterSettings {

    static final String CONFIG_FILE = "bf-conf.json";

    private int capacity;
    private double fpp;
    private String hashMethod;
    private int maxItems;
    private int freqLevel;

    static FilterSettings instantiate() {
        try {
            return new GsonBuilder()
                .setLenient()
                .create()
                .fromJson(new FileReader(CONFIG_FILE), FilterSettings.class);
        } catch (IOException ex) {
        }
        return new FilterSettings();
    }

    int getCapacity() {
        return capacity;
    }

    double getFpp() {
        return fpp;
    }

    HashProvider.HashMethod getHashMethod() {
        try {
            return HashProvider.HashMethod.valueOf(hashMethod);
        } catch (Exception ex) {
        }
        return null;
    }

    int getMaxItems() {
        return maxItems;
    }

    int getFreqLevel() {
        return freqLevel;
    }

}
