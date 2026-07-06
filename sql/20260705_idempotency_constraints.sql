-- Patch for an existing local database.
-- Fresh databases already get these objects from schema.sql.

CREATE TABLE IF NOT EXISTS idempotency_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    api_key_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    request_id VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    response_json JSON NULL,
    error_code VARCHAR(64) NULL,
    expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_idempotency_api_key_key (api_key_id, idempotency_key),
    UNIQUE KEY uk_idempotency_request_id (request_id),
    KEY idx_idempotency_expires_at (expires_at),
    CONSTRAINT fk_idempotency_api_key FOREIGN KEY (api_key_id) REFERENCES api_key (id)
);

SET @schema_name := DATABASE();

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE usage_record ADD UNIQUE KEY uk_usage_record_request_id (request_id)',
        'SELECT ''uk_usage_record_request_id already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'usage_record'
      AND index_name = 'uk_usage_record_request_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE wallet_transaction ADD UNIQUE KEY uk_wallet_transaction_request_type (request_id, type)',
        'SELECT ''uk_wallet_transaction_request_type already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'wallet_transaction'
      AND index_name = 'uk_wallet_transaction_request_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
