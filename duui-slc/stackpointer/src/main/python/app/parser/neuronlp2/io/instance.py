__author__ = "max; modified by Manuel Stoeckel"

__all__ = ["Sentence", "DependencyInstance", "NERInstance"]


from dataclasses import dataclass


@dataclass
class Sentence:
    words: list[str]
    word_ids: list[int]
    char_seqs: list[str]
    char_id_seqs: list[int]

    def length(self):
        return len(self.words)


@dataclass
class DependencyInstance:
    sentence: Sentence
    postags: list[str]
    pos_ids: list[int]
    heads: list[int]
    types: list[str]
    type_ids: list[str]

    def length(self):
        return self.sentence.length()


@dataclass
class NERInstance:
    sentence: Sentence
    postags: list[str]
    pos_ids: list[int]
    chunk_tags: list[str]
    chunk_ids: list[int]
    ner_tags: list[str]
    ner_ids: list[int]

    def length(self):
        return self.sentence.length()
