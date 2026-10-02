# 深度缺陷审查与最小修复记录

日期：2026-10-01。起点提交：`3ccc47610658a502085bbe8d54a881dcfef778c5`。保留已有工作区修改；本轮没有提交、推送、部署，没有修改原业务库或旧演示库。

## 确认并修复的缺陷

本轮确认 8 类 P2 缺陷；未确认 P0、P1 或需要额外修改的 P3。以下行号对应本轮完成时的代码。

| 缺陷与触发条件 | 根因、影响 | 最小修复位置 |
| --- | --- | --- |
| 两张工单同时确认同一维修员的重叠预约 | 学生归属查询先建立 REPEATABLE_READ 快照；等待维修员锁后，冲突 COUNT 仍读取旧快照，两次确认均提交 | `backend/src/main/java/com/campus/repair/service/OrderWorkflowService.java:153`：仅确认方法使用 READ_COMMITTED，保留工单→维修员锁和原校验 |
| 同一学生已有 9 张未绑定图片时并发上传 | 档案查询先建立快照，用户锁之后的配额查询仍读旧计数，实际保存 11 张 | `backend/src/main/java/com/campus/repair/service/ImageService.java:46`：上传事务使用 READ_COMMITTED，保留用户锁、配额、文件回滚清理 |
| 管理员保存资料期间，维修员状态/评分/任务量被另一事务更新 | 完整 WorkerEntity 的 updateById 回写读取时的旧业务字段，覆盖新值；实验中暂停状态被恢复为启用 | `backend/src/main/java/com/campus/repair/service/AdminManagementService.java:102`：显式更新技能与坐标，空坐标仍可清除 |
| 自动聊天同步过程中点击手动刷新 | 旧请求 finally 因版本过期不释放 polling，新加载又未重置它，后续轮询永久跳过 | `frontend/src/views/OrderChatView.vue:32`：新加载重置轮询标志，保留版本和取消保护 |
| 消息 POST 已成功，但随后补消息 GET 失败 | 原先将两次操作视为同一次失败，保留已发送草稿，再次发送会重复入库 | `frontend/src/views/OrderChatView.vue:73`：收到 POST 成功立即清理匹配草稿并反馈成功；后续失败明确提示同步失败，保留请求期间新输入 |
| 对方已读，但没有新消息 | afterId 增量查询不包含旧消息的已读变化，页面长期显示未读 | `frontend/src/views/OrderChatView.vue:44`：每次同步至多额外查询一页回执，仅合并已有消息的 read；轮转游标避免永久旧未读阻塞超过 100 条历史中的新回执 |
| 未读通知末页最后一条被标记已读，或派单队列末项被派走 | 总量缩减后仍请求不存在的页；总量降到单页时导航消失，剩余记录不可见 | `frontend/src/components/NotificationPanel.vue:16`、`frontend/src/views/DispatchView.vue:32`：校正页码并有限重查，保留响应版本保护 |
| 保存人员/楼栋/故障类型期间继续输入，或切换另一条编辑记录 | 保存完成后无条件清空当前表单，删除提交后新草稿 | `frontend/src/views/ManagementView.vue:30`、`:50`：只有编辑 ID 与提交快照同时相符才重置 |

七个生产源文件本轮净修改为 +44/-14 行；无 API、表结构、迁移、主状态机、权限模型、算法、Design System 变更。两处隔离级别调整明确针对已经复现的快照读取错误，不增加锁范围。

## 验证证据

- 新增 `backend/src/test/java/com/campus/repair/service/DatabaseConcurrencyTest.java`：真实 Spring 代理、InnoDB 行锁和锁等待；先复现预约双方成功、上传双方成功和资料覆盖，再验证修复；另验证真实主事务回滚不通知、数据库并发通知幂等。
- 该测试需**新建、按当前迁移初始化的隔离库**及现有测试账号。`DB_NAME` 和 `CHECK_DB_NAME` 必须一致且符合 `campus_repair_deep_check_...`；普通构建未配置时跳过。配额测试按新库无图片的前提运行，重复整批执行应新建隔离库，不能对业务库清空数据。
- 本轮最终 `JAVA_HOME` 使用 Java 17，执行 `./backend/mvnw -o -f backend/pom.xml test package`：43 项通过，0 失败、0 错误、0 跳过；4 项数据库测试实际执行。
- 新增 `frontend/scripts/check-deep-races.mjs`：调用真实 Vue 组件处理函数，控制响应次序；8 项通过，包含超过 100 条历史和 ID=1 的回执边界。它不代表浏览器/DOM 验收。
- `npm run build` 内含 `vue-tsc --noEmit`，通过；既有会话竞态 8 项、SSR 路由渲染 21 项及上下文面板、导出响应检查通过。
- 新建隔离库 `campus_repair_demo_deep_20261001_a` 通过现有 `prepare-demo.py` 的真实 HTTP 场景，以及 `check:core` 的 15 项、`check:auth` 的 17 项检查。临时服务只监听本机端口 18086/18088。
- 补充真实 HTTP/SQL 边界验证：驳回重提、创建同键并发重试与异载荷拒绝、拒单重派、待开工 SLA 单事件、收回与接单竞争、维修记录重试/同文新记录、维修中收回及旧维修员详情/聊天/上传图片失权、无负责人历史只读、106 条聊天历史分页、通知第三页及全部已读截止边界、改密/停用撤销 Token、无关学生 IDOR。
- 临时重命名**本轮隔离库**通知表进行故障注入，并在 finally 中恢复：业务 HTTP 仍成功、订单已提交；日志包含 eventId、幂等键、businessId、targetUserId 和 SQL 错误原因。没有把通知故障注入业务库。
- `git diff --check` 通过。本轮差异单独与修改前文件快照复审；第二轮只读复核未发现剩余新增回归。

## 没有确认额外缺陷的区域与限制

已沿调用链检查 JWT/三角色权限、OrderAccessService、状态流转/多轮历史、智能推荐确认、SLA 行锁、AFTER_COMMIT/独立事务、私有图片归属、Dashboard/导出口径；在审查和已执行验证范围内未确认额外缺陷。未修改这些稳定区域。

切单导致上传图片串入新订单的初步线索被排除：实际路由按路径重建组件，旧回调不写新实例。关于已超时后接受新预约是否清除历史超时标记，没有充分依据认定现有策略错误，因此未修改。

没有浏览器、GUI 或自动化视觉验收，也没有高并发压力、进程崩溃、磁盘耗尽和真实断网丢响应试验。通知仍是有界内存后台队列，没有持久补偿；进程退出/队列拒绝可能漏通知。聊天 POST 本身没有数据库请求键幂等，本轮只修复已收到 POST 成功之后的同步失败；POST 响应丢失的结果不确定性仍属于现有接口限制。

本轮没有遗留未修复的已确认缺陷；已验证范围内未发现阻止代码版本冻结的问题，浏览器人工验收仍需单独完成。
