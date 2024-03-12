from pydantic import BaseModel
from pydantic_settings import BaseSettings
from typing import List, Optional, Dict, Union
import logging
from time import time
from fastapi import FastAPI, Response
from cassis import load_typesystem
import torch
from threading import Lock
from functools import lru_cache
from EmotionDetection import EmotionCheck
from Emo_mDeBERTa2 import DebertaEmoCheck
# from sp_correction import SentenceBestPrediction

# Settings
# These are automatically loaded from env variables
from starlette.responses import PlainTextResponse
model_lock = Lock()
sources = {
    "02shanky/finetuned-twitter-xlm-roberta-base-emotion": "https://huggingface.co/02shanky/finetuned-twitter-xlm-roberta-base-emotion",
    "DReAMy-lib/xlm-roberta-large-DreamBank-emotion-presence": "https://huggingface.co/DReAMy-lib/xlm-roberta-large-DreamBank-emotion-presence",
    "pol_emo_mDeBERTa": "https://github.com/tweedmann/pol_emo_mDeBERTa2",
    "MilaNLProc/xlm-emo-t": "https://huggingface.co/MilaNLProc/xlm-emo-t",
    "j-hartmann/emotion-english-distilroberta-base": "https://huggingface.co/j-hartmann/emotion-english-distilroberta-base",
    "michellejieli/emotion_text_classifier": "https://huggingface.co/michellejieli/emotion_text_classifier",
    "cardiffnlp/twitter-roberta-base-emotion": "https://huggingface.co/cardiffnlp/twitter-roberta-base-emotion",
    "finiteautomata/bertweet-base-emotion-analysis": "https://huggingface.co/finiteautomata/bertweet-base-emotion-analysis"
}

languages = {
    "02shanky/finetuned-twitter-xlm-roberta-base-emotion": "Multilingual",
    "DReAMy-lib/xlm-roberta-large-DreamBank-emotion-presence": "Multilingual",
    "pol_emo_mDeBERTa": "Multilingual",
    "MilaNLProc/xlm-emo-t": "Multilingual",
    "j-hartmann/emotion-english-distilroberta-base": "en",
    "michellejieli/emotion_text_classifier": "en",
    "cardiffnlp/twitter-roberta-base-emotion": "en",
    "finiteautomata/bertweet-base-emotion-analysis": "en"
}

versions = {
    "02shanky/finetuned-twitter-xlm-roberta-base-emotion": "28e6d080e9f73171b574dd88ac768da9e6622c36",
    "DReAMy-lib/xlm-roberta-large-DreamBank-emotion-presence": "b3487623ec2dd4b9bd0644d8266291afb9956e9f",
    "pol_emo_mDeBERTa": "523da7dc2523631787ef0712bad53bfe2ac46840",
    "MilaNLProc/xlm-emo-t": "a6ee7c9fad08d60204e7ae437d41d392381496f0",
    "j-hartmann/emotion-english-distilroberta-base": "0e1cd914e3d46199ed785853e12b57304e04178b",
    "michellejieli/emotion_text_classifier": "dc4df5597fcda82589511c3900fedbe1c0ffec82",
    "cardiffnlp/twitter-roberta-base-emotion": "2848306ad936b7cd47c76c2c4e14d694a41e0f54",
    "finiteautomata/bertweet-base-emotion-analysis": "c482c9e1750a29dcc393234816bcf468ff77cd2d"
}


class UimaSentence(BaseModel):
    text: str
    begin: int
    end: int


class UimaSentenceSelection(BaseModel):
    selection: str
    sentences: List[UimaSentence]

class Settings(BaseSettings):
    # Name of this annotator
    emotion_annotator_name: str
    # Version of this annotator
    emotion_annotator_version: str
    # Log level
    emotion_log_level: str
    # # model_name
    # emotion_model_name: str
    # Name of this annotator
    emotion_model_version: str
    #cach_size
    emotion_model_cache_size: int


# Load settings from env vars
settings = Settings()
lru_cache_with_size = lru_cache(maxsize=settings.emotion_model_cache_size)
logging.basicConfig(level=settings.emotion_log_level)
logger = logging.getLogger(__name__)

device = 0 if torch.cuda.is_available() else "cpu"
logger.info(f'USING {device}')
# Load the predefined typesystem that is needed for this annotator to work
typesystem_filename = 'TypeSystemEmotion.xml'
logger.debug("Loading typesystem from \"%s\"", typesystem_filename)
with open(typesystem_filename, 'rb') as f:
    typesystem = load_typesystem(f)
    logger.debug("Base typesystem:")
    logger.debug(typesystem.to_xml())

# Load the Lua communication script
lua_communication_script_filename = "duui_emotion.lua"
logger.debug("Loading Lua communication script from \"%s\"", lua_communication_script_filename)


# Request sent by DUUI
# Note, this is transformed by the Lua script
class TextImagerRequest(BaseModel):
    # The texts language
    doc_len: int
    #
    lang: str
    #
    model_name: str
    #
    selections:  List[UimaSentenceSelection]
    #


# UIMA type: mark modification of the document
class DocumentModification(BaseModel):
    user: str
    timestamp: int
    comment: str


# UIMA type: adds metadata to each annotation
class AnnotationMeta(BaseModel):
    name: str
    version: str
    modelName: str
    modelVersion: str


