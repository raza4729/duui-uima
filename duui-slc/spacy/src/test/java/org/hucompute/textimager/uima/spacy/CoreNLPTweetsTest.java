package org.hucompute.textimager.uima.spacy;


import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Paragraph;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.Sofa;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.apache.uima.util.TypeSystemUtil;
import org.apache.uima.util.XMLSerializer;
import org.apache.uima.util.XmlCasSerializer;
import org.dkpro.core.io.text.TextReader;
import org.dkpro.core.io.xmi.XmiWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
//import org.junit.platform.commons.logging.LoggerFactory;
import org.testcontainers.shaded.org.apache.commons.io.IOUtils;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIRemoteDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIUIMADriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;
import org.texttechnologylab.DockerUnifiedUIMAInterface.tools.AnnotationDropper;
import org.texttechnologylab.annotation.DocumentAnnotation;
import org.texttechnologylab.annotation.Language;
import org.xml.sax.SAXException;

import java.io.*;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIUIMADriver;

import javax.xml.transform.OutputKeys;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;
import static org.apache.uima.fit.factory.CollectionReaderFactory.createReaderDescription;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

public class CoreNLPTweetsTest {
    static DUUIComposer composer;
    static JCas cas;
    static final Logger logger = LoggerFactory.getLogger(CoreNLPTweetsTest.class);

    @BeforeAll
    static void beforeAll() throws CompressorException, URISyntaxException, IOException, UIMAException, SAXException {
        composer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(new DUUILuaContext().withJsonLibrary());

        composer.addDriver(new DUUIUIMADriver());
        composer.addDriver(new DUUIRemoteDriver(1000));
        composer.add(
                new DUUIRemoteDriver.Component("http://localhost:8080"));
        /*Following script is used to drop unnecessary annotations from the cas view*/
//        composer.add(new DUUIUIMADriver.Component(createEngineDescription(
//                AnnotationDropper.class,
//                AnnotationDropper.PARAM_TYPES_TO_RETAIN,
//                new String[] {
//                        Sofa._TypeName,
//                        DocumentAnnotation._TypeName,
//                        DocumentMetaData._TypeName,
//                        Sentence._TypeName,
//                        Paragraph._TypeName
//                }
//        )));

//         corenlp: Manuel
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:9714"));
//         stanza: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:9090"));
//         crf2o: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:7070"));
//         corenlp: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:6060"));


        /*Used to run the duui component from docker repo*/
//        composer.addDriver(new DUUIDockerDriver().withTimeout(10000));
//        composer.add(
//                new DUUIDockerDriver.Component("duui-slc-corenlp/cu124:0.0.1"));
        cas = JCasFactory.createJCas();
        System.out.println("----Before All----");
    }

    @AfterAll
    static void afterAll() throws UnknownHostException {
        composer.shutdown();
        System.out.println("----After All----");
    }

    @AfterEach
    public void afterEach() throws IOException, SAXException {
        composer.resetPipeline();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        XmlCasSerializer.serialize(cas.getCas(), null, stream);
//        System.out.println(stream.toString(StandardCharsets.UTF_8));
        cas.reset();
    }

    public void collectionCounter(List<String> stringList){
        // Create a map to store the count of each string
        Map<String, Integer> stringCountMap = new HashMap<>();

        // Iterate over the list
        for (String str : stringList) {
            // If the string is already in the map, increment its count
            if (stringCountMap.containsKey(str)) {
                stringCountMap.put(str, stringCountMap.get(str) + 1);
            } else {
                // If the string is not in the map, add it with a count of 1
                stringCountMap.put(str, 1);
            }
        }
        System.out.println(stringCountMap);
    }

    public void calculateNegDistribution(String[][] data, List<String> negationsDe){

        Map<String, Integer> dictionary = new HashMap<>();

        for (String key : negationsDe) {
            dictionary.put(key, 0);
        }
        int iter = 0;
        for(String[] sent: data){
            String str = Arrays.toString(sent).toLowerCase();
            for(String key: dictionary.keySet()){
                if(str.contains(key)){
                    int counter = dictionary.get(key);
                    dictionary.put(key, counter+1);
                }
            }
        }
        System.out.println(dictionary);
    }

