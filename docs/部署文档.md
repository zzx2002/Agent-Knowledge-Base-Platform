# 部署说明

## 前置条件

- Docker 20+ 与 Docker Compose v2
- Linux 主机（生产建议 4C8G 以上；启用本地模型推理时需 GPU 与 NVIDIA Container Toolkit）
- 复制 `.env.example` 为 `.env` 并修改所有 `change_me`

## 启动

```bash
cp .env.example .env
./scripts/init.sh
./scripts/start.sh
```

`init.sh` 只创建缺失的目录与 `.env`，不会覆盖已有配置。

## 服务与端口

| 服务 | 容器 | 内部端口 |
| --- | --- | --- |
| 网关 | kb-nginx | 80 / 443 |
| 前端 | kb-web | 80 |
| 业务 API | kb-api | 8080 |
| MCP | kb-mcp | 8081 |
| Parser | kb-parser | 5001 |
| OCR | kb-ocr | 8001 |
| Embedding | kb-embedding | 8002 |
| Reranker | kb-reranker | 8003 |
| PostgreSQL | kb-postgres | 5432 |
| MinIO | kb-minio | 9000 / 9001 |
| Elasticsearch | kb-elasticsearch | 9200 |
| Qdrant | kb-qdrant | 6333 |
| Redis | kb-redis | 6379 |
| RabbitMQ | kb-rabbitmq | 5672 / 15672 |

除 80/443 外，所有端口只在 Docker 内部网络暴露，不映射到公网。

## 运维

```bash
./scripts/stop.sh     # 停止
./scripts/backup.sh   # 备份数据库与卷数据到 backups/
docker compose logs -f kb-api
```

## 生产检查项

- 所有默认密码已替换
- 数据卷已纳入备份
- 健康检查全部通过
- Nginx TLS 证书已配置
- MCP 的 OAuth 与 Token 生命周期已验证
- 上传文件大小与类型限制已生效
