"""PNG / JPG 图片解析器（依据架构文档 4.3 / 4.4）。

使用 pytesseract + Pillow 做 OCR 并提取文字坐标；可选 VLM 图片理解
通过抽象接口 VLMClient 接入，默认占位，可通过环境变量切到 OpenAI
兼容的多模态接口。可选依赖在方法内部 import。
"""

from __future__ import annotations

import base64
import os
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, Dict, List, Optional

from ..common import (
    BoundingBox,
    DocumentParser,
    Element,
    ParserError,
    new_element_id,
)
from ..common.file_utils import detect_file_type, validate_file
from ..common.ocr_client import recognize_image


class VLMClient(ABC):
    """图片语义理解（VLM）抽象接口。"""

    @abstractmethod
    def describe(self, image_path: str) -> str:
        """返回图片的自然语言描述。"""


class PlaceholderVLMClient(VLMClient):
    """本地占位实现：不调用模型，返回空描述。"""

    def describe(self, image_path: str) -> str:
        return ""


class OpenAIVLMClient(VLMClient):
    """OpenAI 兼容的 /chat/completions 多模态接口（占位可运行骨架）。

    通过环境变量配置：
        VLM_BASE_URL, VLM_API_KEY, VLM_MODEL
    """

    def __init__(self) -> None:
        self.base_url = os.environ.get("VLM_BASE_URL", "https://api.openai.com/v1").rstrip("/")
        self.api_key = os.environ.get("VLM_API_KEY", "")
        self.model = os.environ.get("VLM_MODEL", "gpt-4o-mini")

    def describe(self, image_path: str) -> str:
        try:
            import requests
        except ImportError as exc:  # pragma: no cover
            raise ParserError("调用 VLM 需要 requests，请先执行: pip install requests") from exc

        suffix = Path(image_path).suffix.lower().lstrip(".") or "png"
        mime = "image/jpeg" if suffix in ("jpg", "jpeg") else f"image/{suffix}"
        with open(image_path, "rb") as handle:
            encoded = base64.b64encode(handle.read()).decode("ascii")

        payload = {
            "model": self.model,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": "请用中文简要描述这张图片的内容。"},
                        {
                            "type": "image_url",
                            "image_url": {
                                "url": f"data:{mime};base64,{encoded}",
                            },
                        },
                    ],
                }
            ],
            "max_tokens": 512,
        }
        try:
            response = requests.post(
                f"{self.base_url}/chat/completions",
                json=payload,
                headers={"Authorization": f"Bearer {self.api_key}"},
                timeout=60,
            )
            response.raise_for_status()
            data = response.json()
            return data["choices"][0]["message"]["content"]
        except Exception as exc:
            raise ParserError(f"VLM 图片理解失败: {exc}") from exc


def build_vlm_client() -> VLMClient:
    """根据环境变量 VLM_BACKEND 选择 VLM 实现。"""
    backend = os.environ.get("VLM_BACKEND", "none").lower()
    if backend in ("openai", "compatible"):
        return OpenAIVLMClient()
    return PlaceholderVLMClient()


class ImageParser(DocumentParser):
    supported_types = ("PNG", "JPG")

    def __init__(self, vlm_client: Optional[VLMClient] = None) -> None:
        self.vlm_client: VLMClient = vlm_client or build_vlm_client()

    def supports(self, file_type: str) -> bool:
        return file_type.upper() in self.supported_types

    def parse(self, file_path: str) -> dict:
        ok, reason = validate_file(file_path)
        if not ok:
            raise ParserError(f"文件校验失败: {reason}")

        from PIL import Image

        try:
            with Image.open(file_path) as image:
                width, height = image.size
        except Exception as exc:
            raise ParserError(f"无法打开图片: {exc}") from exc

        words: List[Dict[str, Any]] = []
        content = ""
        engine = "tesseract"

        ocr_result = recognize_image(file_path)
        if ocr_result and not ocr_result.get("placeholder") and str(ocr_result.get("text", "")).strip():
            content = str(ocr_result["text"]).strip()
            engine = str(ocr_result.get("backend") or ocr_result.get("engine") or "ocr-service")
            for item in ocr_result.get("words", []):
                bbox = item.get("bbox") or [0, 0, 0, 0]
                words.append({
                    "text": item.get("text", ""),
                    "confidence": float(item.get("confidence", 0.0)),
                    "bbox": bbox,
                    "block": 0,
                    "paragraph": 0,
                    "line": 0,
                })

        if not content:
            try:
                import pytesseract
            except ImportError as exc:  # pragma: no cover
                raise ParserError(
                    "OCR 服务不可用且未安装 pytesseract；"
                    "请配置 OCR_SERVICE_URL 或安装 pytesseract pillow"
                ) from exc
            try:
                with Image.open(file_path) as image:
                    data = pytesseract.image_to_data(
                        image, lang=os.environ.get("OCR_LANG", "chi_sim+eng"),
                        output_type=pytesseract.Output.DICT,
                    )
            except Exception as exc:
                raise ParserError(f"OCR 识别失败: {exc}") from exc

            line_texts: Dict[int, List[str]] = {}
            for i, text in enumerate(data.get("text", [])):
                text = str(text).strip()
                if not text:
                    continue
                try:
                    confidence = float(data["conf"][i])
                except (ValueError, TypeError, IndexError):
                    confidence = 0.0
                left = int(data["left"][i]); top = int(data["top"][i])
                width_w = int(data["width"][i]); height_w = int(data["height"][i])
                block_num = int(data.get("block_num", [0] * len(data["text"]))[i])
                par_num = int(data.get("par_num", [0] * len(data["text"]))[i])
                line_num = int(data.get("line_num", [0] * len(data["text"]))[i])
                words.append({
                    "text": text,
                    "confidence": confidence,
                    "bbox": [left, top, left + width_w, top + height_w],
                    "block": block_num,
                    "paragraph": par_num,
                    "line": line_num,
                })
                line_texts.setdefault(line_num, []).append(text)
            content = "\n".join(" ".join(v) for _, v in sorted(line_texts.items()))

        elements: List[Element] = []
        if content:
            element = self.text_element(
                new_element_id(1), content,
                metadata={
                    "words": words,
                    "confidence": self._avg_confidence(words),
                    "ocr_engine": engine,
                },
            )
            element.bounding_box = BoundingBox(
                x0=0, y0=0, x1=width, y1=height, page=1,
            )
            elements.append(element)

        description = ""
        if os.environ.get("VLM_ENABLED", "false").lower() in ("1", "true", "yes"):
            try:
                description = self.vlm_client.describe(file_path)
            except ParserError:
                description = ""
        if description:
            elements.append(
                self.text_element(
                    new_element_id(len(elements) + 1),
                    description,
                    metadata={"type": "vlm_description"},
                )
            )

        return self.build_document(
            file_path, detect_file_type(file_path), elements,
            metadata={
                "image_width": width,
                "image_height": height,
                "word_count": len(words),
                "ocr_engine": engine,
                "vlm_description": description,
            },
        ).to_dict()

    @staticmethod
    def _avg_confidence(words: List[Dict[str, Any]]) -> float:
        values = [w["confidence"] for w in words if w["confidence"] > 0]
        return round(sum(values) / len(values), 2) if values else 0.0
