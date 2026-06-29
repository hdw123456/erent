-- Seed data for local development and SQL/MyBatis practice.
-- The statements are written to be safe to run more than once.

INSERT IGNORE INTO role (code, name)
VALUES ('USER', '普通用户'),
       ('ADMIN', '管理员');

INSERT IGNORE INTO provider (code, name, enabled)
VALUES ('OPENAI', 'OpenAI', TRUE),
       ('CLAUDE', 'Anthropic Claude', TRUE),
       ('GEMINI', 'Google Gemini', TRUE);

INSERT IGNORE INTO model (provider_id, code, display_name, enabled)
SELECT p.id, seed.code, seed.display_name, seed.enabled
FROM (
    SELECT 'OPENAI' AS provider_code, 'gpt-4.1-mini' AS code, 'GPT-4.1 Mini' AS display_name, TRUE AS enabled
    UNION ALL SELECT 'OPENAI', 'gpt-4.1', 'GPT-4.1', TRUE
    UNION ALL SELECT 'CLAUDE', 'claude-3-5-sonnet', 'Claude 3.5 Sonnet', TRUE
    UNION ALL SELECT 'GEMINI', 'gemini-1.5-flash', 'Gemini 1.5 Flash', TRUE
    UNION ALL SELECT 'GEMINI', 'gemini-1.5-pro', 'Gemini 1.5 Pro', TRUE
) seed
JOIN provider p ON p.code = seed.provider_code;

INSERT INTO pricing_rule (model_id, input_token_price, output_token_price, currency, enabled)
SELECT m.id, seed.input_token_price, seed.output_token_price, 'CNY', TRUE
FROM (
    SELECT 'OPENAI' AS provider_code, 'gpt-4.1-mini' AS model_code, 0.00000100 AS input_token_price, 0.00000400 AS output_token_price
    UNION ALL SELECT 'OPENAI', 'gpt-4.1', 0.00000500, 0.00001500
    UNION ALL SELECT 'CLAUDE', 'claude-3-5-sonnet', 0.00000300, 0.00001500
    UNION ALL SELECT 'GEMINI', 'gemini-1.5-flash', 0.00000050, 0.00000150
    UNION ALL SELECT 'GEMINI', 'gemini-1.5-pro', 0.00000300, 0.00001000
) seed
JOIN provider p ON p.code = seed.provider_code
JOIN model m ON m.provider_id = p.id AND m.code = seed.model_code
WHERE NOT EXISTS (
    SELECT 1
    FROM pricing_rule pr
    WHERE pr.model_id = m.id
      AND pr.currency = 'CNY'
      AND pr.enabled = TRUE
);

INSERT INTO user_account (username, password_hash, email, enabled)
VALUES ('hdw', '$2a$10$9Nl/Zcx3RH6m/XtYLf.NtOpkdCQM6WSktsdx3kqDjmP/uToi5ouJO', 'hdw@example.com', TRUE),
       ('alice', '$2a$10$9Nl/Zcx3RH6m/XtYLf.NtOpkdCQM6WSktsdx3kqDjmP/uToi5ouJO', 'alice@example.com', TRUE),
       ('bob', '$2a$10$9Nl/Zcx3RH6m/XtYLf.NtOpkdCQM6WSktsdx3kqDjmP/uToi5ouJO', 'bob@example.com', TRUE),
       ('carol', '$2a$10$9Nl/Zcx3RH6m/XtYLf.NtOpkdCQM6WSktsdx3kqDjmP/uToi5ouJO', 'carol@example.com', TRUE),
       ('dave', '$2a$10$9Nl/Zcx3RH6m/XtYLf.NtOpkdCQM6WSktsdx3kqDjmP/uToi5ouJO', 'dave@example.com', TRUE),
       ('admin', '$2a$10$9Nl/Zcx3RH6m/XtYLf.NtOpkdCQM6WSktsdx3kqDjmP/uToi5ouJO', 'admin@example.com', TRUE)
ON DUPLICATE KEY UPDATE
       password_hash = VALUES(password_hash),
       email = VALUES(email),
       enabled = VALUES(enabled);

