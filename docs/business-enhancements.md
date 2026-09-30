# 最后一轮业务增强与验证

2026-09-30，以本机实际代码与数据库为准。本轮无依赖安装，无GUI或浏览器自动化；全部变更保留原Phase1～6未提交工作。

## 五项实现

| 能力 | 实现 |
| --- | --- |
| 审核驳回 / 原单重提 | 管理员必填原因，学生仅可编辑本人REJECTED；原ID回到WAIT_AUDIT，EDIT/RESUBMIT记录与驳回历史保留 |
| 拒单 / 多轮派单 | 当前未接受的人员必填拒单原因，释放worker_id；智能/人工重新派单产生新轮，评分与旧解释不覆盖 |
| 工单SLA | 优先级响应/维修阈值集中于SlaProperties；Spring一分钟调度，订单锁内检查并标记overdue_type；同轮只通知一次 |
| 验收返工 | WAIT_CONFIRM → REWORK_PENDING；管理员选择原人员继续或重新派单，repair_round递增；当前轮有记录才可完成 |
| 上门预约 | 当前负责人员接单后提出校园本地时间，学生接受/拒绝；version与订单锁拒绝旧响应，事件保留历史，不阻塞维修 |

正常状态链保留。人工指定仍为WAIT_ASSIGN，智能指定仍为ASSIGNED；新订单以accepted_time区分是否接受，均需accept后才可start。历史ASSIGNED订单迁移保留已接受语义。COMMENTED禁止返工或再次验收。

## 数据与接口

仍为13张表，新增字段仅涉及repair_order、order_event、dispatch_record、repair_record。order_event.actor_id允许NULL表示系统SLA行为。人工派单不伪造评分，评分列允许NULL。新增SLA和轮次索引。增量脚本business-enhancements.sql按字段/索引检查重复执行，完成标记放在全部回填之后。历史业务update_time显式保留。

本机展示库迁移前已私下备份；迁移重复执行两次，原订单所有业务列逐行比较一致。验收订单、人工过期时间均只写隔离库campus_repair_phase5_check_20260930。

新增7个PUT动作：管理员reject/rework、学生resubmit/acceptance-fail/appointment、维修员reject/appointment。订单列表支持overdue；详情增加当前预约、轮次、SLA和dispatchHistory；overview增加overdueCount/reworkCount。完整契约见api-reference.md。

聊天沿用订单锁，新前端带expectedWorkerId，防止旧草稿在负责人变化后误发；旧维修人员失去访问权，新人员只能读其自己的会话，学生/管理员保留整体历史。私有图片策略未放宽。

## UI与审查修复

复用详情页面和上传组件，新增渐进展开的原因、修改、返工和预约面板；任务卡显示返工轮次与超时提示。时间线按真实事件和维修轮次折叠，派单历史保留人员、解释与拒单原因。Dashboard只增加简洁超时/返工指标，不改图表地图架构。

最终四路只读审查及实际接口检查发现并修复：

- 返工时原START查询多行，以及完成操作覆盖全部轮次：改为订单本轮started_time、当前轮记录更新。
- 平均维修时间按多轮START/FINISH连接会重复组合：按订单和维修轮次聚合真实记录时长。
- MyBatis忽略NULL更新导致负责人/预约/超时信息无法清除：显式ALWAYS空值更新。
- 拒单后智能派单页面的旧确认标记阻止重派：以刷新后的真实订单状态判定。
- 上传、保存可并发且pending过早释放：统一操作禁用与上传状态。
- 聊天预检查与发送间负责人可能变化：锁内比较expectedWorkerId。
- 空字符串图片地址与数据库NULL不同导致记录重试重复：统一规范化后去重。
- 迁移中断可能跳过回填、回填改写update_time：末尾完成标记与显式时间保留。
- 原应用账号只有派单记录INSERT权限，拒单返回503：补充decision/reject_reason/response_time三列UPDATE，文档同步。
- 系统超时事件不能借用学生身份：actor_id为NULL，时间线明确系统动作。

通知事务边界已在后续修复：订单事务内发布轻量事件，提交后独立事务写通知；唯一事件键防重复。通知失败记录事件类型、工单与接收用户，不再回滚已提交业务。SLA扫描每单单独事务，失败不会阻塞其他订单。未增加消息队列或外部通知依赖。

## 实际验证

- 后端最终 `mvnw -o -DskipTests package` 成功，100个源码文件编译，约5秒。
- 定向派单服务与算法14个单元验证通过；测试JVM出现CDS提示，非编译错误。未跑全量历史套件。
- 前端 `npm run build` 类型检查与产物构建成功，无编译警告。
- `backend/scripts/check_enhancements.py` 真实HTTP通过四条流程，含原人员和换人返工、两种预约反馈、并发验收失败、归属、聊天换人隔离、SLA两类幂等、真实统计与地图接口。
- `npm run check:core` 15项前端API/Pinia/路由真实HTTP验证通过，包含图片上传及学生读取维修结果、登录/刷新身份、派单、维修、聊天、通知、确认评价和退出撤销。
- `npm run check:render` 19条路由、7种新增操作面板及真实事件时间线SSR验证通过，无Vue/SSR控制台警告。
- 本机8080及隔离18085健康接口应用/数据库均UP；Vite5173登录HTML返回200。
- `git diff --check`通过。已有核心测试保留，更新过期接单/事件数量断言；本轮未再删除业务组件或新增大量测试。

## 真实限制

未进行浏览器视觉、实际点击、窄屏和动画人工验收，SSR不能代替这些检查。预约使用北京时间，非跨时区日历；调度默认一分钟有检测延迟。旧版本聊天客户端不传expectedWorkerId时仍按当前人员接收，以保留原接口兼容。通知失败会记录日志，但没有跨进程持久重试队列。

五项范围完成后冻结业务扩展，不新增其他模块。
