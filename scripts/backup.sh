#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "============================================"
echo " 备份 Agent Knowledge Base Platform 数据"
echo "============================================"

if ! command -v docker >/dev/null 2>&1; then
  echo "[错误] 未检测到 docker，请先安装 Docker" >&2
  exit 1
fi

if ! docker compose version >/dev/null 2>&1; then
  echo "[错误] docker compose 不可用，请安装 Compose 插件" >&2
  exit 1
fi

cd "${ROOT_DIR}"

# 读取 .env 中的数据库配置（如存在）
if [[ -f "${ROOT_DIR}/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "${ROOT_DIR}/.env"
  set +a
fi

DB_USER="${POSTGRES_USER:-kb_user}"
DB_NAME="${POSTGRES_DB:-knowledge_base}"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
BACKUP_DIR="${ROOT_DIR}/backups"
BACKUP_FILE="${BACKUP_DIR}/kb_backup_${TIMESTAMP}.tar.gz"
TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

mkdir -p "${BACKUP_DIR}"

echo "[信息] 导出 PostgreSQL 数据库 ${DB_NAME}..."
if ! docker compose exec -T postgres pg_dump -U "${DB_USER}" -d "${DB_NAME}" -Fc > "${TMP_DIR}/${DB_NAME}_${TIMESTAMP}.dump"; then
  echo "[错误] pg_dump 导出失败，请确认 postgres 服务正在运行" >&2
  exit 1
fi

echo "[信息] 打包备份文件..."
tar -czf "${BACKUP_FILE}" -C "${TMP_DIR}" .

echo "============================================"
echo " 备份完成：${BACKUP_FILE}"
echo "============================================"
