package tts.smartvoice.markup;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.gson.GsonBuilder;

import orestes.bloomfilter.BloomFilter;
import orestes.bloomfilter.FilterBuilder;
import orestes.bloomfilter.HashProvider;
import orestes.bloomfilter.json.BloomFilterConverter;

class CriterionTest extends Criterion {

    private static final String SRC_DATA_PATH = "data";
    private static final String BF_SRC_EXT = "freqs";
    private static final String BF_DST_EXT = "bf.json";

    private static final int DEFAULT_FILTER_CAPACITY = 50000;
    private static final double DEFAULT_FILTER_FPP = 0.001;

    final String name;
    final Map<String, Summary> results;

    String specimen;
    int filterCount;

    private final Pattern separator;

    private PrintStream hitsLog;
    private PrintStream refutesLog;
    private PrintStream missesLog;
    private Set<String> supplementation;


    CriterionTest(LanguageData language, List<? extends Criterion> alternatives) {
        super(language, alternatives);
        name = language.getName();
        separator = Pattern.compile("^(.+)\\s+(\\d+)$");
        results = new TreeMap<String, Summary>();
        hitsLog = null;
        refutesLog = null;
        missesLog = null;
        filterCount = 0;
        supplementation = null;
    }

    void apply(String record) {
        Matcher info = separator.matcher(record);
        if (info.find())
            apply(info.group(1), Long.parseLong(info.group(2)));
        else apply(record, 1);
    }

    void apply(String text, long weight) {
        Summary result = results.get(specimen);
        if (result == null) {
            result = new Summary();
            results.put(specimen, result);
        }
        if (refute(text)) {
            if (refutesLog != null) {
                if (result.refutes == 0) {
                    refutesLog.println();
                    refutesLog.printf("%s:\n", specimen);
                }
                if (weight > 1)
                    refutesLog.printf("%s %d\n", text, weight);
                else refutesLog.println(text);
            }
            result.refutes += weight;
        } else if (detect(text)) {
            if (hitsLog != null) {
                if (result.hits == 0) {
                    hitsLog.println();
                    hitsLog.printf("%s:\n", specimen);
                }
                if (weight > 1)
                    hitsLog.printf("%s %d\n", text, weight);
                else hitsLog.println(text);
            }
            result.hits += weight;
        } else if (missesLog != null) {
            if (!result.misses) {
                missesLog.println();
                missesLog.printf("%s:\n", specimen);
                result.misses = true;
            }
            if (weight > 1)
                missesLog.printf("%s %d\n", text, weight);
            else missesLog.println(text);
        }
        result.total += weight;
    }

    List<String> extract(String text) {
        List<String> result = new ArrayList<String>();
        Matcher matcher = selector.matcher(text);
        while (matcher.find()) {
            String chunk = matcher.group();
            if (detect(chunk))
                result.add(chunk);
        }
        return result;
    }

    void logHits() {
        try {
            hitsLog = new PrintStream(name + ".hits");
        } catch (Exception ex) {
            hitsLog = null;
        }
    }

    void logRefutes() {
        try {
            refutesLog = new PrintStream(name + ".refutes");
        } catch (Exception ex) {
            refutesLog = null;
        }
    }

    void logMisses() {
        try {
            missesLog = new PrintStream(name + ".misses");
        } catch (Exception ex) {
            missesLog = null;
        }
    }

    void recreateFilter(FilterSettings settings) {
        if (filter == null)
            return;
        int capacity = settings.getCapacity();
        if (capacity <= 0)
            capacity = DEFAULT_FILTER_CAPACITY;
        double fpp = settings.getFpp();
        if ((fpp <= 0.0) || (fpp >= 1.0))
            fpp = DEFAULT_FILTER_FPP;
        FilterBuilder filterBuilder = new FilterBuilder(capacity, fpp);
        HashProvider.HashMethod hashMethod = settings.getHashMethod();
        if (hashMethod != null)
            filterBuilder.hashFunction(hashMethod);
        filter = filterBuilder.buildBloomFilter();
        try {
            long freqLevel = settings.getFreqLevel();
            long level = 0;
            if (freqLevel > 0) {
                BufferedReader source = new BufferedReader(new FileReader(String.format("%s/%s.%s", SRC_DATA_PATH, name, BF_SRC_EXT)));
                for (String line = source.readLine(); line != null; line = source.readLine())
                    level += Long.parseLong(line.split("\\s+")[1]);
                source.close();
                freqLevel *= level;
                freqLevel += 50;
                freqLevel /= 100;
                level = 0;
            } else {
                level = -1;
                freqLevel = 0;
            }
            int maxItems = settings.getMaxItems();
            if (maxItems <= 0)
                maxItems = capacity;
            int items = 0;
            BufferedReader source = new BufferedReader(new FileReader(String.format("%s/%s.%s", SRC_DATA_PATH, name, BF_SRC_EXT)));
            for (String line = source.readLine(); (line != null) && (items < maxItems) && (level < freqLevel); line = source.readLine()) {
                String[] recordFields = line.split("\\s+");
                if (filter.add(recordFields[0].toLowerCase()))
                    filterCount++;
                if (freqLevel > 0)
                    level += Long.parseLong(recordFields[1]);
                items++;
            }
            source.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void collectSupplementation() {
        if (filter == null)
            return;
        supplementation = new HashSet<String>();
        try {
            BufferedReader source = new BufferedReader(new FileReader(String.format("%s/%s.%s", SRC_DATA_PATH, name, BF_SRC_EXT)));
            for (String line = source.readLine(); line != null; line = source.readLine()) {
                String word = line.split("\\s+")[0].toLowerCase();
                if (!filter.contains(word))
                    for (Criterion language : alternatives)
                        if ((language != this) && (language.filter != null) && language.filter.contains(word)) {
                            supplementation.add(word);
                            break;
                        }
            }
            source.close();
            source = new BufferedReader(new FileReader(String.format("%s/%s", SRC_DATA_PATH, name)));
            for (String line = source.readLine(); line != null; line = source.readLine()) {
                String word = line.toLowerCase();
                if (!filter.contains(word))
                    for (Criterion language : alternatives)
                        if ((language != this) && (language.filter != null) && language.filter.contains(word)) {
                            supplementation.add(word);
                            break;
                        }
            }
            source.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void supplementFilter() {
        if (supplementation != null) {
            for (String word : supplementation)
                if (filter.add(word))
                    filterCount++;
            supplementation = null;
        }
    }

    void saveFilter() {
        if (filter == null)
            return;
        try {
            PrintStream output = new PrintStream(String.format("%s.%s", name, BF_DST_EXT));
            output.println(new GsonBuilder()
                           .setPrettyPrinting()
                           .create()
                           .toJson(BloomFilterConverter.toJson(filter)));
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void closeLogs() {
        if (hitsLog != null) {
            hitsLog.close();
            hitsLog = null;
        }
        if (refutesLog != null) {
            refutesLog.close();
            refutesLog = null;
        }
        if (missesLog != null) {
            missesLog.close();
            missesLog = null;
        }
    }

}