INSERT IGNORE INTO user_role (user_id, role_id)
SELECT u.id, r.id
FROM (
    SELECT 'hdw' AS username, 'USER' AS role_code
    UNION ALL SELECT 'alice', 'USER'
    UNION ALL SELECT 'bob', 'USER'
    UNION ALL SELECT 'carol', 'USER'
    UNION ALL SELECT 'dave', 'USER'
    UNION ALL SELECT 'admin', 'USER'
    UNION ALL SELECT 'admin', 'ADMIN'
) seed
JOIN user_account u ON u.username = seed.username
JOIN role r ON r.code = seed.role_code;

INSERT IGNORE INTO api_key (user_id, name, key_hash, prefix, enabled, last_used_at, created_at)
SELECT u.id, seed.name, seed.key_hash, seed.prefix, seed.enabled, seed.last_used_at, seed.created_at
FROM (
    SELECT 'hdw' AS username, 'hdw-dev' AS name, 'hash_seed_hdw_dev' AS key_hash, 'ak_hdw' AS prefix, TRUE AS enabled, '2026-06-26 09:30:00' AS last_used_at, '2026-06-18 09:00:00' AS created_at
    UNION ALL SELECT 'alice', 'alice-main', 'hash_seed_alice_main', 'ak_ali', TRUE, '2026-06-26 10:00:00', '2026-06-18 10:00:00'
    UNION ALL SELECT 'alice', 'alice-report', 'hash_seed_alice_report', 'ak_alr', TRUE, '2026-06-20 11:00:00', '2026-06-19 10:00:00'
    UNION ALL SELECT 'bob', 'bob-main', 'hash_seed_bob_main', 'ak_bob', TRUE, '2026-06-23 16:30:00', '2026-06-19 11:00:00'
    UNION ALL SELECT 'carol', 'carol-main', 'hash_seed_carol_main', 'ak_car', TRUE, '2026-06-24 08:20:00', '2026-06-20 08:00:00'
    UNION ALL SELECT 'dave', 'dave-disabled', 'hash_seed_dave_disabled', 'ak_dav', FALSE, NULL, '2026-06-20 09:00:00'
    UNION ALL SELECT 'admin', 'admin-ops', 'hash_seed_admin_ops', 'ak_adm', TRUE, '2026-06-25 20:00:00', '2026-06-18 08:00:00'
) seed
JOIN user_account u ON u.username = seed.username;

INSERT INTO provider_key (provider_id, user_id, encrypted_key, key_hint, enabled)
SELECT p.id, u.id, seed.encrypted_key, seed.key_hint, seed.enabled
FROM (
    SELECT 'OPENAI' AS provider_code, NULL AS username, 'enc::platform-openai-key' AS encrypted_key, 'plat-openai-001' AS key_hint, TRUE AS enabled
    UNION ALL SELECT 'GEMINI', NULL, 'enc::platform-gemini-key', 'plat-gemini-001', TRUE
    UNION ALL SELECT 'OPENAI', 'bob', 'enc::bob-openai-key', 'bob-openai-001', TRUE
    UNION ALL SELECT 'CLAUDE', 'alice', 'enc::alice-claude-key', 'alice-claude-001', FALSE
) seed
JOIN provider p ON p.code = seed.provider_code
LEFT JOIN user_account u ON u.username = seed.username
WHERE NOT EXISTS (
    SELECT 1
    FROM provider_key pk
    WHERE pk.provider_id = p.id
      AND ((pk.user_id IS NULL AND u.id IS NULL) OR pk.user_id = u.id)
      AND pk.key_hint = seed.key_hint
);

INSERT INTO wallet (user_id, balance)
SELECT u.id, seed.balance
FROM (
    SELECT 'hdw' AS username, 20.000000 AS balance
    UNION ALL SELECT 'alice', 99.993200
    UNION ALL SELECT 'bob', 2.470000
    UNION ALL SELECT 'carol', 0.198700
    UNION ALL SELECT 'dave', 0.000000
    UNION ALL SELECT 'admin', 499.976000
) seed
JOIN user_account u ON u.username = seed.username
WHERE NOT EXISTS (
    SELECT 1
    FROM wallet w
    WHERE w.user_id = u.id
);

