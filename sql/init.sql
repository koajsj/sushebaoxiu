-- MySQL 8.0+：幂等初始化；基础认证结构；现有版本依次执行phase3.sql、phase4.sql、phase5.sql。
CREATE DATABASE IF NOT EXISTS campus_repair
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- 对已存在的库显式设置默认字符集，不修改或删除任何表。
ALTER DATABASE campus_repair
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE campus_repair;
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `user` (
  id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  username VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_as_cs NOT NULL,
  password VARCHAR(100) NOT NULL COMMENT 'BCrypt hash only',
  real_name VARCHAR(64) NOT NULL,
  phone VARCHAR(20) DEFAULT NULL,
  role VARCHAR(16) NOT NULL,
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1 enabled, 0 disabled',
  token_version BIGINT NOT NULL DEFAULT 0 COMMENT 'Logout invalidates all earlier tokens',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  CONSTRAINT chk_user_role CHECK (role IN ('STUDENT', 'WORKER', 'ADMIN')),
  CONSTRAINT chk_user_status CHECK (status IN (0, 1)),
  CONSTRAINT chk_user_token_version CHECK (token_version >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
