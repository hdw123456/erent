CREATE TABLE IF NOT EXISTS user_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(64) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    email VARCHAR(128) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_account_username (username)
);

CREATE TABLE IF NOT EXISTS role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    UNIQUE KEY uk_role_code (code)
);

CREATE TABLE IF NOT EXISTS user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES role (id)
);

CREATE TABLE IF NOT EXISTS api_key (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    key_hash VARCHAR(128) NOT NULL,
    prefix VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    last_used_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_api_key_hash (key_hash),
    KEY idx_api_key_user_id (user_id),
    CONSTRAINT fk_api_key_user FOREIGN KEY (user_id) REFERENCES user_account (id)
);

CREATE TABLE IF NOT EXISTS provider (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(32) NOT NULL,
    name VARCHAR(64) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_code (code)
);

CREATE TABLE IF NOT EXISTS provider_key (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT NOT NULL,
    user_id BIGINT NULL,
    provider_key_type VARCHAR(32) NOT NULL DEFAULT 'OFFICIAL_API_KEY',
    base_url VARCHAR(255) NULL,
    encrypted_key TEXT NOT NULL,
    key_hint VARCHAR(32) NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    schedulable BOOLEAN NOT NULL DEFAULT TRUE,
    priority INT NOT NULL DEFAULT 100,
    rate_limited_until DATETIME NULL,
    overloaded_until DATETIME NULL,
    temp_disabled_until DATETIME NULL,
    expires_at DATETIME NULL,
    last_error_code VARCHAR(64) NULL,
    last_error_message VARCHAR(255) NULL,
    last_used_at DATETIME NULL,
    last_success_at DATETIME NULL,
    last_failed_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_provider_key_user_id (user_id),
    KEY idx_provider_key_schedulable (provider_id, enabled, status, schedulable, priority),
    CONSTRAINT fk_provider_key_provider FOREIGN KEY (provider_id) REFERENCES provider (id),
    CONSTRAINT fk_provider_key_user FOREIGN KEY (user_id) REFERENCES user_account (id)
);

CREATE TABLE IF NOT EXISTS provider_key_quota_window (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_key_id BIGINT NOT NULL,
    window_type VARCHAR(32) NOT NULL,
    quota_limit DECIMAL(18, 6) NULL,
    quota_used DECIMAL(18, 6) NOT NULL DEFAULT 0,
    window_start_at DATETIME NULL,
    reset_at DATETIME NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_provider_key_window (provider_key_id, window_type),
    KEY idx_quota_window_reset (reset_at),
    CONSTRAINT fk_quota_window_provider_key FOREIGN KEY (provider_key_id) REFERENCES provider_key (id)
);

CREATE TABLE IF NOT EXISTS model (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    provider_id BIGINT NOT NULL,
    code VARCHAR(96) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_model_provider_code (provider_id, code),
    CONSTRAINT fk_model_provider FOREIGN KEY (provider_id) REFERENCES provider (id)
);

CREATE TABLE IF NOT EXISTS pricing_rule (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    model_id BIGINT NOT NULL,
    input_token_price DECIMAL(18, 8) NOT NULL,
    output_token_price DECIMAL(18, 8) NOT NULL,
    currency VARCHAR(8) NOT NULL DEFAULT 'CNY',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pricing_rule_model FOREIGN KEY (model_id) REFERENCES model (id)
);

CREATE TABLE IF NOT EXISTS wallet (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    balance DECIMAL(18, 6) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_user_id (user_id),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES user_account (id)
);

CREATE TABLE IF NOT EXISTS wallet_transaction (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    wallet_id BIGINT NOT NULL,
    type VARCHAR(32) NOT NULL,
    amount DECIMAL(18, 6) NOT NULL,
    balance_after DECIMAL(18, 6) NOT NULL,
    request_id VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_wallet_transaction_request_type (request_id, type),
    KEY idx_wallet_transaction_wallet_created (wallet_id, created_at),
    CONSTRAINT fk_wallet_transaction_wallet FOREIGN KEY (wallet_id) REFERENCES wallet (id)
);

CREATE TABLE IF NOT EXISTS request_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    api_key_id BIGINT NULL,
    provider_id BIGINT NULL,
    provider_key_id BIGINT NULL,
    model_id BIGINT NULL,
    status_code INT NOT NULL,
    latency_ms INT NOT NULL,
    error_code VARCHAR(64) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_request_log_request_id (request_id),
    KEY idx_request_log_user_created (user_id, created_at),
    KEY idx_request_log_provider_key_created (provider_key_id, created_at),
    CONSTRAINT fk_request_log_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_request_log_api_key FOREIGN KEY (api_key_id) REFERENCES api_key (id),
    CONSTRAINT fk_request_log_provider FOREIGN KEY (provider_id) REFERENCES provider (id),
    CONSTRAINT fk_request_log_provider_key FOREIGN KEY (provider_key_id) REFERENCES provider_key (id),
    CONSTRAINT fk_request_log_model FOREIGN KEY (model_id) REFERENCES model (id)
);

CREATE TABLE IF NOT EXISTS idempotency_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    scope VARCHAR(128) NOT NULL,
    api_key_id BIGINT NOT NULL,
    idempotency_key_hash CHAR(64) NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_json JSON NULL,
    error_code VARCHAR(64) NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency_scope_key (scope, idempotency_key_hash),
    UNIQUE KEY uk_idempotency_request_id (request_id),
    KEY idx_idempotency_api_key_id (api_key_id),
    KEY idx_idempotency_expires_at (expires_at),
    KEY idx_idempotency_status_updated (status, updated_at),
    CONSTRAINT fk_idempotency_api_key FOREIGN KEY (api_key_id) REFERENCES api_key (id)
);

CREATE TABLE IF NOT EXISTS usage_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    model_id BIGINT NOT NULL,
    provider_key_id BIGINT NULL,
    input_tokens INT NOT NULL DEFAULT 0,
    output_tokens INT NOT NULL DEFAULT 0,
    total_tokens INT NOT NULL DEFAULT 0,
    usage_source VARCHAR(16) NOT NULL DEFAULT 'PROVIDER',
    cost_amount DECIMAL(18, 6) NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usage_record_request_id (request_id),
    KEY idx_usage_record_user_created (user_id, created_at),
    KEY idx_usage_record_provider_key_created (provider_key_id, created_at),
    CONSTRAINT fk_usage_record_user FOREIGN KEY (user_id) REFERENCES user_account (id),
    CONSTRAINT fk_usage_record_provider_key FOREIGN KEY (provider_key_id) REFERENCES provider_key (id),
    CONSTRAINT fk_usage_record_model FOREIGN KEY (model_id) REFERENCES model (id)
);

CREATE TABLE IF NOT EXISTS usage_billing_dedup (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    api_key_id BIGINT NOT NULL,
    request_fingerprint CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_usage_billing_request_api_key (request_id, api_key_id),
    KEY idx_usage_billing_api_key_created (api_key_id, created_at),
    CONSTRAINT fk_usage_billing_api_key FOREIGN KEY (api_key_id) REFERENCES api_key (id)
);
