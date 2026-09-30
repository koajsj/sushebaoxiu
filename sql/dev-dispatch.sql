-- 仅开发演示。WGS84示例坐标，不代表实际学校或实时人员位置。
-- 只填充完整空坐标，不覆盖已配置坐标、密码、评分、负载或角色。
USE campus_repair;
SET NAMES utf8mb4;
START TRANSACTION;
UPDATE building SET longitude=116.3100000,latitude=39.9900000 WHERE name='学生宿舍1号楼' AND longitude IS NULL AND latitude IS NULL;
UPDATE building SET longitude=116.3140000,latitude=39.9915000 WHERE name='教学楼A栋' AND longitude IS NULL AND latitude IS NULL;
UPDATE building SET longitude=116.3115000,latitude=39.9940000 WHERE name='校园图书馆' AND longitude IS NULL AND latitude IS NULL;
UPDATE worker w JOIN `user` u ON u.id=w.user_id SET w.longitude=116.3120000,w.latitude=39.9920000
 WHERE u.username='worker001' AND w.longitude IS NULL AND w.latitude IS NULL;
-- 新演示账号复用既有开发维修账号的 BCrypt 哈希（初始开发密码123456）。
INSERT INTO `user`(username,password,real_name,role,status)
 SELECT 'worker002',seed.password,'电工示例人员','WORKER',1 FROM `user` seed WHERE seed.username='worker001'
 ON DUPLICATE KEY UPDATE username=`user`.username;
INSERT INTO `user`(username,password,real_name,role,status)
 SELECT 'worker003',seed.password,'水暖示例人员','WORKER',1 FROM `user` seed WHERE seed.username='worker001'
 ON DUPLICATE KEY UPDATE username=`user`.username;
INSERT INTO worker(user_id,skill_type,longitude,latitude)
 SELECT id,'照明与电路,电工',116.3102000,39.9902000 FROM `user` WHERE username='worker002' AND role='WORKER'
 ON DUPLICATE KEY UPDATE user_id=user_id;
INSERT INTO worker(user_id,skill_type,longitude,latitude)
 SELECT id,'给排水,水暖',116.3270000,40.0020000 FROM `user` WHERE username='worker003' AND role='WORKER'
 ON DUPLICATE KEY UPDATE user_id=user_id;
COMMIT;
