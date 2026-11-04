-- Run before deploying code using RptFile generation metadata.
-- MySQL DDL commits implicitly. Back up schema first; do not wrap in a transaction.
-- Existing input/Excel/legacy files remain NULL (not assessed).
SET @column_exists = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'rpt_files' AND column_name = 'generation_complete');
SET @ddl = IF(@column_exists = 0,
 'ALTER TABLE rpt_files ADD COLUMN generation_complete TINYINT(1) NULL', 'SELECT 1');
PREPARE metadata_statement FROM @ddl;
EXECUTE metadata_statement;
DEALLOCATE PREPARE metadata_statement;

SET @column_exists = (SELECT COUNT(*) FROM information_schema.columns
 WHERE table_schema = DATABASE() AND table_name = 'rpt_files' AND column_name = 'generation_warnings');
SET @ddl = IF(@column_exists = 0,
 'ALTER TABLE rpt_files ADD COLUMN generation_warnings TEXT NULL', 'SELECT 1');
PREPARE metadata_statement FROM @ddl;
EXECUTE metadata_statement;
DEALLOCATE PREPARE metadata_statement;