# Response sent by DUUI
# Note, this is transformed by the Lua script
class TextImagerResponse(BaseModel):
    # Symspelloutput
    # List of Sentence with every token
    # Every token is a dictionary with following Infos:
    # Symspelloutput right if the token is correct, wrong if the token is incorrect, skipped if the token was skipped, unkownn if token can corrected with Symspell
    # If token is unkown it will be predicted with BERT Three output pos:
    # 1. Best Prediction with BERT MASKED
    # 2. Best Cos-sim with Sentence-Bert and with perdicted words of BERT MASK
    # 3. Option 1 and 2 together
    meta: AnnotationMeta
    # Modification meta, one per document
    modification_meta: DocumentModification
    begin_emo: List[int]
    end_emo: List[int]
    results: List
    factors: List
    len_results: List[int]
    model_name: str
    model_version: str
    model_source: str
    model_lang: str



app = FastAPI(
    openapi_url="/openapi.json",
    docs_url="/api",
    redoc_url=None,
    title=settings.emotion_annotator_name,
    description="Factuality annotator",
    version=settings.emotion_annotator_version,
    terms_of_service="https://www.texttechnologylab.org/legal_notice/",
    contact={
        "name": "TTLab Team",
        "url": "https://texttechnologylab.org",
        "email": "bagci@em.uni-frankfurt.de",
    },
    license_info={
        "name": "AGPL",
        "url": "http://www.gnu.org/licenses/agpl-3.0.en.html",
    },
)

with open(lua_communication_script_filename, 'rb') as f:
    lua_communication_script = f.read().decode("utf-8")
logger.debug("Lua communication script:")
logger.debug(lua_communication_script_filename)


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


# Return Lua communication script
@app.get("/v1/communication_layer", response_class=PlainTextResponse)
def get_communication_layer() -> str:
    return lua_communication_script


# Return documentation info
@app.get("/v1/documentation")
def get_documentation():
    return "Test"

@lru_cache_with_size
def load_model(model_name):
    if model_name == "pol_emo_mDeBERTa":
        model_i = DebertaEmoCheck(f"{model_name}/model/pytorch_model.pt", device)
    else:
        model_i = EmotionCheck(model_name, device)
    return model_i


def fix_unicode_problems(text):
    # fix emoji in python string and prevent json error on response
    # File "/usr/local/lib/python3.8/site-packages/starlette/responses.py", line 190, in render
    # UnicodeEncodeError: 'utf-8' codec can't encode characters in position xx-yy: surrogates not allowed
    clean_text = text.encode('utf-16', 'surrogatepass').decode('utf-16', 'surrogateescape')
    return clean_text

def process_selection(model_name, selection, doc_len):
    begin = []
    end = []
    results_out = []
    factors = []
    len_results = []
    for s in selection.sentences:
        s.text = fix_unicode_problems(s.text)

    texts = [
        s.text
        for s in selection.sentences
    ]
    logger.debug("Preprocessed texts:")
    logger.debug(texts)

    with model_lock:
        classifier = load_model(model_name)

        results = classifier.emotion_prediction(texts)
        for c, res in enumerate(results):
            res_i = []
            factor_i = []
            sentence_i = selection.sentences[c]
            begin_i = sentence_i.begin
            end_i = sentence_i.end
            len_rel = len(res)
            begin.append(begin_i)
            end.append(end_i)
            for i in res:
                res_i.append(i)
                factor_i.append(res[i])
            len_results.append(len_rel)
            results_out.append(res_i)
            factors.append(factor_i)
    output = {
        "begin": begin,
        "end": end,
        "len_results": len_results,
        "results": results_out,
        "factors": factors
    }

    return output, classifier.model._version

# Process request from DUUI
@app.post("/v1/process")
def post_process(request: TextImagerRequest):
    # Return data
    meta = None
    begin = []
    end = []
    len_results = []
    results = []
    factors = []
    # Save modification start time for later
    modification_timestamp_seconds = int(time())
    try:
        model_source = sources[request.model_name]
        model_lang = languages[request.model_name]
        model_version = versions[request.model_name]
        # set meta Informations
        meta = AnnotationMeta(
            name=settings.emotion_annotator_name,
            version=settings.emotion_annotator_version,
            modelName=request.model_name,
            modelVersion=model_version,
        )
        # Add modification info
        modification_meta_comment = f"{settings.emotion_annotator_name} ({settings.emotion_annotator_version}))"
        modification_meta = DocumentModification(
            user=settings.emotion_annotator_name,
            timestamp=modification_timestamp_seconds,
            comment=modification_meta_comment
        )
        mv = ""

        for selection in request.selections:
            processed_sentences, model_version_2 = process_selection(request.model_name, selection, request.doc_len)
            begin = begin+ processed_sentences["begin"]
            end = end + processed_sentences["end"]
            len_results = len_results + processed_sentences["len_results"]
            results = results + processed_sentences["results"]
            factors = factors + processed_sentences["factors"]
    except Exception as ex:
        logger.exception(ex)
    return TextImagerResponse(meta=meta, modification_meta=modification_meta, begin_emo=begin, end_emo=end, results=results, len_results=len_results, factors=factors, model_name=request.model_name, model_version=model_version, model_source=model_source, model_lang=model_lang)



