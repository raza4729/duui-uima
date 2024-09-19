package org.hucompute.textimager.uima.spacy;

import org.dkpro.core.io.xmi.XmiWriter;
import org.texttechnologylab.DockerUnifiedUIMAInterface.DUUIComposer;
import org.texttechnologylab.DockerUnifiedUIMAInterface.driver.*;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.reader.DUUIFileReader;
import org.texttechnologylab.DockerUnifiedUIMAInterface.io.DUUIAsynchronousProcessor;
import org.texttechnologylab.DockerUnifiedUIMAInterface.lua.DUUILuaContext;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.apache.uima.fit.factory.AnalysisEngineFactory.createEngineDescription;

public class hateTest {
    @org.junit.jupiter.api.Test
    public void testOne() {
        System.out.println("------------------------Testing---------------");
    }


    @org.junit.jupiter.api.Test
    public void testHate() throws Exception {
        Path sourceLocation = Paths.get("/home/raza/Documents/Data/germany2-xmi/de/spacy");
        Path targetLocation = Paths.get("/home/raza/Documents/Data/hate/");
        int scale = 1;

        DUUIAsynchronousProcessor processor = new DUUIAsynchronousProcessor(
                new DUUIFileReader(
                        sourceLocation.toString(),
                        ".xmi.gz",
                        1,
                        -1,
                        false,
                        "",
                        false,
                        null,
                        -1,
                        targetLocation.toString(),
                        ".xmi.gz"
                )
        );

        DUUIComposer composer = new DUUIComposer()
                .withSkipVerification(true)
                .withWorkers(scale)
                .withLuaContext(new DUUILuaContext().withJsonLibrary());

        DUUIUIMADriver uimaDriver = new DUUIUIMADriver();
        DUUIRemoteDriver remote_driver = new DUUIRemoteDriver(10000);
        composer.addDriver(uimaDriver, remote_driver);


        List<String> urls = new ArrayList<>();
        urls.add("http://127.0.0.1:6262");
//        urls.add("http://127.0.0.1:8502");
//        urls.add("http://127.0.0.1:8503");
//        urls.add("http://127.0.0.1:8504");
        composer.add(
                new DUUIRemoteDriver.Component(urls)
                        .withScale(1)
                        .withParameter("selection", "text")
        );
        composer.add(new DUUIUIMADriver.Component(createEngineDescription(XmiWriter.class,
                XmiWriter.PARAM_TARGET_LOCATION, targetLocation.toString(),
                XmiWriter.PARAM_PRETTY_PRINT, true,
                XmiWriter.PARAM_OVERWRITE, true,
                XmiWriter.PARAM_VERSION, "1.1",
                XmiWriter.PARAM_COMPRESSION, "GZIP"
        )).build());

        composer.run(processor, "spacy_plus");
        composer.shutdown();
    }

}





