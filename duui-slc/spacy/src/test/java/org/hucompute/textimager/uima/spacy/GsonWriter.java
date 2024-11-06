package org.hucompute.textimager.uima.spacy;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasIOUtils;
import org.texttechnologylab.utilities.helper.ArchiveUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

public class GsonWriter {

    private static final String FILE_PATH = "D:\\data\\Annotated_en\\spacy_view_annotated_en.json";
    private static final String sInputPath = "D:\\data\\outputs_en";
    private static final String OUTPUT_FILE_PREFIX = "D:\\data\\Annotated_en\\corenlp_View_en\\batched_data";
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final int BATCH_SIZE = 100;  // Adjust batch size as needed
    private static JsonArray batchArray = new JsonArray();
    private static int fileIndex = 0;

    public static void main(String[] args) throws ResourceInitializationException, IOException, CASException {
        File directory = new File(sInputPath);
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".xmi.gz"));
        int iter = 0;
        if (files != null) {
            for (File gzFile : files) {
                iter++;
                File decompressedFile = ArchiveUtils.decompressGZ(new File(String.valueOf(gzFile)));
                decompressedFile.deleteOnExit();
                JCas pCas = JCasFactory.createJCas();
                CasIOUtils.load(new FileInputStream(decompressedFile), pCas.getCas());
                //JCas initView = pCas.getView("_InitialView");
                JCas spacyView = pCas.getView("spacy");
                //JCas coreNLPView = pCas.getView("corenlp");
                JsonObject sentenceObject = createSentenceObject(spacyView);
                System.out.println(sentenceObject.get("sentence_text"));
                break;
                /*try {
                    //appendToJsonArray(sentenceObject);
                    appendToBatch(sentenceObject);
                } catch (IOException e) {
                    e.printStackTrace();
                }*/
            }
            //System.out.println(iter);
        }
    }

    private static JsonObject createSentenceObject(JCas view) {
        JsonObject sentenceObject = new JsonObject();
        for (Sentence sen : JCasUtil.select(view, Sentence.class)) {
            sentenceObject.addProperty("sentence_text", sen.getCoveredText());
            JsonArray offsetArray = new JsonArray();
            offsetArray.add(sen.getBegin());
            offsetArray.add(sen.getEnd());
            sentenceObject.add("sentence_offset", offsetArray);
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
                tokensArray.add(tokenObj);
            }
            sentenceObject.add("tokens", tokensArray);
        }
        //String prettyJson = gson.toJson(sentenceObject);
        //(System.out.println(prettyJson);
        return sentenceObject;
    }

    public static void appendToJsonArray(JsonObject newObject) throws IOException {
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

    public static void appendToBatch(JsonObject newObject) throws IOException {
        // Add the new object to the batch array
        batchArray.add(newObject);
        // When the batch reaches the specified size, write it to a separate file
        //if (batchArray.size() >= BATCH_SIZE) {
            writeBatchToFile();
            batchArray = new JsonArray();  // Reset the batch array for the next file
            fileIndex++;  // Increment file index for the next batch
        //}
    }

    private static void writeBatchToFile() throws IOException {
        if (batchArray.isEmpty()) {
            return;  // No data to write
        }

        // Define the output file name for each batch
        String outputFileName = OUTPUT_FILE_PREFIX + "_" + fileIndex + ".json";
        try (FileWriter writer = new FileWriter(outputFileName)) {
            JsonObject batchObject = new JsonObject();
            batchObject.add("Sentences", batchArray);
            gson.toJson(batchObject, writer);
            System.out.println("Written batch to " + outputFileName + "\n" + batchArray.size());
        }
    }
}
