#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "============================================"
echo " 停止 Agent Knowledge Base Platform 服务"
echo "============================================"

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未检测到 docker，请先安装 Docker" >&2
  exit 1
fi

cd "${ROOT_DIR}"

if ! docker compose version >/dev/null 2>&1; then
  echo "[错误] docker compose 不可用，请安装 Compose 插件" >&2
  exit 1
fi

echo "[信息] 停止并移除容器（保留数据卷）..."
docker compose down

echo "============================================"
echo " 服务已停止，数据卷未删除"
echo "============================================"
