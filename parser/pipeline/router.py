"""文件类型识别与解析路由（架构文档 4.2 / 4.5）。

Router 根据扩展名识别类型并分发到对应 Parser；新增格式只需
register() 一个新 Parser，无需修改下游代码。
"""

from __future__ import annotations

from typing import List, Optional

from ..common import DocumentParser, ParserError, detect_file_type, validate_file
from ..image import ImageParser
from ..office import (
    CsvParser,
    DocParser,
    DocxParser,
    ExcelParser,
    PptParser,
    PptxParser,
)
from ..pdf import PdfParser
from ..text import MarkdownParser, TextParser


class ParserRouter:
    """按文件类型分发解析器。"""

    def __init__(self) -> None:
        self._parsers: List[DocumentParser] = []

    def register(self, parser: DocumentParser) -> None:
        # 同类型的新解析器覆盖旧解析器
        for file_type in parser.supported_types:
            self._parsers = [p for p in self._parsers if not p.supports(file_type)]
        self._parsers.append(parser)

    def get_parser(self, file_type: str) -> DocumentParser:
        normalized = file_type.upper()
        for parser in self._parsers:
            if parser.supports(normalized):
                return parser
        raise ParserError(f"没有支持 {normalized} 格式的解析器")

    def list_supported_types(self) -> List[str]:
        types = set()
        for parser in self._parsers:
            types.update(t.upper() for t in parser.supported_types)
        return sorted(types)

    def route(self, file_path: str, *, check_magic: bool = True) -> DocumentParser:
        """识别并校验文件，返回对应解析器。"""
        ok, reason = validate_file(file_path, check_magic=check_magic)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")
        file_type = detect_file_type(file_path)
        return self.get_parser(file_type)

    def parse(
        self,
        file_path: str,
        *,
        document_id: Optional[str] = None,
        source_file_id: Optional[str] = None,
        task_id: Optional[str] = None,
        check_magic: bool = True,
    ) -> dict:
        """路由并解析文件，返回统一文档 dict。"""
        parser = self.route(file_path, check_magic=check_magic)
        result = parser.parse(file_path)
        return self._enrich(result, document_id, source_file_id, task_id)

    @staticmethod
    def _enrich(
        result: dict,
        document_id: Optional[str],
        source_file_id: Optional[str],
        task_id: Optional[str],
    ) -> dict:
        if document_id:
            result["document_id"] = document_id
        if source_file_id:
            result["source_file_id"] = source_file_id
            for element in result.get("elements", []):
                element["source_file_id"] = source_file_id
            for chunk in result.get("chunks", []):
                chunk["source_file_id"] = source_file_id
        if task_id:
            result.setdefault("metadata", {})["task_id"] = task_id
        return result


def create_default_router() -> ParserRouter:
    """创建注册了第一版全部解析器的默认 Router。"""
    router = ParserRouter()
    for parser in (
        DocxParser(),
        ExcelParser(),
        CsvParser(),
        DocParser(),
        PptParser(),
        PptxParser(),
        PdfParser(),
        ImageParser(),
        MarkdownParser(),
        TextParser(),
    ):
        router.register(parser)
    return router


_default_router: Optional[ParserRouter] = None


def get_default_router() -> ParserRouter:
    global _default_router
    if _default_router is None:
        _default_router = create_default_router()
    return _default_router
