-- Apply after phase5.sql. Existing notifications have no historical event key and remain unchanged.
USE campus_repair;
SET NAMES utf8mb4;
SET @campus_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='notification' AND column_name='idempotency_key')=0,
    'ALTER TABLE notification ADD COLUMN idempotency_key VARCHAR(128) NULL', 'SELECT 1');
PREPARE campus_stmt FROM @campus_ddl; EXECUTE campus_stmt; DEALLOCATE PREPARE campus_stmt;
SET @campus_ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='notification' AND index_name='uk_notification_event')=0,
    'CREATE UNIQUE INDEX uk_notification_event ON notification(idempotency_key)', 'SELECT 1');
PREPARE campus_stmt FROM @campus_ddl; EXECUTE campus_stmt; DEALLOCATE PREPARE campus_stmt;
