-- =============================================================
-- Agent Knowledge Base Platform - 初始化建表脚本 (PostgreSQL)
-- 对应架构文档第 10 章：核心数据表
-- =============================================================

-- ---------- 用户与权限 ----------

CREATE TABLE IF NOT EXISTS sys_user (
    id            BIGINT       PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    display_name  VARCHAR(64),
    email         VARCHAR(128),
    phone         VARCHAR(32),
    status        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE sys_user IS '系统用户表';

CREATE TABLE IF NOT EXISTS sys_role (
    id          BIGINT      PRIMARY KEY,
    role_code   VARCHAR(32) NOT NULL UNIQUE,
    role_name   VARCHAR(64) NOT NULL,
    description TEXT,
    created_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE sys_role IS '系统角色表';

CREATE TABLE IF NOT EXISTS sys_user_role (
    id         BIGINT    PRIMARY KEY,
    user_id    BIGINT    NOT NULL,
    role_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sys_user_role UNIQUE (user_id, role_id)
);
COMMENT ON TABLE sys_user_role IS '用户角色关联表';

-- ---------- 知识库与文档 ----------

CREATE TABLE IF NOT EXISTS kb_knowledge_base (
    id              BIGINT       PRIMARY KEY,
    name            VARCHAR(128) NOT NULL,
    description     TEXT,
    embedding_model VARCHAR(64)  DEFAULT 'bge-m3',
    chunk_strategy  VARCHAR(32)  DEFAULT 'STRUCTURE_FIRST',
    chunk_size      INTEGER      DEFAULT 800,
    chunk_overlap   INTEGER      DEFAULT 100,
    owner_id        BIGINT,
    status          VARCHAR(16)  DEFAULT 'ACTIVE',
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted         BOOLEAN      NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE kb_knowledge_base IS '知识库表';

CREATE TABLE IF NOT EXISTS kb_document (
    id           BIGINT       PRIMARY KEY,
    kb_id        BIGINT       NOT NULL,
    file_name    VARCHAR(255) NOT NULL,
    file_type    VARCHAR(16),
    file_size    BIGINT       DEFAULT 0,
    storage_key  VARCHAR(512),
    bucket       VARCHAR(128),
    storage_path VARCHAR(512),
    status       VARCHAR(16)  DEFAULT 'PENDING',
    uploader_id  BIGINT,
    version      INTEGER      DEFAULT 1,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted      BOOLEAN      NOT NULL DEFAULT FALSE
);
COMMENT ON TABLE kb_document IS '文档元数据表';
CREATE INDEX IF NOT EXISTS idx_kb_document_kb ON kb_document(kb_id);

CREATE TABLE IF NOT EXISTS kb_document_version (
    id          BIGINT       PRIMARY KEY,
    document_id BIGINT       NOT NULL,
    version     INTEGER      NOT NULL,
    storage_key VARCHAR(512),
    file_name   VARCHAR(255),
    file_size   BIGINT,
    remark      TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_document_version IS '文档版本表';
CREATE INDEX IF NOT EXISTS idx_doc_version_doc ON kb_document_version(document_id);

CREATE TABLE IF NOT EXISTS kb_parse_task (
    id            BIGINT    PRIMARY KEY,
    document_id   BIGINT    NOT NULL,
    kb_id         BIGINT    NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    progress      INTEGER   DEFAULT 0,
    error_message TEXT,
    retry_count   INTEGER   DEFAULT 0,
    started_at    TIMESTAMP,
    finished_at   TIMESTAMP,
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_parse_task IS '文档解析任务表';
CREATE INDEX IF NOT EXISTS idx_parse_task_status ON kb_parse_task(status);
CREATE INDEX IF NOT EXISTS idx_parse_task_doc ON kb_parse_task(document_id);

CREATE TABLE IF NOT EXISTS kb_document_element (
    id           BIGINT       PRIMARY KEY,
    document_id  BIGINT       NOT NULL,
    element_id   VARCHAR(64),
    type         VARCHAR(32),
    content      TEXT,
    level        INTEGER,
    page         INTEGER,
    table_data   JSONB,
    heading_path VARCHAR(512),
    page_start   INTEGER,
    page_end     INTEGER,
    bounding_box JSONB,
    metadata     JSONB,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_document_element IS '统一文档结构元素表';
CREATE INDEX IF NOT EXISTS idx_doc_element_doc ON kb_document_element(document_id);

CREATE TABLE IF NOT EXISTS kb_chunk (
    id              BIGINT       PRIMARY KEY,
    document_id     BIGINT       NOT NULL,
    kb_id           BIGINT       NOT NULL,
    version_id      BIGINT,
    parent_chunk_id BIGINT,
    chunk_index     INTEGER,
    content         TEXT,
    token_count     INTEGER,
    heading_path    VARCHAR(512),
    page_start      INTEGER,
    page_end        INTEGER,
    metadata        JSONB,
    embedding_model VARCHAR(64),
    indexed         BOOLEAN      DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_chunk IS '文档分块表';
CREATE INDEX IF NOT EXISTS idx_chunk_kb ON kb_chunk(kb_id);
CREATE INDEX IF NOT EXISTS idx_chunk_doc ON kb_chunk(document_id);

-- ---------- 模型配置 ----------

CREATE TABLE IF NOT EXISTS kb_embedding_model (
    id          BIGINT       PRIMARY KEY,
    model_code  VARCHAR(64)  NOT NULL UNIQUE,
    model_name  VARCHAR(128),
    provider    VARCHAR(32),
    dimension   INTEGER,
    endpoint    VARCHAR(255),
    api_key     VARCHAR(255),
    enabled     BOOLEAN      DEFAULT TRUE,
    is_default  BOOLEAN      DEFAULT FALSE,
    description TEXT,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_embedding_model IS 'Embedding 模型配置表';

CREATE TABLE IF NOT EXISTS kb_model_config (
    id          BIGINT       PRIMARY KEY,
    config_key  VARCHAR(64)  NOT NULL UNIQUE,
    model_type  VARCHAR(32),
    model_name  VARCHAR(128),
    base_url    VARCHAR(255),
    api_key     VARCHAR(255),
    params      JSONB,
    enabled     BOOLEAN      DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_model_config IS 'AI 模型配置表';

-- ---------- Agent / OAuth ----------

CREATE TABLE IF NOT EXISTS kb_agent_client (
    id             BIGINT       PRIMARY KEY,
    client_name    VARCHAR(128) NOT NULL,
    client_type    VARCHAR(32),
    owner_id       BIGINT,
    allowed_kb_ids JSONB,
    status         VARCHAR(16)  DEFAULT 'ACTIVE',
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_agent_client IS 'Agent 客户端表';

CREATE TABLE IF NOT EXISTS kb_oauth_client (
    id             BIGINT       PRIMARY KEY,
    client_id      VARCHAR(128) NOT NULL UNIQUE,
    client_secret  VARCHAR(255),
    agent_client_id BIGINT,
    redirect_uris  JSONB,
    grant_types    JSONB,
    scopes         JSONB,
    enabled        BOOLEAN      DEFAULT TRUE,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_oauth_client IS 'OAuth 客户端表';

CREATE TABLE IF NOT EXISTS kb_oauth_token (
    id                BIGINT       PRIMARY KEY,
    client_id         VARCHAR(128),
    user_id           BIGINT,
    access_token      VARCHAR(512),
    refresh_token     VARCHAR(512),
    token_type        VARCHAR(16)  DEFAULT 'Bearer',
    scope             VARCHAR(255),
    expires_at        TIMESTAMP,
    refresh_expires_at TIMESTAMP,
    revoked           BOOLEAN      DEFAULT FALSE,
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_oauth_token IS 'OAuth 授权令牌表';
CREATE INDEX IF NOT EXISTS idx_oauth_token_access ON kb_oauth_token(access_token);

CREATE TABLE IF NOT EXISTS kb_permission (
    id           BIGINT      PRIMARY KEY,
    subject_type VARCHAR(16) NOT NULL,
    subject_id   BIGINT      NOT NULL,
    kb_id        BIGINT,
    permission   VARCHAR(32) NOT NULL,
    granted      BOOLEAN     DEFAULT TRUE,
    created_at   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_kb_permission UNIQUE (subject_type, subject_id, kb_id, permission)
);
COMMENT ON TABLE kb_permission IS '权限配置表';

CREATE TABLE IF NOT EXISTS kb_access_log (
    id             BIGINT       PRIMARY KEY,
    user_id        BIGINT,
    agent_client_id BIGINT,
    operation      VARCHAR(64),
    resource_type  VARCHAR(32),
    resource_id    VARCHAR(64),
    request_ip     VARCHAR(64),
    user_agent     VARCHAR(255),
    success        BOOLEAN      DEFAULT TRUE,
    detail         JSONB,
    created_at     TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_access_log IS '调用审计日志表';
CREATE INDEX IF NOT EXISTS idx_access_log_created ON kb_access_log(created_at);

-- =============================================================
-- 种子数据（仅首次初始化）
-- 默认管理员账号：admin / admin123 （BCrypt, strength 10）
-- =============================================================

INSERT INTO sys_role (id, role_code, role_name, description) VALUES
    (1, 'ADMIN',    '系统管理员',   '系统全部权限'),
    (2, 'KB_ADMIN', '知识库管理员', '知识库管理权限'),
    (3, 'EDITOR',   '编辑者',       '文档上传和管理权限'),
    (4, 'VIEWER',   '只读用户',     '查询及阅读权限')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user (id, username, password_hash, display_name, email, status) VALUES
    (1, 'admin', '$2b$10$/0w/wlXwSQHjl.5WXsqj..INtwARUAmHv9nhFZUb2EkBlF.d0vVfW', '系统管理员', 'admin@localhost', 'ACTIVE')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sys_user_role (id, user_id, role_id) VALUES
    (1, 1, 1)
ON CONFLICT (id) DO NOTHING;

INSERT INTO kb_embedding_model (id, model_code, model_name, provider, dimension, enabled, is_default) VALUES
    (1, 'bge-m3', 'BGE-M3', 'local', 1024, TRUE, TRUE)
ON CONFLICT (id) DO NOTHING;

INSERT INTO kb_model_config (id, config_key, model_type, model_name, enabled) VALUES
    (1, 'DEFAULT_LLM', 'LLM', 'qwen', TRUE)
ON CONFLICT (id) DO NOTHING;
