package tts.smartvoice.markup;

import java.io.Reader;

import com.google.gson.GsonBuilder;

import orestes.bloomfilter.BloomFilter;

final class LanguageData extends Selector {

    private String name;
    private String alphabet;
    private BloomFilter<String> filter;

    public static LanguageData load(Reader source) {
        return new GsonBuilder()
            .registerTypeAdapter(BloomFilter.class, new BloomFilterDeserializer())
            .create()
            .fromJson(source, LanguageData.class);
    }

    public String getName() {
        return name;
    }

    public String getAlphabet() {
        return alphabet;
    }

    public BloomFilter<String> getFilter() {
        return filter;
    }

}
