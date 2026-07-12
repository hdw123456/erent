-- Adds provenance for provider-reported versus estimated streaming token usage.
SET @usage_source_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'usage_record'
      AND column_name = 'usage_source'
);

SET @usage_source_sql = IF(
    @usage_source_column_exists = 0,
    'ALTER TABLE usage_record ADD COLUMN usage_source VARCHAR(16) NOT NULL DEFAULT ''PROVIDER'' AFTER total_tokens',
    'SELECT ''usage_record.usage_source already exists'''
);

PREPARE usage_source_statement FROM @usage_source_sql;
EXECUTE usage_source_statement;
DEALLOCATE PREPARE usage_source_statement;
