-- PostgreSQL 初始化脚本：创建知识库数据库与专用用户
-- 由 docker-entrypoint-initdb.d 在首次启动时执行

-- 创建数据库（若不存在）
SELECT 'CREATE DATABASE knowledge_base'
WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'knowledge_base')\gexec

-- 创建用户（若不存在），密码由环境变量注入，此处为占位默认密码
DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_catalog.pg_roles WHERE rolname = 'kb_user') THEN
        CREATE ROLE kb_user LOGIN PASSWORD 'change_me';
    END IF;
END
$$;

-- 授权
GRANT ALL PRIVILEGES ON DATABASE knowledge_base TO kb_user;
ALTER DATABASE knowledge_base OWNER TO kb_user;
