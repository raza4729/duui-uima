import itertools
import logging
from typing import Self

import spacy
from app.abc import ModelProxyABC, ProcessorABC, SentenceValidatorABC
from app.model import DuuiRequest, DuuiResponse, Offset, UimaDependency, UimaToken
from app.utils import EOS_MARKERS, TokenToId
from spacy.language import Language
from spacy.tokens import Doc, Span

logger = logging.getLogger(__name__)

# model_en = "en_core_web_trf"
model_en = "en_core_web_sm"
# model_de = "de_dep_news_trf"
model_de = "de_core_news_sm"
model_map = {
    "en": model_en,
    "en_US": model_en,
    "en_GB": model_en,
    "en_AU": model_en,
    "en_CA": model_en,
    "de": model_de,
    "de_DE": model_de,
    "de_AT": model_de,
    "de_CH": model_de,
}


class SpaCyModelProxy(ModelProxyABC[Language]):
    def __init__(self):
        self.models = {}

    def __getitem__(self, lang: str):
        lang = model_map.get(lang.replace("-", "_"), lang)
        if self.models.get(lang) is None:
            logger.info(f"spacy.load('{lang}')")
            device = "cuda" if spacy.prefer_gpu() else "cpu"
            nlp = spacy.load(
                lang,
                disable=[
                    "ner",
                ],
            )
            nlp.add_pipe("sentencizer")
            logger.info(
                f"spacy: loaded\npipeline({device})\n  "
                + "\n  ".join(nlp.component_names)
            )
            self.models[lang] = nlp
        return self.models[lang]


class SpaCySentenceValidator(SentenceValidatorABC[Span]):
    @classmethod
    def check(cls, sentence: Span):
        if not sentence.text.strip()[0].isupper():
            return cls(False)
        if sentence.text.strip()[-1] not in EOS_MARKERS:
            return cls(True, False)
        if not any((tok.pos_ == "VERB" or tok.pos_ == "AUX" for tok in sentence)):
            return cls(True, True, False)
        if sentence.text.count('"') % 2 != 0:
            return cls(True, True, True, False)
        if sentence.text.count("(") != sentence.text.count(")"):
            return cls(True, True, True, True, False)
        return cls(True, True, True, True, True)


class SpaCyProcessor(ProcessorABC[Doc]):
    def __init__(self, proxy: SpaCyModelProxy) -> None:
        super().__init__()
        self.proxy = proxy

    @classmethod
    def with_proxy(cls, proxy: ModelProxyABC) -> Self:
        return cls(proxy)

    def process(self, request: DuuiRequest):
        nlp = self.proxy[request.language]
        offsets = request.sentences or request.paragraphs
        if offsets:
            annotations = []
            for offset in offsets:
                annotations.append(nlp(request.text[offset.begin : offset.end]))
        else:
            annotations = [nlp(request.text)]
            offsets = [Offset(begin=0, end=0)]

        return self.post_process(
            annotations,
            offsets,
            validate=request.validate_sentences,
        )

    @staticmethod
    def post_process(
        annotations: list[Doc],
        offsets: list[Offset],
        validate: bool = True,
    ):
        token_to_id = TokenToId()
        results = DuuiResponse(sentences=[], tokens=[], dependencies=[])
        for doc, offset in zip(annotations, offsets):
            # Add a None to the end of the iterator to include the last sentence if it ends with a semicolon.
            it = itertools.chain(doc.sents, (None,))

            semicolon_begin = None
            for sentence in it:
                if sentence and sentence.text.strip().endswith(";"):
                    semicolon_begin = semicolon_begin or sentence.start
                    continue

                # If the previous sentence(s) ends with a semicolon, then the current sentence is a continuation of the previous sentence(s).
                if semicolon_begin is not None:
                    # If the very last sentence ended with a semicolon, we include it anyways.
                    sentence_end = sentence and sentence.end
                    sentence = doc[semicolon_begin:sentence_end]

                # If the last sentence in a document ends with a semicolon, the following element in `it` will be None.
                # In this case, the above lines will resolve to `sentence = doc[last_sentence.begin:None]`, which works as expected.
                # If the last sentence did not end with a semicolon, `sentence == None` here.
                if sentence is None:
                    continue

                checked = SpaCySentenceValidator.check(sentence)
                if validate and not checked.is_standalone():
                    continue

                results.sentences.append(
                    Offset(
                        begin=sentence[0].idx + offset.begin,
                        # add one to point to the next character after sentence end
                        end=sentence[-1].idx + offset.begin + 1,
                    )
                )
                for token in sentence:
                    token_idx = token_to_id.add(token)

                    begin = token.idx + offset.begin
                    # No need to add one here: `end` already points to the next character
                    end = begin + len(token)
                    results.tokens.append(
                        UimaToken(
                            begin=begin,
                            end=end,
                            idx=token_idx,
                            pos=doc[token.i].pos_,
                            tag=doc[token.i].tag_,
                            lemma=doc[token.i].lemma_,
                            morph={
                                k.lower(): v for k, v in token.morph.to_dict().items()
                            },
                        )
                    )

                for token in sentence:
                    begin = token.idx + offset.begin
                    end = begin + len(token)
                    results.dependencies.append(
                        UimaDependency(
                            begin=begin,
                            end=end,
                            governor=token_to_id[token.head],
                            dependent=token_to_id[token],
                            type=token.dep_.lower(),
                            flavor="basic",
                        )
                    )

        return results
