package org.hucompute.textimager.uima.testing;

import org.apache.uima.UIMAException;
import org.apache.uima.cas.TypeSystem;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.util.XmlCasSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIDockerDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.DUUIRemoteDriver;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;
import org.xml.sax.SAXException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

import de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity;

public class NerSpacyTest {
    static DUUIComposer composer;
    static JCas cas;
    static int iWorkers = 1; // define the number of workers

    @BeforeAll
    static void beforeAll() throws URISyntaxException, IOException, UIMAException, SAXException {
        composer = new DUUIComposer()
                .withSkipVerification(true)
                .withLuaContext(new DUUILuaContext().withJsonLibrary());
        composer.addDriver(new DUUIRemoteDriver(10000));
        composer.addDriver(new DUUIDockerDriver(10000));

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
        XmlCasSerializer.serialize(cas.getCas(), stream);
        System.out.println(stream.toString(StandardCharsets.UTF_8));

        Collection<NamedEntity> entities_out = JCasUtil.select(cas, NamedEntity.class);
        System.out.println(entities_out);
        for (NamedEntity entity_i: entities_out){
            System.out.println(entity_i.getCoveredText());
            System.out.println(entity_i.getValue());
        }
        cas.reset();
    }

    @Test
    public void simpleTest() throws Exception {
//        composer.add(new DUUIRemoteDriver
//                .Component("http://0.0.0.0:9714")
//                .withScale(iWorkers)
//        );
//        composer.add(new DUUIDockerDriver
//                .Component("duui-ner-spacy:v0.1")
//                .withImageFetching()
//        );
        composer.add(new DUUIDockerDriver
                .Component("docker.texttechnologylab.org/duui-ner-spacy:v0.1")
                .withImageFetching()
        );
        cas.setDocumentText("This is an IPhone by Apple. And this is an iMac. They all belong to me, Omar. And I work in Frankfurt.");
        cas.setDocumentLanguage("en");
        composer.run(cas);
    }
}