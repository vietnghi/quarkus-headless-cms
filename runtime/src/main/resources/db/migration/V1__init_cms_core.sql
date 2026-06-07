-- V1__init_cms_core.sql
-- Initial schema for Quarkus Headless CMS.
-- Creates all core tables: entries, relations, schemas, users, roles, permissions, tokens, webhooks.

-- Core content entries (hybrid document-on-RDBMS)
CREATE TABLE IF NOT EXISTS cms_entries (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    status VARCHAR(15) NOT NULL DEFAULT 'draft',
    version_number INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    created_by_id BIGINT,
    updated_by_id BIGINT,
    published_by_id BIGINT,
    data JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX IF NOT EXISTS idx_entries_doc_id ON cms_entries(document_id);
CREATE INDEX IF NOT EXISTS idx_entries_content_type_status ON cms_entries(content_type, status);
CREATE INDEX IF NOT EXISTS idx_entries_locale ON cms_entries(locale);
CREATE INDEX IF NOT EXISTS idx_entries_data_gin ON cms_entries USING gin (data);

-- Relations table (generic adjacency for dynamic schemas)
CREATE TABLE IF NOT EXISTS cms_relations (
    id BIGSERIAL PRIMARY KEY,
    field_name VARCHAR(100) NOT NULL,
    source_document_id VARCHAR(36) NOT NULL,
    source_type VARCHAR(100) NOT NULL,
    target_document_id VARCHAR(36) NOT NULL,
    target_type VARCHAR(100) NOT NULL,
    order_index INT DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_relations_source ON cms_relations(source_document_id, field_name);
CREATE INDEX IF NOT EXISTS idx_relations_target ON cms_relations(target_document_id);

-- Schema registry (content-type and component definitions)
CREATE TABLE IF NOT EXISTS core_schema (
    id BIGSERIAL PRIMARY KEY,
    uid VARCHAR(150) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    definition JSONB NOT NULL,
    version INT NOT NULL DEFAULT 1,
    version_history JSONB,
    previous_definition JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_core_schema_uid ON core_schema(uid);
CREATE INDEX IF NOT EXISTS idx_core_schema_kind ON core_schema(kind);

-- Admin users
CREATE TABLE IF NOT EXISTS cms_users (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_blocked BOOLEAN NOT NULL DEFAULT FALSE,
    preferred_locale VARCHAR(10) DEFAULT 'en',
    last_login_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_users_username ON cms_users(username);
CREATE INDEX IF NOT EXISTS idx_users_email ON cms_users(email);

-- Roles
CREATE TABLE IF NOT EXISTS cms_roles (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(100) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_roles_code ON cms_roles(code);

-- User-to-role assignments (many-to-many)
CREATE TABLE IF NOT EXISTS cms_user_roles (
    user_id BIGINT NOT NULL REFERENCES cms_users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES cms_roles(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

-- Permissions (actions linked to roles)
CREATE TABLE IF NOT EXISTS cms_permissions (
    id BIGSERIAL PRIMARY KEY,
    action VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    fields JSONB,
    conditions JSONB,
    role_id BIGINT REFERENCES cms_roles(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_permissions_role ON cms_permissions(role_id);
CREATE INDEX IF NOT EXISTS idx_permissions_action ON cms_permissions(action);

-- API tokens (for client API authentication)
CREATE TABLE IF NOT EXISTS cms_api_tokens (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    type VARCHAR(50) NOT NULL DEFAULT 'full-access',
    description TEXT,
    last_used_at TIMESTAMP WITH TIME ZONE,
    expires_at TIMESTAMP WITH TIME ZONE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_id BIGINT REFERENCES cms_users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_api_tokens_hash ON cms_api_tokens(token_hash);

-- Webhooks (event-driven HTTP callbacks)
CREATE TABLE IF NOT EXISTS cms_webhooks (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    url VARCHAR(2048) NOT NULL,
    events JSONB NOT NULL DEFAULT '[]'::jsonb,
    headers JSONB DEFAULT '{}'::jsonb,
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by_id BIGINT REFERENCES cms_users(id),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_webhooks_enabled ON cms_webhooks(is_enabled);
