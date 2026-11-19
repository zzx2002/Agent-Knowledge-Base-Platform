"""调用独立 OCR 服务的轻量客户端。

未配置 OCR_SERVICE_URL 时返回 None，调用方自行回退本地 OCR。
"""

from __future__ import annotations

import base64
import os
from pathlib import Path
from typing import Any, Dict, Optional

import requests


def recognize_image(image_path: str) -> Optional[Dict[str, Any]]:
    """识别图片，返回 OCR 服务响应中的 data；失败时返回 None。"""
    base_url = os.environ.get("OCR_SERVICE_URL", "").strip()
    if not base_url:
        return None
    suffix = Path(image_path).suffix.lower().lstrip(".") or "png"
    mime = "image/jpeg" if suffix in ("jpg", "jpeg") else f"image/{suffix}"
    with open(image_path, "rb") as handle:
        encoded = base64.b64encode(handle.read()).decode("ascii")
    try:
        response = requests.post(
            base_url.rstrip("/") + "/ocr",
            json={"image_base64": encoded, "lang": os.environ.get("OCR_LANG", "chi_sim+eng")},
            timeout=120,
        )
        response.raise_for_status()
        return response.json()
    except Exception:
        return None
