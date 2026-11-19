MinIO 对象存储配置说明
========================

用途：存储原始文件、格式转换文件、解析产物与图片资源（见架构文档 10.3）。

关键环境变量（在根目录 .env 中配置，勿提交真实密码）：
  MINIO_ROOT_USER=kb_admin
  MINIO_ROOT_PASSWORD=change_me

服务端口：
  9000  - S3 API
  9001  - Web 控制台

启动时由 docker-compose 通过命令行参数创建数据桶：
  kb-raw       原始文件
  kb-parsed    解析产物

数据目录：./data/minio（由 scripts/init.sh 创建，需持久化挂载）
生产环境请修改默认密码并启用 TLS。
