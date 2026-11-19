"""PDF 解析器（依据架构文档 4.3 / 4.4）。

使用 pdfplumber 提取文本与表格，按页生成元素，保留页码。
标题通过字号启发式识别；扫描件可结合 OCR 服务补充（见 ai/ocr）。
可选依赖在方法内部 import，缺库时抛清晰错误。
"""

from __future__ import annotations

import re
import statistics
from typing import List, Optional, Tuple

from ..common import (
    DocumentParser,
    Element,
    ParserError,
    TableData,
    new_element_id,
)
from ..common.file_utils import validate_file

_HEADING_PATTERNS = (
    re.compile(r"^\s*第[一二三四五六七八九十百\d]+[章节篇部]\s"),
    re.compile(r"^\s*\d+(?:\.\d+){0,3}\s+\S"),
    re.compile(r"^\s*[A-Z]\.\s+\S"),
)


class PdfParser(DocumentParser):
    supported_types = ("PDF",)

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")

        try:
            import pdfplumber
        except ImportError as exc:  # pragma: no cover
            raise ParserError(
                "解析 PDF 需要 pdfplumber，请先执行: pip install pdfplumber"
            ) from exc

        elements: List[Element] = []
        heading_path: List[str] = []
        index = 0
        page_count = 0

        try:
            with pdfplumber.open(file_path) as pdf:
                page_count = len(pdf.pages)
                for page_number, page in enumerate(pdf.pages, start=1):
                    tables = self._extract_tables(page)
                    for table_data, bbox in tables:
                        index += 1
                        element = self.table_element(
                            new_element_id(index), table_data,
                            page=page_number, heading_path=heading_path,
                        )
                        if bbox:
                            from ..common import BoundingBox

                            element.bounding_box = BoundingBox(
                                x0=bbox[0], y0=bbox[1], x1=bbox[2], y1=bbox[3],
                                page=page_number,
                            )
                        elements.append(element)

                    text = page.extract_text() or ""
                    if not text.strip():
                        index += 1
                        elements.append(
                            self.text_element(
                                new_element_id(index),
                                "[占位] 本页未提取到文本，疑似扫描件，可调用 OCR 服务补充。",
                                page=page_number,
                                heading_path=heading_path,
                                metadata={"scan_page": True},
                            )
                        )
                        continue

                    sizes = self._font_sizes(page)
                    median_size = statistics.median(sizes) if sizes else 10.0
                    for raw_line in text.splitlines():
                        line = raw_line.strip()
                        if not line:
                            continue
                        index += 1
                        level, is_heading = self._is_heading(line, sizes, median_size)
                        if is_heading:
                            heading_path = heading_path[: max(0, level - 1)]
                            while len(heading_path) < level - 1:
                                heading_path.append("")
                            heading_path.append(line)
                            element = self.heading_element(
                                new_element_id(index), line, level,
                                page=page_number, heading_path=heading_path,
                            )
                        else:
                            element = self.text_element(
                                new_element_id(index), line,
                                page=page_number, heading_path=heading_path,
                            )
                        elements.append(element)
        except Exception as exc:
            raise ParserError(f"无法解析 PDF 文件: {exc}") from exc

        return self.build_document(
            file_path, "PDF", elements,
            page_count=page_count,
            metadata={"table_count": sum(1 for e in elements if e.type == "table")},
        ).to_dict()

    # ------------------------------------------------------------------
    @staticmethod
    def _extract_tables(page) -> List[Tuple[TableData, Optional[tuple]]]:
        results: List[Tuple[TableData, Optional[tuple]]] = []
        try:
            for table in page.extract_tables() or []:
                rows = [
                    ["" if cell is None else str(cell).replace("\n", " ") for cell in row]
                    for row in table
                ]
                if not rows:
                    continue
                results.append(
                    (TableData(headers=rows[0], rows=rows[1:]), None)
                )
        except Exception:
            pass
        return results

    @staticmethod
    def _font_sizes(page) -> List[float]:
        sizes: List[float] = []
        try:
            chars = page.chars
            sizes = [float(c.get("size", 0)) for c in chars if c.get("size")]
        except Exception:
            pass
        return sizes

    @classmethod
    def _is_heading(
        cls, line: str, sizes: List[float], median_size: float
    ) -> Tuple[int, bool]:
        for pattern in _HEADING_PATTERNS:
            if pattern.match(line):
                dots = re.match(r"^\s*(\d+(?:\.\d+)*)", line)
                level = dots.group(1).count(".") + 1 if dots else 1
                return min(level, 6), True
        if sizes and median_size > 0:
            # 采用页面中较大字号作为标题特征
            threshold = median_size * 1.15
            large = [s for s in sizes if s >= threshold]
            if large and len(line) <= 60:
                return 1, True
        return 0, False
