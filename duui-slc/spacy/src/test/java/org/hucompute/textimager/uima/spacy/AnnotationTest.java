package org.hucompute.textimager.uima.spacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import de.tudarmstadt.ukp.dkpro.core.api.metadata.type.DocumentMetaData;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.uima.UIMAException;
import org.apache.uima.cas.CASException;
import org.apache.uima.cas.SerialFormat;
import org.apache.uima.cas.impl.XmiCasSerializer;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.*;
import org.dkpro.core.api.resources.CompressionMethod;
import org.dkpro.core.io.xmi.XmiWriter;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.shaded.com.fasterxml.jackson.core.JsonLocation;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIDockerDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIRemoteDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUISwarmDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIUIMADriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.DUUIAsynchronousProcessor;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.DUUICollectionReader;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.reader.DUUIFileReaderLazy;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;
import org.texttechnologylab.DockerUnifiedUIMAInterface.tools.Data;
import org.texttechnologylab.DockerUnifiedUIMAInterface.tools.LocalSentence;
import org.texttechnologylab.annotation.AnnotationComment;
import org.texttechnologylab.utilities.helper.ArchiveUtils;
import org.xml.sax.SAXException;

import javax.xml.transform.OutputKeys;
import java.io.*;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

public class AnnotationTest {
    static DUUIComposer composer;
    static JCas cas;
    static final Logger logger = LoggerFactory.getLogger(AnnotationTest.class);

