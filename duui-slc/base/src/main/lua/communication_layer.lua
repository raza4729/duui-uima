-- Bind static classes from java
StandardCharsets = luajava.bindClass("java.nio.charset.StandardCharsets")
JCasUtil = luajava.bindClass("org.apache.uima.fit.util.JCasUtil")
Token = luajava.bindClass("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token")
TokenForm = luajava.bindClass("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.TokenForm")
Sentence = luajava.bindClass("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence")
Paragraph = luajava.bindClass("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Paragraph")

-- This "serialize" function is called to transform the CAS object into an stream that is sent to the annotator
-- Inputs:
--  - inputCas: The actual CAS object to serialize
--  - outputStream: Stream that is sent to the annotator, can be e.g. a string, JSON payload, ...
--  - parameters: A map of optional parameters
function serialize(inputCas, outputStream, parameters)
    print("parameters:")
    for k, v in pairs(parameters) do;
        print("  ", k, v)
    end

    -- Get data from CAS
    local document_text = inputCas:getDocumentText();

    local language = nil
    if parameters and parameters["language"] ~= nil then
        language = parameters["language"]
    else
        print("No parameters were given, inferring language from CAS")
    end
    if language == nil then
        language = inputCas:getDocumentLanguage()
    end
    if language == "x-unspecified" or language == nil then
        error("Document language was not given and could not be inferred", 2)
    end

    local validate = true
    if parameters and parameters["validate"] ~= nil then
        validate = tostring(parameters["validate"])=="true" and true or false
    elseif parameters and parameters["validate_sentences"] ~= nil then
        validate = tostring(parameters["validate_sentences"])=="true" and true or false
    end

    local sentences = {}
    local counter = 1
    local it_sentences = JCasUtil:select(inputCas, Sentence):iterator()
    while it_sentences:hasNext() do
        local annotation = it_sentences:next()
        local annotation_begin = annotation:getBegin()
        local annotation_end = annotation:getEnd()
        sentences[counter] = {}
        sentences[counter]["begin"] = annotation_begin
        sentences[counter]["end"] = annotation_end
        counter = counter + 1
    end

    local paragraphs = {}
    counter = 1
    local it_paragraphs = JCasUtil:select(inputCas, Paragraph):iterator()
    while it_paragraphs:hasNext() do
        local annotation = it_paragraphs:next()
        local annotation_begin = annotation:getBegin()
        local annotation_end = annotation:getEnd()
        paragraphs[counter] = {}
        paragraphs[counter]["begin"] = annotation_begin
        paragraphs[counter]["end"] = annotation_end
        counter = counter + 1
    end

    -- Encode data as JSON object and write to stream
    outputStream:write(json.encode({
        text = document_text,
        language = language,
        sentences = sentences,
        paragraphs = paragraphs,
        validate_sentences = validate
    }))
end

