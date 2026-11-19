# 私有化 Agent Knowledge Base Platform 架构设计方案

> 项目名称：Agent Knowledge Base Platform
> 项目定位：可私有化部署、支持多格式文档解析、混合检索、RAG 问答及 MCP Agent 接入的独立知识库平台
> 文档版本：V1.0
> 文档状态：初版架构设计

## 1. 项目概述

### 1.1 项目背景

建设一套独立部署的知识库系统，实现文档统一管理、自动解析、内容结构化、向量化存储、全文检索、语义检索和 AI 问答。

系统不仅提供 Web 管理端，还需要提供标准化的 API 和 MCP 服务，使 WorkBuddy、Codex 以及其他支持 MCP 协议的 Agent 客户端能够直接连接知识库。

系统的核心目标：

1. 支持多种办公文档、文本、表格和图片格式。
2. 支持复杂文档解析，包括 PDF 表格、Office 表格和图片 OCR。
3. 支持文档自动分块、向量化及索引管理。
4. 支持全文检索、向量检索和混合检索。
5. 支持基于知识库的 RAG 问答。
6. 支持多知识库管理及权限隔离。
7. 提供 REST API，支持外部业务系统调用。
8. 提供独立 MCP Server，支持 Agent 客户端连接。
9. 支持 Docker Compose 私有化部署。
10. 支持本地模型和第三方模型服务切换。

### 1.2 项目定位

本项目定位为独立的 Agent 知识服务基础设施，而不仅仅是一个 RAG 对话工具。

核心架构原则：

- 业务服务与 AI 解析服务分离。
- MCP 服务与知识库业务服务分离。
- 文档解析器与 RAG 引擎解耦。
- 原始文件与解析结果分离存储。
- 模型服务统一抽象，支持灵活切换。
- 所有知识检索必须执行用户权限校验。

## 2. 系统总体架构

### 2.1 总体架构图

```
flowchart TB
    subgraph Client["客户端接入层"]
        WEB["Web 管理端"]
        AGENT["Agent 客户端<br/>WorkBuddy / Codex"]
        APP["外部业务系统"]
    end

    subgraph Gateway["统一接入层"]
        NGINX["Nginx Gateway"]
        API["REST API"]
        MCP["MCP Server"]
        AUTH["认证与权限中心"]
    end

    subgraph Business["核心业务层"]
        KB["知识库管理"]
        DOC["文档管理"]
        TASK["解析任务管理"]
        SEARCH["检索服务"]
        RAG["RAG 问答服务"]
        MODEL["模型配置管理"]
        ADMIN["系统管理"]
    end

    subgraph Processing["文档处理层"]
        ROUTER["文件类型识别与解析路由"]
        OFFICE["Office Parser"]
        PDF["PDF Parser / Docling"]
        IMAGE["OCR / VLM"]
        TEXT["文本解析器"]
        NORMALIZE["统一数据标准化"]
        CHUNK["智能分块"]
        EMBED["Embedding"]
        RERANK["Reranker"]
    end

    subgraph Storage["数据存储层"]
        PG["PostgreSQL"]
        MINIO["MinIO"]
        ES["Elasticsearch"]
        QDRANT["Qdrant"]
        REDIS["Redis"]
        MQ["RabbitMQ"]
    end

    WEB --> NGINX
    AGENT --> MCP
    APP --> API

    NGINX --> API
    NGINX --> AUTH
    MCP --> AUTH
    MCP --> SEARCH
    MCP --> RAG
    API --> AUTH

    API --> KB
    API --> DOC
    API --> TASK
    API --> SEARCH
    API --> RAG

    DOC --> MINIO
    DOC --> PG
    DOC --> MQ
    MQ --> ROUTER

    ROUTER --> OFFICE
    ROUTER --> PDF
    ROUTER --> IMAGE
    ROUTER --> TEXT

    OFFICE --> NORMALIZE
    PDF --> NORMALIZE
    IMAGE --> NORMALIZE
    TEXT --> NORMALIZE

    NORMALIZE --> CHUNK
    CHUNK --> EMBED
    EMBED --> QDRANT
    CHUNK --> ES

    SEARCH --> ES
    SEARCH --> QDRANT
    SEARCH --> RERANK
    RAG --> SEARCH

    KB --> PG
    TASK --> PG
    AUTH --> PG

    API --> REDIS
    TASK --> MQ
```

### 2.2 系统分层说明

