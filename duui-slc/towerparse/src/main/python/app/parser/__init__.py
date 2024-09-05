import sys

__meta__ = {
    "sys.version": sys.version,
    "Towerparse source": "towerparse @ 29d0170",
}


from app.parser.towerparse_ import TowerparseModelProxy as ParserProxy
from app.parser.towerparse_ import TowerparseProcessor as DependencyParser
from app.parser.towerparse_ import TowerparseSentenceValidator as SentenceValidator

__all__ = [
    "ParserProxy",
    "DependencyParser",
    "SentenceValidator",
]
