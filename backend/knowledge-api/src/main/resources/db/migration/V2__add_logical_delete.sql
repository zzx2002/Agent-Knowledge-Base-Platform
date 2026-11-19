-- =============================================================
-- V2: 全库逻辑删除支持
-- 为所有业务表补齐 deleted 字段，并把唯一约束改为「仅未删除记录唯一」，
-- 使逻辑删除后的记录不阻塞同名/同键数据重新创建。
-- =============================================================

-- ---------- 1. 补齐 deleted 字段 ----------
ALTER TABLE sys_user                 ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_role                 ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE sys_user_role            ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_knowledge_base        ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_document              ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_document_version      ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_parse_task            ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_document_element      ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_chunk                 ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_embedding_model       ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_model_config          ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_agent_client          ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_oauth_client          ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_oauth_token           ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_permission            ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE kb_access_log            ADD COLUMN IF NOT EXISTS deleted BOOLEAN NOT NULL DEFAULT FALSE;

-- ---------- 2. 原唯一约束改为「仅未删除记录唯一」 ----------
ALTER TABLE sys_user            DROP CONSTRAINT IF EXISTS sys_user_username_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_username_active ON sys_user (username) WHERE deleted = FALSE;

ALTER TABLE sys_role            DROP CONSTRAINT IF EXISTS sys_role_role_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_role_code_active ON sys_role (role_code) WHERE deleted = FALSE;

ALTER TABLE sys_user_role       DROP CONSTRAINT IF EXISTS uk_sys_user_role;
CREATE UNIQUE INDEX IF NOT EXISTS uk_sys_user_role_active ON sys_user_role (user_id, role_id) WHERE deleted = FALSE;

ALTER TABLE kb_embedding_model  DROP CONSTRAINT IF EXISTS kb_embedding_model_model_code_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_embedding_model_code_active ON kb_embedding_model (model_code) WHERE deleted = FALSE;

ALTER TABLE kb_model_config     DROP CONSTRAINT IF EXISTS kb_model_config_config_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_model_config_key_active ON kb_model_config (config_key) WHERE deleted = FALSE;

ALTER TABLE kb_oauth_client     DROP CONSTRAINT IF EXISTS kb_oauth_client_client_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS uk_oauth_client_id_active ON kb_oauth_client (client_id) WHERE deleted = FALSE;

ALTER TABLE kb_permission       DROP CONSTRAINT IF EXISTS uk_kb_permission;
CREATE UNIQUE INDEX IF NOT EXISTS uk_kb_permission_active
    ON kb_permission (subject_type, subject_id, kb_id, permission) WHERE deleted = FALSE;

-- ---------- 3. 常用查询索引 ----------
CREATE INDEX IF NOT EXISTS idx_doc_deleted ON kb_document (deleted);
CREATE INDEX IF NOT EXISTS idx_kb_deleted ON kb_knowledge_base (deleted);
CREATE INDEX IF NOT EXISTS idx_task_deleted ON kb_parse_task (deleted);
CREATE INDEX IF NOT EXISTS idx_chunk_deleted ON kb_chunk (deleted);
CREATE INDEX IF NOT EXISTS idx_permission_deleted ON kb_permission (deleted);
