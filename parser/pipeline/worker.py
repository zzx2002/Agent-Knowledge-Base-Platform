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

import io
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
    parse_exchange: str = "kb.parse.exchange"
    parse_routing_key: str = "kb.parse.created"
    dlx_exchange: str = "kb.parse.dlx"
    dlq_queue: str = "kb.parse.dlq"
    dlq_routing_key: str = "kb.parse.dead"
    prefetch_count: int = 1

    api_base_url: str = "http://localhost:8080/api/v1"
    callback_path: str = "/tasks/{task_id}/callback"
    status_path: str = "/tasks/{task_id}/status"
    callback_token: str = ""
    request_timeout: int = 30
    callback_retries: int = 3
    task_timeout_seconds: int = 600

    file_base_dir: str = "./files"
    max_retry: int = 3

    minio_endpoint: str = "http://localhost:9000"
    minio_access_key: str = ""
    minio_secret_key: str = ""
    minio_bucket: str = "knowledge-files"
    result_path_prefix: str = "parse-results"

    embedding_url: str = ""
    embedding_model: str = "bge-m3"

    @classmethod
    def from_env(cls) -> "WorkerConfig":
        return cls(
            rabbitmq_url=os.environ.get("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"),
            queue_name=os.environ.get("PARSE_QUEUE", "document.parse"),
            parse_exchange=os.environ.get("PARSE_EXCHANGE", "kb.parse.exchange"),
            parse_routing_key=os.environ.get("PARSE_ROUTING_KEY", "kb.parse.created"),
            dlx_exchange=os.environ.get("PARSE_DLX_EXCHANGE", "kb.parse.dlx"),
            dlq_queue=os.environ.get("PARSE_DLQ_QUEUE", "kb.parse.dlq"),
            dlq_routing_key=os.environ.get("PARSE_DLQ_ROUTING_KEY", "kb.parse.dead"),
            prefetch_count=int(os.environ.get("PREFETCH_COUNT", "1")),
            api_base_url=os.environ.get("KNOWLEDGE_API_BASE_URL", "http://localhost:8080/api/v1").rstrip("/"),
            callback_path=os.environ.get("TASK_CALLBACK_PATH", "/tasks/{task_id}/callback"),
            status_path=os.environ.get("TASK_STATUS_PATH", "/tasks/{task_id}/status"),
            callback_token=os.environ.get("TASK_CALLBACK_TOKEN", ""),
            request_timeout=int(os.environ.get("HTTP_TIMEOUT", "30")),
            callback_retries=int(os.environ.get("CALLBACK_RETRIES", "3")),
            task_timeout_seconds=int(os.environ.get("TASK_TIMEOUT_SECONDS", "600")),
            file_base_dir=os.environ.get("FILE_BASE_DIR", "./files"),
            max_retry=int(os.environ.get("TASK_MAX_RETRY", "3")),
            minio_endpoint=os.environ.get("MINIO_ENDPOINT", "http://localhost:9000").rstrip("/"),
            minio_access_key=os.environ.get("MINIO_ACCESS_KEY", ""),
            minio_secret_key=os.environ.get("MINIO_SECRET_KEY", ""),
            minio_bucket=os.environ.get("MINIO_BUCKET", "knowledge-files"),
            result_path_prefix=os.environ.get("PARSE_RESULT_PREFIX", "parse-results"),
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
class ParseCancelled(Exception):
    """任务已被用户取消。"""


class ParseTimeout(Exception):
    """任务处理超过配置时限。"""


def classify_error(exc: Exception) -> str:
    """把异常归类为统一错误码。"""
    if isinstance(exc, ParseCancelled):
        return "CANCELLED"
    if isinstance(exc, ParseTimeout):
        return "PARSE_TIMEOUT"
    text = f"{type(exc).__name__}: {exc}".lower()
    if "unsupportedformat" in text.replace("_", "") or "not supported" in text or "不支持" in text:
        return "UNSUPPORTED_FORMAT"
    if "ocr" in text or "tesseract" in text or "paddle" in text:
        return "OCR_FAILED"
    if isinstance(exc, (FileNotFoundError, UnicodeDecodeError)) or "corrupt" in text:
        return "CORRUPTED_FILE"
    return "UNKNOWN_ERROR"


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
    result_key: Optional[str] = None,
    result_summary: Optional[Dict[str, Any]] = None,
    error_code: Optional[str] = None,
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
    if result_key:
        payload["result_key"] = result_key
    if result_summary is not None:
        payload["result_summary"] = result_summary
    if error_code:
        payload["error_code"] = error_code
    if not call_knowledge_api(config, task_id, payload):
        logger.error("任务 %s 状态 %s 回调失败", task_id, status)


def fetch_task_status(config: WorkerConfig, task_id: str) -> Optional[str]:
    """从 API 查询任务当前状态，用于取消检查。"""
    try:
        import requests
    except ImportError:
        return None
    url = config.api_base_url + config.status_path.format(task_id=task_id)
    headers = {"Content-Type": "application/json"}
    if config.callback_token:
        headers["Authorization"] = f"Bearer {config.callback_token}"
    try:
        response = requests.get(url, headers=headers, timeout=config.request_timeout)
        response.raise_for_status()
        body = response.json()
        data = body.get("data", body) if isinstance(body, dict) else {}
        return data.get("status") if isinstance(data, dict) else None
    except Exception as exc:
        logger.warning("查询任务 %s 状态失败（不阻断）: %s", task_id, exc)
        return None


def ensure_active(config: WorkerConfig, task_id: str, stage: str) -> None:
    """阶段开始前检查任务是否被取消。"""
    status = fetch_task_status(config, task_id)
    if status == "CANCELLED":
        raise ParseCancelled(f"任务在「{stage}」阶段前已被取消")


def check_deadline(config: WorkerConfig, started_at: float) -> None:
    """检查任务是否超过配置时限。"""
    if time.time() - started_at > config.task_timeout_seconds:
        raise ParseTimeout(f"任务超过 {config.task_timeout_seconds} 秒超时")


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


def build_minio_client(config: WorkerConfig):
    """构造 MinIO 客户端。"""
    from urllib.parse import urlparse

    from minio import Minio

    endpoint = urlparse(config.minio_endpoint).netloc or config.minio_endpoint
    secure = urlparse(config.minio_endpoint).scheme == "https"
    return Minio(
        endpoint,
        access_key=config.minio_access_key,
        secret_key=config.minio_secret_key,
        secure=secure,
    )


def upload_parse_result(config: WorkerConfig, task: Dict[str, Any], result: Dict[str, Any]) -> str:
    """把完整解析结果写入 MinIO，返回对象键。"""
    task_id = task.get("task_id", "unknown")
    attempt = task.get("attempt") or (int(task.get("retry_count", 0)) + 1)
    key = f"{config.result_path_prefix}/{task_id}/{attempt}.json"
    data = json.dumps(result, ensure_ascii=False).encode("utf-8")
    client = build_minio_client(config)
    client.put_object(
        config.minio_bucket,
        key,
        io.BytesIO(data),
        len(data),
        content_type="application/json",
    )
    logger.info("解析结果已写入 MinIO: %s", key)
    return key


def process_task(config: WorkerConfig, task: Dict[str, Any], router: ParserRouter) -> Dict[str, Any]:
    """执行单个解析任务，返回回调用的 result。"""
    task_id = str(task.get("task_id", "unknown"))
    document_id = task.get("document_id")
    started_at = time.time()

    ensure_active(config, task_id, "下载文件")
    check_deadline(config, started_at)
    report_status(config, task_id, "PROCESSING", document_id=document_id, progress=10)

    file_path = resolve_file(config, task)
    logger.info("任务 %s 开始解析: %s", task_id, file_path)

    ensure_active(config, task_id, "格式解析")
    check_deadline(config, started_at)
    document = router.parse(
        file_path,
        document_id=document_id,
        source_file_id=task.get("source_file_id"),
        task_id=task_id,
    )
    report_status(config, task_id, "PROCESSING", document_id=document_id, progress=50)

    ensure_active(config, task_id, "文本清洗与分块")
    check_deadline(config, started_at)
    document = TextCleaner().clean_dict(document)
    chunk_config = _chunk_config_from_task(task, document.get("file_type", ""))
    document = StructuredChunker(chunk_config).chunk_dict(document)
    report_status(config, task_id, "PROCESSING", document_id=document_id, progress=80)

    ensure_active(config, task_id, "结果上传")
    check_deadline(config, started_at)
    embedded = maybe_embed(config, document)
    elements = document.get("elements", [])
    chunks = document.get("chunks", [])
    stats = {
        "element_count": len(elements),
        "chunk_count": len(chunks),
        "embedded_chunk_count": embedded,
        "elapsed_ms": int((time.time() - started_at) * 1000),
        "warnings": document.get("warnings", []),
    }
    return {
        "schema_version": "1.0",
        "task_id": task_id,
        "document_id": document_id,
        "file_name": document.get("file_name", task.get("file_name", "")),
        "file_type": document.get("file_type", task.get("file_type", "")),
        "parser_version": "worker-1.0",
        "elements": elements,
        "chunks": chunks,
        "stats": stats,
    }


# ----------------------------------------------------------------------
# RabbitMQ 消费
# ----------------------------------------------------------------------
def republish_task(channel, config: WorkerConfig, task: Dict[str, Any]) -> None:
    """按递增 attempt 重新投递任务，避免原消息无限 requeue。"""
    import pika

    channel.basic_publish(
        exchange=config.parse_exchange,
        routing_key=config.parse_routing_key,
        body=json.dumps(task, ensure_ascii=False).encode("utf-8"),
        properties=pika.BasicProperties(delivery_mode=2),
    )


RETRYABLE_ERROR_CODES = {
    "OCR_FAILED",
    "PARSE_TIMEOUT",
    "CALLBACK_FAILED",
    "PERSIST_FAILED",
    "UNKNOWN_ERROR",
}


def publish_to_dlq(channel, config: WorkerConfig, task: Dict[str, Any], error_code: str, message: str) -> None:
    """把最终失败的任务投递到死信队列，便于人工排查。"""
    import pika

    channel.exchange_declare(exchange=config.dlx_exchange, exchange_type="topic", durable=True)
    channel.queue_declare(queue=config.dlq_queue, durable=True)
    channel.queue_bind(
        queue=config.dlq_queue,
        exchange=config.dlx_exchange,
        routing_key=config.dlq_routing_key,
    )
    enriched = dict(task)
    enriched["error_code"] = error_code
    enriched["error_message"] = message
    enriched["failed_at"] = int(time.time())
    channel.basic_publish(
        exchange=config.dlx_exchange,
        routing_key=config.dlq_routing_key,
        body=json.dumps(enriched, ensure_ascii=False).encode("utf-8"),
        properties=pika.BasicProperties(delivery_mode=2),
    )
    logger.error("任务 %s 已进入死信队列: %s", task.get("task_id"), error_code)


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
        result_key = upload_parse_result(config, task, result)
        report_status(
            config, task_id, "COMPLETED",
            document_id=result["document_id"], progress=100,
            message="解析完成", result_key=result_key,
            result_summary=result["stats"],
        )
        channel.basic_ack(delivery_tag=method.delivery_tag)
    except Exception as exc:
        logger.exception("任务 %s 处理失败: %s", task_id, exc)
        error_code = classify_error(exc)
        message = f"[{error_code}] {exc}"

        if isinstance(exc, ParseCancelled):
            report_status(
                config, task_id, "CANCELLED",
                document_id=task.get("document_id"), progress=0,
                message=message, error_code=error_code,
            )
            channel.basic_ack(delivery_tag=method.delivery_tag)
            return

        retry_count = int(task.get("retry_count", 0)) + 1
        task["retry_count"] = retry_count
        task["attempt"] = int(task.get("attempt", 1)) + 1
        task["error_code"] = error_code

        if retry_count < config.max_retry and error_code in RETRYABLE_ERROR_CODES:
            logger.warning("任务 %s 处理失败，准备第 %s 次重试: %s", task_id, retry_count, error_code)
            republish_task(channel, config, task)
            channel.basic_ack(delivery_tag=method.delivery_tag)
        else:
            report_status(
                config, task_id, "FAILED",
                document_id=task.get("document_id"),
                message=message, error_code=error_code,
            )
            publish_to_dlq(channel, config, task, error_code, message)
            channel.basic_ack(delivery_tag=method.delivery_tag)


def start_consumer(config: WorkerConfig) -> None:
    try:
        import pika
    except ImportError as exc:  # pragma: no cover
        raise RuntimeError("消费 RabbitMQ 需要 pika，请先执行: pip install pika") from exc

    router = get_default_router()
    connection = pika.BlockingConnection(pika.URLParameters(config.rabbitmq_url))
    channel = connection.channel()
    channel.queue_declare(queue=config.queue_name, durable=True)
    channel.exchange_declare(exchange=config.dlx_exchange, exchange_type="topic", durable=True)
    channel.queue_declare(queue=config.dlq_queue, durable=True)
    channel.queue_bind(
        queue=config.dlq_queue,
        exchange=config.dlx_exchange,
        routing_key=config.dlq_routing_key,
    )
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
