# 数据库设计

数据库：PostgreSQL 16。初始化脚本：`backend/knowledge-api/src/main/resources/db/migration/V1__init.sql`，逻辑删除迁移：`V2__add_logical_delete.sql`。

## 逻辑删除约定

- 所有业务表都有 `deleted BOOLEAN NOT NULL DEFAULT FALSE` 字段，MyBatis-Plus 实体通过 `@TableLogic` 自动过滤与软删除。
- 删除知识库/文档时，级联把关联的版本、元素、分块、解析任务一并标记为 `deleted=TRUE`。
- 用户名、角色编码等唯一约束改为**仅未删除记录唯一**的部分唯一索引，逻辑删除后可用同名重新创建。
- 例外（允许物理删除）：
  - `kb_permission` 授权记录在撤销/删除知识库时直接 `DELETE`；
  - MinIO 中的原始文件对象在删除文档时直接移除。

## 表清单

| 表 | 用途 |
| --- | --- |
| sys_user | 用户 |
| sys_role | 角色（ADMIN / KB_ADMIN / EDITOR / VIEWER） |
| sys_user_role | 用户角色关系 |
| kb_knowledge_base | 知识库，含 embedding 模型与分块策略配置 |
| kb_document | 文档元数据 |
| kb_document_version | 文档版本 |
| kb_parse_task | 解析任务状态与日志 |
| kb_document_element | 解析后的结构化元素 |
| kb_chunk | 分块，含父子块关系与页码范围 |
| kb_embedding_model | Embedding 模型配置 |
| kb_model_config | AI 模型配置 |
| kb_agent_client | Agent 客户端 |
| kb_oauth_client | OAuth 客户端 |
| kb_oauth_token | 授权令牌 |
| kb_permission | 权限配置 |
| kb_access_log | 调用审计日志 |

## 存储职责边界

- PostgreSQL：用户信息、知识库、文档元数据、任务状态、权限、Agent 配置。
- MinIO：原始文件、格式转换文件、解析产物、图片资源。
- Elasticsearch：文档与分块的全文索引，BM25 检索。
- Qdrant：分块向量及过滤元数据。
- Redis：缓存、临时任务状态、限流、分布式协调。

## 变更规范

修改表结构时：新增迁移脚本 `V2__xxx.sql`，不要修改已发布的 V1；同步更新实体类与本文。
