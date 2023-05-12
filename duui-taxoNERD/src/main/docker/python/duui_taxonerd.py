from typing import List, Optional
from cassis import *
from fastapi import FastAPI, Response
from fastapi.encoders import jsonable_encoder
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel, BaseSettings
from starlette.responses import JSONResponse
from taxonerd import *
from functools import lru_cache


class GBIF(BaseModel):
    id: str
    value: str
    propability: float


# Taxon
class Taxon(BaseModel):
    begin: int
    end: int
    comment: Optional[List[GBIF]]


# Request sent by DUUI
# Note, this is transformed by the Lua script
class DUUIRequest(BaseModel):
    # The text to process
    text: str


# Response of this annotator
# Note, this is transformed by the Lua script
class DUUIResponse(BaseModel):
    # List of Taxon
    taxons: List[Taxon]


class Settings(BaseSettings):
    # Name of the Model
    model: str
    # Name of the linking
    linking: str

# settings + cache
settings = Settings()
lru_cache_with_size = lru_cache(maxsize=3)

config = {"prefer_gpu": False,
          "with_abbrev": True,
          "model": settings.model,
          "with_linking": settings.linking}

@lru_cache_with_size
def load_taxonerd(**kwargs):
    # Add with_linking="gbif_backbone" or with_linking="taxref" to activate entity linking
    return TaxoNERD(**kwargs)


def analyse(text, ner):

    result = ner.find_in_text(text)
    print(result)

    taxons = []


    for index, row in result.iterrows():
        offset = row['offsets'].split(" ")
        entity = row['entity']
        entries = []

        for e in entity:
            entries.append(GBIF(
                    id=str(e[0]),
                    value=str(e[1]),
                    propability=float(e[2])
                ))

        taxons.append(Taxon(
            begin=int(offset[1]),
            end=int(offset[2]),
            comment=entries
        ))

    print(taxons)

    return taxons


# Start fastapi
# TODO openapi types are not shown?
# TODO self host swagger files: https://fastapi.tiangolo.com/advanced/extending-openapi/#self-hosting-javascript-and-css-for-docs
app = FastAPI(
    openapi_url="/openapi.json",
    docs_url="/api",
    redoc_url=None,
    title="TaxoNERD",
    description="TaxoNERD implementation for TTLab TextImager DUUI",
    version="0.1",
    terms_of_service="https://www.texttechnologylab.org/legal_notice/",
    contact={
        "name": "TTLab Team",
        "url": "https://texttechnologylab.org",
        "email": "abrami@em.uni-frankfurt.de",
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


# Get input / output of the annotator
@app.get("/v1/details/input_output")
def get_input_output() -> JSONResponse:

    json_item = {
        "inputs": [],
        "outputs": ["org.texttechnologylab.annotation.type.Taxon"]
    }

    json_compatible_item_data = jsonable_encoder(json_item)
    return JSONResponse(content=json_compatible_item_data)

# Get typesystem of this annotator
@app.get("/v1/typesystem")
def get_typesystem() -> Response:
    # TODO remove cassis dependency, as only needed for typesystem at the moment?
    xml = typesystem.to_xml()
    xml_content = xml.encode("utf-8")

    return Response(
        content=xml_content,
        media_type="application/xml"
    )


# #@app.before_first_request
# @app.on_event("startup")
# def setup():
#     global ner


# Return Lua communication script
@app.get("/v1/communication_layer", response_class=PlainTextResponse)
def get_communication_layer() -> str:
    return communication

# Process request from DUUI
@app.post("/v1/process")
def post_process(request: DUUIRequest) -> DUUIResponse:

    text = request.text
    ner = load_taxonerd(**config)
    taxons = analyse(text, ner)

    # Return data as JSON
    return DUUIResponse(
        taxons=taxons
    )


if __name__ == "__main__":

    uvicorn.run("duui_taxonerd:app", host="0.0.0.0", port=9715, workers=2)