    public void writeToCsv(String[][] data){
        String csvFile = "/home/staff_homes/raza/Documents/Data/output/data.csv";

        try (CSVPrinter printer = new CSVPrinter(new FileWriter(csvFile), CSVFormat.DEFAULT
                .withHeader("Sentence", "Negator", "Negation Dep..", "Negation PoS"))) {

            for (String[] row : data) {
                printer.printRecord((Object[]) row); // Cast to Object[] to handle String[]
            }

            System.out.println("CSV file created successfully!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String[][] appendRow(String[][] original, String[] newRow) {
        int originalLength = original.length;
        String[][] newArray = new String[originalLength + 1][]; // New array with one extra row

        // Copy existing rows
        for (int i = 0; i < originalLength; i++) {
            newArray[i] = original[i];
        }
        // Add the new row
        newArray[originalLength] = newRow;

        return newArray;
    }

    public void dumpXMI(JCas jCas, Path outputXmiFile){
        try(GZIPOutputStream outputStream = new GZIPOutputStream(Files.newOutputStream(outputXmiFile))) {
            XMLSerializer xmlSerializer = new XMLSerializer(outputStream, true);
            xmlSerializer.setOutputProperty(OutputKeys.VERSION, "1.1");
            xmlSerializer.setOutputProperty(OutputKeys.ENCODING, StandardCharsets.UTF_8.toString());
            XmiCasSerializer xmiCasSerializer = new XmiCasSerializer(null);
            xmiCasSerializer.serialize(jCas.getCas(), xmlSerializer.getContentHandler());
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
        /*Following code is used to export XML type system */
//        try (OutputStream outputStreamTS = Files.newOutputStream(outputTsFile)) {
//            TypeSystemUtil.typeSystem2TypeSystemDescription(jCas.getTypeSystem()).toXML(outputStreamTS);
//        } catch (IOException | SAXException e) {
//            throw new RuntimeException(e);
//        }
    }
    @Test
    public void DPCoreNLPTweetsTest() throws Exception {

        String dataPath = "/home/staff_homes/raza/Documents/Data/germany2/text/en/";
        File dir = new File(dataPath);
        List<String> tweetsList = new ArrayList<>(List.of());
        List<String>  idlst = new ArrayList<>(List.of());
//        List<String> negationsDe = Arrays.asList("nicht", "nichts", "kein", "keine", "keinen", "noch", "nie", "niemand", "noch nicht", "nie mehr", "ohne", "keineswegs", "auf keinen");
        List<String> negationsDe = Arrays.asList("not", "no", "nothing", "nobody", "none", "nowhere", "neither", "nor", "never", "naught", "nary", "no one", "nothing", "nobody", "nowhere");

        int iter = 0;
        int nofNegations = 0;
        if(dir.isDirectory()) {
            for(File file : Objects.requireNonNull(dir.listFiles())) {
                if(file.isFile()) {
                    try (FileInputStream inputStream = new FileInputStream(dataPath+file.getName())) {
                        String tweet = IOUtils.toString(inputStream);
                        /*get the tweets that contain negation keywords defined above*/
                        for (String neg: negationsDe){
                            String regex = "\\b" + Pattern.quote(neg) + "\\b";
                            Pattern pattern = Pattern.compile(regex);
                            Matcher matcher = pattern.matcher(tweet);
                            if(matcher.find()) {
                                tweetsList.add(tweet);
                                idlst.add(file.getName());
                                nofNegations++;
                            }
                        }
                        /*limits the loop to run for only 100 tweets*/
//                        if (iter > 100){
//                            break;
//                        }
                        iter++;
                    }
                }
            }
        } else {
            logger.info("The path isn't a valid directory.");
        }
        String[] tweetsArray = tweetsList.toArray(new String[0]);
        String text =  String.join(" ", tweetsArray);

        cas.setDocumentText(text);
        cas.setDocumentLanguage("en");

        /*set the offsets of tweet text using Paragraph class*/
        int offset = 0;
        for (String sen : tweetsArray) {
            int len = sen.length();
            Paragraph p = new Paragraph(cas, offset, offset + len);
            p.addToIndexes(cas);
            offset += len + 1;
        }

        composer.run(cas);

        String[][] data = new String[0][];
        List<String> dependencies = new ArrayList<>();
        List<String> pos = new ArrayList<>();

        /*HashMap<String, HashMap<String, Integer>> negOccurances = new HashMap<>();
        // Populate the outer HashMap with the negation words as keys
        for (String negation : negationsDe) {
            // Initialize the inner HashMap for each negation word
            negOccurances.put(negation, new HashMap<>());
        }*/

        for(Sentence sen: JCasUtil.select(cas, Sentence.class)){
            List<String> row = new ArrayList<>();
            for(Dependency dep : JCasUtil.selectCovered(Dependency.class, sen)) {
                for (String neg: negationsDe){
                    if (neg.equalsIgnoreCase(dep.getCoveredText())){
                        row.add(sen.getCoveredText());
                        row.add(dep.getCoveredText());
                        row.add(dep.getDependencyType());
                        row.add(dep.getDependent().getPos().getCoarseValue());

                        dependencies.add(dep.getDependencyType());
                        pos.add(dep.getDependent().getPos().getCoarseValue()); //Dependent is tag of current token

                        String[] stringRow = row.toArray(new String[0]);
                        data =  appendRow(data, stringRow);
                        row.clear();
                    }
//                    System.out.println(dep.getCoveredText());
//                    System.out.println("Dependant "+dep.getDependent().getPos().getCoarseValue());
//                    System.out.println("Governor "+dep.getGovernor().getPos().getCoarseValue());
//                    System.out.println("Dependency "+dep.getDependencyType());
                }
            }
        }
//        writeToCsv(data);
        calculateNegDistribution(data, negationsDe);
//        collectionCounter(dependencies);
//        collectionCounter(pos);
        System.out.println("Total no. of Sentences : " + data.length);
        System.out.println("Total no. of Tweets : " + iter);
        System.out.println("Total no. of Tweets that Include Negations : " + nofNegations);

        FileWriter fileWriter = new FileWriter("/home/staff_homes/raza/Documents/Data/output/dependencies-corenlp-en.txt");
        for (String str : dependencies) {
            fileWriter.write(str + System.lineSeparator());
        }
        fileWriter.close();

        System.out.println("--------------------Finished---------------------------");
    }

    @Test
    public void lineBylineTest() throws Exception {

        String dataPath = "/home/staff_homes/raza/Documents/Data/germany2/text/de/";
        File dir = new File(dataPath);
        String[][] data = new String[0][];
        List<String> negationsDe = Arrays.asList("nein", "nicht", "nichts", "kein", "keine", "keinen", "noch", "nie", "niemand", "noch nicht", "nie mehr", "ohne", "keineswegs", "auf keinen");
//        List<String> negationsDe = Arrays.asList("not", "no", "nothing", "nobody", "none", "nowhere", "neither", "nor", "never", "naught", "nary", "no one", "nothing", "nobody", "nowhere");

        int iter = 0, nofTwtsWithNeg = 0;
        if(dir.isDirectory()) {
            for(File file : Objects.requireNonNull(dir.listFiles())) {
                if(file.isFile()) {
                    try (FileInputStream inputStream = new FileInputStream(dataPath+file.getName())) {
                        String tweet = IOUtils.toString(inputStream);
                        /*get the tweets that contain negation keywords defined above*/
                        for (String neg: negationsDe){
                            String regex = "\\b" + Pattern.quote(neg) + "\\b";
                            Pattern pattern = Pattern.compile(regex);
                            Matcher matcher = pattern.matcher(tweet);
                            if(matcher.find()) {
                                cas.setDocumentText(tweet);
                                cas.setDocumentLanguage("de");
                                composer.run(cas);
                                String fileName = file.getName().split("\\.")[0];
                                Path path = Paths.get("/home/staff_homes/raza/Documents/Data/germany2-xmi/de/spacy/"+fileName+".xmi.gz");
                                dumpXMI(cas, path);
                                cas.reset();
                                nofTwtsWithNeg++;
                            }
                        }
                    }
                }
            }System.out.println("Total no. of Tweets with Negation : " + nofTwtsWithNeg);
        } else {
            logger.info("The path isn't a valid directory.");
        }
    }

    @Test
    public void spaCyTest() throws Exception {
        String dataPath = "/home/staff_homes/raza/Documents/Data/germany2-xmi/de/spacy/";
        File dir = new File(dataPath);
        if(dir.isDirectory()) {
            for(File file : Objects.requireNonNull(dir.listFiles())){
                if(file.isFile()) {
                    System.out.println(file.getName());
                    Path pathToXmi = Paths.get(dataPath+file.getName());
                    CasIOUtils.load(new GZIPInputStream(Files.newInputStream(pathToXmi)),  cas.getCas());
                    Collection<Sentence> sentences = JCasUtil.select(cas, Sentence.class);
                    for(Sentence s:sentences) {
                        System.out.println(s.getCoveredText());
                    }
                }
                break;
            }
        }
        else {logger.info("The path isn't a valid directory.");}

    }


}
