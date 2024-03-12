package org.hucompute.textimager.uima.transformers.emotion;

import de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence;
import org.apache.commons.compress.compressors.CompressorException;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSArray;
import org.apache.uima.util.XmlCasSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIRemoteDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;
import org.xml.sax.SAXException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.texttechnologylab.annotation.Emotion;
import org.texttechnologylab.annotation.AnnotationComment;

public class EmotionTest {
    static DUUIComposer composer;
    static JCas cas;

    static String url = "http://127.0.0.1:8000";
    static String model = "02shanky/finetuned-twitter-xlm-roberta-base-emotion";
//    static String model = "pol_emo_mDeBERTa";

    @BeforeAll
    static void beforeAll() throws URISyntaxException, IOException, UIMAException, SAXException, CompressorException {
        composer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(new DUUILuaContext().withJsonLibrary());

        DUUIRemoteDriver remoteDriver = new DUUIRemoteDriver();
        composer.addDriver(remoteDriver);
//        DUUIDockerDriver docker_driver = new DUUIDockerDriver();
//        composer.addDriver(docker_driver);


        cas = JCasFactory.createJCas();
    }

    @AfterAll
    static void afterAll() throws UnknownHostException {
        composer.shutdown();
    }

    @AfterEach
    public void afterEach() throws IOException, SAXException {
        composer.resetPipeline();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        XmlCasSerializer.serialize(cas.getCas(), null, stream);
        System.out.println(stream.toString(StandardCharsets.UTF_8));

        cas.reset();
    }

    public void createCas(String language, List<String> sentences) throws UIMAException {
        cas.setDocumentLanguage(language);

        StringBuilder sb = new StringBuilder();
        for (String sentence : sentences) {
            Sentence sentenceAnnotation = new Sentence(cas, sb.length(), sb.length()+sentence.length());
            sentenceAnnotation.addToIndexes();
            sb.append(sentence).append(" ");
        }

        cas.setDocumentText(sb.toString());
    }

    @Test
    public void sentencesTest() throws Exception {
//        composer.add(new DUUIDockerDriver.
//                Component("docker.texttechnologylab.org/textimager-duui-transformers-topic:0.0.1")
//                .withParameter("model_name", model)
//                .withParameter("selection", "text,de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence")
//                .withScale(1)
//                .withImageFetching());
        composer.add(
                new DUUIRemoteDriver.Component(url)
                        .withParameter("model_name", model)
                        .withParameter("selection", "de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence")
        );

        String text_i = "Das Sachgebiet Investive Ausgaben des Bundes Bundesfinanzminister Apel hat gemäß BMF Finanznachrichten vom 1. Januar erklärt, die Investitionsquote des Bundes sei in den letzten zehn Jahren nahezu konstant geblieben.\",\n Bei dieser Anlagenart ersetzt die Photovoltaikanlage Teile der Gebäudehülle, also der Fassadenverkleidung und/oder der Dacheindeckung.";

        List<String> sentences = Arrays.asList(
                "Das Sachgebiet Investive Ausgaben des Bundes Bundesfinanzminister Apel hat gemäß BMF Finanznachrichten vom 1. Januar erklärt, die Investitionsquote des Bundes sei in den letzten zehn Jahren nahezu konstant geblieben.",
                "Bei dieser Anlagenart ersetzt die Photovoltaikanlage Teile der Gebäudehülle, also der Fassadenverkleidung und/oder der Dacheindeckung."
        );

        createCas("de", sentences);
        Emotion e1 = new Emotion(cas, 0, 20);
        e1.setEmotions(new FSArray(cas, 20));
        AnnotationComment t1 = new AnnotationComment(cas);
        t1.setKey("test");
        t1.setValue("t2");
        e1.setEmotions(0, t1);
        composer.run(cas);

        Collection<Emotion> all_emotions = JCasUtil.select(cas, Emotion.class);
//        System.out.println(topics.size());
        for (Emotion emotion: all_emotions){
            System.out.println(emotion.getCoveredText());
            FSArray<AnnotationComment> emotions_all = emotion.getEmotions();
            for (AnnotationComment comment_i: emotions_all){
                System.out.println("key:"+comment_i.getKey()+"; Value:"+comment_i.getValue());
            }
        }
//
//        // 1 sentiment per sentence, +1 for average
//        assertEquals(sentences.size()+1, sentiments.size());
//
//        // Sentiments should be neu (average), pos (sentence 1), neg (s2)
//        Double[] expectedSentiments = new Double[]{ 0d, 1d, -1d };
//        Double[] actualSentiments = sentiments.stream().map(Sentiment::getSentiment).toArray(Double[]::new);
//        assertArrayEquals(expectedSentiments, actualSentiments);
    }
}
