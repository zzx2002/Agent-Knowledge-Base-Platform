"""Markdown / TXT 解析器（依据架构文档 4.3 / 5.1）。

纯 Python 实现，无第三方依赖：
- Markdown：按 ATX 标题（# ~ ######）分层，维护 heading_path。
- TXT：按空行切分段落，逐段生成元素。
"""

from __future__ import annotations

import re
from pathlib import Path
from typing import List

from ..common import (
    DocumentParser,
    Element,
    ParserError,
    new_element_id,
)
from ..common.file_utils import detect_encoding, validate_file

_HEADING_RE = re.compile(r"^(#{1,6})\s+(.*?)\s*#*\s*$")


class MarkdownParser(DocumentParser):
    supported_types = ("MD",)

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")

        encoding = detect_encoding(file_path)
        try:
            text = Path(file_path).read_text(encoding=encoding)
        except (OSError, UnicodeDecodeError) as exc:
            raise ParserError(f"无法读取 Markdown 文件: {exc}") from exc

        elements: List[Element] = []
        heading_path: List[str] = []
        buffer: List[str] = []
        index = 0

        def flush() -> None:
            nonlocal buffer, index
            body = "\n".join(line for line in buffer if line.strip()).strip()
            buffer = []
            if body:
                index += 1
                elements.append(
                    self.text_element(
                        new_element_id(index), body,
                        heading_path=heading_path,
                    )
                )

        for raw_line in text.splitlines():
            match = _HEADING_RE.match(raw_line)
            if match:
                flush()
                level = len(match.group(1))
                title = match.group(2).strip()
                heading_path = heading_path[: level - 1]
                while len(heading_path) < level - 1:
                    heading_path.append("")
                heading_path.append(title)
                index += 1
                elements.append(
                    self.heading_element(
                        new_element_id(index), title, level,
                        heading_path=heading_path,
                    )
                )
            else:
                buffer.append(raw_line.rstrip())
        flush()

        return self.build_document(
            file_path, "MD", elements,
            metadata={"encoding": encoding},
        ).to_dict()


class TextParser(DocumentParser):
    supported_types = ("TXT",)

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")

        encoding = detect_encoding(file_path)
        try:
            text = Path(file_path).read_text(encoding=encoding)
        except (OSError, UnicodeDecodeError) as exc:
            raise ParserError(f"无法读取文本文件: {exc}") from exc

        elements: List[Element] = []
        paragraph: List[str] = []
        index = 0

        def flush() -> None:
            nonlocal paragraph, index
            content = "\n".join(paragraph).strip()
            paragraph = []
            if content:
                index += 1
                elements.append(self.text_element(new_element_id(index), content))

        for line in text.splitlines():
            if not line.strip():
                flush()
            else:
                paragraph.append(line.rstrip())
        flush()

        return self.build_document(
            file_path, "TXT", elements,
            metadata={"encoding": encoding},
        ).to_dict()
