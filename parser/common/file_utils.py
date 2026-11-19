"""文件类型识别与安全校验工具。

对应架构文档 4.2 的「文件类型识别 -> 文件安全校验 -> 解析路由」流程，
以及 15.2 中的上传文件类型校验 / 文件大小限制 / 恶意文件检测。
"""

from __future__ import annotations

import mimetypes
import re
from pathlib import Path
from typing import Dict, Iterable, Optional, Tuple

# 扩展名 -> 统一文件类型（大写）
EXTENSION_TYPE_MAP: Dict[str, str] = {
    ".doc": "DOC",
    ".docx": "DOCX",
    ".ppt": "PPT",
    ".pptx": "PPTX",
    ".md": "MD",
    ".markdown": "MD",
    ".txt": "TXT",
    ".pdf": "PDF",
    ".xls": "XLS",
    ".xlsx": "XLSX",
    ".csv": "CSV",
    ".png": "PNG",
    ".jpg": "JPG",
    ".jpeg": "JPG",
}

# 第一版必须支持的 12 种格式
SUPPORTED_FILE_TYPES = {
    "DOC", "DOCX", "PPT", "PPTX", "MD", "TXT",
    "PDF", "XLS", "XLSX", "CSV", "PNG", "JPG",
}

# 常见文件头（magic bytes）签名，用于识别扩展名伪装
_MAGIC_SIGNATURES: Dict[str, bytes] = {
    "PDF": b"%PDF-",
    "PNG": b"\x89PNG\r\n\x1a\n",
    "DOCX": b"PK\x03\x04",   # OOXML 容器（docx/xlsx/pptx 共用）
    "XLSX": b"PK\x03\x04",
    "PPTX": b"PK\x03\x04",
    "DOC": b"\xd0\xcf\x11\xe0",  # OLE2 复合文档（doc/xls/ppt 共用）
    "XLS": b"\xd0\xcf\x11\xe0",
}

_SAFE_NAME_RE = re.compile(r"^[A-Za-z0-9._\- ]+$")

# 文本类文件常见编码候选
ENCODING_CANDIDATES = ("utf-8-sig", "utf-8", "gb18030", "gbk", "big5", "latin-1")


class FileValidationError(ValueError):
    """文件校验失败。"""


def normalize_extension(file_name_or_path: str) -> str:
    """返回带点的小写扩展名，例如 '.docx'。"""
    suffix = Path(file_name_or_path).suffix
    return suffix.lower()


def detect_file_type(file_path: str, *, use_magic: bool = False) -> str:
    """按扩展名识别文件类型；未知类型返回 'UNKNOWN'。

    :param use_magic: 为 True 时，在扩展名无法识别的情况下尝试读取文件头推断。
    """
    ext = normalize_extension(file_path)
    file_type = EXTENSION_TYPE_MAP.get(ext)
    if file_type or not use_magic:
        return file_type or "UNKNOWN"

    path = Path(file_path)
    if not path.is_file():
        return "UNKNOWN"
    with path.open("rb") as handle:
        head = handle.read(8)
    for candidate, signature in _MAGIC_SIGNATURES.items():
        if head.startswith(signature):
            if candidate in ("DOCX", "XLSX", "PPTX"):
                # OOXML 容器需进一步根据 zip 目录区分
                return _detect_ooxml_type(path) or candidate
            return candidate
    return "UNKNOWN"


def _detect_ooxml_type(path: Path) -> Optional[str]:
    """通过 zip 内部目录判断 OOXML 容器的真实类型。"""
    try:
        import zipfile

        with zipfile.ZipFile(path) as archive:
            names = archive.namelist()
            if any(n.startswith("word/") for n in names):
                return "DOCX"
            if any(n.startswith("xl/") for n in names):
                return "XLSX"
            if any(n.startswith("ppt/") for n in names):
                return "PPTX"
    except Exception:
        return None
    return None


def detect_encoding(file_path: str) -> str:
    """按候选编码逐一尝试解码文件头部，返回第一个可用编码。"""
    raw = Path(file_path).read_bytes()[: 64 * 1024]
    for encoding in ENCODING_CANDIDATES:
        try:
            raw.decode(encoding)
            return encoding
        except UnicodeDecodeError:
            continue
    return "latin-1"


def sniff_mime_type(file_path: str) -> Optional[str]:
    """返回 mimetypes 推测的 MIME 类型（仅作参考）。"""
    return mimetypes.guess_type(str(file_path))[0]


def read_file_head(file_path: str, size: int = 8) -> bytes:
    with Path(file_path).open("rb") as handle:
        return handle.read(size)


def magic_type_matches(file_type: str, file_path: str) -> bool:
    """检查文件头是否与声明的类型一致（不支持签名校验的类型返回 True）。"""
    signature = _MAGIC_SIGNATURES.get(file_type.upper())
    if signature is None:
        return True
    head = read_file_head(file_path, max(len(signature), 8))
    if not head.startswith(signature):
        return False
    if file_type.upper() in ("DOCX", "XLSX", "PPTX"):
        detected = _detect_ooxml_type(Path(file_path))
        return detected is None or detected == file_type.upper()
    return True


def validate_file(
    file_path: str,
    *,
    max_size_mb: float = 200.0,
    allowed_types: Optional[Iterable[str]] = None,
    check_magic: bool = True,
) -> Tuple[bool, str]:
    """校验文件安全性。

    :return: (是否通过, 说明信息)
    """
    path = Path(file_path)

    if not path.exists():
        return False, "文件不存在"
    if not path.is_file():
        return False, "路径不是普通文件"

    # 文件名白名单，避免异常字符 / 路径注入
    if not _SAFE_NAME_RE.match(path.name):
        return False, "文件名包含不允许的字符"

    size_mb = path.stat().st_size / (1024 * 1024)
    if size_mb > max_size_mb:
        return False, f"文件大小 {size_mb:.1f}MB 超过限制 {max_size_mb:.1f}MB"

    file_type = detect_file_type(file_path)
    if file_type == "UNKNOWN":
        return False, f"不支持的文件扩展名: {path.suffix or '(无)'}"
    if allowed_types is not None and file_type not in {t.upper() for t in allowed_types}:
        return False, f"文件类型 {file_type} 不在允许列表中"
    if file_type not in SUPPORTED_FILE_TYPES:
        return False, f"文件类型 {file_type} 不在第一版支持范围内"

    if check_magic and not magic_type_matches(file_type, file_path):
        return False, "文件内容头与扩展名不一致，疑似伪装文件"

    return True, "OK"


def ensure_safe_output_path(base_dir: str, relative_name: str) -> Path:
    """把相对文件名安全地拼接到基础目录下，防止路径穿越。"""
    base = Path(base_dir).resolve()
    candidate = (base / Path(relative_name).name).resolve()
    if base not in candidate.parents and candidate != base:
        raise FileValidationError("非法的输出路径")
    return candidate
