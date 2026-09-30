-- Phase 4 增量迁移：先备份。重复执行不会重建表或覆盖已有数据。
USE campus_repair;
SET NAMES utf8mb4;
SET @campus_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='worker' AND column_name='longitude')=0,
 'ALTER TABLE worker ADD COLUMN longitude DECIMAL(10,7) NULL', 'SELECT 1');
PREPARE campus_stmt FROM @campus_ddl; EXECUTE campus_stmt; DEALLOCATE PREPARE campus_stmt;
SET @campus_ddl = IF((SELECT COUNT(*) FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='worker' AND column_name='latitude')=0,
 'ALTER TABLE worker ADD COLUMN latitude DECIMAL(10,7) NULL', 'SELECT 1');
PREPARE campus_stmt FROM @campus_ddl; EXECUTE campus_stmt; DEALLOCATE PREPARE campus_stmt;
CREATE TABLE IF NOT EXISTS dispatch_record (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT UNSIGNED NOT NULL, worker_id BIGINT UNSIGNED NOT NULL,
 skill_score DECIMAL(5,2) NOT NULL, distance_score DECIMAL(5,2) NOT NULL,
 load_score DECIMAL(5,2) NOT NULL, rating_score DECIMAL(5,2) NOT NULL, total_score DECIMAL(5,2) NOT NULL,
 reason VARCHAR(1000) NOT NULL, recommendation_batch CHAR(36) CHARACTER SET ascii NOT NULL,
 confirmed TINYINT NOT NULL DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(order_id) REFERENCES repair_order(id), FOREIGN KEY(worker_id) REFERENCES worker(id),
 INDEX idx_dispatch_order(order_id,create_time,id), INDEX idx_dispatch_batch(recommendation_batch),
 CHECK(skill_score BETWEEN 0 AND 100), CHECK(distance_score BETWEEN 0 AND 100),
 CHECK(load_score BETWEEN 0 AND 100), CHECK(rating_score BETWEEN 0 AND 100),
 CHECK(total_score BETWEEN 0 AND 100), CHECK(confirmed IN(0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
