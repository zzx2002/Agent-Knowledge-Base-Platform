"""RabbitMQ 解析任务消费 Worker（架构文档 11.1 / 11.2 / 11.3）。

流程：
    消费任务 -> 校验/获取文件 -> ParserRouter 解析 -> 文本清洗 -> 结构化分块
    -> （可选）调用 Embedding 服务 -> HTTP 回调 knowledge-api 更新任务状态

状态流转：PENDING -> PROCESSING -> COMPLETED / FAILED。
所有连接信息与回调地址均通过环境变量配置。

运行示例：
    python -m parser.pipeline.worker
"""

from __future__ import annotations

import json
import logging
import os
import tempfile
import time
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Dict, Optional

from .chunker import ChunkConfig, StructuredChunker
from .cleaner import TextCleaner
from .router import ParserRouter, get_default_router

logger = logging.getLogger("kb.parser.worker")


@dataclass
class WorkerConfig:
    rabbitmq_url: str = "amqp://guest:guest@localhost:5672/"
    queue_name: str = "document.parse"
    prefetch_count: int = 1

    api_base_url: str = "http://localhost:8080/api/v1"
    callback_path: str = "/tasks/{task_id}/callback"
    callback_token: str = ""
    request_timeout: int = 30
    callback_retries: int = 3

    file_base_dir: str = "./files"
    max_retry: int = 3

    minio_endpoint: str = "http://localhost:9000"
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket: str = "knowledge-files"

    embedding_url: str = ""
    embedding_model: str = "bge-m3"

    @classmethod
    def from_env(cls) -> "WorkerConfig":
        return cls(
            rabbitmq_url=os.environ.get("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"),
            queue_name=os.environ.get("PARSE_QUEUE", "document.parse"),
            prefetch_count=int(os.environ.get("PREFETCH_COUNT", "1")),
            api_base_url=os.environ.get("KNOWLEDGE_API_BASE_URL", "http://localhost:8080/api/v1").rstrip("/"),
            callback_path=os.environ.get("TASK_CALLBACK_PATH", "/tasks/{task_id}/callback"),
            callback_token=os.environ.get("TASK_CALLBACK_TOKEN", ""),
            request_timeout=int(os.environ.get("HTTP_TIMEOUT", "30")),
            callback_retries=int(os.environ.get("CALLBACK_RETRIES", "3")),
            file_base_dir=os.environ.get("FILE_BASE_DIR", "./files"),
            max_retry=int(os.environ.get("TASK_MAX_RETRY", "3")),
            minio_endpoint=os.environ.get("MINIO_ENDPOINT", "http://localhost:9000").rstrip("/"),
            minio_access_key=os.environ.get("MINIO_ACCESS_KEY", ""),
            minio_secret_key=os.environ.get("MINIO_SECRET_KEY", ""),
            minio_bucket=os.environ.get("MINIO_BUCKET", "knowledge-files"),
            embedding_url=os.environ.get("EMBEDDING_SERVICE_URL", "").rstrip("/"),
            embedding_model=os.environ.get("EMBEDDING_MODEL", "bge-m3"),
        )


def configure_logging() -> None:
    logging.basicConfig(
        level=os.environ.get("LOG_LEVEL", "INFO").upper(),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )


# ----------------------------------------------------------------------
# HTTP 回调
# ----------------------------------------------------------------------
def call_knowledge_api(config: WorkerConfig, task_id: str, payload: Dict[str, Any]) -> bool:
    """向 knowledge-api 回写任务状态，带简单重试。"""
    try:
        import requests
    except ImportError as exc:  # pragma: no cover
        logger.error("HTTP 回调需要 requests，请先执行: pip install requests")
        raise RuntimeError("缺少 requests 依赖") from exc

    url = config.api_base_url + config.callback_path.format(task_id=task_id)
    headers = {"Content-Type": "application/json"}
    if config.callback_token:
        headers["Authorization"] = f"Bearer {config.callback_token}"

    for attempt in range(1, config.callback_retries + 1):
        try:
            response = requests.post(
                url, json=payload, headers=headers, timeout=config.request_timeout
            )
            response.raise_for_status()
            return True
        except Exception as exc:
            logger.warning("回调失败 (%d/%d): %s", attempt, config.callback_retries, exc)
            if attempt < config.callback_retries:
                time.sleep(2 ** (attempt - 1))
    return False


