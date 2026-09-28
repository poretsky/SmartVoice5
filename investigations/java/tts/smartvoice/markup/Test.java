package tts.smartvoice.markup;

import java.io.BufferedReader;
import java.io.File;
import java.io.FilenameFilter;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class Test {

    private static final List<CriterionTest> languages = new ArrayList<CriterionTest>();
    private static final File langData = new File("../res/raw");

    public static void main(String[] args) {
        if ((args == null) || (args.length == 0)) {
            System.err.println("No files specified");
            return;
        }
        for (String langFile : langData.list(new FilenameFilter() {
                @Override
                public boolean accept(File dir, String filename) {
                    return filename.endsWith(".json");
                }
            }))
            languages.add(new CriterionTest(loadLangData(new File(langData, langFile)), languages));
        int optind = 0;
        CriterionTest language = null;
        boolean audit = "-audit".equals(args[0]);
        boolean extract = "-extract".equals(args[0]);
        if (extract) {
            if ((++optind) >= args.length) {
                System.err.println("Too few arguments");
                return;
            }
        } else if (audit || "-lang".equals(args[0])) {
            if ((++optind) >= args.length) {
                System.err.println("Too few arguments");
                return;
            }
            if (audit) {
                language = new CriterionTest(loadLangData(new File(args[optind])), languages);
                for (int i = 0; i < languages.size(); i++)
                    if (language.name.equals(languages.get(i).name))
                        languages.set(i, language);
            } else {
                for (CriterionTest lang : languages)
                    if (lang.name.equals(args[optind])) {
                        language = lang;
                        break;
                    }
            }
            if (language != null) {
                if (audit)
                    language.logMisses();
                language.logHits();
                language.logRefutes();
            } else {
                System.err.printf("Unknown language `%s'\n", args[optind]);
                return;
            }
            if ((++optind) >= args.length) {
                System.err.println("No files specified");
                return;
            }
        }
        File filterConfig = new File(FilterSettings.CONFIG_FILE);
        if (filterConfig.exists()) {
            FilterSettings settings = FilterSettings.instantiate();
            for (CriterionTest lang : languages)
                lang.recreateFilter(settings);
            for (CriterionTest lang : languages)
                lang.collectSupplementation();
            for (CriterionTest lang : languages)
                lang.supplementFilter();
        }
        int textSize = 0;
        long lines = 0;
        long startTime = System.currentTimeMillis();
        for (int i = optind; i < args.length; i++) {
            String file = args[i];
            if (language == null)
                for (CriterionTest lang : languages)
                    lang.specimen = file;
            else language.specimen = file;
            try {
                BufferedReader reader = new BufferedReader(new FileReader(file));
                StringBuilder text = new StringBuilder();
                int n = 0;
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    if (extract) {
                        text.append(line).append('\n');;
                    } else if (language == null)
                        for (CriterionTest lang : languages)
                            lang.apply(line);
                    else language.apply(line);
                    n++;
                    lines++;
                }
                reader.close();
                if (extract) {
                    PrintStream extractions = new PrintStream(file + ".extractions");
                    for (CriterionTest lang : languages) {
                        n = 0;
                        List<String> chunkList = lang.extract(text.toString());
                        for (String chunk : chunkList) {
                            if (n == 0)
                                extractions.printf("\n%s %d chunks:\n", lang.name, chunkList.size());
                            extractions.printf("%d) %s\n", ++n, chunk);
                        }
                    }
                    extractions.close();
                    textSize += text.length();
                } else if (language == null) {
                    System.out.printf("File %s contains %d lines\n", file, n);
                    for (CriterionTest lang : languages) {
                        Summary result = lang.results.get(file);
                        if (result != null)
                            result.report(lang.name);
                        lang.closeLogs();
                    }
                    System.out.println();
                } else {
                    language.closeLogs();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (extract) {
            float procTime = ((float) (System.currentTimeMillis() - startTime)) / 1000;
            System.out.printf("Processed %d characters in %f seconds\n", textSize, procTime);
            if (procTime > 0)
                System.out.printf("Approximately %d characters per second\n", Math.round(((float) textSize) / procTime));
            return;
        }
        if (language != null) {
            for (Map.Entry<String, Summary> item : language.results.entrySet())
                item.getValue().report(item.getKey());
        } else {
            System.out.println();
            System.out.println("Summary by language detectors");
            System.out.println();
            for (CriterionTest lang : languages) {
                System.out.printf("%s:\n", lang.name);
                for (Map.Entry<String, Summary> item : lang.results.entrySet())
                    item.getValue().report(item.getKey());
                System.out.println();
            }
        }
        long procTime = (System.currentTimeMillis() - startTime + 500) / 1000;
        System.out.printf("Processed %d lines in %d seconds\n", lines, procTime);
        System.out.printf("Approximately %d lines per second\n", (lines + (procTime / 2)) / procTime);
        if (filterConfig.exists()) {
            System.out.println();
            System.out.println("Filters:");
            int maxCount = 0;
            for (CriterionTest lang : languages) {
                if (lang.filterCount > 0)
                    System.out.printf("%s - %d items\n", lang.name, lang.filterCount);
                if (lang.filterCount > maxCount)
                    maxCount = lang.filterCount;
                lang.saveFilter();
            }
            System.out.printf("Max count - %d\n", maxCount);
        }
    }

    private static LanguageData loadLangData(File langFile) {
        try {
            FileReader dataSource = new FileReader(langFile);
            LanguageData result = LanguageData.load(dataSource);
            dataSource.close();
            return result;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
