"""parser 公共模块：统一数据结构、解析接口与文件工具。"""

from .base import DocumentParser, ParserError
from .file_utils import (
    EXTENSION_TYPE_MAP,
    SUPPORTED_FILE_TYPES,
    FileValidationError,
    detect_encoding,
    detect_file_type,
    magic_type_matches,
    normalize_extension,
    validate_file,
)
from .models import (
    BoundingBox,
    Chunk,
    Document,
    Element,
    TableData,
    new_chunk_id,
    new_element_id,
)

__all__ = [
    "DocumentParser",
    "ParserError",
    "BoundingBox",
    "Chunk",
    "Document",
    "Element",
    "TableData",
    "new_chunk_id",
    "new_element_id",
    "detect_file_type",
    "detect_encoding",
    "normalize_extension",
    "validate_file",
    "magic_type_matches",
    "FileValidationError",
    "EXTENSION_TYPE_MAP",
    "SUPPORTED_FILE_TYPES",
]
