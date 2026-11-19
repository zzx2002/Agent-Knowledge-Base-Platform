# Agent Knowledge Base Platform

可私有化部署的知识库基础设施：多格式文档解析、混合检索、RAG 问答，并通过 MCP 对外提供 Agent 可调用的知识服务。

架构设计来源见 [`Agent Knowledge Base Platform.md`](./Agent%20Knowledge%20Base%20Platform.md)。

## 开发文档

- [REST API](./docs/API.md)
- [数据库设计](./docs/DATABASE.md)
- [MCP 工具说明](./docs/MCP.md)
- [部署说明](./docs/DEPLOYMENT.md)
- [开发环境](./docs/DEVELOPMENT.md)
- [文档解析模块开发设计](./docs/DOCUMENT_PARSING_DESIGN.md)

## 技术栈

| 层 | 选型 |
| --- | --- |
| 业务后端 | Java 17 + Spring Boot 3 + MyBatis-Plus |
| Agent 接入 | Java MCP SDK，Streamable HTTP |
| 前端 | Vue 3 + TypeScript + Element Plus + Vite |
| 文档解析 | Python + Docling / Apache POI / PaddleOCR |
| 检索 | Elasticsearch (BM25) + Qdrant (向量) + BGE-Reranker |
| 存储 | PostgreSQL + MinIO + Redis + RabbitMQ |
| 部署 | Docker Compose + Nginx |

## 目录结构

```
.
├── docs/                     # 架构、数据库、API、MCP、部署、开发文档
├── backend/
│   ├── knowledge-api/        # 业务 REST API（8080）
│   └── knowledge-mcp-server/ # 独立 MCP Server（8081）
├── parser/                   # 文档解析与分块 Python 工程
├── ai/                       # Embedding / Reranker / OCR 模型服务
├── frontend/web-admin/       # Web 管理端
├── deploy/                   # 各中间件配置
└── scripts/                  # 初始化、启停、备份脚本
```

## 快速开始

```bash
cp .env.example .env
./scripts/init.sh
./scripts/start.sh
```

- Web: http://localhost
- API: http://localhost/api/v1
- Swagger: http://localhost/api/v1/swagger-ui.html
- MCP: http://localhost/mcp

生产环境必须修改 `.env` 中所有 `change_me` 项。

## 开发阶段

见架构文档第 16 章。当前工程骨架覆盖第一阶段（基础工程），并为后续阶段预留了解析器插件、检索、问答和 MCP 工具的扩展点。
