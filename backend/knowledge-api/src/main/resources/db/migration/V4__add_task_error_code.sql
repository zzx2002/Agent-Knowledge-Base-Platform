-- =============================================================
-- V4: 解析任务错误码
-- 为任务表补充标准化错误码，便于前端展示与统计。
-- =============================================================

ALTER TABLE kb_parse_task
    ADD COLUMN IF NOT EXISTS error_code VARCHAR(32);

CREATE INDEX IF NOT EXISTS idx_task_error_code ON kb_parse_task (error_code, deleted);
