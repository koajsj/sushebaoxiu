-- Apply after notification-after-commit.sql. Back up the database first.
-- Nullable request keys preserve all pre-existing rows; new requests require keys in the API.
USE campus_repair;
SET NAMES utf8mb4;

SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='repair_order' AND column_name='start_due_time')=0,
  'ALTER TABLE repair_order ADD COLUMN start_due_time DATETIME NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='repair_order' AND column_name='request_key')=0,
  'ALTER TABLE repair_order ADD COLUMN request_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD COLUMN request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='repair_record' AND column_name='request_key')=0,
  'ALTER TABLE repair_record ADD COLUMN request_key VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL, ADD COLUMN request_hash CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='repair_order' AND index_name='uk_order_request')=0,
  'CREATE UNIQUE INDEX uk_order_request ON repair_order(student_id,request_key)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='repair_record' AND index_name='uk_record_request')=0,
  'CREATE UNIQUE INDEX uk_record_request ON repair_record(worker_id,request_key)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='repair_order' AND index_name='idx_order_start_sla')=0,
  'CREATE INDEX idx_order_start_sla ON repair_order(overdue_type,status,start_due_time,id)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @ddl = IF((SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name='repair_order' AND index_name='idx_worker_appointment')=0,
  'CREATE INDEX idx_worker_appointment ON repair_order(worker_id,appointment_status,appointment_start,appointment_end)', 'SELECT 1');
PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Legacy accepted but unstarted tasks receive a per-priority deadline. Reruns do not move it.
UPDATE repair_order SET start_due_time=DATE_ADD(
  CASE WHEN appointment_status='ACCEPTED' AND appointment_start>accepted_time
       THEN appointment_start ELSE accepted_time END,
  INTERVAL CASE priority WHEN 'HIGH' THEN 2 WHEN 'LOW' THEN 24 ELSE 8 END HOUR)
WHERE start_due_time IS NULL AND accepted_time IS NOT NULL AND started_time IS NULL AND status='ASSIGNED';