def report_status(
    config: WorkerConfig,
    task_id: str,
    status: str,
    *,
    document_id: Optional[str] = None,
    progress: int = 0,
    message: str = "",
    result: Optional[Dict[str, Any]] = None,
) -> None:
    payload: Dict[str, Any] = {
        "task_id": task_id,
        "status": status,
        "progress": progress,
        "message": message,
        "timestamp": int(time.time()),
    }
    if document_id:
        payload["document_id"] = document_id
    if result is not None:
        payload["result"] = result
    if not call_knowledge_api(config, task_id, payload):
        logger.error("任务 %s 状态 %s 回调失败", task_id, status)


# ----------------------------------------------------------------------
# 文件获取
# ----------------------------------------------------------------------
def resolve_file(config: WorkerConfig, task: Dict[str, Any]) -> str:
    """根据任务信息定位待解析文件，返回本地路径。"""
    local_path = task.get("local_path")
    if local_path and Path(local_path).is_file():
        return str(local_path)

    source_file_id = task.get("source_file_id")
    if source_file_id:
        candidate = Path(config.file_base_dir) / Path(source_file_id).name
        if candidate.is_file():
            return str(candidate)

    # Java API 上传后把 MinIO 对象键写入 storage_key，Worker 直接拉取对象。
    storage_key = task.get("storage_key")
    if storage_key:
        try:
            from minio import Minio
        except ImportError as exc:  # pragma: no cover
            raise RuntimeError("从 MinIO 获取文件需要 minio 依赖") from exc
        from urllib.parse import urlparse

        endpoint = urlparse(config.minio_endpoint).netloc or config.minio_endpoint
        secure = urlparse(config.minio_endpoint).scheme == "https"
        client = Minio(
            endpoint,
            access_key=config.minio_access_key,
            secret_key=config.minio_secret_key,
            secure=secure,
        )
        suffix = Path(str(task.get("file_name", ""))).suffix or ".bin"
        target = Path(tempfile.gettempdir()) / f"kb_download_{task.get('task_id', 'task')}{suffix}"
        client.fget_object(config.minio_bucket, str(storage_key), str(target))
        return str(target)

    file_url = task.get("file_url")
    if file_url:
        try:
            import requests
        except ImportError as exc:  # pragma: no cover
            raise RuntimeError("下载文件需要 requests") from exc
        suffix = Path(str(task.get("file_name", ""))).suffix or ".bin"
        target = Path(tempfile.gettempdir()) / f"kb_download_{task.get('task_id', 'task')}{suffix}"
        response = requests.get(file_url, timeout=config.request_timeout)
        response.raise_for_status()
        target.write_bytes(response.content)
        return str(target)

    raise FileNotFoundError("任务中未提供可用的 local_path / source_file_id / storage_key / file_url")


# ----------------------------------------------------------------------
# 任务处理
# ----------------------------------------------------------------------
def _chunk_config_from_task(task: Dict[str, Any], file_type: str) -> ChunkConfig:
    raw = dict(task.get("chunk_config") or {})
    allowed = {field for field in ChunkConfig.__dataclass_fields__}
    filtered = {k: v for k, v in raw.items() if k in allowed}
    filtered.setdefault("file_type", file_type)
    return ChunkConfig(**filtered)


