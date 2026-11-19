"""Excel / CSV 解析器（依据架构文档 4.3 / 4.4 / 5.1）。

XLSX 使用 openpyxl 流式读取（read_only），CSV 使用标准库 csv 并自动
识别编码与分隔符。旧版 XLS 需要 LibreOffice 转换，见 libreoffice_adapter。
可选依赖在方法内部 import。
"""

from __future__ import annotations

import csv
import tempfile
from pathlib import Path
from typing import List, Optional

from ..common import (
    DocumentParser,
    Element,
    ParserError,
    TableData,
    new_element_id,
)
from ..common.file_utils import detect_encoding, detect_file_type, validate_file


def detect_delimiter(sample: str) -> str:
    """用 csv.Sniffer 推断分隔符，失败时按常见分隔符出现次数猜测。"""
    try:
        return csv.Sniffer().sniff(sample, delimiters=",;\t|").delimiter
    except csv.Error:
        candidates = [",", "\t", ";", "|"]
        return max(candidates, key=lambda d: sample.count(d))


class ExcelParser(DocumentParser):
    supported_types = ("XLS", "XLSX")

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")

        file_type = detect_file_type(file_path)
        if file_type == "XLS":
            from .libreoffice_adapter import LibreOfficeConverter

            converter = LibreOfficeConverter()
            with tempfile.TemporaryDirectory(prefix="kb_xls_convert_") as tmp_dir:
                converted_path = converter.convert(file_path, tmp_dir, "xlsx")
                parsed = self.parse(converted_path)
                parsed["file_name"] = Path(file_path).name
                parsed["file_type"] = "XLS"
                metadata = parsed.setdefault("metadata", {})
                metadata["converter"] = "LibreOffice"
                metadata["converted_ext"] = "xlsx"
                return parsed

        try:
            from openpyxl import load_workbook
        except ImportError as exc:  # pragma: no cover
            raise ParserError(
                "解析 XLSX 需要 openpyxl，请先执行: pip install openpyxl"
            ) from exc

        try:
            workbook = load_workbook(file_path, read_only=True, data_only=True)
        except Exception as exc:
            raise ParserError(f"无法打开 XLSX 文件: {exc}") from exc

        elements: List[Element] = []
        index = 0
        try:
            for sheet_index, sheet in enumerate(workbook.worksheets):
                rows_iter = sheet.iter_rows(values_only=True)
                all_rows = [["" if v is None else str(v) for v in row] for row in rows_iter]
                if not all_rows:
                    continue
                headers = all_rows[0]
                body_rows = all_rows[1:]
                table_data = TableData(headers=headers, rows=body_rows)
                index += 1
                element = self.table_element(
                    new_element_id(index),
                    table_data,
                    heading_path=[sheet.title],
                    metadata={
                        "sheet_name": sheet.title,
                        "sheet_index": sheet_index,
                        "row_count": len(body_rows),
                        "column_count": len(headers),
                    },
                )
                elements.append(element)
        finally:
            workbook.close()

        return self.build_document(
            file_path, "XLSX", elements,
            metadata={"sheet_count": len(elements)},
        ).to_dict()


class CsvParser(DocumentParser):
    supported_types = ("CSV",)

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str, *, max_rows: Optional[int] = None) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")

        encoding = detect_encoding(file_path)
        sample = Path(file_path).read_text(encoding=encoding)[: 8 * 1024]
        delimiter = detect_delimiter(sample)

        try:
            with open(file_path, "r", encoding=encoding, newline="") as handle:
                reader = csv.reader(handle, delimiter=delimiter)
                rows = []
                for row_index, row in enumerate(reader):
                    if max_rows is not None and row_index >= max_rows:
                        break
                    rows.append([cell.strip() for cell in row])
        except (OSError, UnicodeDecodeError, csv.Error) as exc:
            raise ParserError(f"无法解析 CSV 文件: {exc}") from exc

        if not rows:
            return self.build_document(file_path, "CSV", []).to_dict()

        headers = rows[0]
        body_rows = rows[1:]
        table_data = TableData(headers=headers, rows=body_rows)
        element = self.table_element(
            new_element_id(1),
            table_data,
            metadata={
                "encoding": encoding,
                "delimiter": delimiter,
                "row_count": len(body_rows),
                "column_count": len(headers),
            },
        )
        return self.build_document(
            file_path, "CSV", [element],
            metadata={"encoding": encoding, "delimiter": delimiter},
        ).to_dict()
