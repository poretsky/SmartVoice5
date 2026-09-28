package tts.smartvoice.markup;

import java.io.File;
import java.io.PrintStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.google.gson.GsonBuilder;

class ExtractCldrAnnotations {

    private static final String[] subdirs = {
        "annotations",
        "annotationsDerived"
    };

    private static Map<String, String> langs = new HashMap<String, String>();

    static {
        for (Locale locale : Locale.getAvailableLocales()) {
            try {
                String langKey = locale.getISO3Language();
                String langValue = locale.getLanguage();
                if (langKey != null && !langKey.isEmpty() && langValue != null && !langValue.isEmpty()) {
                    langs.put(langKey, langValue);
                    String country = locale.getCountry();
                    String iso3Country = locale.getISO3Country();
                    if (country != null && !country.isEmpty() && iso3Country != null && !iso3Country.isEmpty()) {
                        langKey = String.format(Locale.ROOT, "%s-%s", langKey, iso3Country);
                        langValue = String.format(Locale.ROOT, "%s_%s", langValue, country);
                        langs.put(langKey, langValue);
                    }
                }
            } catch (Exception ex) {
                continue;
            }
        }
    }

    private static void loadEmojiData(File xmlFile, Map<String, String> emojiMap) {
        try {
            Document metadata = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlFile);
            metadata.getDocumentElement().normalize();
            Node node = metadata.getElementsByTagName("annotations").item(0);
            if (node == null)
                return;
            if (node.getNodeType() == Node.ELEMENT_NODE) {
                NodeList annotations = ((Element)node).getElementsByTagName("annotation");
                for (int i = 0; i < annotations.getLength(); i++) {
                    Node item = annotations.item(i);
                    NamedNodeMap attributes = item.getAttributes();
                    if (attributes != null) {
                        Node itemType = attributes.getNamedItem("type");
                        if ((itemType != null) && itemType.getNodeValue().equals("tts")) {
                            Node cp = attributes.getNamedItem("cp");
                            if (cp != null) {
                                String emojiCode = Normalizer.normalize(cp.getNodeValue(), Normalizer.Form.NFKC);
                                String emojiString = Normalizer.normalize(item.getTextContent(), Normalizer.Form.NFKC);
                                if (!(emojiString.equals("↑↑↑") ||
                                      emojiCode.matches("^[[\\w\\s\\pL\\pM\\pP\\p{Punct}]&&\\P{Sc}]+$"))) {
                                    emojiMap.put(emojiCode, emojiString);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception exception) {
            System.err.printf("Error while input file %s.\n", xmlFile.getAbsolutePath());
            System.err.println(exception);
        }
    }

    private static void transfer(File jsonFile, List<File> xmlFiles) {
        Map<String, String> emojiMap = new TreeMap<String, String>();
        for (File xmlFile : xmlFiles)
            if (xmlFile.canRead())
                loadEmojiData(xmlFile, emojiMap);
        if (emojiMap.isEmpty())
            return;
        List<EmojiItem> emojiList = new ArrayList<EmojiItem>();
        for (Map.Entry<String, String> item : emojiMap.entrySet()) {
            EmojiItem emoji = new EmojiItem();
            emoji.cp = item.getKey();
            emoji.tts = item.getValue();
            emojiList.add(emoji);
        }
        String jsonData = new GsonBuilder()
            .setPrettyPrinting()
            .create()
            .toJson(emojiList);
        try {
            PrintStream output = new PrintStream(jsonFile);
            output.println(jsonData);
        } catch (Exception exception) {
            System.err.printf("Cannot write to %s.\n", jsonFile.getAbsolutePath());
        }
    }

    private static void collectSources(List<File> xmlFiles, File basePath, String lang) {
        String langCode = langs.get(lang);
        for (String subdir : subdirs) {
            File path = new File(basePath, subdir);
            File file = new File(path, langCode + ".xml");
            if (file.exists())
                xmlFiles.add(file);
        }
    }

    public static void main(String[] args) {
        File basePath = new File(args[0], "common");
        File destination = new File(args[1]);
        for (String lang : langs.keySet()) {
            List<File> xmlFiles = new ArrayList<File>();
            if (lang.length() > 3)
                collectSources(xmlFiles, basePath, lang.substring(0, 3));
            int sourceCount = xmlFiles.size();
            collectSources(xmlFiles, basePath, lang);
            if (xmlFiles.size() > sourceCount) {
                File jsonFile = new File(destination, lang + ".json");
                transfer(jsonFile, xmlFiles);
            }
        }
        System.out.println("Done");
    }

}
