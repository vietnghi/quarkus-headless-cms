-- V3__supporting_entities.sql
-- Adds remaining entity tables: media library, audit log, locales, custom fields, review system.
-- Also adds missing constraints from V1 (unique uid on core_schema).

-- ────────────────────────────────────────────────────────────
-- 1. Media Library: Folders
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cms_folders (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    path VARCHAR(1024) NOT NULL,
    parent_id BIGINT,
    created_by_id BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_folders_parent_id ON cms_folders(parent_id);
CREATE INDEX IF NOT EXISTS idx_folders_path ON cms_folders(path);

-- ────────────────────────────────────────────────────────────
-- 2. Media Library: Files
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cms_files (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    hash VARCHAR(64),
    ext VARCHAR(20),
    mime_type VARCHAR(127) NOT NULL,
    size BIGINT NOT NULL,
    url VARCHAR(2048),
    provider VARCHAR(50) NOT NULL,
    storage_key VARCHAR(1024),
    alt_text VARCHAR(512),
    caption VARCHAR(1024),
    width INT,
    height INT,
    folder_id BIGINT,
    folder_path VARCHAR(1024),
    related_content_type VARCHAR(255),
    uploaded_by_id BIGINT,
    formats JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_files_hash ON cms_files(hash);
CREATE INDEX IF NOT EXISTS idx_files_provider ON cms_files(provider);
CREATE INDEX IF NOT EXISTS idx_files_folder_id ON cms_files(folder_id);
CREATE INDEX IF NOT EXISTS idx_files_created_at ON cms_files(created_at);

-- ────────────────────────────────────────────────────────────
-- 3. Audit Log
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cms_audit_log (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    entry_id BIGINT,
    content_type VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    action VARCHAR(50) NOT NULL,
    user_id BIGINT NOT NULL,
    changes JSONB,
    summary VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_document ON cms_audit_log(document_id);
CREATE INDEX IF NOT EXISTS idx_audit_content_type ON cms_audit_log(content_type);
CREATE INDEX IF NOT EXISTS idx_audit_user ON cms_audit_log(user_id);
CREATE INDEX IF NOT EXISTS idx_audit_action ON cms_audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_created ON cms_audit_log(created_at);

-- ────────────────────────────────────────────────────────────
-- 4. I18n Locales
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cms_locales (
    code VARCHAR(10) NOT NULL PRIMARY KEY,
    display_name VARCHAR(100) NOT NULL,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_locales_code ON cms_locales(code);

-- ────────────────────────────────────────────────────────────
-- 5. Custom Field Definitions
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cms_custom_field_defs (
    id BIGSERIAL PRIMARY KEY,
    content_type VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    label VARCHAR(200) NOT NULL,
    field_type VARCHAR(20) NOT NULL DEFAULT 'text',
    default_value VARCHAR(1000),
    is_required BOOLEAN NOT NULL DEFAULT FALSE,
    placeholder VARCHAR(500),
    options VARCHAR(2000),
    sort_order INT DEFAULT 0,
    description VARCHAR(2000),
    config JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_custom_field_content_type_name
    ON cms_custom_field_defs(content_type, field_name);

-- ────────────────────────────────────────────────────────────
-- 6. Custom Field Values (separate-table storage strategy)
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cms_custom_field_values (
    id BIGSERIAL PRIMARY KEY,
    entry_id BIGINT NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    field_name VARCHAR(100) NOT NULL,
    value_json JSONB
);

CREATE INDEX IF NOT EXISTS idx_cfv_entry_id ON cms_custom_field_values(entry_id);
CREATE INDEX IF NOT EXISTS idx_cfv_content_type ON cms_custom_field_values(content_type);
CREATE UNIQUE INDEX IF NOT EXISTS idx_cfv_entry_field
    ON cms_custom_field_values(entry_id, field_name);

-- ────────────────────────────────────────────────────────────
-- 7. Content Review System
-- ────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS cms_reviews (
    id BIGSERIAL PRIMARY KEY,
    document_id VARCHAR(36) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    requested_by_id BIGINT NOT NULL,
    reviewer_id BIGINT,
    comment VARCHAR(2000),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    resolved_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX IF NOT EXISTS idx_review_document ON cms_reviews(document_id, locale);
CREATE INDEX IF NOT EXISTS idx_review_reviewer ON cms_reviews(reviewer_id);
CREATE INDEX IF NOT EXISTS idx_review_status ON cms_reviews(status);
CREATE INDEX IF NOT EXISTS idx_review_created ON cms_reviews(created_at);

-- ────────────────────────────────────────────────────────────
-- 8. Add missing UNIQUE constraint on core_schema.uid
-- The CoreSchema entity uses findByUid() which expects unique results.
-- V1 did not enforce this at the schema level.
-- ────────────────────────────────────────────────────────────
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_core_schema_uid'
          AND conrelid = 'core_schema'::regclass
    ) THEN
        ALTER TABLE core_schema ADD CONSTRAINT uq_core_schema_uid UNIQUE (uid);
    END IF;
END $$;
