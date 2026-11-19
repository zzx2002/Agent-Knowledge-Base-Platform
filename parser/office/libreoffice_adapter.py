"""DOC / PPT / PPTX 的 LibreOffice 转换适配器（占位实现）。

第一版架构中旧版 Office 走 LibreOffice 转换：
    DOC  -> DOCX -> Docling/DocxParser
    PPT  -> PPTX -> Docling
    PPTX -> Docling

本模块提供可运行的转换工具 LibreOfficeConverter；三个 Parser 为占位骨架，
完成转换后返回标注 placeholder 的最小 Document，后续可替换为 Docling/
python-pptx 的完整解析实现。
"""

from __future__ import annotations

import os
import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Optional

from ..common import (
    BoundingBox,
    DocumentParser,
    Element,
    ParserError,
    TableData,
    new_element_id,
)
from ..common.file_utils import detect_file_type, validate_file

_CONVERT_MAP = {"DOC": "docx", "PPT": "pptx", "PPTX": "pptx"}


class LibreOfficeConverter:
    """调用 soffice --headless 完成格式转换。"""

    def __init__(self, soffice_path: Optional[str] = None, timeout: int = 120):
        self.soffice_path = soffice_path or os.environ.get("LIBREOFFICE_PATH", "soffice")
        self.timeout = timeout

    def is_available(self) -> bool:
        return shutil.which(self.soffice_path) is not None

    def convert(self, file_path: str, output_dir: str, target_ext: str) -> str:
        """把文件转换为目标格式，返回输出文件路径。"""
        if not self.is_available():
            raise ParserError(
                "未找到 LibreOffice(soffice)，请安装后重试或设置 LIBREOFFICE_PATH 环境变量"
            )
        target_ext = target_ext.lstrip(".").lower()
        output_path = Path(output_dir)
        output_path.mkdir(parents=True, exist_ok=True)

        command = [
            self.soffice_path,
            "--headless",
            "--norestore",
            "--convert-to", target_ext,
            "--outdir", str(output_path),
            str(file_path),
        ]
        try:
            completed = subprocess.run(
                command,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                timeout=self.timeout,
                check=False,
            )
        except (OSError, subprocess.TimeoutExpired) as exc:
            raise ParserError(f"LibreOffice 转换失败: {exc}") from exc

        if completed.returncode != 0:
            detail = completed.stderr.decode("utf-8", errors="replace")
            raise ParserError(f"LibreOffice 转换失败: {detail}")

        stem = Path(file_path).stem
        candidate = output_path / f"{stem}.{target_ext}"
        if not candidate.exists():
            matches = list(output_path.glob(f"{stem}.*"))
            if not matches:
                raise ParserError("LibreOffice 转换完成但未找到输出文件")
            candidate = matches[0]
        return str(candidate)


class _ConvertedParserBase(DocumentParser):
    """转换型解析器的公共骨架。"""

    source_type: str = ""
    target_ext: str = ""

    def supports(self, file_type: str) -> bool:
        return file_type.upper() == self.source_type

    def target_parser(self) -> DocumentParser:
        raise NotImplementedError

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")
        if detect_file_type(file_path) != self.source_type:
            raise ParserError(f"文件类型与 {self.source_type} 解析器不匹配")

        converter = LibreOfficeConverter()
        with tempfile.TemporaryDirectory(prefix="kb_convert_") as tmp_dir:
            converted_path = converter.convert(file_path, tmp_dir, self.target_ext)
            parsed = self.target_parser().parse(converted_path)
            parsed["file_name"] = Path(file_path).name
            parsed["file_type"] = self.source_type
            metadata = parsed.setdefault("metadata", {})
            metadata["converter"] = "LibreOffice"
            metadata["converted_ext"] = self.target_ext
            metadata["placeholder"] = False
            return parsed


class DocParser(_ConvertedParserBase):
    supported_types = ("DOC",)
    source_type = "DOC"
    target_ext = "docx"

    def target_parser(self) -> DocumentParser:
        from .docx_parser import DocxParser

        return DocxParser()


class PptParser(_ConvertedParserBase):
    supported_types = ("PPT",)
    source_type = "PPT"
    target_ext = "pptx"

    def target_parser(self) -> DocumentParser:
        return PptxParser()


class PptxParser(DocumentParser):
    """使用 python-pptx 解析幻灯片文本与表格。"""

    supported_types = ("PPTX",)

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")
        try:
            from pptx import Presentation
        except ImportError as exc:  # pragma: no cover
            raise ParserError(
                "解析 PPTX 需要 python-pptx，请先执行: pip install python-pptx"
            ) from exc
        try:
            presentation = Presentation(file_path)
        except Exception as exc:
            raise ParserError(f"无法打开 PPTX 文件: {exc}") from exc

        elements = []
        index = 0
        for slide_no, slide in enumerate(presentation.slides, start=1):
            for shape in slide.shapes:
                if getattr(shape, "has_table", False):
                    rows = [
                        [cell.text.strip() for cell in row.cells]
                        for row in shape.table.rows
                    ]
                    if not rows:
                        continue
                    index += 1
                    element = self.table_element(
                        new_element_id(index),
                        TableData(headers=rows[0], rows=rows[1:]),
                        page=slide_no,
                    )
                    if shape.left is not None and shape.top is not None:
                        element.bounding_box = BoundingBox(
                            x0=float(shape.left), y0=float(shape.top),
                            x1=float(shape.left + shape.width),
                            y1=float(shape.top + shape.height),
                            page=slide_no,
                        )
                    elements.append(element)
                    continue
                if getattr(shape, "has_text_frame", False):
                    text = shape.text.strip()
                    if not text:
                        continue
                    index += 1
                    elements.append(
                        self.text_element(
                            new_element_id(index), text, page=slide_no,
                            metadata={"shape_name": shape.name},
                        )
                    )

        return self.build_document(
            file_path, "PPTX", elements,
            page_count=len(presentation.slides),
            metadata={"slide_count": len(presentation.slides)},
        ).to_dict()
