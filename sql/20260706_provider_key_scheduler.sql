-- Upgrade provider_key into a schedulable upstream account model.

SET @schema_name := DATABASE();

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN provider_key_type VARCHAR(32) NOT NULL DEFAULT ''OFFICIAL_API_KEY'' AFTER user_id',
        'SELECT ''provider_key.provider_key_type already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'provider_key_type'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN base_url VARCHAR(255) NULL AFTER provider_key_type',
        'SELECT ''provider_key.base_url already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'base_url'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT ''ACTIVE'' AFTER enabled',
        'SELECT ''provider_key.status already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'status'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN schedulable BOOLEAN NOT NULL DEFAULT TRUE AFTER status',
        'SELECT ''provider_key.schedulable already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'schedulable'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN priority INT NOT NULL DEFAULT 100 AFTER schedulable',
        'SELECT ''provider_key.priority already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'priority'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN rate_limited_until DATETIME NULL AFTER priority',
        'SELECT ''provider_key.rate_limited_until already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'rate_limited_until'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN overloaded_until DATETIME NULL AFTER rate_limited_until',
        'SELECT ''provider_key.overloaded_until already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'overloaded_until'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN temp_disabled_until DATETIME NULL AFTER overloaded_until',
        'SELECT ''provider_key.temp_disabled_until already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'temp_disabled_until'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN expires_at DATETIME NULL AFTER temp_disabled_until',
        'SELECT ''provider_key.expires_at already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'expires_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN last_error_code VARCHAR(64) NULL AFTER expires_at',
        'SELECT ''provider_key.last_error_code already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'last_error_code'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN last_error_message VARCHAR(255) NULL AFTER last_error_code',
        'SELECT ''provider_key.last_error_message already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'last_error_message'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN last_used_at DATETIME NULL AFTER last_error_message',
        'SELECT ''provider_key.last_used_at already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'last_used_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN last_success_at DATETIME NULL AFTER last_used_at',
        'SELECT ''provider_key.last_success_at already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'last_success_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD COLUMN last_failed_at DATETIME NULL AFTER last_success_at',
        'SELECT ''provider_key.last_failed_at already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND column_name = 'last_failed_at'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE provider_key ADD KEY idx_provider_key_schedulable (provider_id, enabled, status, schedulable, priority)',
        'SELECT ''idx_provider_key_schedulable already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'provider_key'
      AND index_name = 'idx_provider_key_schedulable'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

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

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE request_log ADD COLUMN provider_key_id BIGINT NULL AFTER provider_id',
        'SELECT ''request_log.provider_key_id already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'request_log'
      AND column_name = 'provider_key_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE request_log ADD KEY idx_request_log_provider_key_created (provider_key_id, created_at)',
        'SELECT ''idx_request_log_provider_key_created already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'request_log'
      AND index_name = 'idx_request_log_provider_key_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE request_log ADD CONSTRAINT fk_request_log_provider_key FOREIGN KEY (provider_key_id) REFERENCES provider_key (id)',
        'SELECT ''fk_request_log_provider_key already exists'''
    )
    FROM information_schema.table_constraints
    WHERE table_schema = @schema_name
      AND table_name = 'request_log'
      AND constraint_name = 'fk_request_log_provider_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE usage_record ADD COLUMN provider_key_id BIGINT NULL AFTER model_id',
        'SELECT ''usage_record.provider_key_id already exists'''
    )
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'usage_record'
      AND column_name = 'provider_key_id'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE usage_record ADD KEY idx_usage_record_provider_key_created (provider_key_id, created_at)',
        'SELECT ''idx_usage_record_provider_key_created already exists'''
    )
    FROM information_schema.statistics
    WHERE table_schema = @schema_name
      AND table_name = 'usage_record'
      AND index_name = 'idx_usage_record_provider_key_created'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE usage_record ADD CONSTRAINT fk_usage_record_provider_key FOREIGN KEY (provider_key_id) REFERENCES provider_key (id)',
        'SELECT ''fk_usage_record_provider_key already exists'''
    )
    FROM information_schema.table_constraints
    WHERE table_schema = @schema_name
      AND table_name = 'usage_record'
      AND constraint_name = 'fk_usage_record_provider_key'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
