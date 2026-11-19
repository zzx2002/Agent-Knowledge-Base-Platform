"""Embedding 服务骨架（架构文档 5.3 / 13.1，端口 8002）。

提供 POST /embed 与 GET /health。通过 EMBEDDING_BACKEND 环境变量在
本地占位模型与 OpenAI 兼容接口之间切换：

    EMBEDDING_BACKEND=local|openai
    EMBEDDING_MODEL=bge-m3
    EMBEDDING_DIM=1024
    OPENAI_BASE_URL=https://api.openai.com/v1
    OPENAI_API_KEY=...
    OPENAI_EMBEDDING_MODEL=text-embedding-3-small

本地实现使用确定性哈希向量，可在未加载真实模型时完整跑通链路（占位）。
"""

from __future__ import annotations

import hashlib
import math
import os
import random
from abc import ABC, abstractmethod
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="KB Embedding Service", version="0.1.0")


class EmbedRequest(BaseModel):
    texts: List[str] = Field(..., description="待向量化的文本列表")
    model: Optional[str] = Field(None, description="可选：覆盖默认模型名")


class EmbedResponse(BaseModel):
    model: str
    backend: str
    dimension: int
    embeddings: List[List[float]]
    placeholder: bool = False


class EmbeddingModel(ABC):
    """Embedding 模型抽象接口。"""

    @property
    @abstractmethod
    def name(self) -> str:
        ...

    @property
    @abstractmethod
    def dimension(self) -> int:
        ...

    @abstractmethod
    def embed(self, texts: List[str]) -> List[List[float]]:
        ...


class LocalHashEmbeddingModel(EmbeddingModel):
    """本地占位实现：确定性伪向量，无需下载模型即可运行。

    生产环境应替换为 BGE-M3 / sentence-transformers 等真实实现。
    """

    def __init__(self, model_name: str, dimension: int) -> None:
        self._name = model_name
        self._dimension = dimension

    @property
    def name(self) -> str:
        return self._name

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed(self, texts: List[str]) -> List[List[float]]:
        vectors = []
        for text in texts:
            digest = hashlib.sha256(text.encode("utf-8")).digest()
            seed = int.from_bytes(digest[:8], "big")
            rng = random.Random(seed)
            vector = [rng.uniform(-1.0, 1.0) for _ in range(self._dimension)]
            norm = math.sqrt(sum(v * v for v in vector)) or 1.0
            vectors.append([round(v / norm, 6) for v in vector])
        return vectors


class OpenAICompatibleEmbeddingModel(EmbeddingModel):
    """OpenAI 兼容 /embeddings 接口实现。"""

    def __init__(self, model_name: str) -> None:
        self.base_url = os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
        self.api_key = os.environ.get("OPENAI_API_KEY", "")
        self._name = os.environ.get("OPENAI_EMBEDDING_MODEL", model_name)
        self._dimension = int(os.environ.get("EMBEDDING_DIM", "1024"))

    @property
    def name(self) -> str:
        return self._name

    @property
    def dimension(self) -> int:
        return self._dimension

    def embed(self, texts: List[str]) -> List[List[float]]:
        try:
            import requests
        except ImportError as exc:  # pragma: no cover
            raise RuntimeError("OpenAI 兼容接口需要 requests") from exc
        try:
            response = requests.post(
                f"{self.base_url}/embeddings",
                json={"model": self._name, "input": texts},
                headers={"Authorization": f"Bearer {self.api_key}"},
                timeout=60,
            )
            response.raise_for_status()
            data = response.json()["data"]
            ordered = sorted(data, key=lambda item: item.get("index", 0))
            vectors = [item["embedding"] for item in ordered]
            if vectors:
                self._dimension = len(vectors[0])
            return vectors
        except Exception as exc:
            raise RuntimeError(f"Embedding 接口调用失败: {exc}") from exc


def get_model(model_name: Optional[str] = None) -> EmbeddingModel:
    name = model_name or os.environ.get("EMBEDDING_MODEL", "bge-m3")
    backend = os.environ.get("EMBEDDING_BACKEND", "local").lower()
    if backend in ("openai", "compatible"):
        return OpenAICompatibleEmbeddingModel(name)
    return LocalHashEmbeddingModel(name, int(os.environ.get("EMBEDDING_DIM", "1024")))


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "embedding", "backend": os.environ.get("EMBEDDING_BACKEND", "local")}


@app.post("/embed", response_model=EmbedResponse)
def embed(request: EmbedRequest) -> EmbedResponse:
    if not request.texts:
        raise HTTPException(status_code=400, detail="texts 不能为空")
    try:
        model = get_model(request.model)
        vectors = model.embed(request.texts)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    backend = os.environ.get("EMBEDDING_BACKEND", "local").lower()
    return EmbedResponse(
        model=model.name,
        backend=backend,
        dimension=len(vectors[0]) if vectors else model.dimension,
        embeddings=vectors,
        placeholder=backend in ("local", ""),
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8002")))
