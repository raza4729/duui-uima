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
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.component.ViewTextCopierAnnotator;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.Sofa;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.*;
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
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:8080"));
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
        composer.add(
                new DUUIRemoteDriver.Component("http://localhost:4040").withParameter("validate", "false"));
//         spaCy: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:5050"));

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

    public void printMatrix(String[] rowLabels, String[] colLabels, int[][] matrix){
        System.out.println("2D Matrix (Counts of Negations and Dependency Labels):");

        System.out.print("          "); // For spacing of the row labels
        for (String depLabel : colLabels) {
            System.out.print(String.format("%-6s", depLabel)); // Adjust the width for alignment
        }
        System.out.println();
        for (int i = 0; i < rowLabels.length; i++) {
            System.out.print(String.format("%-11s", rowLabels[i]));
            for (int j = 0; j < colLabels.length; j++) {
                System.out.print(String.format("%-6s", matrix[i][j]));
            }
            System.out.println(); // New line after each row
        }
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
        List<String> negationsDe = Arrays.asList("nein", "nicht", "nichts", "kein", "keine", "keinen", "noch", "nie", "niemand", "noch nicht", "nie mehr", "ohne", "keineswegs", "auf keinen");
//        List<String> negationsDe = Arrays.asList("not", "no", "nothing", "nobody", "none", "nowhere", "neither", "nor", "never", "naught", "nary", "no one", "nothing", "nobody", "nowhere");

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
                            }
                        }
                    }
                }
            }
        } else {
            logger.info("The path isn't a valid directory.");
        }
    }

    @Test
    public void spaCyTest() throws Exception {
//        List<String> negators = Arrays.asList("nein", "nicht", "nichts", "kein", "keine", "keinen", "noch", "nie",
//                "niemand", "noch nicht", "nie mehr", "ohne", "keineswegs", "auf keinen");
        List<String> negators = Arrays.asList("not", "no", "nothing", "nobody", "none", "nowhere", "neither", "nor", "never", "naught", "nary", "no one", "nothing", "nobody", "nowhere");

        String[] negationsDe = negators.toArray(new String[0]);
//        String[] depLabels = {"--", "AC", "AG", "CC", "CD", "CJ", "CM", "CP", "DA", "DM", "EP", "JU", "MO", "NG", "NK",
//                "OA", "OC", "OG", "OP", "PD", "PG", "PH", "PM", "RC", "RE", "RS", "SB", "UC", "VO", "AMS", "APP", "CVC",
//                "DEP", "MNR", "PUNCT", "NMC", "PAR", "PNC", "SBP", "SVP"};

        String[] depLabels = {"--", "CC", "RELCL", "META", "DOBJ", "NSUBJ", "ADVCL", "INTJ", "AUXPASS", "QUANTMOD", "NMOD", "EXPL", "COMPOUND",
                "AMOD", "DATIVE", "CSUBJPASS", "ATTR", "PCOMP", "ACL", "AUX", "DEP", "DET", "NPADVMOD", "PREDET", "OPRD", "CSUBJ",
                "NEG", "PUNCT", "PRT", "AGENT", "NUMMOD", "CASE", "APPOS", "POBJ", "POSS", "PREP", "ACOMP", "CONJ", "PARATAXIS",
                "XCOMP", "CCOMP", "NSUBJPASS", "ADVMOD", "PRECONJ", "MARK"};

        int[][] cooccurMat = new int[negationsDe.length][depLabels.length];

        List<String> dependencies = new ArrayList<>();
        String dataPath = "/home/staff_homes/raza/Documents/Data/germany2-xmi/en/spacy/";
        String[][] data = new String[0][];
        int totalTweets = 0, totalSentences = 0, avgLenSentences = 0, iter = 0;
        File dir = new File(dataPath);
        /*check the given path if it is dir or not*/
        if(dir.isDirectory()) {
            /*loop through the files in the dir: *.xmi files*/
            for(File file : Objects.requireNonNull(dir.listFiles())){
                /*check if it is a valid file*/
                if(file.isFile()) {
                    Path pathToXmi = Paths.get(dataPath+file.getName());
                    /*load the file in the JCas*/
                    CasIOUtils.load(new GZIPInputStream(Files.newInputStream(pathToXmi)),  cas.getCas());
                    /*get the sentences using Sentence class from UIMA*/
                    Collection<Sentence> sentences = JCasUtil.select(cas, Sentence.class);
                    totalTweets ++;
                    for(Sentence s:sentences) {
                        /*filter the sentences that contain predefined negators*/
                        for (String neg: negators){
                            if (s.getCoveredText().contains(neg)){
                                String[] words = s.getCoveredText().split("\\s+");
                                avgLenSentences += words.length;
                                totalSentences++;
                                String[] stringRow = new String[]{s.getCoveredText()};
                                data =  appendRow(data, stringRow);
                                for (Dependency dep : JCasUtil.selectCovered(Dependency.class, s)) {
                                    dependencies.add(dep.getDependencyType());
                                    /*int colIndex = Arrays.asList(depLabels).indexOf(dep.getDependencyType());
                                    int rowIndex = Arrays.asList(negationsDe).indexOf(dep.getCoveredText());
                                    if (colIndex!=-1 && rowIndex!=-1) {
                                        cooccurMat[rowIndex][colIndex]++;
                                    }*/
                                }
                            }
                        }
                    }
//                    break;
                }
            }
        }
        else {logger.info("The path isn't a valid directory.");}
        System.out.println("\nTotal no. of sentences that use negation : " + totalSentences);
        System.out.println("Total no. of Tweets : " + totalTweets);
        System.out.println("Avg. length of sentences : " + avgLenSentences/totalSentences);
        calculateNegDistribution(data, negators);
        Set<String> depSet = new HashSet<>(dependencies);
        System.out.println("Dependencies found :"+depSet);

        FileWriter fileWriter = new FileWriter("/home/staff_homes/raza/Documents/Data/output/spacy/dependencies-spacy-en.txt");
        for (String str : dependencies) {
            fileWriter.write(str + System.lineSeparator());
        }
        fileWriter.close();
//        printMatrix(negationsDe, depLabels, cooccurMat);
    }

    @Test
    public void coreNLPTest() throws Exception {
//        List<String> negators = Arrays.asList("nein", "nicht", "nichts", "kein", "keine", "keinen", "noch", "nie",
//                "niemand", "noch nicht", "nie mehr", "ohne", "keineswegs", "auf keinen");
        List<String> negators = Arrays.asList("not", "no", "nothing", "nobody", "none", "nowhere", "neither", "nor", "never", "naught", "nary", "no one", "nothing", "nobody", "nowhere");


        List<String> dependencies = new ArrayList<>();
        String dataPath = "/home/staff_homes/raza/Documents/Data/germany2-xmi/en/spacy/";
        String[][] data = new String[0][];
        int totalTweets = 0, totalSentences = 0, avgLenSentences = 0, iter = 0;
        File dir = new File(dataPath);
        /*check the given path if it is dir or not*/
        if(dir.isDirectory()) {
            /*loop through the files in the dir: *.xmi files*/
            for(File file : Objects.requireNonNull(dir.listFiles())){
                /*check if it is a valid file*/
                if(file.isFile()) {
                    System.out.println(file.getName());
                    Path pathToXmi = Paths.get(dataPath+file.getName());
                    /*load the file in the JCas*/
                    CasIOUtils.load(new GZIPInputStream(Files.newInputStream(pathToXmi)),  cas.getCas());
                    Collection<Sentence> casSent = JCasUtil.select(cas, Sentence.class);
                    System.out.println("---I'm from original CAS generated from Spacy (Daniel)---");
                    for(Sentence s:casSent) {
                        for (String neg : negators){
                            String regex = "\\b" + neg + "\\b";
                            if (s.getCoveredText().matches(".*"+regex+".*")){
                                System.out.println(neg+"    "+s.getCoveredText());
                                for (Dependency dep : JCasUtil.selectCovered(Dependency.class, s)) {
                                    if(neg.equals(dep.getCoveredText())) {
                                        System.out.println(dep.getCoveredText()+"       "+dep.getDependencyType());
                                    }
                                }
                            }
                        }
                    }System.out.println("---I'm from copied CAS (pCoreNLPCas) from original CAS and the output is from coreNLP---");
                    /*create a copy of an existing view*/
                    JCas pCoreNLPCas = copyView(cas, "_InitialView", "coreNLP");


//                    cas.createView("coreNLP");
//                    JCas pCoreNLPCas = cas.getView("coreNLP");
//                    JCas pEmpty = JCasFactory.createJCas();
//                    CasCopier.copyCas(cas.getView("_InitialView").getCas(), pEmpty.getCas(), true);
//                    CasCopier.copyCas(pEmpty.getCas(), pCoreNLPCas.getCas().getView("coreNLP"), false);

                    /*get the sentences using Sentence class from UIMA*/
                    Collection<Sentence> pCasSent = JCasUtil.select(pCoreNLPCas, Sentence.class);
                    totalTweets ++;
                    Set<String> sentenceSet = new HashSet<>(0);
                    for(Sentence s:pCasSent) {
                        /*filter the sentences that contain predefined negators*/
                        for (String neg: negators){
                            String regex = "\\b" + neg + "\\b";
                            if (s.getCoveredText().matches(".*"+regex+".*")){
                                  sentenceSet.add(s.getBegin()+"-"+s.getEnd());
                            }
                        }
                    }
                    /*remove the annotations from previous sofa*/
                    Set<Annotation> removals = new HashSet<>(0);
                    JCasUtil.select(pCoreNLPCas, Sentence.class).stream().filter(s->{
                        String sValue = s.getBegin()+"-"+s.getEnd();
                        return !sentenceSet.contains(sValue);
                    }).forEach(s->{
                        removals.add(s);
                    });
                    JCasUtil.select(pCoreNLPCas, Dependency.class).stream().forEach(s->{
                        removals.add(s);
                    });
                    removals.stream().forEach(a->{
                        a.removeFromIndexes();
                    });

                    composer.run(pCoreNLPCas);

                    for(Sentence s:pCasSent) {
                        /*filter the sentences that contain predefined negators*/
                        for (String neg: negators){
                            String regex = "\\b" + neg + "\\b";
                            if (s.getCoveredText().matches(".*"+regex+".*")){
//                                String[] words = s.getCoveredText().split("\\s+");
//                                avgLenSentences += words.length;
//                                totalSentences++;
//                                String[] stringRow = new String[]{s.getCoveredText()};
//                                data =  appendRow(data, stringRow);
                                System.out.println(neg+"     "+s.getCoveredText());
                                for (Dependency dep : JCasUtil.selectCovered(Dependency.class, s)) {
                                    dependencies.add(dep.getDependencyType());
                                    if(neg.equals(dep.getCoveredText())) {
                                        System.out.println(dep.getCoveredText()+"       "+dep.getDependencyType()+"\n");
                                    }
                                }
                            }
                        }
                    }
                    if(iter>3){
                    break;}
                    iter++;
                }
            }
        }

        else {logger.info("The path isn't a valid directory.");}
        /*System.out.println("\nTotal no. of sentences that use negation : " + totalSentences);
        System.out.println("Total no. of Tweets : " + totalTweets);
        System.out.println("Avg. length of sentences : " + avgLenSentences/totalSentences);
        calculateNegDistribution(data, negators);
        Set<String> depSet = new HashSet<>(dependencies);
        System.out.println("Dependencies found :"+depSet);

        FileWriter fileWriter = new FileWriter("/home/staff_homes/raza/Documents/Data/output/corenlp/dependencies-corenlp-en.txt");
        for (String str : dependencies) {
            fileWriter.write(str + System.lineSeparator());
        }
        fileWriter.close();*/
    }

    public static JCas copyView(JCas pCas, String sSourceView, String sTargetView) throws UIMAException {

        JCas copyCas = JCasFactory.createJCas();
        CasCopier copyTo = new CasCopier(pCas.getCas(), copyCas.getCas());
        copyTo.copyCasView(pCas.getView(sSourceView).getCas(), true);

        CasCopier copyBack = new CasCopier(copyCas.getCas(), pCas.getCas());
        copyBack.copyCasView(copyCas.getView(sSourceView).getCas(), sTargetView, true);

        return pCas.getView(sTargetView);
    }

}
