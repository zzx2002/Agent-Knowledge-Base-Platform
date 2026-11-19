"""OCR 服务骨架（架构文档 3.3 / 13.1，端口 8001）。

提供 POST /ocr 与 GET /health。支持三种后端：

    OCR_BACKEND=placeholder|tesseract|openai

- placeholder：默认，无需任何依赖即可跑通链路（占位返回）。
- tesseract：本地 pytesseract + Pillow，返回文字与坐标。
- openai：OpenAI 兼容多模态 /chat/completions 图片理解，返回描述文本。

注意：架构文档规划的首选引擎为 PaddleOCR，可在 PaddleOCRBackend 中接入；
当前提供可运行的 Tesseract 与占位实现作为骨架。
"""

from __future__ import annotations

import base64
import os
import tempfile
from abc import ABC, abstractmethod
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="KB OCR Service", version="0.1.0")


class OcrRequest(BaseModel):
    image_path: Optional[str] = Field(None, description="服务端可访问的图片路径")
    image_base64: Optional[str] = Field(None, description="图片的 base64 编码（不含 data URI 头）")
    lang: Optional[str] = Field(None, description="识别语言，默认 chi_sim+eng")


class OcrWord(BaseModel):
    text: str
    bbox: List[float]
    confidence: float = 0.0


class OcrResponse(BaseModel):
    text: str
    words: List[OcrWord]
    engine: str
    backend: str
    placeholder: bool = False


class OCRBackend(ABC):
    """OCR 引擎抽象接口。"""

    @property
    @abstractmethod
    def name(self) -> str:
        ...

    @abstractmethod
    def recognize(self, image_path: str, lang: str) -> Tuple[str, List[Dict[str, Any]]]:
        """返回 (整页文本, 词条列表)。"""


class PlaceholderOCRBackend(OCRBackend):
    """占位实现：无外部依赖，返回说明性文本。"""

    @property
    def name(self) -> str:
        return "placeholder"

    def recognize(self, image_path: str, lang: str) -> Tuple[str, List[Dict[str, Any]]]:
        return "[OCR 占位] 未配置 OCR 引擎；设置 OCR_BACKEND=tesseract 或 openai 以启用识别。", []


class TesseractOCRBackend(OCRBackend):
    """本地 Tesseract 实现，返回文字与坐标。"""

    def __init__(self) -> None:
        self._lang_default = os.environ.get("OCR_LANG", "chi_sim+eng")

    @property
    def name(self) -> str:
        return "tesseract"

    def recognize(self, image_path: str, lang: str) -> Tuple[str, List[Dict[str, Any]]]:
        try:
            import pytesseract
            from PIL import Image
        except ImportError as exc:  # pragma: no cover
            raise RuntimeError(
                "Tesseract OCR 需要 pytesseract 与 pillow，"
                "请先执行: pip install pytesseract pillow 并安装 Tesseract 引擎"
            ) from exc
        try:
            with Image.open(image_path) as image:
                data = pytesseract.image_to_data(
                    image, lang=lang or self._lang_default,
                    output_type=pytesseract.Output.DICT,
                )
        except Exception as exc:
            raise RuntimeError(f"Tesseract 识别失败: {exc}") from exc

        words: List[Dict[str, Any]] = []
        lines: Dict[int, List[str]] = {}
        texts = data.get("text", [])
        for i, text in enumerate(texts):
            text = str(text).strip()
            if not text:
                continue
            try:
                confidence = float(data["conf"][i])
            except (ValueError, TypeError, IndexError):
                confidence = 0.0
            left = int(data["left"][i]); top = int(data["top"][i])
            width = int(data["width"][i]); height = int(data["height"][i])
            line_no = int(data.get("line_num", [0] * len(texts))[i])
            words.append({
                "text": text,
                "bbox": [left, top, left + width, top + height],
                "confidence": confidence,
            })
            lines.setdefault(line_no, []).append(text)
        text_result = "\n".join(" ".join(v) for _, v in sorted(lines.items()))
        return text_result, words


class OpenAIVisionOCRBackend(OCRBackend):
    """OpenAI 兼容多模态接口，返回图片描述文本。"""

    def __init__(self) -> None:
        self.base_url = os.environ.get("VLM_BASE_URL", os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1")).rstrip("/")
        self.api_key = os.environ.get("VLM_API_KEY", os.environ.get("OPENAI_API_KEY", ""))
        self.model = os.environ.get("VLM_MODEL", "gpt-4o-mini")

    @property
    def name(self) -> str:
        return f"openai-vision:{self.model}"

    def recognize(self, image_path: str, lang: str) -> Tuple[str, List[Dict[str, Any]]]:
        try:
            import requests
        except ImportError as exc:  # pragma: no cover
            raise RuntimeError("OpenAI 兼容接口需要 requests") from exc
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
                        {"type": "text", "text": "请识别并输出图片中的全部文字，保持原始排版。"},
                        {"type": "image_url", "image_url": {"url": f"data:{mime};base64,{encoded}"}},
                    ],
                }
            ],
            "max_tokens": 2048,
        }
        try:
            response = requests.post(
                f"{self.base_url}/chat/completions",
                json=payload,
                headers={"Authorization": f"Bearer {self.api_key}"},
                timeout=60,
            )
            response.raise_for_status()
            text = response.json()["choices"][0]["message"]["content"]
            return text, []
        except Exception as exc:
            raise RuntimeError(f"多模态 OCR 调用失败: {exc}") from exc


def get_backend() -> OCRBackend:
    backend = os.environ.get("OCR_BACKEND", "placeholder").lower()
    if backend == "tesseract":
        return TesseractOCRBackend()
    if backend in ("openai", "vlm", "compatible"):
        return OpenAIVisionOCRBackend()
    return PlaceholderOCRBackend()


def _materialize_image(request: OcrRequest) -> Tuple[str, bool]:
    """把请求中的图片落盘，返回 (路径, 是否为临时文件)。"""
    if request.image_path:
        if not Path(request.image_path).is_file():
            raise HTTPException(status_code=400, detail="image_path 指向的文件不存在")
        return request.image_path, False
    if request.image_base64:
        try:
            content = base64.b64decode(request.image_base64, validate=True)
        except Exception as exc:
            raise HTTPException(status_code=400, detail="image_base64 解码失败") from exc
        with tempfile.NamedTemporaryFile(suffix=".png", delete=False) as handle:
            handle.write(content)
            return handle.name, True
    raise HTTPException(status_code=400, detail="必须提供 image_path 或 image_base64")


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "ocr", "backend": os.environ.get("OCR_BACKEND", "placeholder")}


@app.post("/ocr", response_model=OcrResponse)
def ocr(request: OcrRequest) -> OcrResponse:
    image_path, is_temp = _materialize_image(request)
    try:
        backend = get_backend()
        try:
            text, words = backend.recognize(image_path, request.lang or "")
        except Exception as exc:
            raise HTTPException(status_code=502, detail=str(exc)) from exc
        return OcrResponse(
            text=text,
            words=[OcrWord(**word) for word in words],
            engine=backend.name,
            backend=os.environ.get("OCR_BACKEND", "placeholder").lower(),
            placeholder=isinstance(backend, PlaceholderOCRBackend),
        )
    finally:
        if is_temp and Path(image_path).exists():
            Path(image_path).unlink(missing_ok=True)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8001")))
