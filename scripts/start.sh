#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "============================================"
echo " 启动 Agent Knowledge Base Platform 服务"
echo "============================================"

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未检测到 docker，请先安装 Docker" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[错误] docker compose 不可用，请安装 Compose 插件" >&2
  exit 1
fi

if [[ ! -f "${ROOT_DIR}/docker-compose.yml" ]]; then
  echo "[错误] 未找到 ${ROOT_DIR}/docker-compose.yml" >&2
  exit 1
fi

cd "${ROOT_DIR}"

echo "[信息] 后台启动所有服务..."
docker compose up -d

echo "[信息] 当前服务状态："
docker compose ps

echo "============================================"
echo " 启动命令已执行，可用 docker compose logs -f 查看日志"
echo "============================================"
