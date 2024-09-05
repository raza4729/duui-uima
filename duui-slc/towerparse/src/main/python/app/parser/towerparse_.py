import logging
from typing import Self, TypeVar, Final

import sys
import os
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from app.abc import ModelProxyABC, ProcessorABC, SentenceValidatorABC
from app.model import DuuiRequest, DuuiResponse, Offset, UimaDependency, UimaToken
from app.utils import EOS_MARKERS

from stanza import Pipeline
from stanza.models.common.doc import Document as StanzaDocument
from stanza.models.common.doc import Sentence as StanzaSentence

from towerparse.tower import TowerParser as TowerParserModel

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

model_path_map = {
    "en": "app/parser/models/towerparse/UD_English-EWT",
    "de": "app/parser/models/towerparse/UD_German-HDT",
}

POS_VERBS: Final = {"VERB", "AUX"}

class TowerparseModelProxy(ModelProxyABC[Language]):
    def __init__(self):
        self.models = {}

    def __getitem__(self, lang: str):
        lang = model_map.get(lang.replace("-", "_"), lang)
        model_path = model_path_map.get(lang, None)
        if self.models.get(lang) is None:
            logger.info(f"Towerparse: Initiate a dependency parser for '{lang}'")
            nlp = TowerParserModel(
                model_path
            )
            logger.info(
                f"Towerparse: Loaded a Dependency Parser ({lang})"
            )
            
            logger.info(f"Towerparse: Initiate a stanza preprocessing pipeline for '{lang}'")
            preprocessor = Pipeline(
                lang=lang,
                processors='tokenize,pos,lemma',
                tokenize_no_ssplit=True
            )
            logger.info(
                f"Towerparse: Loaded a stanza preprocessing pipeline ({lang})\n"
            )
            
            self.models[lang] = {
                "parser": nlp,
                "preprocessor": preprocessor
            }
            
        return self.models[lang]

class TowerparseSentenceValidator(SentenceValidatorABC[StanzaSentence]):
    @classmethod
    def check(cls, sentence: StanzaSentence):
        if not sentence.words[0].text[0].isupper():
            logger.info("Towerparse: Sentence does not start with a capitalized character")
            return cls(False)
        if sentence.words[-1].text[-1] not in EOS_MARKERS:
            logger.info("Towerparse: Sentence does not end with a period, question mark, or exclamation mark")
            return cls(True, False)
        if not any(word.pos in POS_VERBS for word in sentence.words):
            logger.info("Towerparse: Sentence does not contain a verb")
            return cls(True, True, False)
        if sentence.text.count('"') % 2 != 0:
            logger.info("Towerparse: The number of quotation marks is not even")
            return cls(True, True, True, False)
        if sentence.text.count("(") != sentence.text.count(")"):
            logger.info("Towerparse: The number of left brackets is not equal to that of right brackets")
            return cls(True, True, True, True, False)
        
        return cls(True, True, True, True, True)

class TowerparseProcessor(ProcessorABC[StanzaDocument]):
    def __init__(self, proxy: TowerparseModelProxy) -> None:
        super().__init__()
        self.proxy = proxy

    @classmethod
    def with_proxy(cls, proxy: ModelProxyABC) -> Self:
        return cls(proxy)

    def process(self, request: DuuiRequest):
        if request.sentences is None:
            raise ValueError("Sentences offsets are required for Towerparse")

        lang = request.language
        
        nlp = self.proxy[lang]['parser']
        preprocessor = self.proxy[lang]['preprocessor']

        annotations = []
        offsets = request.sentences
        
        for offset in offsets:
            document: StanzaDocument = preprocessor(request.text[offset.begin : offset.end])
            results: list[list] = nlp.parse(
                    lang,
                    [[word.text for word in document.sentences[0].words]],
                    batch_size=128, # TODO: enable batch processing of sentences batch
            )
            for ann in results:
                for word_tp, word_stanza in zip(ann, document.sentences[0].words):
                    word_stanza.head = word_tp[2]
                    word_stanza.deprel = word_tp[3]
                    
            annotations.append(document)
            
        return self.post_process(
            annotations,
            offsets,
        )

    @staticmethod
    def post_process(annotations: list[StanzaDocument], offsets: list[Offset]):

        results = DuuiResponse(sentences=None, tokens=[], dependencies=[])
        tokens_indices = 0
        for annotation, offset in zip(annotations, offsets):
            if not bool(TowerparseSentenceValidator.check(annotation.sentences[0]).is_standalone()):
                logger.info("Towerparse: Sentence is not standalone. Skipping.")
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
    
