-- Bind static classes from java
StandardCharsets = luajava.bindClass("java.nio.charset.StandardCharsets")

-- This "serialize" function is called to transform the CAS object into an stream that is sent to the annotator
-- Inputs:
--  - inputCas: The actual CAS object to serialize
--  - outputStream: Stream that is sent to the annotator, can be e.g. a string, JSON payload, ...
function serialize(inputCas, outputStream, params)
    -- Get data from CAS
    local doc_text = inputCas:getDocumentText()

    -- Encode data as JSON object and write to stream
    outputStream:write(json.encode({
        doc = doc_text
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
    for i, pEntity in ipairs(results["entities"]) do

        local entity = luajava.newInstance("de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity", inputCas)

        entity:setBegin(pEntity["iBegin"])
        entity:setEnd(pEntity["iEnd"])
        entity:setValue(pEntity["type"])
        entity:addToIndexes()

     end

end