| 层级     | 模块                             | 主要职责           |
| -------- | -------------------------------- | ------------------ |
| 客户端层 | Web / Agent / 外部系统           | 提供系统使用入口   |
| 接入层   | Nginx / REST / MCP               | 请求接入、协议适配 |
| 安全层   | Auth / RBAC                      | 用户认证、权限控制 |
| 业务层   | KB / Document / Search / RAG     | 核心业务逻辑       |
| 处理层   | Parser / OCR / Embedding         | 文档解析和 AI 处理 |
| 存储层   | PostgreSQL / MinIO / ES / Qdrant | 数据持久化及检索   |

## 3. 技术选型

### 3.1 后端技术

| 模块         | 技术              | 说明           |
| ------------ | ----------------- | -------------- |
| 核心业务后端 | Java 17/21        | 业务服务       |
| Web 框架     | Spring Boot 3     | REST API       |
| ORM          | MyBatis-Plus      | 数据访问       |
| MCP          | Java MCP SDK      | Agent 接入     |
| API 文档     | SpringDoc OpenAPI | 接口文档       |
| 认证         | Spring Security   | 身份认证及授权 |
| 异步通信     | RabbitMQ          | 解析任务调度   |

### 3.2 前端技术

| 模块        | 技术         |
| ----------- | ------------ |
| 前端框架    | Vue 3        |
| 开发语言    | TypeScript   |
| UI 组件     | Element Plus |
| 状态管理    | Pinia        |
| HTTP 客户端 | Axios        |
| 图表        | ECharts      |
| 构建工具    | Vite         |

### 3.3 AI 与文档处理

| 模块        | 技术         | 作用                   |
| ----------- | ------------ | ---------------------- |
| 文档解析    | Docling      | PDF、DOCX、PPTX 等     |
| Office 兼容 | Apache POI   | DOC/XLS/PPT 等兼容处理 |
| 格式转换    | LibreOffice  | 旧版 Office 转换       |
| OCR         | PaddleOCR    | 图片文字识别           |
| VLM         | Qwen-VL 等   | 图片语义理解           |
| Embedding   | BGE-M3       | 文本向量化             |
| Reranker    | BGE-Reranker | 召回结果重排序         |
| 文本处理    | Python       | 清洗、分块和预处理     |

### 3.4 数据存储

| 组件       | 技术          | 用途                     |
| ---------- | ------------- | ------------------------ |
| 关系数据库 | PostgreSQL    | 用户、知识库、文档元数据 |
| 对象存储   | MinIO         | 原始文件及解析产物       |
| 全文检索   | Elasticsearch | BM25 关键词检索          |
| 向量数据库 | Qdrant        | 语义向量检索             |
| 缓存       | Redis         | 缓存、任务状态           |
| 消息队列   | RabbitMQ      | 异步解析任务             |

### 3.5 部署技术

- Docker
- Docker Compose
- Nginx
- Linux
- NVIDIA Container Toolkit（需要 GPU 推理时）

## 4. 文件解析模块

### 4.1 支持格式

系统第一版必须支持以下 12 种格式。

| 分类       | 文件格式 | 解析方式                   |
| ---------- | -------- | -------------------------- |
| Word       | DOC      | LibreOffice 转换 + Docling |
| Word       | DOCX     | Docling                    |
| PowerPoint | PPT      | LibreOffice 转换           |
| PowerPoint | PPTX     | Docling                    |
| 文本       | MD       | Markdown Parser            |
| 文本       | TXT      | Python 文本解析            |
| PDF        | PDF      | Docling + PDFBox           |
| Excel      | XLS      | Apache POI                 |
| Excel      | XLSX     | Apache POI                 |
| 表格       | CSV      | CSV Parser                 |
| 图片       | PNG      | PaddleOCR + VLM            |
| 图片       | JPG      | PaddleOCR + VLM            |

### 4.2 解析模块架构

```
flowchart TD
    A["上传文件"] --> B["文件类型识别"]
    B --> C["文件安全校验"]
    C --> D["解析路由"]

    D --> E["Office Parser"]
    D --> F["PDF Parser"]
    D --> G["Text Parser"]
    D --> H["Image Parser"]

    E --> I["统一数据结构"]
    F --> I
    G --> I
    H --> I

    I --> J["文本清洗"]
    J --> K["智能分块"]
    K --> L["元数据提取"]
    L --> M["Embedding"]
    M --> N["索引入库"]
```