    @BeforeAll
    static void beforeAll() throws CompressorException, URISyntaxException, IOException, UIMAException, SAXException {
        composer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(new DUUILuaContext().withJsonLibrary());

        composer.addDriver(new DUUIUIMADriver());
        composer.addDriver(new DUUIRemoteDriver(1000));
//        composer.addDriver(new DUUIDockerDriver(10000).withTimeout(10000));
//        composer.add(
//                new DUUIDockerDriver.Component("docker.texttechnologylab.org/duui-spacy:latest"));

//         corenlp: Manuel
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:9714").withParameter("validate", "false"));
//         stanza: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:9090").withParameter("validate", "false"));
//         crf2o: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:7070"));
//         corenlp: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:6060"));
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:4040").withParameter("validate", "false"));
//         spaCy: Omar
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:5050").withParameter("validate", "false"));
//         spaCy: Daniel
//        composer.add(
//                new DUUIRemoteDriver.Component("http://localhost:6060"));


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
        Path path = Paths.get("/home/raza/Documents/Data/germany2-complete/spacy/typesystem.xml");
        try (OutputStream outputStreamTS = Files.newOutputStream(path)) {
            TypeSystemUtil.typeSystem2TypeSystemDescription(jCas.getTypeSystem()).toXML(outputStreamTS);
        } catch (IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    public void writeToJson(JsonObject jsonArray, String path) throws IOException {
        Gson gson = new Gson();
        try (FileWriter writer = new FileWriter(path)) {
            gson.toJson(jsonArray, writer);
            System.out.println("JSON file created successfully with Gson.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    @Test
    public void viewCas() throws IOException, ResourceInitializationException, CASException {

        String sInputPath = "/home/raza/Documents/Data/Annotations/spacy-corenlp/output_en/";
        File directory = new File(sInputPath);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".xmi.gz"));
        /*Write data to json*/
        JsonArray jsonArraySentences = new JsonArray();
        JsonArray jsonArrayAnnotations = new JsonArray();
        JsonArray sentenceArray = new JsonArray();
        JsonObject root = new JsonObject();

        final int[] corenlpCount = {0};
        final int[] spacyCount = {0};
        final int[] initViewCount = {0};
        if (files != null) {
            for (File gzFile : files){
                File decompressedFile = ArchiveUtils.decompressGZ(new File(String.valueOf(gzFile)));
                decompressedFile.deleteOnExit();
                JCas pCas = JCasFactory.createJCas();
                CasIOUtils.load(new FileInputStream(decompressedFile), pCas.getCas());
                JCas initView = pCas.getView("_InitialView");
                JCas spacyView = pCas.getView("spacy");
                JCas coreNLPView = pCas.getView("corenlp");
                int iter = 0;
                for(Sentence sen: JCasUtil.select(spacyView, Sentence.class)){
                    JsonObject sentenceObj = new JsonObject();
                    sentenceObj.addProperty("sentence_text", sen.getCoveredText());
                    JsonArray tokensArray = new JsonArray();

                    iter++;
                    for(Dependency dep : JCasUtil.selectCovered(Dependency.class, sen)){

                        JsonObject tokenObj = new JsonObject();
                        tokenObj.addProperty("token_text", dep.getCoveredText());
                        if (dep.getDependent().getPos().getCoarseValue() != null){
                            tokenObj.addProperty("POS", dep.getDependent().getPos().getCoarseValue());
                        }else{
                            tokenObj.addProperty("POS", dep.getDependent().getPos().getPosValue());
                        }
                        tokenObj.addProperty("dependency", dep.getDependencyType());
                        tokenObj.addProperty("begin", dep.getBegin());
                        tokenObj.addProperty("end", dep.getEnd());
                        if (dep.getGovernor().getText() != null){
                            tokenObj.addProperty("head", dep.getGovernor().getText());
                            tokenObj.addProperty("head_POS", dep.getGovernor().getPos().getCoarseValue());
                        }
                        tokensArray.add(tokenObj);
                    }
                    sentenceObj.add("tokens", tokensArray);
                    sentenceArray.add(sentenceObj);
//                    System.out.println("Inner loop: "+ iter);
                    if (iter == 100000){
                        root.add("sentences", sentenceArray);
                        writeToJson(root, "/home/raza/Documents/Python/classification/data/view_spacy_en_batch_1.json");
                        break;
                    }
                }


//                List<Sentence> defaultViewSentences = new ArrayList<>(JCasUtil.select(initView, Sentence.class));
//                ArrayList<AnnotationComment> defaultViewAnnotation = new ArrayList<>(JCasUtil.select(initView, AnnotationComment.class));
//                for (int i = 0; i < defaultViewSentences.size() && i < defaultViewAnnotation.size(); i++) {
//                    int sentenceReference = defaultViewSentences.get(i).getAddress();
//                    int sentenceBegin = defaultViewSentences.get(i).getBegin();
//                    int annotationReference = defaultViewAnnotation.get(i).getReference().getAddress();
//
//                    JsonObject sentencesJson = new JsonObject();
//                    sentencesJson.addProperty("sentence", defaultViewSentences.get(i).getCoveredText());
//                    sentencesJson.addProperty("reference", sentenceReference);
//                    sentencesJson.addProperty("beginID", sentenceBegin);
//                    jsonArraySentences.add(sentencesJson);
//
//                    JsonObject labelsJson = new JsonObject();
//                    if (defaultViewAnnotation.get(i).getKey().equalsIgnoreCase("label")){
//                        labelsJson.addProperty("label", defaultViewAnnotation.get(i).getValue());
//                        labelsJson.addProperty("reference", annotationReference);
//                    }
//                    if (defaultViewAnnotation.get(i).getKey().equalsIgnoreCase("tweetID")){
//                        labelsJson.addProperty("tweetID", defaultViewAnnotation.get(i).getValue());
//                        labelsJson.addProperty("reference", annotationReference);
//                    }
//                    jsonArrayAnnotations.add(labelsJson);
//                }

            }
        }
//        writeToJson(root, "/home/raza/Documents/Python/classification/data/view_spacy_en_batch_1.json");
//        writeToJson(jsonArraySentences, "/home/raza/Documents/Python/classification/data/sentences_all.json");
//        writeToJson(jsonArrayAnnotations, "/home/raza/Documents/Python/classification/data/labels_all.json");

    }

    public void archived(){

        //                        System.out.print(iter +". "+ dep.getCoveredText()+ " | ");
//                        System.out.print( dep.getDependent().getText()+ " | ");
//                        System.out.print( dep.getDependencyType()+ " | ");
//                        System.out.print( dep.getDependent().getPos().getPosValue()+ " | ");
//                        System.out.print( dep.getDependent().getPos().getCoarseValue()+ " | ");
//                        System.out.print(dep.getBegin() + "   " + dep.getEnd()+ " | ");
//                        System.out.print("Governor: "+ dep.getGovernor().getText()+ " | ");
//                        System.out.print(dep.getGovernor().getPos().getPosValue()+ " | ");
//                        System.out.print(dep.getGovernor().getPos().getCoarseValue()+ " | ");
//                        System.out.println();


        //        JCasUtil.select(initView, AnnotationComment.class).forEach(ac->{
//            System.out.println(ac.getKey() + "  "+ ac.getValue());
//
//        });
//        JCasUtil.select(spacyView, Sentence.class).forEach(sentence->{
//            System.out.println("\n"+ sentence.getCoveredText());
//            for(Dependency dep : JCasUtil.selectCovered(Dependency.class, sentence)){
//                  System.out.print(dep.getDependencyType()+ " ");
//            }
//            System.out.println();
//            for(Dependency dep : JCasUtil.selectCovered(Dependency.class, sentence)){
//                System.out.print(dep.getDependent().getPos().getCoarseValue()+ " ");
//            }
//        });


//        JCasUtil.select(initView, Sentence.class).forEach(s->{
//            System.out.println(s.getBegin()+"\t"+s.getEnd()+"\t"+s.getCoveredText());
//
//            System.out.println("spaCy\n");
//            StringBuilder sb = new StringBuilder();
//            StringBuilder finalSb = sb;
//            JCasUtil.selectCovered(spacyView, POS.class, s.getBegin(), s.getEnd()).forEach(p->{
//                if(finalSb.length()>0){
//                    finalSb.append(" ");
//                }
//                finalSb.append(p.getCoarseValue());
//            });
//            System.out.println(finalSb.toString());

//            StringBuilder sb2 = new StringBuilder();
//            StringBuilder finalSb2 = sb2;
//
//            System.out.println("\ncoreNLP\n");
//            JCasUtil.selectCovered(coreNLPView, POS.class, s.getBegin(), s.getEnd()).forEach(p->{
//                if(finalSb2.length()>0){
//                    finalSb2.append(" ");
//                }
//                finalSb2.append(p.getCoarseValue());
//            });
//            System.out.println(finalSb2.toString());
//            System.out.println("==================");
//        });

//        pCas.getViewIterator().forEachRemaining(v->{
//            System.out.println(v.getViewName());
//            System.out.println(JCasUtil.selectAll(v).size());
//        });

        //                pCas.getViewIterator().forEachRemaining(v->{
////                   System.out.println(v.getViewName());
//                   if (Objects.equals(v.getViewName(), "corenlp")){ corenlpCount[0] = corenlpCount[0] + JCasUtil.selectAll(v).size();}
//                    if (Objects.equals(v.getViewName(), "spacy")){spacyCount[0] = spacyCount[0] + JCasUtil.selectAll(v).size();}
//                    if (Objects.equals(v.getViewName(), "_InitialView")){ initViewCount[0] = initViewCount[0] + JCasUtil.selectAll(v).size();}
////                   System.out.println(JCasUtil.selectAll(v).size());
//                });
    }

    public static String sanitizeText(String text) {
        StringBuilder sanitized = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Character.isDefined(c) && !Character.isSurrogate(c)) {
                sanitized.append(c);
            }
        }
        return sanitized.toString();
    }
    @Test
    public void spaCyTest() throws Exception {
        String pathToFile = "/home/raza/Documents/Python/classification/data/negation_dataset_en.json";
        int iWorker = 3;

        String sInputPath = "/home/raza/Documents/Data/germany2-complete/spacy/negation_dataset_en_annotated/";
        String sOutputPath = "/home/raza/Documents/Data/germany2-complete/spacy/";
        String sSuffix = "xmi";

        DUUICollectionReader pReader = new DUUIFileReaderLazy(sInputPath, sSuffix, sOutputPath, ".xmi.gz", 1);

        // Asynchroner reader für die Input-Dateien
        DUUIAsynchronousProcessor pProcessor = new DUUIAsynchronousProcessor(pReader);
        new File(sOutputPath).mkdir();

        DUUILuaContext ctx = new DUUILuaContext().withJsonLibrary();

        // Instanziierung des Composers, mit einigen Parametern
        DUUIComposer composer = new DUUIComposer()
                .withSkipVerification(true)     // wir überspringen die Verifikation aller Componenten =)
                .withLuaContext(ctx)            // wir setzen den definierten Kontext
                .withWorkers(iWorker);         // wir geben dem Composer eine Anzahl an Threads mit.

        DUUIDockerDriver docker_driver = new DUUIDockerDriver();
        DUUISwarmDriver swarm_driver = new DUUISwarmDriver();
        DUUIUIMADriver uima_driver = new DUUIUIMADriver()
                .withDebug(true);

        // Hinzufügen der einzelnen Driver zum Composer
        composer.addDriver(docker_driver, uima_driver, swarm_driver);  // remote_driver und swarm_driver scheint nicht benötigt zu werden.

//        composer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/duui-spacy-en_core_web_sm:0.4.3")
//                .withScale(iWorker).withImageFetching()
//                .withTargetView("spacy")
//                .build().withTimeout(3600));

        int iter = 0;
//        ObjectMapper mapper = new ObjectMapper();
//        try {
//            Data data = mapper.readValue(new File(pathToFile), Data.class);
//            for (Sentence sentence : data.getSentences()) {
//                String input = sanitizeText(sentence.getSentence());
//                cas.setDocumentText(input);
//                cas.setDocumentLanguage("en");
//                composer.run(cas);
//                String[] fileName = sentence.getId().split("\\.");
//                Path path = Paths.get("/home/raza/Documents/Data/germany2-complete/corenlp/negation_dataset_de/"+fileName[0]+".xmi.gz");
////                dumpXMI(cas, path);
//                cas.reset();
//                iter++;
//                if (iter>5){break;}
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        System.out.println(iter);
    }
    @Test
    public void createTypesystem() throws Exception {
        JCas jCas = JCasFactory.createJCas();
        Path path = Paths.get("/home/raza/Documents/Data/germany2-complete/spacy/typesystem.xml");

        try (OutputStream outputStreamTS = Files.newOutputStream(path)) {
            TypeSystemUtil.typeSystem2TypeSystemDescription(jCas.getTypeSystem()).toXML(outputStreamTS);
        }
    }

    @Test
    public void spaCyTestOneDocument() throws Exception {
//        String pathToFile =  "/home/raza/Documents/Python/classification/data/negation_dataset_en.json";
        String pathToFile =  "/home/raza/Documents/Python/classification/data/negation_dataset_de.json";

        int iter = 0;
        JCas pCas = JCasFactory.createJCas();
        ObjectMapper mapper = new ObjectMapper();
        Map<String, String> mapIDs = new HashMap<>();
        int iCut = 1000;

        try {
            Data data = mapper.readValue(new File(pathToFile), Data.class);
//            DUUICollectionReader data = new DUUIFileReaderLazy(pathToFile, ".json", 1);
            StringBuilder sb = new StringBuilder();
            AtomicInteger iCount = new AtomicInteger(0);

            for (LocalSentence localSentence : data.getSentences()) {

                if(sb.length()>0){
                    sb.append(" ");
                }
                String newString = "";
                newString = sanitizeText(localSentence.getSentence());

                String xml11pattern = "[^"
                        + "\u0001-\uD7FF"
                        + "\uE000-\uFFFD"
                        + "\\x{10000}-\\x{10FFFF}"
                        + "]+";

                Pattern characterFilter = Pattern.compile(xml11pattern);
//                Pattern characterFilter = Pattern.compile("[^\\x{00}-\\x{024F}]");
                newString = newString.replaceAll(xml11pattern," ");
                newString = characterFilter.matcher(newString).replaceAll(" ");

                sb.append(newString);
                int iStart = sb.lastIndexOf(newString);
                int iEnd = iStart+newString.length();

                de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence pSentence = new de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence(pCas, iStart, iEnd);
                pSentence.addToIndexes();
                AnnotationComment pComment = new AnnotationComment(pCas);
                pComment.setKey("label");
                pComment.setValue(localSentence.getLabel());
                pComment.setReference(pSentence);
                pComment.addToIndexes();

                AnnotationComment pComment2 = new AnnotationComment(pCas);
                pComment2.setKey("tweetID");
                pComment2.setValue(localSentence.getId());
                pComment2.setReference(pSentence);
                pComment2.addToIndexes();
                mapIDs.put(localSentence.getId(), iStart+"_"+iEnd);
                iCount.getAndIncrement();

                if(iCount.get()%iCut==0){
                    pCas.setDocumentText(sb.toString());
                    pCas.setDocumentLanguage("de");

                    JCas spacy = pCas.createView("spacy");
                    spacy.setDocumentText(sb.toString());
                    spacy.setDocumentLanguage("de");

                    JCas corenlp = pCas.createView("corenlp");
                    corenlp.setDocumentText(sb.toString());
                    corenlp.setDocumentLanguage("de");


                    DocumentMetaData dmd = new DocumentMetaData(pCas);
                    dmd.setDocumentId(iCount+"");
                    dmd.setDocumentUri("/opt/files/"+iCount);
                    dmd.setDocumentBaseUri("/opt/files");
                    dmd.addToIndexes();
                    CasIOUtils.save(pCas.getCas(), new FileOutputStream(new File("/home/raza/Documents/Data/germany2-complete/spacy/negation_dataset_de_xmi/"+iCount+".xmi")), SerialFormat.XMI_1_1_PRETTY);
                    pCas.reset();
                    sb = new StringBuilder();
                }
            }
            if(data.getSentences().size()%iCut!=0){
                pCas.setDocumentText(sb.toString());
                pCas.setDocumentLanguage("de");

                JCas spacy = pCas.createView("spacy");
                spacy.setDocumentText(sb.toString());
                spacy.setDocumentLanguage("de");

                JCas corenlp = pCas.createView("corenlp");
                corenlp.setDocumentText(sb.toString());
                corenlp.setDocumentLanguage("de");

                DocumentMetaData dmd = new DocumentMetaData(pCas);
                dmd.setDocumentId(iCount+"");
                dmd.setDocumentUri("/opt/files/"+iCount);
                dmd.setDocumentBaseUri("/opt/files");
                dmd.addToIndexes();
                CasIOUtils.save(pCas.getCas(), new FileOutputStream(new File("/home/raza/Documents/Data/germany2-complete/spacy/negation_dataset_de_xmi/"+iCount+".xmi")), SerialFormat.XMI_1_1_PRETTY);
                pCas.reset();
                sb = new StringBuilder();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        System.out.println(iter);
    }
    @Test
    public void twitterParser() throws Exception {

        int iWorker = 3;

        String sInputPath = "/home/raza/Documents/Data/germany2-complete/spacy/negation_dataset_en_2/";
        String sOutputPath = "/home/raza/Documents/Data/germany2-complete/spacy/negation_dataset_en_annotated_2/";
        new File(sOutputPath).mkdir();
        String sSuffix = "xmi";

        DUUICollectionReader pReader = new DUUIFileReaderLazy(sInputPath, sSuffix, sOutputPath, ".xmi.gz", 1);

        // Asynchroner reader für die Input-Dateien
        DUUIAsynchronousProcessor pProcessor = new DUUIAsynchronousProcessor(pReader);
        new File(sOutputPath).mkdir();

        DUUILuaContext ctx = new DUUILuaContext().withJsonLibrary();

        // Instantiation of the composer, with some parameters
        DUUIComposer composer = new DUUIComposer()
                .withSkipVerification(true)     // we skip the verification of all components
                .withLuaContext(ctx)            // we set the defined context
                .withWorkers(iWorker);         // we give the composer a number of threads.

        DUUIDockerDriver docker_driver = new DUUIDockerDriver();
        DUUISwarmDriver swarm_driver = new DUUISwarmDriver();
        DUUIUIMADriver uima_driver = new DUUIUIMADriver()
                .withDebug(true);

        // Adding the individual drivers to the composer
        composer.addDriver(docker_driver, uima_driver, swarm_driver);  // remote_driver and swarm_driver don't seem to be needed.

//        composer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/duui-spacy-en_core_web_sm:0.4.3")
//                .withScale(iWorker).withImageFetching()
//                .withTargetView("spacy")
//                .build().withTimeout(3600));

//        composer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/duui-slc-corenlp/cu124:latest")
//                .withParameter("validate", "false")
//                .withScale(iWorker).withImageFetching()
//                .withTargetView("corenlp")
//                .build().withTimeout(3600));

//        composer.add(new DUUIDockerDriver.Component("docker.texttechnologylab.org/duui-slc-stanza/cu124:latest").withImageFetching()
//                .withScale(iWorker)
//                .withTargetView("stanza")
//                .build().withTimeout(3600));

        composer.add(new DUUIUIMADriver.Component(createEngineDescription(XmiWriter.class,
                XmiWriter.PARAM_TARGET_LOCATION, sOutputPath,
                XmiWriter.PARAM_PRETTY_PRINT, true,
                XmiWriter.PARAM_OVERWRITE, true,
                XmiWriter.PARAM_VERSION, "1.1",
                XmiWriter.PARAM_COMPRESSION, CompressionMethod.GZIP
        )).withScale(iWorker).build());

        composer.run(pProcessor, "twitter");
    }

}
