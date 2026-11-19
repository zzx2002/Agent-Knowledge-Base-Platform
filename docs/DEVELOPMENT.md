# 开发说明

## 环境

| 工具 | 版本 |
| --- | --- |
| JDK | 17+ |
| Maven | 3.9+ |
| Node.js | 20+ |
| Python | 3.11+ |
| Docker Compose | v2 |

## 后端

```bash
mvn -f backend/knowledge-api/pom.xml clean package
mvn -f backend/knowledge-mcp-server/pom.xml clean package
```

Swagger：`http://localhost:8080/swagger-ui.html`（docker 环境下经 Nginx 为 `/api/v1/swagger-ui.html`）。

## 前端

```bash
cd frontend/web-admin
npm install
npm run dev
```

开发代理：`/api` → `http://localhost:8080`。

## Python

```bash
python -m venv .venv
pip install -r parser/requirements.txt
pip install -r ai/requirements.txt
python -m compileall -q parser ai
```

## 代码规范

- Java 包前缀 `com.agentkb`，不使用 Lombok。
- 所有对外接口统一响应结构与异常处理。
- 解析器通过统一接口注册，不修改后续链路。
- 数据库变更以迁移脚本形式追加。
- 新增接口同步更新 `docs/API.md`，新增 MCP 工具同步更新 `docs/MCP.md`。
