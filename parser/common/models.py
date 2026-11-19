"""统一文档数据结构定义。

所有解析器最终都把结果序列化为本文档中定义的 dataclass 结构，
从而把「格式相关的解析逻辑」与下游的清洗、分块、向量化、检索解耦。
字段设计参考架构文档 4.4，并补充了 chunk_id、parent_chunk_id、
element_id、source_file_id、heading_path、page_start、page_end、
bounding_box、metadata 等追踪字段。
"""

from __future__ import annotations

from dataclasses import asdict, dataclass, field
from typing import Any, Dict, List, Optional


@dataclass
class BoundingBox:
    """元素在页面上的矩形坐标，原点为左上角。"""

    x0: float
    y0: float
    x1: float
    y1: float
    page: int = 1

    @property
    def width(self) -> float:
        return abs(self.x1 - self.x0)

    @property
    def height(self) -> float:
        return abs(self.y1 - self.y0)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Optional[Dict[str, Any]]) -> Optional["BoundingBox"]:
        if not data:
            return None
        return cls(
            x0=float(data.get("x0", 0.0)),
            y0=float(data.get("y0", 0.0)),
            x1=float(data.get("x1", 0.0)),
            y1=float(data.get("y1", 0.0)),
            page=int(data.get("page", 1)),
        )


@dataclass
class TableData:
    """表格结构化数据：表头 + 数据行。"""

    headers: List[str] = field(default_factory=list)
    rows: List[List[str]] = field(default_factory=list)
    caption: Optional[str] = None

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Optional[Dict[str, Any]]) -> Optional["TableData"]:
        if not data:
            return None
        return cls(
            headers=[str(h) for h in data.get("headers", [])],
            rows=[[str(c) for c in row] for row in data.get("rows", [])],
            caption=data.get("caption"),
        )


@dataclass
class Element:
    """文档中的最小语义单元：标题、段落、表格、图片、幻灯片等。"""

    element_id: str
    type: str
    content: str
    level: Optional[int] = None
    page: Optional[int] = None
    page_start: Optional[int] = None
    page_end: Optional[int] = None
    heading_path: List[str] = field(default_factory=list)
    bounding_box: Optional[BoundingBox] = None
    table_data: Optional[TableData] = None
    source_file_id: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        return data

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "Element":
        return cls(
            element_id=str(data.get("element_id", "")),
            type=str(data.get("type", "text")),
            content=str(data.get("content", "")),
            level=data.get("level"),
            page=data.get("page"),
            page_start=data.get("page_start"),
            page_end=data.get("page_end"),
            heading_path=list(data.get("heading_path", [])),
            bounding_box=BoundingBox.from_dict(data.get("bounding_box")),
            table_data=TableData.from_dict(data.get("table_data")),
            source_file_id=data.get("source_file_id"),
            metadata=dict(data.get("metadata", {})),
        )


@dataclass
class Chunk:
    """检索用的文本块，支持 Parent-Child 两级结构。"""

    chunk_id: str
    content: str
    parent_chunk_id: Optional[str] = None
    element_id: Optional[str] = None
    element_ids: List[str] = field(default_factory=list)
    chunk_type: str = "child"
    level: int = 0
    token_count: int = 0
    page_start: Optional[int] = None
    page_end: Optional[int] = None
    heading_path: List[str] = field(default_factory=list)
    chunk_strategy: str = "paragraph"
    source_file_id: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "Chunk":
        return cls(
            chunk_id=str(data.get("chunk_id", "")),
            content=str(data.get("content", "")),
            parent_chunk_id=data.get("parent_chunk_id"),
            element_id=data.get("element_id"),
            element_ids=list(data.get("element_ids", [])),
            chunk_type=str(data.get("chunk_type", "child")),
            level=int(data.get("level", 0)),
            token_count=int(data.get("token_count", 0)),
            page_start=data.get("page_start"),
            page_end=data.get("page_end"),
            heading_path=list(data.get("heading_path", [])),
            chunk_strategy=str(data.get("chunk_strategy", "paragraph")),
            source_file_id=data.get("source_file_id"),
            metadata=dict(data.get("metadata", {})),
        )


@dataclass
class Document:
    """解析器的统一输出：一个文件对应一个 Document。"""

    document_id: str
    file_name: str
    file_type: str
    elements: List[Element] = field(default_factory=list)
    chunks: List[Chunk] = field(default_factory=list)
    source_file_id: Optional[str] = None
    page_count: Optional[int] = None
    chunk_id: Optional[str] = None
    metadata: Dict[str, Any] = field(default_factory=dict)

    def add_element(self, element: Element) -> None:
        self.elements.append(element)

    def to_dict(self) -> Dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "Document":
        return cls(
            document_id=str(data.get("document_id", "")),
            file_name=str(data.get("file_name", "")),
            file_type=str(data.get("file_type", "")).upper(),
            elements=[Element.from_dict(e) for e in data.get("elements", [])],
            chunks=[Chunk.from_dict(c) for c in data.get("chunks", [])],
            source_file_id=data.get("source_file_id"),
            page_count=data.get("page_count"),
            chunk_id=data.get("chunk_id"),
            metadata=dict(data.get("metadata", {})),
        )


def new_element_id(index: int) -> str:
    """生成稳定的元素 ID，便于同一文档重复解析时结果可比。"""
    return f"element_{index:04d}"


def new_chunk_id(index: int) -> str:
    return f"chunk_{index:04d}"
