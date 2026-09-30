-- Phase 5 incremental migration. Apply after phase4.sql; safe to repeat.
USE campus_repair;
SET NAMES utf8mb4;
CREATE TABLE IF NOT EXISTS chat_message (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
 order_id BIGINT UNSIGNED NOT NULL, sender_id BIGINT UNSIGNED NOT NULL,
 receiver_id BIGINT UNSIGNED NOT NULL, content VARCHAR(2000) NOT NULL,
 read_status TINYINT NOT NULL DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(order_id) REFERENCES repair_order(id),
 FOREIGN KEY(sender_id) REFERENCES `user`(id), FOREIGN KEY(receiver_id) REFERENCES `user`(id),
 INDEX idx_chat_order(order_id,id), INDEX idx_chat_receiver(receiver_id,read_status,id),
 CHECK(read_status IN(0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
CREATE TABLE IF NOT EXISTS notification (
 id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY, user_id BIGINT UNSIGNED NOT NULL,
 title VARCHAR(100) NOT NULL, content VARCHAR(500) NOT NULL,
 read_status TINYINT NOT NULL DEFAULT 0, create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(user_id) REFERENCES `user`(id), INDEX idx_notification_user(user_id,create_time,id),
 CHECK(read_status IN(0,1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
