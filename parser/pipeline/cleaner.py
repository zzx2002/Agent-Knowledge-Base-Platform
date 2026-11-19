"""解析后文本清洗（架构文档 4.2 的「文本清洗」环节）。"""

from __future__ import annotations

import re
import unicodedata
from dataclasses import dataclass
from typing import Dict, List, Optional

from ..common import Document

_CONTROL_CHARS = re.compile(r"[\x00-\x08\x0b\x0c\x0e-\x1f\x7f]")
_MULTI_SPACES = re.compile(r"[ \t\u3000]{2,}")
_MULTI_NEWLINES = re.compile(r"\n{3,}")


@dataclass
class CleanConfig:
    unicode_nfkc: bool = True
    normalize_width: bool = True
    strip_control_chars: bool = True
    collapse_spaces: bool = True
    collapse_newlines: bool = True
    strip_lines: bool = True
    drop_empty_elements: bool = True
    #: 需要剔除的页眉/页脚重复文本（前缀匹配）
    drop_line_prefixes: List[str] = None  # type: ignore[assignment]

    def __post_init__(self) -> None:
        if self.drop_line_prefixes is None:
            self.drop_line_prefixes = []


class TextCleaner:
    """对 Element.content 做无损语义的快速清洗。"""

    def __init__(self, config: Optional[CleanConfig] = None) -> None:
        self.config = config or CleanConfig()

    def clean(self, text: str) -> str:
        cfg = self.config
        if cfg.unicode_nfkc:
            text = unicodedata.normalize("NFKC", text)
        if cfg.strip_control_chars:
            text = _CONTROL_CHARS.sub("", text)
        text = text.replace("\r\n", "\n").replace("\r", "\n")
        if cfg.strip_lines:
            lines = [line.strip() for line in text.split("\n")]
        else:
            lines = text.split("\n")
        if cfg.drop_line_prefixes:
            lines = [
                line for line in lines
                if not any(line.startswith(p) for p in cfg.drop_line_prefixes)
            ]
        text = "\n".join(lines)
        if cfg.collapse_spaces:
            text = _MULTI_SPACES.sub(" ", text)
        if cfg.collapse_newlines:
            text = _MULTI_NEWLINES.sub("\n\n", text)
        return text.strip()

    def clean_document(self, document: Document) -> Document:
        kept = []
        for element in document.elements:
            element.content = self.clean(element.content)
            if self.config.drop_empty_elements and not element.content:
                continue
            if element.table_data:
                element.table_data.headers = [self.clean(h) for h in element.table_data.headers]
                element.table_data.rows = [
                    [self.clean(cell) for cell in row] for row in element.table_data.rows
                ]
            kept.append(element)
        document.elements = kept
        document.metadata.setdefault("cleaned", True)
        return document

    def clean_dict(self, data: Dict) -> Dict:
        """对 Document.to_dict() 的结果原地清洗并返回。"""
        document = Document.from_dict(data)
        return self.clean_document(document).to_dict()