def maybe_embed(config: WorkerConfig, document: Dict[str, Any]) -> int:
    """可选：调用 Embedding 服务，返回已向量化的 chunk 数。"""
    if not config.embedding_url:
        return 0
    try:
        import requests
    except ImportError:  # pragma: no cover
        return 0
    texts = [chunk["content"] for chunk in document.get("chunks", [])]
    if not texts:
        return 0
    try:
        response = requests.post(
            f"{config.embedding_url}/embed",
            json={"texts": texts, "model": config.embedding_model},
            timeout=config.request_timeout,
        )
        response.raise_for_status()
        return len(response.json().get("embeddings", []))
    except Exception as exc:
        logger.warning("Embedding 调用失败（忽略）: %s", exc)
        return 0


def process_task(config: WorkerConfig, task: Dict[str, Any], router: ParserRouter) -> Dict[str, Any]:
    """执行单个解析任务，返回回调用的 result。"""
    task_id = str(task.get("task_id", "unknown"))
    document_id = task.get("document_id")

    report_status(config, task_id, "PROCESSING", document_id=document_id, progress=10)

    file_path = resolve_file(config, task)
    logger.info("任务 %s 开始解析: %s", task_id, file_path)

    document = router.parse(
        file_path,
        document_id=document_id,
        source_file_id=task.get("source_file_id"),
        task_id=task_id,
    )
    report_status(config, task_id, "PROCESSING", document_id=document_id, progress=50)

    document = TextCleaner().clean_dict(document)
    chunk_config = _chunk_config_from_task(task, document.get("file_type", ""))
    document = StructuredChunker(chunk_config).chunk_dict(document)
    report_status(config, task_id, "PROCESSING", document_id=document_id, progress=80)

    embedded = maybe_embed(config, document)
    return {
        "document_id": document["document_id"],
        "file_type": document["file_type"],
        "element_count": len(document.get("elements", [])),
        "chunk_count": len(document.get("chunks", [])),
        "embedded_chunk_count": embedded,
        "document": document,
    }


# ----------------------------------------------------------------------
# RabbitMQ 消费
# ----------------------------------------------------------------------
def on_message(config: WorkerConfig, router: ParserRouter, channel, method, properties, body) -> None:
    task: Optional[Dict[str, Any]] = None
    try:
        task = json.loads(body.decode("utf-8"))
    except Exception as exc:
        logger.error("无法解析任务消息: %s", exc)
        channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)
        return

    task_id = str(task.get("task_id", "unknown"))
    try:
        result = process_task(config, task, router)
        report_status(
            config, task_id, "COMPLETED",
            document_id=result["document_id"], progress=100,
            message="解析完成", result=result,
        )
        channel.basic_ack(delivery_tag=method.delivery_tag)
    except Exception as exc:
        logger.exception("任务 %s 处理失败: %s", task_id, exc)
        retry_count = int(task.get("retry_count", 0)) + 1
        task["retry_count"] = retry_count
        if retry_count < config.max_retry:
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=True)
        else:
            report_status(
                config, task_id, "FAILED",
                document_id=task.get("document_id"),
                message=f"解析失败: {exc}",
            )
            channel.basic_nack(delivery_tag=method.delivery_tag, requeue=False)


def start_consumer(config: WorkerConfig) -> None:
    try:
        import pika
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("消费 RabbitMQ 需要 pika，请先执行: pip install pika") from exc

    router = get_default_router()
    connection = pika.BlockingConnection(pika.URLParameters(config.rabbitmq_url))
    channel = connection.channel()
    channel.queue_declare(queue=config.queue_name, durable=True)
    channel.basic_qos(prefetch_count=config.prefetch_count)
    channel.basic_consume(
        queue=config.queue_name,
        on_message_callback=lambda ch, method, props, body: on_message(
            config, router, ch, method, props, body
        ),
        auto_ack=False,
    )
    logger.info("Worker 已启动，监听队列: %s", config.queue_name)
    try:
        channel.start_consuming()
    except KeyboardInterrupt:
        logger.info("Worker 已停止")
    finally:
        if connection.is_open:
            connection.close()


def main() -> None:
    configure_logging()
    config = WorkerConfig.from_env()
    start_consumer(config)


if __name__ == "__main__":
    main()
