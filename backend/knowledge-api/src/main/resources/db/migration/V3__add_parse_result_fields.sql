-- =============================================================
-- V3: 文档解析结果落库支持
-- 为元素/分块/任务表补充版本归属与结果摘要字段，支持重新解析追溯。
-- =============================================================

ALTER TABLE kb_document_element
    ADD COLUMN IF NOT EXISTS version_id BIGINT,
    ADD COLUMN IF NOT EXISTS parse_task_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_file_id VARCHAR(128);

ALTER TABLE kb_chunk
    ADD COLUMN IF NOT EXISTS version_id BIGINT,
    ADD COLUMN IF NOT EXISTS parse_task_id BIGINT,
    ADD COLUMN IF NOT EXISTS chunk_type VARCHAR(16) DEFAULT 'child',
    ADD COLUMN IF NOT EXISTS element_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS element_ids JSONB;

ALTER TABLE kb_chunk ALTER COLUMN parent_chunk_id TYPE VARCHAR(64) USING parent_chunk_id::text;

ALTER TABLE kb_parse_task
    ADD COLUMN IF NOT EXISTS result_key VARCHAR(512),
    ADD COLUMN IF NOT EXISTS result_summary JSONB;

CREATE INDEX IF NOT EXISTS idx_element_doc_version ON kb_document_element (document_id, version_id, deleted);
CREATE INDEX IF NOT EXISTS idx_element_task ON kb_document_element (parse_task_id);
CREATE INDEX IF NOT EXISTS idx_chunk_doc_version ON kb_chunk (document_id, version_id, deleted);
CREATE INDEX IF NOT EXISTS idx_chunk_task ON kb_chunk (parse_task_id);
CREATE INDEX IF NOT EXISTS idx_task_kb_status ON kb_parse_task (kb_id, status, deleted);
