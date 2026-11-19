"""DOCX 解析器（依据架构文档 4.3 / 4.4）。

使用 python-docx。可选依赖在方法内部 import，缺库时抛出清晰错误。
按文档正文顺序提取标题、段落和表格，保留标题层级与页码线索。
"""

from __future__ import annotations

import re
from typing import List, Optional, Tuple

from ..common import (
    DocumentParser,
    Element,
    ParserError,
    TableData,
    new_element_id,
)
from ..common.file_utils import validate_file

_HEADING_RE = re.compile(r"^(?:Heading|标题)\s*(\d+)", re.IGNORECASE)


class DocxParser(DocumentParser):
    supported_types = ("DOCX",)

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")

        try:
            from docx import Document as DocxDocument
            from docx.oxml.ns import qn
            from docx.table import Table
            from docx.text.paragraph import Paragraph
        except ImportError as exc:  # pragma: no cover - 取决于运行环境
            raise ParserError(
                "解析 DOCX 需要 python-docx，请先执行: pip install python-docx"
            ) from exc

        try:
            doc = DocxDocument(file_path)
        except Exception as exc:
            raise ParserError(f"无法打开 DOCX 文件: {exc}") from exc

        elements: List[Element] = []
        heading_path: List[str] = []
        page = 1
        index = 0

        body = doc.element.body
        for child in body.iterchildren():
            if child.tag == qn("w:p"):
                paragraph = Paragraph(child, doc)
                text = paragraph.text.strip()
                # 检测显式分页符，用于追踪页面位置
                if child.findall(".//" + qn("w:br")):
                    for br in child.findall(".//" + qn("w:br")):
                        if br.get(qn("w:type")) == "page":
                            page += 1

                if not text:
                    continue
                level, is_heading = self._heading_level(paragraph)
                index += 1
                if is_heading:
                    heading_path = heading_path[: max(0, level - 1)]
                    while len(heading_path) < level - 1:
                        heading_path.append("")
                    heading_path.append(text)
                    element = self.heading_element(
                        new_element_id(index), text, level,
                        page=page, heading_path=heading_path,
                    )
                else:
                    element = self.text_element(
                        new_element_id(index), text,
                        page=page, heading_path=heading_path,
                    )
                    hyperlinks = self._hyperlinks(paragraph, qn)
                    if hyperlinks:
                        element.metadata["hyperlinks"] = hyperlinks
                elements.append(element)

            elif child.tag == qn("w:tbl"):
                table_data = self._read_table(Table(child, doc))
                index += 1
                elements.append(
                    self.table_element(
                        new_element_id(index), table_data,
                        page=page, heading_path=heading_path,
                    )
                )

        document = self.build_document(
            file_path, "DOCX", elements,
            page_count=page,
            metadata={"heading_count": sum(1 for e in elements if e.type == "heading")},
        )
        return document.to_dict()

    # ------------------------------------------------------------------
    @staticmethod
    def _heading_level(paragraph) -> Tuple[int, bool]:
        style_name = ""
        try:
            style_name = paragraph.style.name or ""
        except Exception:
            pass
        match = _HEADING_RE.match(style_name)
        if match:
            return int(match.group(1)), True
        if style_name.lower() in ("title", "标题"):
            return 1, True
        return 0, False

    @staticmethod
    def _read_table(table) -> TableData:
        rows = []
        for row in table.rows:
            rows.append([cell.text.strip() for cell in row.cells])
        headers = rows[0] if rows else []
        body_rows = rows[1:] if rows else []
        return TableData(headers=headers, rows=body_rows)

    @staticmethod
    def _hyperlinks(paragraph, qn) -> List[str]:
        links = []
        try:
            for hyperlink in paragraph._p.findall(qn("w:hyperlink")):
                rel_id = hyperlink.get(qn("r:id"))
                if rel_id and rel_id in paragraph.part.rels:
                    links.append(paragraph.part.rels[rel_id].target_ref)
        except Exception:
            pass
        return links
