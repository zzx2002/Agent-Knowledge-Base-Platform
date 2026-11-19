"""Reranker 服务骨架（架构文档 6.2 / 13.1，端口 8003）。

提供 POST /rerank 与 GET /health。通过 RERANKER_BACKEND 切换：

    RERANKER_BACKEND=local|openai
    RERANKER_MODEL=bge-reranker
    OPENAI_BASE_URL=https://api.openai.com/v1
    OPENAI_API_KEY=...
    OPENAI_RERANK_MODEL=...

本地实现为基于关键词重合度的确定性占位打分，可跑通链路（占位）。
"""

from __future__ import annotations

import os
import re
from abc import ABC, abstractmethod
from typing import List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

app = FastAPI(title="KB Reranker Service", version="0.1.0")

_TOKEN_RE = re.compile(r"[\u4e00-\u9fff]|[A-Za-z0-9_]+")


class RerankRequest(BaseModel):
    query: str = Field(..., description="查询文本")
    documents: List[str] = Field(..., description="待重排文本列表")
    top_k: Optional[int] = Field(None, description="返回前 K 条")
    model: Optional[str] = Field(None, description="可选：覆盖默认模型名")


class RerankResult(BaseModel):
    index: int
    score: float
    document: str


class RerankResponse(BaseModel):
    model: str
    backend: str
    results: List[RerankResult]
    placeholder: bool = False


class Reranker(ABC):
    """Reranker 模型抽象接口。"""

    @property
    @abstractmethod
    def name(self) -> str:
        ...

    @abstractmethod
    def rerank(
        self, query: str, documents: List[str], top_k: Optional[int] = None
    ) -> List[RerankResult]:
        ...


def _tokenize(text: str) -> List[str]:
    tokens = _TOKEN_RE.findall(text.lower())
    # 中文按字符切分后补充 bigram，改善占位打分的区分度
    bigrams = [a + b for a, b in zip(tokens, tokens[1:]) if len(a) == 1 and len(b) == 1]
    return tokens + bigrams


class LocalKeywordReranker(Reranker):
    """本地占位实现：query 与 document 的词重合度打分。"""

    def __init__(self, model_name: str) -> None:
        self._name = model_name

    @property
    def name(self) -> str:
        return self._name

    def rerank(
        self, query: str, documents: List[str], top_k: Optional[int] = None
    ) -> List[RerankResult]:
        query_tokens = set(_tokenize(query))
        results: List[RerankResult] = []
        for index, document in enumerate(documents):
            doc_tokens = set(_tokenize(document))
            overlap = len(query_tokens & doc_tokens)
            union = len(query_tokens | doc_tokens) or 1
            score = overlap / union
            results.append(
                RerankResult(index=index, score=round(score, 6), document=document)
            )
        results.sort(key=lambda item: item.score, reverse=True)
        return results[:top_k] if top_k else results


class OpenAICompatibleReranker(Reranker):
    """OpenAI 兼容 /rerank 接口占位实现。"""

    def __init__(self, model_name: str) -> None:
        self.base_url = os.environ.get("OPENAI_BASE_URL", "https://api.openai.com/v1").rstrip("/")
        self.api_key = os.environ.get("OPENAI_API_KEY", "")
        self._name = os.environ.get("OPENAI_RERANK_MODEL", model_name)

    @property
    def name(self) -> str:
        return self._name

    def rerank(
        self, query: str, documents: List[str], top_k: Optional[int] = None
    ) -> List[RerankResult]:
        try:
            import requests
        except ImportError as exc:  # pragma: no cover
            raise RuntimeError("OpenAI 兼容接口需要 requests") from exc
        payload = {
            "model": self._name,
            "query": query,
            "documents": documents,
            "top_k": top_k or len(documents),
        }
        try:
            response = requests.post(
                f"{self.base_url}/rerank",
                json=payload,
                headers={"Authorization": f"Bearer {self.api_key}"},
                timeout=60,
            )
            response.raise_for_status()
            data = response.json()
            raw_results = data.get("results", data if isinstance(data, list) else [])
            return [
                RerankResult(
                    index=int(item["index"]),
                    score=float(item["score"]),
                    document=str(item.get("document", documents[int(item["index"])])),
                )
                for item in raw_results
            ]
        except Exception as exc:
            raise RuntimeError(f"Rerank 接口调用失败: {exc}") from exc


def get_reranker(model_name: Optional[str] = None) -> Reranker:
    name = model_name or os.environ.get("RERANKER_MODEL", "bge-reranker")
    backend = os.environ.get("RERANKER_BACKEND", "local").lower()
    if backend in ("openai", "compatible"):
        return OpenAICompatibleReranker(name)
    return LocalKeywordReranker(name)


@app.get("/health")
def health() -> dict:
    return {"status": "ok", "service": "reranker", "backend": os.environ.get("RERANKER_BACKEND", "local")}


@app.post("/rerank", response_model=RerankResponse)
def rerank(request: RerankRequest) -> RerankResponse:
    if not request.query or not request.documents:
        raise HTTPException(status_code=400, detail="query 与 documents 不能为空")
    try:
        model = get_reranker(request.model)
        results = model.rerank(request.query, request.documents, request.top_k)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    backend = os.environ.get("RERANKER_BACKEND", "local").lower()
    return RerankResponse(
        model=model.name,
        backend=backend,
        results=results,
        placeholder=backend in ("local", ""),
    )


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8003")))