### 4.3 各类型文件解析要求

#### DOC / DOCX

- 提取标题、段落和正文。
- 保留标题层级。
- 提取表格结构。
- 保留页码或可追踪的文档位置。
- 处理页眉、页脚和超链接。
- 支持复杂文档转换。

#### PPT / PPTX

- 按幻灯片顺序解析。
- 保留幻灯片编号。
- 提取文本框和标题。
- 提取表格内容。
- 提取图片及图片 OCR。
- 保留演示文稿层级结构。

#### PDF

- 提取文本。
- 提取表格。
- 保留页码。
- 处理多栏排版。
- 处理扫描件。
- 支持 OCR。
- 尽可能保留图片与文本的关联关系。

#### XLS / XLSX

- 保留 Sheet 名称。
- 保留单元格行列坐标。
- 提取表头。
- 保留表格结构。
- 处理合并单元格。
- 支持公式及可读取的计算结果。
- 大型 Excel 支持流式解析。

#### CSV

- 自动识别字符编码。
- 自动识别分隔符。
- 提取表头。
- 保留行列结构。
- 支持大文件流式处理。

#### PNG / JPG

- OCR 文字识别。
- 文字坐标提取。
- 版面识别。
- 可选 VLM 图片理解。
- 保存图片与 OCR 结果之间的关联关系。

### 4.4 统一文档数据结构

所有解析器必须输出统一的数据结构，避免不同格式的数据处理逻辑直接耦合到 RAG 模块。

示例：

```
{
  "document_id": "doc_001",
  "file_name": "技术方案.docx",
  "file_type": "DOCX",
  "elements": [
    {
      "element_id": "element_001",
      "type": "heading",
      "content": "系统架构设计",
      "level": 1,
      "page": 1
    },
    {
      "element_id": "element_002",
      "type": "text",
      "content": "系统采用微服务架构...",
      "page": 1
    },
    {
      "element_id": "element_003",
      "type": "table",
      "content": "服务名称 | 职责",
      "page": 2,
      "table_data": {
        "headers": ["服务名称", "职责"],
        "rows": [
          ["API", "业务接口"],
          ["Parser", "文档解析"]
        ]
      }
    }
  ]
}
```

实际实现还需要增加：

- chunk_id
- parent_chunk_id
- element_id
- source_file_id
- heading_path
- page_start
- page_end
- bounding_box
- metadata

### 4.5 Parser 插件化设计

建议定义统一解析接口：

```
from abc import ABC, abstractmethod

class DocumentParser(ABC):

    @abstractmethod
    def supports(self, file_type: str) -> bool:
        pass

    @abstractmethod
    def parse(self, file_path: str) -> dict:
        pass
```

不同文件格式分别实现 Parser。

未来增加 HTML、JSON、XML 等格式时，只需要增加新的解析器，不需要修改后续处理链路。

## 5. 文档分块与向量化

### 5.1 分块策略

不建议所有文件统一使用固定字符长度切分。

采用结构化分块策略：

| 文件类型 | 分块策略             |
| -------- | -------------------- |
| DOCX     | 按标题、段落、表格   |
| PPTX     | 按幻灯片             |
| PDF      | 按标题、段落、页面   |
| MD       | 按 Markdown 标题     |
| TXT      | 按段落               |
| XLSX     | 按 Sheet、表格、行组 |
| CSV      | 按表头和行组         |
| PNG/JPG  | OCR 结果及图片描述   |

建议支持以下配置：

- chunk_size
- chunk_overlap
- min_chunk_size
- max_chunk_size
- chunk_strategy
- parent_child_enabled

初始建议：

| 参数       | 建议值             |
| ---------- | ------------------ |
| 子块长度   | 500～800 Token     |
| 重叠长度   | 80～120 Token      |
| 父块长度   | 1500～3000 Token   |
| 最小块长度 | 100 Token          |
| 分块方式   | 结构优先，长度兜底 |

以上参数应允许用户按知识库调整。

### 5.2 父子分块

采用 Parent-Child Chunk 模式：

- 父块：完整章节或较大语义段落。
- 子块：用于向量检索的小型文本片段。

检索时匹配子块，返回时可以扩展父块上下文。

### 5.3 Embedding

第一版默认采用 BGE-M3。

要求：

