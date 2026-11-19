"""结构化分块与父子分块（架构文档 5.1 / 5.2）。

策略：结构优先、长度兜底。按文件类型选择按标题 / 幻灯片 / 页 / Sheet /
段落 / 行组分块，再在结构单元内部按 token 长度切片，并生成
Parent-Child 两级 Chunk。
"""

from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from typing import Dict, List, Optional

from ..common import Chunk, Document, Element, new_chunk_id

# 各文件类型的默认分块策略
STRATEGY_BY_FILE_TYPE: Dict[str, str] = {
    "DOC": "heading",
    "DOCX": "heading",
    "PPT": "slide",
    "PPTX": "slide",
    "PDF": "page",
    "MD": "heading",
    "TXT": "paragraph",
    "XLS": "sheet",
    "XLSX": "sheet",
    "CSV": "row_group",
    "PNG": "ocr",
    "JPG": "ocr",
}

_CJK_RE = re.compile(r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]")
_TOKEN_RE = re.compile(
    r"[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff]|[A-Za-z0-9_]+|\s+|[^\s]"
)


@dataclass
class ChunkConfig:
    chunk_size: int = 640          # 子块目标 token 数（500～800）
    chunk_overlap: int = 96        # 重叠 token 数（80～120）
    min_chunk_size: int = 100
    max_chunk_size: int = 1536
    parent_chunk_size: int = 2048  # 父块 token 数（1500～3000）
    chunk_strategy: str = "auto"   # auto / heading / slide / page / sheet / paragraph / row_group / ocr
    parent_child_enabled: bool = True
    rows_per_group: int = 30
    file_type: Optional[str] = None

    def effective_strategy(self, file_type: str) -> str:
        if self.chunk_strategy and self.chunk_strategy != "auto":
            return self.chunk_strategy
        return STRATEGY_BY_FILE_TYPE.get(file_type.upper(), "paragraph")


@dataclass
class _Group:
    elements: List[Element] = field(default_factory=list)
    text: str = ""
    heading_path: List[str] = field(default_factory=list)
    page_start: Optional[int] = None
    page_end: Optional[int] = None
    label: str = ""

    def add(self, element: Element, text: str) -> None:
        self.elements.append(element)
        self.text = (self.text + "\n\n" + text).strip() if self.text else text.strip()
        if element.page_start is not None:
            self.page_start = element.page_start if self.page_start is None else min(
                self.page_start, element.page_start
            )
        if element.page_end is not None:
            self.page_end = element.page_end if self.page_end is None else max(
                self.page_end, element.page_end
            )


