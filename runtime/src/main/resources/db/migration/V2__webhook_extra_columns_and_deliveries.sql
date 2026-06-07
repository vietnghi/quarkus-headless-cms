-- V2__webhook_extra_columns_and_deliveries.sql
-- Adds missing columns to cms_webhooks and creates the webhook deliveries audit table.

-- Add missing columns to cms_webhooks (aligning with CmsWebhook entity definition)
ALTER TABLE cms_webhooks
    ADD COLUMN IF NOT EXISTS secret VARCHAR(512),
    ADD COLUMN IF NOT EXISTS max_retries INT NOT NULL DEFAULT 3,
    ADD COLUMN IF NOT EXISTS timeout_ms INT NOT NULL DEFAULT 10000;

-- Webhook delivery audit log
CREATE TABLE IF NOT EXISTS cms_webhook_deliveries (
    id BIGSERIAL PRIMARY KEY,
    webhook_id BIGINT NOT NULL,
    webhook_name VARCHAR(255),
    event_type VARCHAR(100) NOT NULL,
    document_id VARCHAR(36),
    content_type VARCHAR(100),
    target_url VARCHAR(2048) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    http_status INT,
    response_body VARCHAR(4096),
    error_message VARCHAR(2048),
    attempt_number INT NOT NULL DEFAULT 1,
    max_attempts INT NOT NULL DEFAULT 3,
    duration_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_deliveries_webhook ON cms_webhook_deliveries(webhook_id);
CREATE INDEX IF NOT EXISTS idx_deliveries_event ON cms_webhook_deliveries(event_type);
CREATE INDEX IF NOT EXISTS idx_deliveries_status ON cms_webhook_deliveries(status);
CREATE INDEX IF NOT EXISTS idx_deliveries_created ON cms_webhook_deliveries(created_at);