1. 支持批量向量化。
2. 支持异步向量化。
3. 支持向量模型配置。
4. 支持模型版本管理。
5. 支持重新向量化。
6. 支持不同模型的索引隔离。

## 6. RAG 检索模块

### 6.1 检索架构

```
flowchart TD
    A["用户问题"] --> B["Query 预处理"]
    B --> C["权限过滤"]

    C --> D["向量召回"]
    C --> E["关键词召回"]

    D --> F["结果融合"]
    E --> F

    F --> G["去重及过滤"]
    G --> H["Reranker"]
    H --> I["上下文构建"]
    I --> J["LLM"]
    J --> K["答案及引用"]
```

### 6.2 混合检索

采用：

- Qdrant：向量语义检索。
- Elasticsearch：BM25 关键词检索。
- RRF：多路召回结果融合。
- BGE-Reranker：最终结果重排序。

### 6.3 检索能力

必须支持：

1. 按知识库检索。
2. 按文档检索。
3. 多知识库联合检索。
4. 按文件类型过滤。
5. 按元数据过滤。
6. 按用户权限过滤。
7. 检索结果评分。
8. 检索结果去重。
9. 检索过程调试。
10. 原文引用。

### 6.4 RAG 问答

问答返回格式：

```
{
  "success": true,
  "answer": "系统采用 Redis + Lua 实现分布式配额管理。",
  "sources": [
    {
      "document_id": "doc_001",
      "file_name": "Redis限流设计方案.docx",
      "page": 5,
      "chunk_id": "chunk_001",
      "content": "通过 Lua 脚本保证配额分配的原子性。"
    }
  ]
}
```

必须支持：

- 答案生成。
- 原文引用。
- 多文档引用。
- 无答案时拒绝编造。
- 上下文长度控制。
- 检索结果可追溯。

## 7. Agent 接入模块

### 7.1 设计原则

Agent 接入是本项目的重要功能。

系统提供独立 MCP Server，供 WorkBuddy、Codex 等客户端调用。

MCP 不直接访问数据库，而是调用知识库业务 API。

### 7.2 MCP 架构

```
flowchart TB
    A["WorkBuddy"] --> D["MCP Streamable HTTP"]
    B["Codex"] --> D
    C["其他 Agent"] --> D

    D --> E["Knowledge MCP Server"]
    E --> F["认证与权限校验"]
    F --> G["Knowledge Base API"]

    G --> H["知识库管理"]
    G --> I["文档管理"]
    G --> J["RAG 检索"]
```

### 7.3 MCP 工具列表

| Tool                 | 功能           | 权限   |
| -------------------- | -------------- | ------ |
| list_knowledge_bases | 获取知识库列表 | READ   |
| search_knowledge     | 搜索知识库     | READ   |
| ask_knowledge        | 知识库问答     | READ   |
| get_document         | 获取文档详情   | READ   |
| list_documents       | 获取文档列表   | READ   |
| get_document_content | 获取文档内容   | READ   |
| upload_document      | 上传文档       | WRITE  |
| get_parse_status     | 获取解析状态   | READ   |
| delete_document      | 删除文档       | DELETE |

建议第一版优先实现前六个查询工具，写入及删除工具作为可选能力，并且必须增加权限和操作确认机制。

### 7.4 MCP 工具参数

#### search_knowledge

```
{
  "knowledge_base_id": "kb_001",
  "query": "Redis 分布式限流如何实现？",
  "top_k": 5,
  "rerank": true
}
```

#### ask_knowledge

```
{
  "knowledge_base_id": "kb_001",
  "question": "公司的 Redis 限流方案是什么？",
  "top_k": 5
}
```

#### list_documents

```
{
  "knowledge_base_id": "kb_001",
  "keyword": "Redis",
  "page": 1,
  "page_size": 20
}
```

### 7.5 MCP 通信协议

第一版采用：

- MCP Streamable HTTP。
- 独立 MCP Server。
- 统一 REST API 作为业务入口。
- 支持远程连接。
- 支持 OAuth 2.0 授权。
- 支持访问令牌校验。

推荐地址：

```
MCP:
https://kb.example.com/mcp

REST API:
https://kb.example.com/api/v1

Web:
https://kb.example.com
```

## 8. 用户认证与权限管理

### 8.1 用户登录

采用账号密码登录。

用户登录后由认证服务签发访问令牌。

### 8.2 Agent 授权

采用 OAuth 2.0 Authorization Code + PKCE。

预期流程：

