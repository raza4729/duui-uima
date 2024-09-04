from typing import List
from pydantic import BaseModel

import spacy

from cassis import *

from fastapi import FastAPI, Response
from fastapi.encoders import jsonable_encoder
from fastapi.responses import PlainTextResponse, JSONResponse

import uvicorn

class Sentence(BaseModel):
    text: str
    iBegin: int
    iEnd: int


class Entity(BaseModel):
    type: str
    iBegin: int
    iEnd: int

# Request sent by DUUI
# Note, this is transformed by the Lua script
class DUUIRequest(BaseModel):
    # The doc to process
    doc: str

# Response of this tool
# Note, this is transformed by the Lua script
class DUUIResponse(BaseModel):
    # List of Entity
    entities: List[Entity]

# Load the tool's model
nlp = spacy.load('en_core_web_sm')

def analyse(doc):
    
    analysis = nlp(doc)
    entities = []
    
    for entity in analysis.ents:
        print(entity.text, entity.label_)
        entities.append(Entity(
            type=entity.label_,
            iBegin=entity.start_char,
            iEnd=entity.end_char
        ))

    return entities

# Start fastapi
app = FastAPI(
    openapi_url="/openapi.json",
    docs_url="/api",
    redoc_url=None,
    title="NER Spacy DUUI",
    description="Spacy NER implementation for TTLab DUUI",
    version="0.1",
    terms_of_service="https://www.texttechnologylab.org/legal_notice/",
    contact={
        "name": "TTLab Team",
        "url": "https://texttechnologylab.org",
        "email": "omar.momen@em.uni-frankfurt.de",
    },
    license_info={
        "name": "AGPL",
        "url": "http://www.gnu.org/licenses/agpl-3.0.en.html",
    },
)

# Load the Lua communication script
communication = "communication.lua"
with open(communication, 'rb') as f:
    communication = f.read().decode("utf-8")

# Load the predefined typesystem that is needed for this annotator to work
typesystem_filename = 'dkpro-core-types.xml'
with open(typesystem_filename, 'rb') as f:
    typesystem = load_typesystem(f)


# Get input/output of the annotator
@app.get("/v1/details/input_output")
def get_input_output() -> JSONResponse:
    json_item = {
        "inputs": [],
        "outputs": ["de.tudarmstadt.ukp.dkpro.core.api.ner.type.NamedEntity"]
    }

    json_compatible_item_data = jsonable_encoder(json_item)
    return JSONResponse(content=json_compatible_item_data)

# Get typesystem of this annotator
@app.get("/v1/typesystem")
def get_typesystem() -> Response:
    xml = typesystem.to_xml()
    xml_content = xml.encode("utf-8")

    return Response(
        content=xml_content,
        media_type="application/xml"
    )

# Return Lua communication script
@app.get("/v1/communication_layer", response_class=PlainTextResponse)
def get_communication_layer() -> str:
    return communication

# Process request from DUUI
@app.post("/v1/process")
def post_process(request: DUUIRequest) -> DUUIResponse:
    doc = request.doc
    print(doc)
    entities = analyse(doc)

    # Return data as JSON
    return DUUIResponse(
        entities=entities
    )


# if __name__ == "__main__":
#     uvicorn.run(app, host="0.0.0.0", port=9000, workers=1)