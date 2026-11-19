-- =============================================================
-- V5: 分块索引状态
-- 记录外部检索系统文档 ID、索引时间与失败原因。
-- =============================================================

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS es_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS qdrant_point_id VARCHAR(128),
    ADD COLUMN IF NOT EXISTS indexed_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS index_error TEXT;

CREATE INDEX IF NOT EXISTS idx_chunk_indexed ON kb_chunk (indexed, deleted);