```
sequenceDiagram
    participant A as Agent Client
    participant K as Knowledge Base
    participant U as User
    participant T as Token Service

    A->>K: 发起 OAuth 授权
    K->>U: 浏览器登录
    U->>K: 账号密码认证
    K->>U: 用户授权确认
    U->>K: 同意授权
    K->>A: 返回授权码
    A->>T: 使用授权码交换 Token
    T->>A: Access Token / Refresh Token
    A->>K: MCP 请求携带 Token
    K->>K: 验证身份和权限
    K->>A: 返回知识库结果
```

需要注意：

- Agent 客户端是否支持完整 OAuth 流程需要单独验证。
- 不应假定所有 MCP 客户端都支持动态客户端注册。
- 需要支持 OAuth Discovery。
- 需要校验 redirect_uri。
- Access Token 设置有效期。
- Refresh Token 支持轮换和撤销。
- 所有知识检索都需要验证知识库访问权限。

### 8.3 权限模型

第一版采用 RBAC。

角色：

| 角色     | 权限           |
| -------- | -------------- |
| ADMIN    | 系统管理       |
| KB_ADMIN | 知识库管理     |
| EDITOR   | 文档上传和管理 |
| VIEWER   | 查询及阅读     |

权限需要同时覆盖：

- Web 用户。
- REST API 用户。
- MCP Agent 用户。

## 9. Web 管理端功能

### 9.1 系统首页

展示：

- 知识库数量。
- 文档数量。
- 文件存储容量。
- 解析任务数量。
- 解析成功率。
- 检索调用量。
- 问答调用量。

### 9.2 知识库管理

支持：

- 创建知识库。
- 修改知识库。
- 删除知识库。
- 设置知识库描述。
- 配置 Embedding 模型。
- 配置分块策略。
- 设置知识库权限。
- 查看知识库统计。

### 9.3 文档管理

支持：

- 单文件上传。
- 批量上传。
- 文档列表。
- 文档搜索。
- 文档删除。
- 文档重新解析。
- 文档版本管理。
- 解析结果预览。
- Chunk 内容预览。
- 原始文件下载。

### 9.4 知识库对话

支持：

- 选择知识库。
- 输入问题。
- 流式回答。
- 来源引用。
- 查看引用原文。
- 检索结果调试。
- 对话历史。

### 9.5 解析任务管理

支持：

- 查看任务状态。
- 查看任务进度。
- 查看解析日志。
- 查看失败原因。
- 手动重试。
- 任务取消。

### 9.6 Agent 接入管理

支持：

- 查看 MCP Endpoint。
- 创建 Agent 客户端。
- OAuth 授权管理。
- Access Token 管理。
- 授权撤销。
- 查看调用日志。
- 配置 Agent 可访问的知识库。

## 10. 数据库设计

### 10.1 核心数据表

第一版建议使用 PostgreSQL。

| 表名                | 说明               |
| ------------------- | ------------------ |
| sys_user            | 用户表             |
| sys_role            | 角色表             |
| sys_user_role       | 用户角色关系       |
| kb_knowledge_base   | 知识库表           |
| kb_document         | 文档表             |
| kb_document_version | 文档版本表         |
| kb_parse_task       | 解析任务表         |
| kb_document_element | 文档元素表         |
| kb_chunk            | 文档分块表         |
| kb_embedding_model  | Embedding 模型配置 |
| kb_model_config     | AI 模型配置        |
| kb_agent_client     | Agent 客户端       |
| kb_oauth_client     | OAuth 客户端       |
| kb_oauth_token      | 授权令牌           |
| kb_permission       | 权限配置           |
| kb_access_log       | 调用审计日志       |

### 10.2 核心关联关系

```
erDiagram
    SYS_USER ||--o{ KB_KNOWLEDGE_BASE : creates
    KB_KNOWLEDGE_BASE ||--o{ KB_DOCUMENT : contains
    KB_DOCUMENT ||--o{ KB_DOCUMENT_VERSION : versions
    KB_DOCUMENT ||--o{ KB_PARSE_TASK : tasks
    KB_DOCUMENT ||--o{ KB_DOCUMENT_ELEMENT : contains
    KB_DOCUMENT ||--o{ KB_CHUNK : chunks
    SYS_USER ||--o{ KB_AGENT_CLIENT : owns
    KB_AGENT_CLIENT ||--o{ KB_OAUTH_TOKEN : authorizes
```

