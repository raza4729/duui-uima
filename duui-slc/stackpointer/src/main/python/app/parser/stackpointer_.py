import logging
from typing import Self, TypeVar, Final

from app.abc import ModelProxyABC, ProcessorABC, SentenceValidatorABC
from app.model import DuuiRequest, DuuiResponse, Offset, UimaDependency, UimaToken
from app.utils import EOS_MARKERS

from stanza import Pipeline
from stanza.models.common.doc import Document as StanzaDocument
from stanza.models.common.doc import Sentence as StanzaSentence

from .stackpointer_model import *

import warnings
warnings.filterwarnings("ignore", message=".*weights_only=False.*", category=FutureWarning)
logger = logging.getLogger(__name__)

Language = TypeVar("Language")

model_map = {
    "en": 'en',
    "en_US": 'en',
    "en_GB": 'en',
    "en_AU": 'en',
    "en_CA": 'en',
    "de": 'de',
    "de_DE": 'de',
    "de_AT": 'de',
    "de_CH": 'de',
}

POS_VERBS: Final = {"VERB", "AUX"}

class StackpointerModelProxy(ModelProxyABC[Language]):
    def __init__(self):
        self.models = {}

    def __getitem__(self, lang: str):
        lang = model_map.get(lang.replace("-", "_"), lang)
        if self.models.get(lang) is None:
            logger.info(f"Stackpointer: Initiate a dependency parser for '{lang}'")
            nlp = StackPointerParser(
                language = lang
            )
            logger.info(
                f"Stackpointer: Loaded a Dependency Parser ({lang})"
            )
            
            logger.info(f"Stackpointer: Initiate a stanza preprocessing pipeline for '{lang}'")
            preprocessor = Pipeline(
                lang=lang,
                processors='tokenize,pos,lemma',
                tokenize_no_ssplit=True
            )
            logger.info(
                f"Stackpointer: Loaded a stanza preprocessing pipeline ({lang})\n"
            )
            
            self.models[lang] = {
                "parser": nlp,
                "preprocessor": preprocessor
            }
            
        return self.models[lang]

class StackpointerSentenceValidator(SentenceValidatorABC[StanzaSentence]):
    @classmethod
    def check(cls, sentence: StanzaSentence):
        if not sentence.words[0].text[0].isupper():
            logger.info("Stackpointer: Sentence does not start with a capitalized character")
            return cls(False)
        if sentence.words[-1].text[-1] not in EOS_MARKERS:
            logger.info("Stackpointer: Sentence does not end with a period, question mark, or exclamation mark")
            return cls(True, False)
        if not any(word.pos in POS_VERBS for word in sentence.words):
            logger.info("Stackpointer: Sentence does not contain a verb")
            return cls(True, True, False)
        if sentence.text.count('"') % 2 != 0:
            logger.info("Stackpointer: The number of quotation marks is not even")
            return cls(True, True, True, False)
        if sentence.text.count("(") != sentence.text.count(")"):
            logger.info("Stackpointer: The number of left brackets is not equal to that of right brackets")
            return cls(True, True, True, True, False)
        
        return cls(True, True, True, True, True)

class StackpointerProcessor(ProcessorABC[StanzaDocument]):
    def __init__(self, proxy: StackpointerModelProxy) -> None:
        super().__init__()
        self.proxy = proxy

    @classmethod
    def with_proxy(cls, proxy: ModelProxyABC) -> Self:
        return cls(proxy)

    def process(self, request: DuuiRequest):
        if request.sentences is None:
            raise ValueError("Sentences offsets are required for Stackpointer")

        nlp = self.proxy[request.language]['parser']
        preprocessor = self.proxy[request.language]['preprocessor']

        annotations = []
        offsets = request.sentences
        
        for offset in offsets:
            document: StanzaDocument = preprocessor(request.text[offset.begin : offset.end])
            annotation: StanzaDocument = nlp.process(
                    document
                    )            
            annotations.append(annotation)
            
        return self.post_process(
            annotations,
            offsets,
        )

    @staticmethod
    def post_process(annotations: list[StanzaDocument], offsets: list[Offset]):

        results = DuuiResponse(sentences=None, tokens=[], dependencies=[])
        tokens_indices = 0
        for annotation, offset in zip(annotations, offsets):
            if not bool(StackpointerSentenceValidator.check(annotation.sentences[0]).is_standalone()):
                logger.info("Stackpointer: Sentence is not standalone. Skipping.")
                continue
                        
            sentence_begin_index = tokens_indices
            
            for word in annotation.sentences[0].words:
                token_idx = tokens_indices
                tokens_indices += 1
                
                begin = word.start_char + offset.begin
                end = word.end_char + offset.begin
                
                if word.head == 0:
                    head_index = token_idx
                    dep_rel = "root"
                else:
                    head_index = sentence_begin_index + word.head - 1
                    dep_rel = word.deprel

                
                results.tokens.append(
                    UimaToken(
                        begin=begin,
                        end=end,
                        idx=token_idx,
                        pos=word.upos,
                        tag=word.xpos,
                        lemma=word.lemma,
                        morph=(
                            {
                                k.lower(): v
                                for k, v in (
                                    feat.split("=")
                                    for feat in word.feats.split("|")
                                    if "=" in feat
                                )
                            }
                            if word.feats
                            else None
                        ),
                    )
                )
            
                results.dependencies.append(
                    UimaDependency(
                        begin=begin,
                        end=end,
                        governor=head_index,
                        dependent=token_idx,
                        type=dep_rel,
                        flavor="basic",
                    )
            )
                
        return results
    
