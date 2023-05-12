package org.hucompute.textimager.uima.ddc.fasttext.service.service;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.tcas.Annotation;
import org.apache.uima.resource.ResourceInitializationException;
import org.hucompute.textimager.uima.type.category.CategoryCoveredTagged;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;

public class FastTextDDC2Service extends FastTextService {
    String disambigTag = "disambigTag";
    String disambigLabelReplace = "__label__";
    String disambigLabelReplaceWith = "__disambig_word__";
    String appendDDC = "";
    String appendDDCVariant = "";
    String ddcClassNamesFilename = "";

    private HashMap<String, HashMap<String, String>> ddcNames;

    FastTextDDC2Service(String fasttextLocation,
                        String fastTextLanguageModelsLabels,
                        boolean lazyLoad,
                        int maxLoaded,
                        boolean useLemma,
                        boolean addPOS,
                        String posmapLocation,
                        boolean removePunct,
                        boolean removeFunctionwords,
                        boolean ignoreMissingLemmaPOS,
                        boolean cutoff,
                        int fasttextK,
                        String tags,
                        String disambigTag,
                        String disambigLabelReplace,
                        String disambigLabelReplaceWith
    ) throws Exception {
        super(
            fasttextLocation,
            fastTextLanguageModelsLabels,
            lazyLoad,
            maxLoaded,
            useLemma,
            addPOS,
            posmapLocation,
            removePunct,
            removeFunctionwords,
            ignoreMissingLemmaPOS,
            cutoff,
            fasttextK,
            tags
        );

        this.disambigTag = disambigTag;
        this.disambigLabelReplace = disambigLabelReplace;
        this.disambigLabelReplaceWith = disambigLabelReplaceWith;

        ddcNames = new HashMap<>();
        if (!appendDDC.isEmpty()) {
            String[] ddcClassNamesFilenames = ddcClassNamesFilename.split(",", -1);
            for (String entry : ddcClassNamesFilenames) {
                String[] entryFields = entry.split(":", 2);
                String lang = entryFields[0].trim();
                String filename = entryFields[1].trim();

                System.out.println("loading ddc class names for language " + lang + " from file " + filename + "...");
                try {
                    ddcNames.put(lang, new HashMap<>());

                    BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(filename), StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        line = line.trim();
                        if (!line.isEmpty()) {
                            String[] fields = line.split("\t");
                            if (fields.length == 2) {
                                String id = fields[0];
                                String name = fields[1];
                                ddcNames.get(lang).put("__label_ddc__" + id.toString(), name);
                            }
                        }
                    }
                    reader.close();
                } catch (IOException e) {
                    throw new ResourceInitializationException(e);
                }
                System.out.println("loaded " + ddcNames.get(lang).size() + " ddc class names.");
            }

            System.out.println("loaded ddc class names for " + ddcNames.size() + " languages.");
        }
    }

    // __label_ddc__480 -> __ddc__480
    private String ddcLabelToFeature(String ddc) {
        return "__ddc__" + ddc.substring(13);
    }

    @Override
    protected void processCoveredWithFastText(JCas jCas, Annotation ref) throws AnalysisEngineProcessException {
        String documentText = getTextWithDisambig(jCas, ref, useLemma, addPOS, removePunct, removeFunctionwords, disambigTag, disambigLabelReplace, disambigLabelReplaceWith, ignoreMissingLemmaPOS);
        //System.out.println(documentText);
        if (documentText.isEmpty()) {
            return;
        }

        if (!appendDDC.isEmpty()) {
            // Add DDC Predictions to the Text...
            StringBuilder ddcsSB = new StringBuilder();
            Collection<CategoryCoveredTagged> ddcCats = JCasUtil.select(jCas, CategoryCoveredTagged.class);
            ArrayList<CategoryCoveredTagged> ddcCatsSorted = new ArrayList<>();
            for (CategoryCoveredTagged ddcCat : ddcCats) {
                if (ddcCat.getTags().equals(appendDDC)) {
                    ddcCatsSorted.add(ddcCat);
                }
            }

            if (!ddcCatsSorted.isEmpty()) {
                Collections.sort(ddcCatsSorted, (r1, r2) -> ((r1.getScore() > r2.getScore()) ? -1 : ((r1.getScore() < r2.getScore()) ? 1 : 0)));

                //System.out.println("ddc variant: " + appendDDCVariant);

                if (appendDDCVariant.equals("top_10x")) {
                    CategoryCoveredTagged topCat = ddcCatsSorted.get(0);

                    //System.out.println("top ddc: " + topCat.getValue());
                    //System.out.println("top score: " + topCat.getScore());

                    for (int i = 0; i < 10; ++i) {
                        ddcsSB.append(" ").append(ddcLabelToFeature(topCat.getValue()));
                        if (ddcNames.containsKey(jCas.getDocumentLanguage())) {
                            if (ddcNames.get(jCas.getDocumentLanguage()).containsKey(topCat.getValue())) {
                                ddcsSB.append(" ").append(ddcNames.get(jCas.getDocumentLanguage()).get(topCat.getValue()));
                            }
                        }
                    }
                } else if (appendDDCVariant.equals("top_scorex")) {
                    CategoryCoveredTagged topCat = ddcCatsSorted.get(0);

                    //System.out.println("top ddc: " + topCat.getValue());
                    //System.out.println("top score: " + topCat.getScore());

                    int reps = Math.max(1, (int)(topCat.getScore()*10));

                    //System.out.println("-> reps: " + reps);

                    for (int i = 0; i < reps; ++i) {
                        ddcsSB.append(" ").append(ddcLabelToFeature(topCat.getValue()));
                        if (ddcNames.containsKey(jCas.getDocumentLanguage())) {
                            if (ddcNames.get(jCas.getDocumentLanguage()).containsKey(topCat.getValue())) {
                                ddcsSB.append(" ").append(ddcNames.get(jCas.getDocumentLanguage()).get(topCat.getValue()));
                            }
                        }
                    }
                } else if (appendDDCVariant.equals("top_text_length_x")) {
                    CategoryCoveredTagged topCat = ddcCatsSorted.get(0);

                    //System.out.println("top ddc: " + topCat.getValue());
                    //System.out.println("top score: " + topCat.getScore());

                    int reps = 10;
                    int textLen = documentText.length();
                    if (textLen < 1000) {
                        reps = Math.max(1, textLen / 100);
                    }

                    //System.out.println("-> reps: " + reps);

                    for (int i = 0; i < reps; ++i) {
                        ddcsSB.append(" ").append(ddcLabelToFeature(topCat.getValue()));
                        if (ddcNames.containsKey(jCas.getDocumentLanguage())) {
                            if (ddcNames.get(jCas.getDocumentLanguage()).containsKey(topCat.getValue())) {
                                ddcsSB.append(" ").append(ddcNames.get(jCas.getDocumentLanguage()).get(topCat.getValue()));
                            }
                        }
                    }
                }
            }

            String ddcs = ddcsSB.toString();
            //System.out.println("Found DDC Predictions: " + ddcs);

            documentText += ddcs;
        }

        // Begin und End setzen, entweder passend zu Ref oder kompletter Text
        int begin = (ref != null ? ref.getBegin() : 0);
        int end = (ref != null ? ref.getEnd() : jCas.getDocumentText().length());

        try {
            // result is a list, there can be more than one model loaded
            ArrayList<FastTextResult> results = input(jCas.getDocumentLanguage(), documentText);

            for (FastTextResult ftResult : results) {
                ArrayList<ProbabilityLabel> labels = ftResult.getSortedResults(cutoff);

                // Insgesamt nur "fasttextK" Tags ausgeben
                // TODO Pro Model?
                int num = 0;
                for (ProbabilityLabel result : labels) {
                    if (num >= fasttextK) {
                        break;
                    }
                    num++;

                    CategoryCoveredTagged cat = new CategoryCoveredTagged(jCas, begin, end);
                    cat.setValue(result.getLabel());
                    cat.setScore(result.getLogProb());
                    cat.setTags(tags);
                    cat.setRef(ref);
                    cat.addToIndexes();

                    //addAnnotatorComment(jCas, cat);
                }
            }
        } catch (Exception ex) {
            throw new AnalysisEngineProcessException("error processing: " + ex.getMessage(), null, ex);
        }
    }
}
