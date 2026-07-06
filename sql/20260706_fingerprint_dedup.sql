-- Upgrade idempotency records to the sub2api-style fingerprint model
-- and add a billing deduplication table.

SET @schema_name := DATABASE();

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

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE idempotency_record ADD COLUMN scope VARCHAR(128) NULL AFTER id',
        'SELECT ''idempotency_record.scope already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND column_name = 'scope'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE idempotency_record ADD COLUMN idempotency_key_hash CHAR(64) NULL AFTER api_key_id',
        'SELECT ''idempotency_record.idempotency_key_hash already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND column_name = 'idempotency_key_hash'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE idempotency_record ADD COLUMN request_fingerprint CHAR(64) NULL AFTER idempotency_key_hash',
        'SELECT ''idempotency_record.request_fingerprint already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND column_name = 'request_fingerprint'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE idempotency_record
SET scope = CONCAT('api_key:', api_key_id)
WHERE scope IS NULL OR scope = '';

SET @has_legacy_key := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND column_name = 'idempotency_key'
);

SET @sql := IF(
    @has_legacy_key > 0,
    'UPDATE idempotency_record SET idempotency_key_hash = SHA2(idempotency_key, 256) WHERE idempotency_key_hash IS NULL',
    'SELECT ''idempotency_record.idempotency_key legacy column missing'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @has_legacy_hash := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND column_name = 'request_hash'
);

SET @sql := IF(
    @has_legacy_hash > 0,
    'UPDATE idempotency_record SET request_fingerprint = request_hash WHERE request_fingerprint IS NULL',
    'SELECT ''idempotency_record.request_hash legacy column missing'''
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE idempotency_record
SET idempotency_key_hash = SHA2(request_id, 256)
WHERE idempotency_key_hash IS NULL;

UPDATE idempotency_record
SET request_fingerprint = SHA2(request_id, 256)
WHERE request_fingerprint IS NULL;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE idempotency_record ADD KEY idx_idempotency_api_key_id (api_key_id)',
        'SELECT ''idx_idempotency_api_key_id already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND index_name = 'idx_idempotency_api_key_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT ''uk_idempotency_api_key_key already absent''',
        'ALTER TABLE idempotency_record DROP INDEX uk_idempotency_api_key_key'
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND index_name = 'uk_idempotency_api_key_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE idempotency_record ADD UNIQUE KEY uk_idempotency_scope_key (scope, idempotency_key_hash)',
        'SELECT ''uk_idempotency_scope_key already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND index_name = 'uk_idempotency_scope_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE idempotency_record ADD KEY idx_idempotency_status_updated (status, updated_at)',
        'SELECT ''idx_idempotency_status_updated already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND index_name = 'idx_idempotency_status_updated'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE idempotency_record
    MODIFY scope VARCHAR(128) NOT NULL,
    MODIFY idempotency_key_hash CHAR(64) NOT NULL,
    MODIFY request_fingerprint CHAR(64) NOT NULL;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT ''idempotency_record.idempotency_key already absent''',
        'ALTER TABLE idempotency_record DROP COLUMN idempotency_key'
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND column_name = 'idempotency_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'SELECT ''idempotency_record.request_hash already absent''',
        'ALTER TABLE idempotency_record DROP COLUMN request_hash'
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'idempotency_record'
      AND column_name = 'request_hash'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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
