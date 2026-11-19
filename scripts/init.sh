#!/usr/bin/env bash
set -euo pipefail

# 路径基于脚本所在目录的上级（Monorepo 根）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "============================================"
echo " 初始化 Agent Knowledge Base Platform 环境"
echo "============================================"

# 1. 生成 .env（如不存在）
if [[ -f "${ROOT_DIR}/.env" ]]; then
  echo "[跳过] .env 已存在，未做修改"
elif [[ -f "${ROOT_DIR}/.env.example" ]]; then
  cp "${ROOT_DIR}/.env.example" "${ROOT_DIR}/.env"
  echo "[完成] 已由 .env.example 生成 .env，请按实际环境修改密码等配置"
else
  echo "[错误] 未找到 .env.example，无法生成 .env" >&2
  exit 1
fi

# 2. 创建持久化数据目录（MinIO / Postgres / Elasticsearch / Qdrant）
DATA_DIRS=(
  "data/minio"
  "data/postgres"
  "data/elasticsearch"
  "data/qdrant"
  "data/redis"
  "backups"
)

for dir in "${DATA_DIRS[@]}"; do
  if mkdir -p "${ROOT_DIR}/${dir}"; then
    echo "[完成] 创建目录 ${dir}"
  else
    echo "[错误] 创建目录失败：${dir}" >&2
    exit 1
  fi
done

echo "============================================"
echo " 初始化完成，可执行 ./scripts/start.sh 启动服务"
echo "============================================"
