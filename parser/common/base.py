"""解析器抽象基类。

新增文件格式时只需实现 DocumentParser 并注册到 ParserRouter，
不需要修改清洗、分块、向量化等下游处理链路（架构文档 4.5）。
"""

from __future__ import annotations

import uuid
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Dict, Iterable, List, Optional

from .models import Document, Element, TableData


class DocumentParser(ABC):
    """所有格式解析器的统一接口。"""

    #: 该解析器支持的文件类型集合（大写），子类可直接覆盖。
    supported_types: Iterable[str] = ()

    @abstractmethod
    def supports(self, file_type: str) -> bool:
        """判断本解析器是否支持给定的文件类型。"""

    @abstractmethod
    def parse(self, file_path: str) -> dict:
        """解析文件并返回统一文档结构的 dict。"""

    def name(self) -> str:
        return self.__class__.__name__

    # ------------------------------------------------------------------
    # 子类可复用的构建辅助方法
    # ------------------------------------------------------------------
    @staticmethod
    def _new_document_id(file_path: str) -> str:
        return "doc_" + uuid.uuid5(uuid.NAMESPACE_URL, str(file_path)).hex[:16]

    def build_document(
        self,
        file_path: str,
        file_type: str,
        elements: List[Element],
        *,
        source_file_id: Optional[str] = None,
        page_count: Optional[int] = None,
        metadata: Optional[dict] = None,
    ) -> Document:
        """把解析得到的元素列表包装成统一 Document。"""
        return Document(
            document_id=self._new_document_id(file_path),
            file_name=Path(file_path).name,
            file_type=file_type.upper(),
            elements=elements,
            source_file_id=source_file_id,
            page_count=page_count,
            metadata={
                "parser": self.name(),
                "original_path": str(file_path),
                **(metadata or {}),
            },
        )

    @staticmethod
    def text_element(
        element_id: str,
        text: str,
        *,
        page: Optional[int] = None,
        heading_path: Optional[List[str]] = None,
        metadata: Optional[dict] = None,
    ) -> Element:
        return Element(
            element_id=element_id,
            type="text",
            content=text,
            page=page,
            page_start=page,
            page_end=page,
            heading_path=list(heading_path or []),
            metadata=dict(metadata or {}),
        )

    @staticmethod
    def heading_element(
        element_id: str,
        text: str,
        level: int,
        *,
        page: Optional[int] = None,
        heading_path: Optional[List[str]] = None,
        metadata: Optional[dict] = None,
    ) -> Element:
        return Element(
            element_id=element_id,
            type="heading",
            content=text,
            level=level,
            page=page,
            page_start=page,
            page_end=page,
            heading_path=list(heading_path or []),
            metadata=dict(metadata or {}),
        )

    @staticmethod
    def table_element(
        element_id: str,
        table: TableData,
        *,
        page: Optional[int] = None,
        heading_path: Optional[List[str]] = None,
        metadata: Optional[dict] = None,
    ) -> Element:
        content = " | ".join(table.headers)
        if table.rows:
            content += "\n" + "\n".join(" | ".join(row) for row in table.rows)
        return Element(
            element_id=element_id,
            type="table",
            content=content,
            page=page,
            page_start=page,
            page_end=page,
            heading_path=list(heading_path or []),
            table_data=table,
            metadata=dict(metadata or {}),
        )


class ParserError(RuntimeError):
    """解析过程中的统一异常类型。"""