INSERT IGNORE INTO request_log (request_id, user_id, api_key_id, provider_id, model_id, status_code, latency_ms, error_code, created_at)
SELECT seed.request_id, u.id, k.id, p.id, m.id, seed.status_code, seed.latency_ms, seed.error_code, seed.created_at
FROM (
    SELECT 'req-seed-hdw-001' AS request_id, 'hdw' AS username, 'hdw-dev' AS api_key_name, 'OPENAI' AS provider_code, 'gpt-4.1-mini' AS model_code, 200 AS status_code, 700 AS latency_ms, NULL AS error_code, '2026-06-26 09:30:00' AS created_at
    UNION ALL SELECT 'req-seed-alice-001', 'alice', 'alice-main', 'OPENAI', 'gpt-4.1-mini', 200, 820, NULL, '2026-06-20 10:00:00'
    UNION ALL SELECT 'req-seed-alice-002', 'alice', 'alice-main', 'OPENAI', 'gpt-4.1-mini', 200, 760, NULL, '2026-06-20 11:30:00'
    UNION ALL SELECT 'req-seed-alice-003', 'alice', 'alice-report', 'CLAUDE', 'claude-3-5-sonnet', 500, 2100, 'PROVIDER_TIMEOUT', '2026-06-21 15:00:00'
    UNION ALL SELECT 'req-seed-bob-001', 'bob', 'bob-main', 'OPENAI', 'gpt-4.1', 200, 1400, NULL, '2026-06-22 14:10:00'
    UNION ALL SELECT 'req-seed-bob-002', 'bob', 'bob-main', 'GEMINI', 'gemini-1.5-flash', 429, 30, 'RATE_LIMITED', '2026-06-23 16:30:00'
    UNION ALL SELECT 'req-seed-carol-001', 'carol', 'carol-main', 'OPENAI', 'gpt-4.1-mini', 402, 45, 'INSUFFICIENT_BALANCE', '2026-06-24 08:00:00'
    UNION ALL SELECT 'req-seed-carol-002', 'carol', 'carol-main', 'OPENAI', 'gpt-4.1-mini', 200, 900, NULL, '2026-06-24 08:20:00'
    UNION ALL SELECT 'req-seed-admin-001', 'admin', 'admin-ops', 'GEMINI', 'gemini-1.5-pro', 200, 1800, NULL, '2026-06-25 20:00:00'
    UNION ALL SELECT 'req-seed-dave-001', 'dave', 'dave-disabled', 'OPENAI', 'gpt-4.1-mini', 401, 20, 'API_KEY_DISABLED', '2026-06-25 09:00:00'
    UNION ALL SELECT 'req-seed-alice-004', 'alice', 'alice-main', 'GEMINI', 'gemini-1.5-flash', 200, 600, NULL, '2026-06-26 10:00:00'
) seed
JOIN user_account u ON u.username = seed.username
LEFT JOIN api_key k ON k.user_id = u.id AND k.name = seed.api_key_name
JOIN provider p ON p.code = seed.provider_code
JOIN model m ON m.provider_id = p.id AND m.code = seed.model_code;

INSERT INTO usage_record (request_id, user_id, model_id, input_tokens, output_tokens, total_tokens, cost_amount, created_at)
SELECT seed.request_id, u.id, m.id, seed.input_tokens, seed.output_tokens,
       seed.input_tokens + seed.output_tokens, seed.cost_amount, seed.created_at
