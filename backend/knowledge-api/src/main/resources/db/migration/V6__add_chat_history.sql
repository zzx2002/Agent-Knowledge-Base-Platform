-- =============================================================
-- V6: 对话历史持久化
-- =============================================================

CREATE TABLE IF NOT EXISTS kb_chat_session (
    id          BIGINT       PRIMARY KEY,
    user_id     BIGINT       NOT NULL,
    kb_id       BIGINT       NOT NULL,
    title       VARCHAR(255),
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_chat_session IS '问答会话表';
CREATE INDEX IF NOT EXISTS idx_chat_session_user_kb ON kb_chat_session (user_id, kb_id, deleted);

CREATE TABLE IF NOT EXISTS kb_chat_message (
    id          BIGINT       PRIMARY KEY,
    session_id  BIGINT       NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    content     TEXT,
    sources     TEXT,
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
COMMENT ON TABLE kb_chat_message IS '问答消息表';
CREATE INDEX IF NOT EXISTS idx_chat_message_session ON kb_chat_message (session_id, deleted, created_at);
