-- V4__seed_data.sql
-- Seeds the CMS with initial data: default roles, admin user, default locale, and default
-- permissions. These are the minimum records required for the CMS to be usable after a fresh
-- install — the admin user can log in, manage content, and configure additional settings.
--
-- NOTE: If you're migrating an existing database, consider reviewing which seed records
-- are already present and adjusting accordingly.
--
-- SQL Server compatibility notes:
--   - Replace `gen_random_uuid()` with `NEWID()` for SQL Server
--   - The bcrypt hash below uses the $2b$ prefix (compatible with $2a$/crypt implementations)
--   - For SQL Server, uncomment the MERGE variant below

-- ────────────────────────────────────────────────────────────
-- 1. Default Locale (English)
-- ────────────────────────────────────────────────────────────
INSERT INTO cms_locales (code, display_name, is_default, enabled)
SELECT 'en', 'English (US)', TRUE, TRUE
WHERE NOT EXISTS (SELECT 1 FROM cms_locales WHERE code = 'en');

-- ────────────────────────────────────────────────────────────
-- 2. Default Roles
-- ────────────────────────────────────────────────────────────
INSERT INTO cms_roles (code, name, description)
SELECT 'strapi-super-admin', 'Super Admin', 'Super admin role with unrestricted access to all CMS features and settings.'
WHERE NOT EXISTS (SELECT 1 FROM cms_roles WHERE code = 'strapi-super-admin');

INSERT INTO cms_roles (code, name, description)
SELECT 'strapi-editor', 'Editor', 'Can create, read, update, publish, and delete content entries.'
WHERE NOT EXISTS (SELECT 1 FROM cms_roles WHERE code = 'strapi-editor');

INSERT INTO cms_roles (code, name, description)
SELECT 'strapi-author', 'Author', 'Can create and edit their own content entries but cannot publish.'
WHERE NOT EXISTS (SELECT 1 FROM cms_roles WHERE code = 'strapi-author');

-- ────────────────────────────────────────────────────────────
-- 3. Super Admin Permissions (wildcard — all actions on all subjects)
-- ────────────────────────────────────────────────────────────
INSERT INTO cms_permissions (action, subject, role_id)
SELECT '*', '*', r.id
FROM cms_roles r
WHERE r.code = 'strapi-super-admin'
  AND NOT EXISTS (
      SELECT 1 FROM cms_permissions p
      WHERE p.role_id = r.id AND p.action = '*' AND p.subject = '*'
  );

-- ────────────────────────────────────────────────────────────
-- 4. Editor Permissions (full CRUD + publish on all content types)
-- ────────────────────────────────────────────────────────────
INSERT INTO cms_permissions (action, subject, role_id)
SELECT a.action, s.subject, r.id
FROM cms_roles r
CROSS JOIN (
    SELECT unnest(ARRAY[
        'create', 'read', 'update', 'delete', 'publish', 'unpublish'
    ]) AS action
) a
CROSS JOIN (
    SELECT unnest(ARRAY[
        'api::article.article',
        'api::page.page',
        'api::category.category',
        'api::tag.tag',
        'plugin::upload.file',
        'plugin::i18n.locale'
    ]) AS subject
) s
WHERE r.code = 'strapi-editor'
  AND NOT EXISTS (
      SELECT 1 FROM cms_permissions p
      WHERE p.role_id = r.id AND p.action = a.action AND p.subject = s.subject
  );

-- ────────────────────────────────────────────────────────────
-- 5. Author Permissions (limited — create/edit own content, no publish)
-- ────────────────────────────────────────────────────────────
INSERT INTO cms_permissions (action, subject, role_id)
SELECT a.action, s.subject, r.id
FROM cms_roles r
CROSS JOIN (
    SELECT unnest(ARRAY['create', 'read', 'update', 'delete']) AS action
) a
CROSS JOIN (
    SELECT unnest(ARRAY[
        'api::article.article',
        'api::page.page'
    ]) AS subject
) s
WHERE r.code = 'strapi-author'
  AND NOT EXISTS (
      SELECT 1 FROM cms_permissions p
      WHERE p.role_id = r.id AND p.action = a.action AND p.subject = s.subject
  );

-- ────────────────────────────────────────────────────────────
-- 6. Default Admin User
--    Username: admin
--    Email:    admin@cms.local
--    Password: admin123 (bcrypt hash below)
--    Role:     strapi-super-admin
-- ────────────────────────────────────────────────────────────
INSERT INTO cms_users (
    username, email, password_hash,
    first_name, last_name, is_active, is_blocked, preferred_locale
)
SELECT
    'admin',
    'admin@cms.local',
    '$2b$12$PM9mJ/JEyZXgFFLP/YskeeUwRXqJL0SvmE4fhEioAkCZJS3BsMQma',
    'Super',
    'Admin',
    TRUE,
    FALSE,
    'en'
WHERE NOT EXISTS (SELECT 1 FROM cms_users WHERE username = 'admin');

-- Assign the admin user to the Super Admin role
INSERT INTO cms_user_roles (user_id, role_id)
SELECT u.id, r.id
FROM cms_users u, cms_roles r
WHERE u.username = 'admin'
  AND r.code = 'strapi-super-admin'
  AND NOT EXISTS (
      SELECT 1 FROM cms_user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