FROM (
    SELECT 'req-seed-hdw-001' AS request_id, 'hdw' AS username, 'OPENAI' AS provider_code, 'gpt-4.1-mini' AS model_code, 1200 AS input_tokens, 150 AS output_tokens, 0.001800 AS cost_amount, '2026-06-26 09:30:00' AS created_at
    UNION ALL SELECT 'req-seed-alice-001', 'alice', 'OPENAI', 'gpt-4.1-mini', 1500, 600, 0.003900, '2026-06-20 10:00:00'
    UNION ALL SELECT 'req-seed-alice-002', 'alice', 'OPENAI', 'gpt-4.1-mini', 1000, 300, 0.002200, '2026-06-20 11:30:00'
    UNION ALL SELECT 'req-seed-bob-001', 'bob', 'OPENAI', 'gpt-4.1', 3000, 1000, 0.030000, '2026-06-22 14:10:00'
    UNION ALL SELECT 'req-seed-carol-002', 'carol', 'OPENAI', 'gpt-4.1-mini', 900, 100, 0.001300, '2026-06-24 08:20:00'
    UNION ALL SELECT 'req-seed-admin-001', 'admin', 'GEMINI', 'gemini-1.5-pro', 4000, 1200, 0.024000, '2026-06-25 20:00:00'
    UNION ALL SELECT 'req-seed-alice-004', 'alice', 'GEMINI', 'gemini-1.5-flash', 800, 200, 0.000700, '2026-06-26 10:00:00'
) seed
JOIN user_account u ON u.username = seed.username
JOIN provider p ON p.code = seed.provider_code
JOIN model m ON m.provider_id = p.id AND m.code = seed.model_code
WHERE NOT EXISTS (
    SELECT 1
    FROM usage_record ur
    WHERE ur.request_id = seed.request_id
);

INSERT INTO wallet_transaction (wallet_id, type, amount, balance_after, request_id, created_at)
SELECT w.id, seed.type, seed.amount, seed.balance_after, seed.request_id, seed.created_at
FROM (
    SELECT 'hdw' AS username, 'RECHARGE' AS type, 20.001800 AS amount, 20.001800 AS balance_after, 'seed-recharge-hdw' AS request_id, '2026-06-18 09:00:00' AS created_at
    UNION ALL SELECT 'hdw', 'USAGE_DEDUCT', -0.001800, 20.000000, 'req-seed-hdw-001', '2026-06-26 09:30:00'
    UNION ALL SELECT 'alice', 'RECHARGE', 100.000000, 100.000000, 'seed-recharge-alice', '2026-06-18 10:00:00'
    UNION ALL SELECT 'alice', 'USAGE_DEDUCT', -0.003900, 99.996100, 'req-seed-alice-001', '2026-06-20 10:00:00'
    UNION ALL SELECT 'alice', 'USAGE_DEDUCT', -0.002200, 99.993900, 'req-seed-alice-002', '2026-06-20 11:30:00'
    UNION ALL SELECT 'alice', 'USAGE_DEDUCT', -0.000700, 99.993200, 'req-seed-alice-004', '2026-06-26 10:00:00'
    UNION ALL SELECT 'bob', 'RECHARGE', 2.500000, 2.500000, 'seed-recharge-bob', '2026-06-19 11:00:00'
    UNION ALL SELECT 'bob', 'USAGE_DEDUCT', -0.030000, 2.470000, 'req-seed-bob-001', '2026-06-22 14:10:00'
    UNION ALL SELECT 'carol', 'RECHARGE', 0.200000, 0.200000, 'seed-recharge-carol', '2026-06-20 08:00:00'
    UNION ALL SELECT 'carol', 'USAGE_DEDUCT', -0.001300, 0.198700, 'req-seed-carol-002', '2026-06-24 08:20:00'
    UNION ALL SELECT 'admin', 'RECHARGE', 500.000000, 500.000000, 'seed-recharge-admin', '2026-06-18 08:00:00'
    UNION ALL SELECT 'admin', 'USAGE_DEDUCT', -0.024000, 499.976000, 'req-seed-admin-001', '2026-06-25 20:00:00'
) seed
JOIN user_account u ON u.username = seed.username
JOIN wallet w ON w.user_id = u.id
WHERE NOT EXISTS (
    SELECT 1
    FROM wallet_transaction wt
    WHERE wt.wallet_id = w.id
      AND wt.type = seed.type
      AND wt.request_id = seed.request_id
);