### 10.3 数据存储职责

PostgreSQL：

- 用户信息。
- 知识库信息。
- 文档元数据。
- 任务状态。
- 权限关系。
- Agent 配置。

MinIO：

- 原始文件。
- 格式转换文件。
- 解析产物。
- 图片资源。

Elasticsearch：

- 文档全文索引。
- Chunk 关键词索引。
- BM25 检索。

Qdrant：

- Chunk 向量。
- 向量检索元数据。

Redis：

- 缓存。
- 临时任务状态。
- 分布式协调。
- 限流。

## 11. 文档处理任务设计

### 11.1 异步任务流程

```
flowchart TD
    A["用户上传文件"] --> B["Java API"]
    B --> C["MinIO 保存原文件"]
    B --> D["PostgreSQL 创建文档"]
    B --> E["RabbitMQ 投递任务"]

    E --> F["Python Parser Worker"]
    F --> G["文档解析"]
    G --> H["文本清洗"]
    H --> I["智能分块"]
    I --> J["Embedding"]

    J --> K["Elasticsearch"]
    J --> L["Qdrant"]

    K --> M["更新任务状态"]
    L --> M
```

### 11.2 任务状态

```
PENDING
   |
   v
PROCESSING
   |
   +----> COMPLETED
   |
   +----> FAILED
```

可选增加：

- CANCELLED
- RETRYING

### 11.3 任务可靠性

必须支持：

1. 任务幂等。
2. 失败重试。
3. 超时控制。
4. 异常日志。
5. 任务取消。
6. 重复文件识别。
7. 文档重新解析。
8. 删除时同步清理索引。
9. 任务状态持久化。

## 12. 项目工程目录

建议采用 Monorepo 组织工程。

```
agent-knowledge-base/
│
├── README.md
├── AGENTS.md
├── docker-compose.yml
├── .env.example
├── .gitignore
│
├── docs/
│   ├── ARCHITECTURE.md
│   ├── DATABASE.md
│   ├── API.md
│   ├── MCP.md
│   ├── DEPLOYMENT.md
│   └── DEVELOPMENT.md
│
├── backend/
│   ├── knowledge-api/
│   │   ├── src/
│   │   └── pom.xml
│   │
│   └── knowledge-mcp-server/
│       ├── src/
│       └── pom.xml
│
├── parser/
│   ├── common/
│   ├── office/
│   ├── pdf/
│   ├── image/
│   ├── text/
│   ├── pipeline/
│   └── requirements.txt
│
├── ai/
│   ├── embedding/
│   ├── reranker/
│   └── ocr/
│
├── frontend/
│   └── web-admin/
│
├── deploy/
│   ├── nginx/
│   ├── postgres/
│   ├── elasticsearch/
│   ├── qdrant/
│   ├── minio/
│   └── redis/
│
└── scripts/
    ├── init.sh
    ├── start.sh
    ├── stop.sh
    └── backup.sh
```

## 13. Docker 部署规划

### 13.1 服务清单

| 服务          | 容器          | 建议端口     |
| ------------- | ------------- | ------------ |
| 网关          | nginx         | 80 / 443     |
| 前端          | kb-web        | 内部端口     |
| 业务 API      | kb-api        | 8080         |
| MCP           | kb-mcp        | 8081         |
| Docling       | kb-parser     | 5001         |
| OCR           | kb-ocr        | 8001         |
| Embedding     | kb-embedding  | 8002         |
| Reranker      | kb-reranker   | 8003         |
| PostgreSQL    | postgres      | 5432         |
| MinIO         | minio         | 9000 / 9001  |
| Elasticsearch | elasticsearch | 9200         |
| Qdrant        | qdrant        | 6333         |
| Redis         | redis         | 6379         |
| RabbitMQ      | rabbitmq      | 5672 / 15672 |

端口仅作为内部规划，实际部署中应优先使用 Docker 内部网络，避免数据库和中间件端口直接暴露到公网。

### 13.2 Docker Compose 部署要求

- 所有服务容器化。
- 配置持久化 Volume。
- 配置统一网络。
- 配置健康检查。
- 配置环境变量。
- 配置日志轮转。
- 支持服务独立重启。
- 支持服务依赖顺序。
- 支持数据备份。
- 支持离线镜像部署的扩展能力。

### 13.3 环境变量

