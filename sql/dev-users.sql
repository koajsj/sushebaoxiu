-- 仅限本地开发测试；密码均为 123456。不要在生产环境执行。
USE campus_repair;
SET NAMES utf8mb4;

INSERT INTO `user` (username, password, real_name, role, status)
SELECT 'student001', '$2a$12$XzSd.jq5Y4JNlg9i5v3P0ucX8wP77JC2UNZHptKkM7.UJuhGkzHtW', '测试学生', 'STUDENT', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'student001');

INSERT INTO `user` (username, password, real_name, role, status)
SELECT 'worker001', '$2a$12$HNOFzLleSJvnhADJPbIZs.UL/MkbTuXFQmCJSFhwILm0JBAJrj.Jq', '测试维修人员', 'WORKER', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'worker001');

INSERT INTO `user` (username, password, real_name, role, status)
SELECT 'admin001', '$2a$12$o.nRkBA689wpiuDsEuFhGu4n0xf1/1htaRx4HoUIODgY0pbbJ0mQG', '测试管理员', 'ADMIN', 1
WHERE NOT EXISTS (SELECT 1 FROM `user` WHERE username = 'admin001');

