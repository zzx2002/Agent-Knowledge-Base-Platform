# AGENTS.md

本文件供 AI Agent 在本仓库中工作时参考。

## 项目定位

私有化 Agent Knowledge Base Platform。业务服务、AI 解析服务、MCP 服务三者分离；REST API 是唯一业务入口；MCP Server 不直接访问数据库。

## 约定

- Java 17 + Spring Boot 3，包名前缀 `com.agentkb`，后端不使用 Lombok。
- Python 3.11+，解析器和模型服务依赖可选导入，缺少依赖时给出清晰错误。
- 前端 Vue 3 + TypeScript，API 前缀 `/api/v1`。
- 所有知识检索必须携带用户身份并执行知识库权限校验，禁止绕过鉴权。
- 原始文件、解析产物、索引分别存放于 MinIO、PostgreSQL、Elasticsearch/Qdrant。
- 新增文档格式时只增加 Parser 实现，不修改 RAG 链路。
- 生产环境禁止默认密码；密钥通过环境变量注入。

## Git 提交规范

- **所有提交说明（标题与正文）必须使用简体中文**，禁止英文提交说明。
- 标题格式：`<中文类型>: <简短描述>`，描述不超过 50 个汉字，不以句号结尾。
- 允许的中文类型：`初始化`、`新增`、`修复`、`重构`、`优化`、`文档`、`样式`、`测试`、`构建`、`配置`、`数据库`、`其他`。
- 需要正文时，空一行后用中文分点说明改动原因与影响。
- 示例：`新增: 知识库级权限隔离与授权管理`、`修复: MinIO 健康检查在未安装 mc 的镜像中失败`、`数据库: 为全部业务表补充逻辑删除字段`。

## 常用命令

```bash
# 后端编译（Maven 3.9+）
mvn -f backend/knowledge-api/pom.xml clean package
mvn -f backend/knowledge-mcp-server/pom.xml clean package

# 前端
cd frontend/web-admin && npm install && npm run dev

# Python 语法检查
python -m compileall -q parser ai

# 容器
./scripts/start.sh
./scripts/stop.sh
```

## 代码修改原则

1. 先读 `Agent Knowledge Base Platform.md` 对应章节再动手。
2. 保持模块边界，不让 MCP 直连数据库、不让解析器耦合 RAG。
3. 修改数据库结构时同步更新 `backend/knowledge-api/src/main/resources/db/migration/`。
4. 修改接口时同步更新 `docs/API.md` 或 `docs/MCP.md`。