```
# PostgreSQL
POSTGRES_DB=knowledge_base
POSTGRES_USER=kb_user
POSTGRES_PASSWORD=change_me

# Redis
REDIS_PASSWORD=change_me

# MinIO
MINIO_ROOT_USER=kb_admin
MINIO_ROOT_PASSWORD=change_me

# Elasticsearch
ES_PASSWORD=change_me

# Qdrant
QDRANT_API_KEY=change_me

# Model
EMBEDDING_MODEL=bge-m3
RERANKER_MODEL=bge-reranker

# LLM
LLM_BASE_URL=
LLM_API_KEY=
LLM_MODEL=
```

生产环境禁止使用默认密码，所有密钥必须通过安全的环境变量或密钥管理服务注入。

## 14. REST API 规划

统一 API 前缀：

```
/api/v1
```

### 14.1 认证接口

| Method | Path          | 说明         |
| ------ | ------------- | ------------ |
| POST   | /auth/login   | 用户登录     |
| POST   | /auth/logout  | 用户退出     |
| POST   | /auth/refresh | 刷新 Token   |
| GET    | /auth/me      | 获取用户信息 |

### 14.2 知识库接口

| Method | Path                  | 说明       |
| ------ | --------------------- | ---------- |
| POST   | /knowledge-bases      | 创建知识库 |
| GET    | /knowledge-bases      | 知识库列表 |
| GET    | /knowledge-bases/{id} | 知识库详情 |
| PUT    | /knowledge-bases/{id} | 更新知识库 |
| DELETE | /knowledge-bases/{id} | 删除知识库 |

### 14.3 文档接口

| Method | Path                    | 说明         |
| ------ | ----------------------- | ------------ |
| POST   | /documents/upload       | 上传文档     |
| GET    | /documents              | 文档列表     |
| GET    | /documents/{id}         | 文档详情     |
| GET    | /documents/{id}/content | 获取文档内容 |
| DELETE | /documents/{id}         | 删除文档     |
| POST   | /documents/{id}/reparse | 重新解析     |
| GET    | /documents/{id}/chunks  | 获取分块     |

### 14.4 检索接口

| Method | Path          | 说明     |
| ------ | ------------- | -------- |
| POST   | /search       | 混合检索 |
| POST   | /chat         | RAG 问答 |
| POST   | /chat/stream  | 流式问答 |
| GET    | /search/debug | 检索调试 |

### 14.5 任务接口

| Method | Path               | 说明     |
| ------ | ------------------ | -------- |
| GET    | /tasks             | 任务列表 |
| GET    | /tasks/{id}        | 任务详情 |
| POST   | /tasks/{id}/retry  | 重试任务 |
| POST   | /tasks/{id}/cancel | 取消任务 |

## 15. 非功能性要求

### 15.1 性能

- 支持批量文档上传。
- 解析任务异步化。
- 支持多 Worker 并发解析。
- 支持 Embedding 批量推理。
- 支持检索缓存。
- 支持模型并发控制。
- 支持大文件流式处理。

### 15.2 安全

- 用户身份认证。
- RBAC 权限管理。
- 知识库数据隔离。
- MCP 请求鉴权。
- OAuth Token 生命周期管理。
- 上传文件类型校验。
- 文件大小限制。
- 恶意文件检测。
- API 限流。
- 操作审计。
- 敏感信息保护。

### 15.3 可维护性

- 模块之间低耦合。
- Parser 插件化。
- 模型配置化。
- API 版本管理。
- 结构化日志。
- 统一异常处理。
- 服务健康检查。
- 自动化测试。
- 版本升级和数据迁移机制。

## 16. 开发实施计划

建议分为六个阶段。

### 第一阶段：基础工程

开发内容：

- Monorepo 工程搭建。
- Spring Boot 初始化。
- Vue 前端初始化。
- PostgreSQL 集成。
- Redis 集成。
- MinIO 集成。
- 用户登录。
- 知识库 CRUD。
- 文档上传。

验收标准：

- 能够正常启动。
- 能够登录。
- 能够创建知识库。
- 能够上传文件。
- 能够保存文件及元数据。

### 第二阶段：文档解析

开发内容：

- Parser 统一接口。
- Docling 集成。
- LibreOffice 集成。
- Apache POI 集成。
- PaddleOCR 集成。
- 文件格式路由。
- 统一文档数据结构。
- RabbitMQ 异步任务。
- 解析结果预览。

验收标准：

