package org.texttechnologylab.DockerUnifiedUIMAInterface.tools;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.cas.Type;
import org.apache.uima.fit.component.JCasAnnotator_ImplBase;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;

/**
 * @author Manuel Stoeckel
 * @version 0.1.0
 */
public class AnnotationDropper extends JCasAnnotator_ImplBase {
    /**
     * The types to drop from the CAS.
     * Must be the fully qualified class name of the type.
     */
    public static final String PARAM_TYPES_TO_DROP = "typesToDrop";
    @ConfigurationParameter(name = PARAM_TYPES_TO_DROP, mandatory = false, defaultValue = {})
    private String[] typesToDrop;

    /**
     * The types to drop from the CAS.
     * Must be the fully qualified class name of the type.
     */
    public static final String PARAM_TYPES_TO_RETAIN = "typesToRetain";
    @ConfigurationParameter(name = PARAM_TYPES_TO_RETAIN, mandatory = false, defaultValue = {})
    private String[] typesToRetain;
    private HashSet<String> typesToRetainSet;

    @Override
    public void initialize(UimaContext context) throws ResourceInitializationException {
        super.initialize(context);

        if (typesToDrop.length == 0 && typesToRetain.length == 0) {
            throw new ResourceInitializationException(new IllegalArgumentException("At least one of typesToDrop or typesToRetain must be set"));
        } else if (typesToDrop.length > 0 && typesToRetain.length > 0) {
            throw new ResourceInitializationException(new IllegalArgumentException("Only one of typesToDrop or typesToRetain can be set"));
        }
        typesToRetainSet = new HashSet<>(List.of(typesToRetain));
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        aJCas.getDocumentLanguage();

        if (typesToDrop.length > 0) {
            dropTypes(aJCas);
        } else {
            retainTypes(aJCas);
        }
    }

    private void dropTypes(JCas aJCas) {
        for (String typeName : typesToDrop) {
            dropType(aJCas, typeName);
        }
    }

    private static void dropType(JCas aJCas, String typeName) {
        Type type = aJCas.getTypeSystem().getType(typeName);
        aJCas.select(type).forEach(a -> a.removeFromIndexes(aJCas));
    }

    private void retainTypes(JCas aJCas) {
        JCasUtil.selectAll(aJCas).stream().map(a -> a.getType().getName()).distinct().filter(Predicate.not(typesToRetainSet::contains)).forEach(typeName -> {
            dropType(aJCas, typeName);
        });
    }
}