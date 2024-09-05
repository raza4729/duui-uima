import sys

__meta__ = {
    "sys.version": sys.version,
    "NeuroNLP2 source": "NeuroNLP2 @ d473b68",
}


from app.parser.stackpointer_ import StackpointerModelProxy as ParserProxy
from app.parser.stackpointer_ import StackpointerProcessor as DependencyParser
from app.parser.stackpointer_ import StackpointerSentenceValidator as SentenceValidator

__all__ = [
    "ParserProxy",
    "DependencyParser",
    "SentenceValidator",
]
