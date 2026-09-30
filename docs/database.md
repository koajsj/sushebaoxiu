# 数据库与迁移

MySQL 8，数据库 `campus_repair`，InnoDB / utf8mb4。当前共13张表；本轮扩展repair_order、order_event、dispatch_record、repair_record，未新增表。

## 初始化顺序

先备份现有数据库和图片目录。结构脚本与开发数据分开执行；应用不自动执行DDL。

```bash
mysql -u root -p < sql/init.sql
mysql -u root -p < sql/phase3.sql
mysql -u root -p < sql/phase4.sql
mysql -u root -p < sql/phase5.sql
mysql -u root -p < sql/business-enhancements.sql
mysql -u root -p < sql/notification-after-commit.sql
# 下列三个脚本仅用于本地演示：
mysql -u root -p < sql/dev-users.sql
mysql -u root -p < sql/dev-business.sql
mysql -u root -p < sql/dev-dispatch.sql
```

结构脚本支持重复执行，不删除已有用户或工单。开发脚本补齐学生、维修人员档案和示例坐标，不替换已有真实坐标。worker002/worker003复制worker001现有密码哈希，若worker001密码已改，新增账号继承该密码。

## 表的职责

| 表 | 职责与约束 |
| --- | --- |
| user | BCrypt账号、角色、启用状态、token_version |
| student / worker | user_id唯一档案；人员评分、累计完成量及静态坐标 |
| building / repair_type | 校园楼栋坐标与故障分类 |
| repair_order | 工单归属、位置、指定人员及后端状态 |
| repair_record | 维修说明、受限图片与处理时间 |
| evaluation | 每单唯一学生评价，1～5分 |
| order_event | 操作人、实际事件与时间线 |
| repair_image | 图片上传者及一次绑定订单 |
| dispatch_record | 推荐批次、各项评分、解释和确认快照 |
| chat_message | 绑定工单与双方账号的文字消息 |
| notification | 单个用户的事件通知与已读状态；新通知用唯一事件键防重复 |

外键采用默认限制删除，避免删除账号或楼栋后留下悬空工单；没有新增删除接口。订单、消息、通知、推荐记录已有归属/状态/时间索引。`worker.task_count`是累计完成维修量；算法的当前负载独立统计WAIT_ASSIGN、ASSIGNED、PROCESSING。

## 最小应用权限

迁移使用管理员账号；后端应用账号仅获得读取和必要写入权限。自行替换密码，不把真实值写入文档或源码。

```sql
CREATE USER 'campus_repair'@'127.0.0.1' IDENTIFIED BY '自行设置的强密码';
GRANT SELECT ON campus_repair.* TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (token_version) ON campus_repair.user TO 'campus_repair'@'127.0.0.1';
GRANT INSERT, UPDATE ON campus_repair.repair_order TO 'campus_repair'@'127.0.0.1';
GRANT INSERT, UPDATE ON campus_repair.repair_record TO 'campus_repair'@'127.0.0.1';
GRANT INSERT, UPDATE ON campus_repair.repair_image TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.order_event TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.evaluation TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (decision, reject_reason, response_time) ON campus_repair.dispatch_record TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.dispatch_record TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.chat_message TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (read_status) ON campus_repair.chat_message TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.notification TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (read_status) ON campus_repair.notification TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (score, task_count) ON campus_repair.worker TO 'campus_repair'@'127.0.0.1';
```

## 备份与搬迁

升级通知事务边界前先执行 `sql/notification-after-commit.sql`，可重复执行。新增 `idempotency_key` 为可空唯一列，历史通知保持原样；新通知以动作、工单、轮次、订单事件ID、接收用户组成稳定键。通知写入在核心事务提交后使用独立事务，失败记录日志，不回滚工单；当前没有跨进程持久重试队列。

- 数据库、`UPLOAD_DIRECTORY`图片目录一起备份；只复制jar无法恢复现场图片。
- JWT_SECRET、DB_PASSWORD及本机`.runtime/`分别保存在私有配置中，不放入交付包。
- 本机展示库与隔离验收库分开，验收脚本只能指向对应隔离端口。不要在正式库执行修改状态/角色的历史认证脚本。

## 最终流程增强迁移

迁移按information_schema条件添加字段，末尾CHECK约束作为完整回填的完成标记；中断后可重跑。MySQL DDL不具备整体事务回滚，必须先备份并停服务。回填旧接单/开始时间与派单记录，显式保留订单update_time，不删除历史。旧SLA使用默认优先级时限，新派单使用当前配置。系统自动超时事件actor_id为NULL，避免伪装为用户行为。

维修记录round_no默认1；派单记录包含round_no、method、decision、reject_reason、response_time，人工记录各项分数为NULL。原推荐批次和评分不覆盖。worker.task_count继续表示累计完成维修轮次数，人员完成工单统计按学生最终确认的订单去重；返工不会制造额外订单或评价。