-- This "deserialize" function is called on receiving the results from the annotator that have to be transformed into a CAS object
-- Inputs:
--  - inputCas: The actual CAS object to deserialize into
--  - inputStream: Stream that is received from to the annotator, can be e.g. a string, JSON payload, ...
function deserialize(inputCas, inputStream)
    -- Get string from stream, assume UTF-8 encoding
    local inputString = luajava.newInstance("java.lang.String", inputStream:readAllBytes(), StandardCharsets.UTF_8)

    -- Parse JSON data from string into object
    local results = json.decode(inputString)

    -- TODO: -- Add modification annotation
    -- local modification_meta = results["modification_meta"]
    -- local modification_anno = luajava.newInstance("org.texttechnologylab.annotation.DocumentModification", inputCas)
    -- modification_anno:setUser(modification_meta["user"])
    -- modification_anno:setTimestamp(modification_meta["timestamp"])
    -- modification_anno:setComment(modification_meta["comment"])
    -- modification_anno:addToIndexes()

    -- TODO: -- Get meta data, this is the same for every annotation
    -- local meta = results["meta"]

    -- Add sentences
    if results["sentences"] ~= nil then
        for i, sent in ipairs(results["sentences"]) do
            -- Note: spaCy will still run the full pipeline, and all results are based on these results
            -- Create sentence annotation
            local sent_anno = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Sentence", inputCas)
            sent_anno:setBegin(sent["begin"])
            sent_anno:setEnd(sent["end"])
            sent_anno:addToIndexes()
        end
    end

    -- Add tokens
    if results["tokens"] == nil then
        error("No tokens were returned by the annotator", 2)
    end

    -- Save all tokens, to allow for retrieval in dependencies
    local all_tokens = {}
    for i, token in ipairs(results["tokens"]) do
        -- Create token annotation
        local token_anno = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Token", inputCas)
        token_anno:setBegin(token["begin"])
        token_anno:setEnd(token["end"])
        token_anno:addToIndexes()

        if token["form"] ~= nil then  -- Token is part of a MWT
            local token_form = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.TokenForm", inputCas)
            token_form:setBegin(token["begin"])
            token_form:setEnd(token["end"])
            token_form:setValue(token["form"])
            token_form:addToIndexes()

            token_anno:setForm(token_form)
        end

        all_tokens[token["idx"]] = token_anno

        if token["lemma"] ~= nil then
            local lemma_anno = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.segmentation.type.Lemma", inputCas)
            lemma_anno:setBegin(token["begin"])
            lemma_anno:setEnd(token["end"])
            lemma_anno:setValue(token["lemma"])
            lemma_anno:addToIndexes()

            token_anno:setLemma(lemma_anno)
        end

        if token["pos"] ~= nil then
            -- TODO Add full pos mapping for different pos types
            local pos_anno = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.pos.POS", inputCas)
            pos_anno:setBegin(token["begin"])
            pos_anno:setEnd(token["end"])
            pos_anno:setCoarseValue(token["pos"])
            if token["tag"] ~= nil then
                pos_anno:setPosValue(token["tag"])
            end
            pos_anno:addToIndexes()

            token_anno:setPos(pos_anno)
        end

        if token["morph"] ~= nil then
            local morph_anno = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.lexmorph.type.morph.MorphologicalFeatures", inputCas)
            morph_anno:setBegin(token["begin"])
            morph_anno:setEnd(token["end"])
            morph_anno:setValue(token["morph"])

            -- Add detailed infos, if available
            if token["morph"]["gender"] ~= nil then
                morph_anno:setGender(token["morph"]["gender"])
            end
            if token["morph"]["number"] ~= nil then
                morph_anno:setNumber(token["morph"]["number"])
            end
            if token["morph"]["case"] ~= nil then
                morph_anno:setCase(token["morph"]["case"])
            end
            if token["morph"]["degree"] ~= nil then
                morph_anno:setDegree(token["morph"]["degree"])
            end
            if token["morph"]["verbForm"] ~= nil then
                morph_anno:setVerbForm(token["morph"]["verbForm"])
            end
            if token["morph"]["tense"] ~= nil then
                morph_anno:setTense(token["morph"]["tense"])
            end
            if token["morph"]["mood"] ~= nil then
                morph_anno:setMood(token["morph"]["mood"])
            end
            if token["morph"]["voice"] ~= nil then
                morph_anno:setVoice(token["morph"]["voice"])
            end
            if token["morph"]["definiteness"] ~= nil then
                morph_anno:setDefiniteness(token["morph"]["definiteness"])
            end
            if token["morph"]["person"] ~= nil then
                morph_anno:setPerson(token["morph"]["person"])
            end
            if token["morph"]["aspect"] ~= nil then
                morph_anno:setAspect(token["morph"]["aspect"])
            end
            if token["morph"]["animacy"] ~= nil then
                morph_anno:setAnimacy(token["morph"]["animacy"])
            end
            if token["morph"]["gender"] ~= nil then
                morph_anno:setNegative(token["morph"]["negative"])
            end
            if token["morph"]["numType"] ~= nil then
                morph_anno:setNumType(token["morph"]["numType"])
            end
            if token["morph"]["possessive"] ~= nil then
                morph_anno:setPossessive(token["morph"]["possessive"])
            end
            if token["morph"]["pronType"] ~= nil then
                morph_anno:setPronType(token["morph"]["pronType"])
            end
            if token["morph"]["reflex"] ~= nil then
                morph_anno:setReflex(token["morph"]["reflex"])
            end
            if token["morph"]["transitivity"] ~= nil then
                morph_anno:setTransitivity(token["morph"]["transitivity"])
            end

            morph_anno:addToIndexes()

            token_anno:setMorph(morph_anno)
        end
    end

    -- Add dependencies
    if results["dependencies"] == nil then
        error("No dependencies were returned by the annotator", 2)
    end
    for i, dep in ipairs(results["dependencies"]) do
        -- Create specific annotation based on type
        local dep_anno
        if string.lower(dep["type"]) == "root" or dep["type"] == "--" then
            dep_anno = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.ROOT", inputCas)
            dep_anno:setDependencyType("--")
        else
            dep_anno = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.syntax.type.dependency.Dependency", inputCas)
            dep_anno:setDependencyType(string.lower(dep["type"]))
        end

        dep_anno:setBegin(dep["begin"])
        dep_anno:setEnd(dep["end"])
        dep_anno:setFlavor(dep["flavor"])

        -- Get needed tokens via indices
        local governor_token = all_tokens[dep["governor"]]
        if governor_token ~= nil then
            dep_anno:setGovernor(governor_token)
        end

        local dependent_token = all_tokens[dep["dependent"]]
        if governor_token ~= nil then
            dep_anno:setDependent(dependent_token)
        end

        if governor_token ~= nil and dependent_token ~= nil then
            dependent_token:setParent(governor_token)
        end

        dep_anno:addToIndexes()
    end
end