class StructuredChunker:
    """把 Document 切分为带父子关系的 Chunk 列表。"""

    def __init__(self, config: Optional[ChunkConfig] = None) -> None:
        self.config = config or ChunkConfig()

    # ------------------------------------------------------------------
    # Token 估算与文本切片
    # ------------------------------------------------------------------
    @staticmethod
    def estimate_tokens(text: str) -> int:
        """近似 token 计数：中日韩字符按 1 token，其他按 0.25 token。"""
        if not text:
            return 0
        weight = 0.0
        for char in text:
            if _CJK_RE.match(char):
                weight += 1.0
            elif char.isspace():
                continue
            else:
                weight += 0.25
        return max(1, math.ceil(weight))

    @staticmethod
    def _tokenize(text: str) -> List[str]:
        return _TOKEN_RE.findall(text)

    @staticmethod
    def _detokenize(tokens: List[str]) -> str:
        result = "".join(" " if token.isspace() else token for token in tokens)
        return re.sub(r" {2,}", " ", result).strip()

    def split_text(self, text: str, size: int, overlap: int) -> List[str]:
        tokens = self._tokenize(text)
        if not tokens:
            return []
        if len(tokens) <= size:
            return [self._detokenize(tokens)]
        step = max(1, size - overlap)
        pieces: List[str] = []
        start = 0
        total = len(tokens)
        while start < total:
            end = min(start + size, total)
            piece = self._detokenize(tokens[start:end])
            if piece:
                pieces.append(piece)
            if end >= total:
                break
            start += step
        return pieces

    # ------------------------------------------------------------------
    # 结构化分组
    # ------------------------------------------------------------------
    def chunk_document(self, document: Document) -> Document:
        strategy = self.config.effective_strategy(
            self.config.file_type or document.file_type
        )
        groups = self._group_elements(document, strategy)
        groups = self._merge_small_groups(groups)
        document.chunks = self._make_chunks(groups, strategy)
        document.chunk_id = document.chunks[0].chunk_id if document.chunks else None
        document.metadata.setdefault("chunk_strategy", strategy)
        document.metadata.setdefault("chunk_count", len(document.chunks))
        return document

    def chunk_dict(self, data: Dict) -> Dict:
        document = Document.from_dict(data)
        return self.chunk_document(document).to_dict()

    def _group_elements(self, document: Document, strategy: str) -> List[_Group]:
        groups: List[_Group] = []

        if strategy == "heading":
            current = _Group()
            for element in document.elements:
                if element.type == "heading":
                    if current.elements:
                        groups.append(current)
                    current = _Group(
                        heading_path=list(element.heading_path),
                        label=element.content,
                    )
                    current.add(element, element.content)
                else:
                    current.add(element, self._element_text(element))
            if current.elements:
                groups.append(current)

        elif strategy == "slide":
            buckets: Dict[str, _Group] = {}
            order: List[str] = []
            for element in document.elements:
                key = str(
                    element.metadata.get("slide_number")
                    or element.metadata.get("slide_index")
                    or (element.heading_path[0] if element.heading_path else element.element_id)
                )
                if key not in buckets:
                    buckets[key] = _Group(
                        heading_path=list(element.heading_path), label=key,
                    )
                    order.append(key)
                buckets[key].add(element, self._element_text(element))
            groups = [buckets[key] for key in order]

        elif strategy == "page":
            buckets: Dict[int, _Group] = {}
            for element in document.elements:
                key = element.page or element.page_start or 0
                group = buckets.setdefault(
                    key,
                    _Group(
                        heading_path=list(element.heading_path),
                        label=f"page_{key}",
                    ),
                )
                group.add(element, self._element_text(element))
            groups = [buckets[key] for key in sorted(buckets)]

        elif strategy == "sheet":
            buckets: Dict[str, _Group] = {}
            order = []
            for element in document.elements:
                key = str(
                    element.metadata.get("sheet_name")
                    or (element.heading_path[0] if element.heading_path else element.element_id)
                )
                if key not in buckets:
                    buckets[key] = _Group(heading_path=[key], label=key)
                    order.append(key)
                buckets[key].add(element, self._element_text(element, row_groups=True))
            groups = [buckets[key] for key in order]

        elif strategy == "row_group":
            for element in document.elements:
                group = _Group(
                    heading_path=list(element.heading_path),
                    label=element.metadata.get("sheet_name", element.element_id),
                )
                group.add(element, self._element_text(element, row_groups=True))
                groups.append(group)

        elif strategy == "ocr":
            for element in document.elements:
                group = _Group(heading_path=list(element.heading_path), label="ocr")
                group.add(element, element.content)
                groups.append(group)

        else:  # paragraph
            for element in document.elements:
                group = _Group(heading_path=list(element.heading_path), label=element.element_id)
                group.add(element, self._element_text(element))
                groups.append(group)

        return [g for g in groups if g.elements and g.text.strip()]

    def _element_text(self, element: Element, *, row_groups: bool = False) -> str:
        if row_groups and element.table_data:
            return self._table_text_with_headers(element)
        return element.content

    def _table_text_with_headers(self, element: Element) -> str:
        table = element.table_data
        if table is None:
            return element.content
        header = " | ".join(table.headers)
        if not table.rows:
            return header
        size = max(1, self.config.rows_per_group)
        blocks = []
        for start in range(0, len(table.rows), size):
            rows = table.rows[start:start + size]
            lines = [header] + [" | ".join(row) for row in rows]
            blocks.append("\n".join(lines))
        return "\n\n".join(blocks)

    def _merge_small_groups(self, groups: List[_Group]) -> List[_Group]:
        merged: List[_Group] = []
        for group in groups:
            if merged and self.estimate_tokens(group.text) < self.config.min_chunk_size:
                target = merged[-1]
                target.elements.extend(group.elements)
                target.text = (target.text + "\n\n" + group.text).strip()
                if group.page_end is not None:
                    target.page_end = (
                        group.page_end if target.page_end is None
                        else max(target.page_end, group.page_end)
                    )
            else:
                merged.append(group)
        return merged

    # ------------------------------------------------------------------
    # Chunk 生成
    # ------------------------------------------------------------------
    def _make_chunks(self, groups: List[_Group], strategy: str) -> List[Chunk]:
        chunks: List[Chunk] = []
        chunk_index = 0

        for group in groups:
            if self.config.parent_child_enabled:
                parents = self.split_text(group.text, self.config.parent_chunk_size, 0)
            else:
                parents = [group.text]

            for parent_text in parents:
                parent_id: Optional[str] = None
                if self.config.parent_child_enabled:
                    chunk_index += 1
                    parent_id = new_chunk_id(chunk_index)
                    chunks.append(
                        self._build_chunk(
                            chunk_id=parent_id,
                            content=parent_text,
                            group=group,
                            strategy=strategy,
                            chunk_type="parent",
                            level=1,
                            parent_chunk_id=None,
                            chunk_index=chunk_index,
                        )
                    )

                children = self.split_text(
                    parent_text, self.config.chunk_size, self.config.chunk_overlap
                )
                for child_text in children:
                    chunk_index += 1
                    chunks.append(
                        self._build_chunk(
                            chunk_id=new_chunk_id(chunk_index),
                            content=child_text,
                            group=group,
                            strategy=strategy,
                            chunk_type="child",
                            level=0,
                            parent_chunk_id=parent_id,
                            chunk_index=chunk_index,
                        )
                    )
        return chunks

    def _build_chunk(
        self,
        *,
        chunk_id: str,
        content: str,
        group: _Group,
        strategy: str,
        chunk_type: str,
        level: int,
        parent_chunk_id: Optional[str],
        chunk_index: int,
    ) -> Chunk:
        return Chunk(
            chunk_id=chunk_id,
            content=content,
            parent_chunk_id=parent_chunk_id,
            element_id=group.elements[0].element_id if group.elements else None,
            element_ids=[e.element_id for e in group.elements],
            chunk_type=chunk_type,
            level=level,
            token_count=self.estimate_tokens(content),
            page_start=group.page_start,
            page_end=group.page_end,
            heading_path=list(group.heading_path),
            chunk_strategy=strategy,
            source_file_id=group.elements[0].source_file_id if group.elements else None,
            metadata={
                "group_label": group.label,
                "chunk_index": chunk_index,
                "element_count": len(group.elements),
            },
        )
