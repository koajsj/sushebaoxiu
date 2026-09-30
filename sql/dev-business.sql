-- 仅用于本地开发：补齐 Phase2 测试账号档案，不覆盖已有档案。
USE campus_repair;
INSERT INTO student(user_id,student_no,college,class_name,building_id,room_no)
SELECT u.id,'2026001001','计算机学院','软件工程一班',b.id,'301' FROM `user` u JOIN building b ON b.name='学生宿舍1号楼'
WHERE u.username='student001' AND u.role='STUDENT' AND NOT EXISTS(SELECT 1 FROM student s WHERE s.user_id=u.id);
INSERT INTO worker(user_id,skill_type)
SELECT id,'校园综合维修' FROM `user` u WHERE username='worker001' AND role='WORKER' AND NOT EXISTS(SELECT 1 FROM worker w WHERE w.user_id=u.id);
