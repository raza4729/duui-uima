package org.hucompute.textimager.uima.spacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import org.apache.uima.cas.CAS;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.texttechnologylab.utilities.helper.ArchiveUtils;
import org.apache.commons.lang3.tuple.Triple;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class GsonWriter {

    private static final String FILE_PATH = "/home/raza/Documents/Data/Annotations/Json-Data/SpacyView/spacy_view_annotated_en.json";
    private static final String sInputPath = "/home/raza/Documents/Data/Annotations/DUUI-Annotations/outputs_en/";
    private static final String OUTPUT_FILE_PREFIX = "/home/raza/Documents/Data/Annotations/Json-Data/en/CorenlpView/batch";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final int BATCH_SIZE = 100;  // Adjust batch size as needed
    private static JsonArray batchArray = new JsonArray();
    private static int fileIndex = 0;
    private static String fileName;

    public static void main(String[] args) throws ResourceInitializationException, IOException, CASException {
        File directory = new File(sInputPath);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".xmi.gz"));

        JCas pCas = JCasFactory.createJCas();
        int iter = 0;
        if (files != null) {
            for (File gzFile : files) {
                try {
                    System.out.println("\nProcessing " + gzFile.getName());
                    fileName = gzFile.getName().split("\\.")[0];
                    iter++;
                    File decompressedFile = ArchiveUtils.decompressGZ(new File(String.valueOf(gzFile)));
                    decompressedFile.deleteOnExit();
                    pCas.reset();
                    CasIOUtils.load(new FileInputStream(decompressedFile), pCas.getCas());
                    JCas initView = pCas.getView("_InitialView");
                    JCas spacyView = pCas.getView("spacy");
                    JCas coreNLPView = pCas.getView("corenlp");
                    Triple<String, Integer, Integer>[] initCasOffsets = getOffsets(initView);
                    createSentenceObject(coreNLPView, initCasOffsets);

                }catch (Exception e) {
                    System.err.println("An error occurred during processing: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            System.out.println("\nTotal no. of files processed: " + iter);
        } else {
            System.out.println("No file found");
        }
    }

    private static Triple<String, Integer, Integer>[] getOffsets(JCas view){
        int iter = 0;
        Collection<Sentence> sentence = JCasUtil.select(view, Sentence.class);
        Triple<String, Integer, Integer>[] sentenceData = new Triple[sentence.size()];
        for (Sentence sen : JCasUtil.select(view, Sentence.class)) {
            sentenceData[iter] = Triple.of(sen.getCoveredText(), sen.getBegin(), Math.min(sen.getEnd(), view.getDocumentText().length()));
            iter++;
        }
        return sentenceData;
    }

    private static void createSentenceObject(JCas cas, Triple<String, Integer, Integer>[] sentenceOffsets) throws IOException {
        int iter = 0;
//        List<String> negations = Arrays.asList("nein", "nicht", "nie", "niemand", "nichts", "keiner", "keine", "kein",
//                "nirgends", "ohne", "weder", "noch", "niemals",
//                "nichtsdestotrotz", "werden nicht", "soll nicht");
        List<String> negations = Arrays.asList("no", "not", "never", "none", "nobody", "nothing", "neither", "nowhere", "without",
                "nor", "can't", "cannot", "won't", "wouldn't", "isn't", "aren't", "wasn't", "weren't", "don't", "doesn't", "didn't",
                "hasn't", "haven't", "hadn't", "shall't", "mightn't", "mustn't", "couldn't");
        JsonObject sentenceObject = new JsonObject();
        for (Triple<String, Integer, Integer> triple : sentenceOffsets){
            iter++;
            int sentBegin = triple.getMiddle();
            int sentEnd = triple.getRight();
            sentenceObject.addProperty("sentence_text", triple.getLeft());
            JsonArray offsetArray = new JsonArray();
            offsetArray.add(sentBegin);
            offsetArray.add(sentEnd);
            sentenceObject.add("sentence_offset", offsetArray);
            int addOnce = 0;
            JsonArray tokensArray = new JsonArray();
            for (Dependency dep : JCasUtil.selectCovered(cas, Dependency.class, sentBegin, sentEnd))
//                for (Dependency dep : JCasUtil.selectAt(cas, Dependency.class, sentBegin, sentEnd))
                {
                JsonObject tokenObj = new JsonObject();
                tokenObj.addProperty("token_text", dep.getCoveredText());
                if (dep.getDependent().getPos().getCoarseValue() != null) {
                    tokenObj.addProperty("POS", dep.getDependent().getPos().getCoarseValue());
                } else {
                    tokenObj.addProperty("POS", dep.getDependent().getPos().getPosValue());
                }
                tokenObj.addProperty("dependency", dep.getDependencyType());
                tokenObj.addProperty("begin", dep.getBegin());
                tokenObj.addProperty("end", dep.getEnd());
                if (dep.getGovernor().getText() != null) {
                    tokenObj.addProperty("head", dep.getGovernor().getText());
                    tokenObj.addProperty("head_POS", dep.getGovernor().getPos().getCoarseValue());
                }
                if (addOnce == 0){
                    if (isNegation(dep.getCoveredText(), negations)) {sentenceObject.addProperty("label", "neg");}
                    else{sentenceObject.addProperty("label", "non-neg");}
                    addOnce = 1;
                }
                tokensArray.add(tokenObj);
            }
            sentenceObject.add("tokens", tokensArray);
//            batchArray.add(sentenceObject);
            sentenceObject = new JsonObject();
        }
//        appendToBatch(iter);
        System.out.println("Total no. of sentences in each file: " + iter);
    }

    private static void createSentenceObject(JCas view) throws IOException {
        int iter = 0;
//        List<String> negations = Arrays.asList("no", "not", "never", "none", "nobody", "nothing", "neither", "nowhere", "without",
//                "nor", "can't", "cannot", "won't", "wouldn't", "isn't", "aren't", "wasn't", "weren't", "don't", "doesn't", "didn't",
//                "hasn't", "haven't", "hadn't", "shall't", "mightn't", "mustn't", "couldn't");
        List<String> negations = Arrays.asList("nein", "nicht", "nie", "niemand", "nichts", "keiner", "keine", "kein",
                "nirgends", "ohne", "weder", "noch", "niemals",
                "nichtsdestotrotz", "werden nicht", "soll nicht");
        JsonObject sentenceObject = new JsonObject();

        for (Sentence sen : JCasUtil.select(view, Sentence.class)) {
            iter++;
            sentenceObject.addProperty("sentence_text", sen.getCoveredText());
            JsonArray offsetArray = new JsonArray();
            offsetArray.add(sen.getBegin());
            offsetArray.add(sen.getEnd());
            sentenceObject.add("sentence_offset", offsetArray);
            int addOnce = 0;
            JsonArray tokensArray = new JsonArray();
            for (Dependency dep : JCasUtil.selectCovered(Dependency.class, sen)) {
                JsonObject tokenObj = new JsonObject();
                tokenObj.addProperty("token_text", dep.getCoveredText());
                if (dep.getDependent().getPos().getCoarseValue() != null) {
                    tokenObj.addProperty("POS", dep.getDependent().getPos().getCoarseValue());
                } else {
                    tokenObj.addProperty("POS", dep.getDependent().getPos().getPosValue());
                }
                tokenObj.addProperty("dependency", dep.getDependencyType());
                tokenObj.addProperty("begin", dep.getBegin());
                tokenObj.addProperty("end", dep.getEnd());
                if (dep.getGovernor().getText() != null) {
                    tokenObj.addProperty("head", dep.getGovernor().getText());
                    tokenObj.addProperty("head_POS", dep.getGovernor().getPos().getCoarseValue());
                }
                if (addOnce == 0){
                if (isNegation(dep.getCoveredText(), negations)) {sentenceObject.addProperty("label", "neg");}
                else{sentenceObject.addProperty("label", "non-neg");}
                addOnce = 1;
                }
                tokensArray.add(tokenObj);
            }
            sentenceObject.add("tokens", tokensArray);
            batchArray.add(sentenceObject);
            sentenceObject = new JsonObject();
        }
        appendToBatch(iter);
        //String prettyJson = gson.toJson(sentenceObject);
        System.out.println("Total no. of sentences in each file: " + iter);
    }

    private static void appendToJsonArray(JsonObject newObject) throws IOException {
        JsonObject jsonFileContent;
        JsonArray sentencesArray;
        if (Files.exists(Paths.get(FILE_PATH))) {
            try (FileReader reader = new FileReader(FILE_PATH)) {
                jsonFileContent = JsonParser.parseReader(reader).getAsJsonObject();
                sentencesArray = jsonFileContent.getAsJsonArray("Sentences");
            }
        } else {
            // Create new JSON structure if the file doesn’t exist
            jsonFileContent = new JsonObject();
            sentencesArray = new JsonArray();
            jsonFileContent.add("Sentences", sentencesArray);
        }
        sentencesArray.add(newObject);
        // Write back to file
        try (FileWriter writer = new FileWriter(FILE_PATH)) {
            gson.toJson(jsonFileContent, writer);
            System.out.println("Successfully dumped.");
        }
    }

//    public static void appendToBatch(JsonObject newObject) throws IOException {
    private static void appendToBatch(int iter) throws IOException {
        // Add the new object to the batch array
        //batchArray.add(newObject);
        // When the batch reaches the specified size, write it to a separate file
        if (batchArray.size() >= iter) {
            writeBatchToFile();
            batchArray = new JsonArray();  // Reset the batch array for the next file
            fileIndex++;  // Increment file index for the next batch
        }
    }

    private static void writeBatchToFile() throws IOException {
        if (batchArray.isEmpty()) {
            return;  // No data to write
        }

        // Define the output file name for each batch
        String outputFileName = OUTPUT_FILE_PREFIX + "_" + fileName + ".json";
        try (FileWriter writer = new FileWriter(outputFileName)) {
            JsonObject batchObject = new JsonObject();
            batchObject.add("Sentences", batchArray);
            gson.toJson(batchObject, writer);
            System.out.println("Written batch to " + outputFileName);
        }
    }

    private static boolean isNegation(String token, List<String> negations) {
        // Checks if the token is an exact match with any negation term
        return negations.stream().anyMatch(neg -> neg.equalsIgnoreCase(token));
    }
}
