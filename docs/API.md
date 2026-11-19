# REST API

统一前缀：`/api/v1`。除登录外均需 `Authorization: Bearer <token>`。统一响应结构：`{ "code": 0, "message": "ok", "data": ... }`。

## 认证

| Method | Path | 说明 |
| --- | --- | --- |
| POST | /auth/login | 账号密码登录 |
| POST | /auth/logout | 退出 |
| POST | /auth/refresh | 刷新 Token |
| GET | /auth/me | 当前用户 |

## 用户管理（仅 ADMIN）

| Method | Path | 说明 |
| --- | --- | --- |
| POST | /users | 创建用户并分配角色 |
| GET | /users | 用户列表 |

创建用户请求体：

```json
{
  "username": "alice",
  "password": "password123",
  "display_name": "Alice",
  "role_code": "EDITOR"
}
```

角色：`ADMIN` / `KB_ADMIN` / `EDITOR` / `VIEWER`。可用不同账号登录验证知识库隔离。

## 知识库

| Method | Path | 说明 |
| --- | --- | --- |
| POST | /knowledge-bases | 创建（自动归属当前用户） |
| GET | /knowledge-bases | 列表（仅返回有权访问的知识库） |
| GET | /knowledge-bases/{id} | 详情 |
| PUT | /knowledge-bases/{id} | 更新 |
| DELETE | /knowledge-bases/{id} | 删除 |
| GET | /knowledge-bases/{id}/permissions | 授权列表 |
| POST | /knowledge-bases/{id}/permissions | 授权用户/角色 |
| DELETE | /knowledge-bases/{id}/permissions/{subjectType}/{subjectId}/{permission} | 撤销授权 |

授权请求体：

```json
{ "subject_type": "USER", "subject_id": 2, "permission": "READ", "granted": true }
```

`subject_type` 支持 `USER` / `ROLE`；`permission` 支持 `READ` / `WRITE` / `DELETE` / `ADMIN`。只有知识库 owner 或全局管理员可管理授权。

## 数据隔离规则

- 创建知识库时自动写入 `owner_id = 当前登录用户`。
- `ADMIN` / `KB_ADMIN` 角色可访问全部知识库。
- 知识库 owner 对自己的库拥有全部权限。
- 其他用户只能访问 `kb_permission` 中显式授权且 `granted=true` 的知识库与操作。
- 知识库列表、文档列表、任务列表均在服务端按上述规则过滤；越权访问返回 403。

## 文档

| Method | Path | 说明 |
| --- | --- | --- |
| POST | /documents/upload | 上传（multipart/form-data） |
| GET | /documents | 列表 |
| GET | /documents/{id} | 详情 |
| GET | /documents/{id}/content | 解析内容 |
| GET | /documents/{id}/chunks | 分块 |
| POST | /documents/{id}/reparse | 重新解析 |
| DELETE | /documents/{id} | 删除 |

## 检索与问答

| Method | Path | 说明 |
| --- | --- | --- |
| POST | /search | 混合检索 |
| GET | /search/debug | 检索调试 |
| POST | /chat | RAG 问答 |
| POST | /chat/stream | 流式问答（SSE） |

问答响应示例：

```json
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

## 任务

| Method | Path | 说明 |
| --- | --- | --- |
| GET | /tasks | 列表 |
| GET | /tasks/{id} | 详情 |
| POST | /tasks/{id}/retry | 重试 |
| POST | /tasks/{id}/cancel | 取消 |
| POST | /tasks/{id}/callback | Parser Worker 内部状态回调（Authorization: Bearer 内部令牌） |

## 解析任务消息

Java API 投递到 RabbitMQ `kb.parse.queue` 的消息字段（snake_case）：

```json
{
  "task_id": 1,
  "document_id": 1,
  "knowledge_base_id": 1,
  "storage_key": "documents/1/uuid_file.docx",
  "file_name": "file.docx",
  "file_type": "DOCX"
}
```

Python Worker 从 MinIO 下载 `storage_key` 对应对象，解析、清洗、分块后回调 `/tasks/{id}/callback`。