- 12 种格式均能正常解析。
- 支持批量上传。
- 支持异步解析。
- 支持解析失败重试。
- 支持表格结构保留。

### 第三阶段：RAG 核心

开发内容：

- 文档分块。
- 父子分块。
- Embedding。
- Qdrant 集成。
- Elasticsearch 集成。
- 混合检索。
- Reranker。
- 检索调试。

验收标准：

- 文档能够完成向量化。
- 能够执行全文检索。
- 能够执行语义检索。
- 能够执行混合检索。
- 检索结果能够追溯原文。

### 第四阶段：RAG 问答

开发内容：

- LLM 接入。
- Prompt 管理。
- 上下文构建。
- RAG 问答。
- 流式输出。
- 来源引用。
- 对话历史。

验收标准：

- 可以通过 Web 提问。
- 能够根据知识库回答。
- 答案附带来源。
- 不相关问题能够识别。
- 支持检索过程调试。

### 第五阶段：MCP Agent 接入

开发内容：

- Java MCP Server。
- MCP Streamable HTTP。
- MCP Tool 注册。
- MCP Tool 参数定义。
- OAuth 2.0。
- Agent 客户端授权。
- MCP 调用审计。

验收标准：

- Agent 可以成功连接。
- Agent 可以获取知识库列表。
- Agent 可以检索知识库。
- Agent 可以调用 RAG 问答。
- Agent 不能访问未授权知识库。

### 第六阶段：生产化部署

开发内容：

- Docker Compose。
- Nginx。
- 服务健康检查。
- 监控。
- 日志。
- 数据备份。
- 权限审计。
- 性能测试。

验收标准：

- 能够通过 Docker Compose 启动。
- 服务异常能够恢复。
- 数据能够持久化。
- 支持完整备份和恢复。
- 支持远程 MCP 连接。

## 17. 第一版范围控制

第一版重点是完整打通：

```
上传文档
    |
    v
文档解析
    |
    v
文档标准化
    |
    v
文本分块
    |
    v
Embedding
    |
    v
全文索引 + 向量索引
    |
    v
混合检索
    |
    v
RAG 问答
    |
    v
MCP Server
    |
    v
Agent 客户端调用
```

第一版必须完成：

- 12 种文件格式支持。
- 知识库管理。
- 文档管理。
- 异步解析。
- 文档分块。
- 向量化。
- 混合检索。
- RAG 问答。
- MCP 接入。
- 用户权限隔离。
- Docker 部署。

暂不作为第一版强制要求：

- 多租户商业化计费。
- 工作流编排平台。
- Agent 自主知识整理。
- 知识图谱。
- 多模态视频解析。
- 复杂 BI 分析。
- 分布式 Kubernetes 部署。

## 18. 最终技术架构总结

| 模块        | 最终选型                          |
| ----------- | --------------------------------- |
| 业务后端    | Java 17/21 + Spring Boot 3        |
| ORM         | MyBatis-Plus                      |
| 前端        | Vue 3 + TypeScript + Element Plus |
| MCP         | Java MCP SDK                      |
| MCP 协议    | Streamable HTTP                   |
| 文档解析    | Docling                           |
| Office 兼容 | Apache POI + LibreOffice          |
| OCR         | PaddleOCR                         |
| 图片理解    | Qwen-VL（可选）                   |
| Embedding   | BGE-M3                            |
| Reranker    | BGE-Reranker                      |
| 数据库      | PostgreSQL                        |
| 对象存储    | MinIO                             |
| 全文检索    | Elasticsearch                     |
| 向量检索    | Qdrant                            |
| 缓存        | Redis                             |
| 消息队列    | RabbitMQ                          |
| 部署        | Docker Compose + Nginx            |

### 最终架构原则

1. Java 负责业务，Python 负责 AI 和文档解析。
2. REST API 是统一业务入口。
3. MCP Server 独立部署，不直接操作数据库。
4. 所有文档格式统一输出标准化结构。
5. 检索采用全文 + 向量混合检索。
6. Agent 通过 OAuth 授权接入。
7. 知识库权限必须在服务端执行。
8. 模型、解析器、存储组件均尽可能支持替换。
9. 第一版优先保证完整业务链路，而不是堆积功能。
10. 系统必须能够独立部署，不依赖任何外部知识库平台。

**项目最终目标：构建一个可以被任意兼容 MCP 的 Agent 客户端调用的私有化知识库基础设施。**