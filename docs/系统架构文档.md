# 架构说明

设计来源：仓库根目录 `Agent Knowledge Base Platform.md`（V1.0）。本文件只记录落地后的工程映射与关键约束。

## 分层

| 层 | 组件 | 职责 |
| --- | --- | --- |
| 客户端 | Web / Agent / 外部系统 | 使用入口 |
| 接入 | Nginx / REST API / MCP Server | 请求接入与协议适配 |
| 安全 | Spring Security / JWT / RBAC | 认证授权，服务端强制校验知识库权限 |
| 业务 | knowledge-api | 知识库、文档、任务、检索、问答 |
| 处理 | parser / ai | 12 种格式解析、分块、Embedding、Rerank、OCR |
| 存储 | PostgreSQL / MinIO / ES / Qdrant / Redis / RabbitMQ | 元数据、文件、全文索引、向量、缓存、异步任务 |

## 关键原则

1. Java 负责业务，Python 负责 AI 与文档解析。
2. MCP Server 独立部署，只通过 REST 调用 knowledge-api，不直连数据库。
3. 所有解析器输出统一文档结构，RAG 不感知具体文件格式。
4. 检索采用全文 + 向量混合召回，RRF 融合，Reranker 重排。
5. 模型、解析器、存储组件均可替换；配置按知识库粒度生效。
6. 第一版优先打通完整链路，而非堆积功能。

## 请求链路

```text
上传 → MinIO 原文件 + PG 元数据 + MQ 任务
     → Parser Worker 解析 → 统一结构 → 清洗 → 分块
     → Embedding → ES 索引 + Qdrant 向量 → 任务完成

检索 → 身份与权限校验 → 向量召回 + BM25 召回 → RRF 融合
     → 去重过滤 → Rerank → 上下文构建 → LLM → 答案 + 引用
```
