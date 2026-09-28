package tts.smartvoice.markup;

import java.lang.reflect.Type;

import com.google.gson.JsonDeserializer;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;

import orestes.bloomfilter.BloomFilter;
import orestes.bloomfilter.json.BloomFilterConverter;

class BloomFilterDeserializer implements JsonDeserializer<BloomFilter<String>> {

    @Override
    public BloomFilter<String> deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        return BloomFilterConverter.fromJson(json);
    }

}
