# MCP 接入

MCP Server 独立部署于 `backend/knowledge-mcp-server`，默认端口 8081，Endpoint `/mcp`，协议 Streamable HTTP。MCP 不直连数据库，所有能力通过 knowledge-api 的 REST 接口实现，并透传 Bearer Token 做身份与权限校验。

## 工具

| Tool | 功能 | 权限 | 第一版 |
| --- | --- | --- | --- |
| list_knowledge_bases | 知识库列表 | READ | 是 |
| search_knowledge | 知识检索 | READ | 是 |
| ask_knowledge | 知识问答 | READ | 是 |
| get_document | 文档详情 | READ | 是 |
| list_documents | 文档列表 | READ | 是 |
| get_document_content | 文档内容 | READ | 是 |
| upload_document | 上传文档 | WRITE | 可选 |
| get_parse_status | 解析状态 | READ | 可选 |
| delete_document | 删除文档 | DELETE | 可选 |

写入与删除工具必须增加权限校验与操作确认机制。

## 参数示例

```json
// search_knowledge
{ "knowledge_base_id": "kb_001", "query": "Redis 分布式限流如何实现？", "top_k": 5, "rerank": true }

// ask_knowledge
{ "knowledge_base_id": "kb_001", "question": "公司的 Redis 限流方案是什么？", "top_k": 5 }

// list_documents
{ "knowledge_base_id": "kb_001", "keyword": "Redis", "page": 1, "page_size": 20 }
```

工具 schema 清单见 `backend/knowledge-mcp-server/src/main/resources/mcp-tools.json`。

## 认证

账号密码登录用于 Web；Agent 使用 OAuth 2.0 Authorization Code + PKCE。需要支持 OAuth Discovery、redirect_uri 校验、Access Token 有效期、Refresh Token 轮换与撤销。任何知识检索都必须在服务端验证知识库访问权限。

## 接入地址

```text
MCP:      https://kb.example.com/mcp
REST API: https://kb.example.com/api/v1
Web:      https://kb.example.com
```
