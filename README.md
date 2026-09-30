# 校园智能报修管理系统

当前完成 Phase 1 基础工程、Phase 2 认证权限、Phase 3 核心报修闭环、Phase 4 智能推荐派单和校园坐标地图，以及 Phase 5 数据驾驶舱、工单沟通和通知。沿用既有 JWT、三角色权限、Pinia、Router、Axios 与 Layout，没有新增项目依赖或安装全局工具。

## 1. 完成功能与结构

学生提交报修 → 管理员审核 → 人工指定维修人员 → 维修人员接单 → 开始维修 → 保存文字/图片维修记录 → 完成维修 → 学生确认 → 一次评价。

- 学生：首页基础业务计数、近期订单、五步报修向导、我的报修分页与状态筛选、详情时间线、完成确认及评价。
- 维修人员：今日分配任务数、待处理/处理中/已确认完成数、本人任务卡片、接单/开始/维修记录/结果图片/完成。
- 管理员：工单卡片、状态/类型/日期筛选、详情审核、人工指定启用维修人员；可解释智能推荐与确认派单、校园任务地图、真实数据驾驶舱。
- 工单沟通：报修学生与当前负责人员发送文字消息；管理员查看记录。站内通知由提交、审核、派单和维修完成事务触发。
- 后端控制所有状态，事务与订单行锁处理并发；唯一评价；真实事件时间线；完成数与平均评分维护。
- 私有 PNG/JPEG 上传与受限读取：校验实际图片格式，5MiB上传上限、20百万像素上限、重新编码、随机文件名、本人图片一次绑定。
- 保留 BCrypt 登录、JWT签发与校验、角色接口隔离、刷新恢复、退出持久化撤销、统一 Result/PageResult、日志、CORS、健康与数据库检查。

```text
backend/src/main/java/com/campus/repair/
  common/       原 Result、PageResult、异常处理；业务错误码扩展
  config/       既有 Security、MyBatis Plus、CORS；multipart上限配置
  controller/   原认证与健康接口；RepairOrderController、ImageController
  service/      RepairOrderService、OrderAccessService、OrderStatus、ImageService、ImageCodec
  entity/       原UserEntity；Student/Worker/Building/RepairType/RepairOrder/RepairRecord/Evaluation/OrderEvent/RepairImage
  mapper/       对应MyBatis Plus Mapper；订单/维修员事务行锁
  dto/          创建、查询、指定人员、维修记录、评价请求
  vo/           OrderVO、OrderDetailVO；既有安全用户VO
  security/     沿用Phase2认证实现
  algorithm/    DispatchAlgorithm纯评分模型
  service/      新增DispatchService、DispatchDataService、MapService
  controller/   新增DispatchController、MapController
  service/      新增StatisticsService、OrderMessageService、NotificationService
  controller/   新增StatisticsController、OrderMessageController、NotificationController
frontend/src/
  api/repair.ts / dispatch.ts / phase5.ts    业务与统计/沟通/通知API封装
  components/                    复用Layout/Brand；OrderCard、OrderTimeline、ProtectedImage、ImageUpload
  views/                         原登录及业务页面；新增DispatchView、MapView、DashboardView、OrderChatView
  router/store/utils/types/      原路由权限与认证；业务路由、格式化和业务类型扩展
sql/
  init.sql                       原数据库/用户表
  dev-users.sql                  原BCrypt开发账号
  phase3.sql                     幂等增量业务结构和基础类型/楼栋
  dev-business.sql               测试账号档案，仅开发使用
  phase4.sql                     推荐记录表与维修员静态坐标增量迁移
  dev-dispatch.sql                示例静态坐标/电工/水暖人员，仅开发使用
  phase5.sql                     工单消息与用户通知增量结构
```

技术栈保持 Java17、Spring Boot3.5.16、Spring Security6.5.11、MyBatis Plus3.5.17、MySQL8、Maven Wrapper3.9.11；Vue3.5.43、Vite8.3.1、TypeScript5.9.3、Element Plus2.14.6、Pinia4.0.3、Router4.6.4、Axios1.20.0。前端沿用Apple风格浅色背景、系统字体、蓝色强调、留白与圆角，增加类型卡片反馈、步骤流程、状态卡片、时间线与成功反馈，支持减少动画和响应式布局。

## 2. 数据库变化

保留原 `user` 结构，新增7个业务表：`student`、`worker`、`building`、`repair_type`、`repair_order`、`repair_record`、`evaluation`。另外新增 `order_event` 保存各步骤真实时间，`repair_image` 保存上传者和绑定订单以校验图片权限。

学生/维修员档案以 `user_id` 唯一关联账号；评价以 `order_id` 唯一；外键保护关联关系；订单按学生/维修员/状态/时间建索引；MySQL8和utf8mb4。

`phase3.sql` 用于现有版本增量升级，重复执行不覆盖用户、档案或工单。`dev-business.sql` 为现有 student001/worker001 补齐档案。常见故障类型5种、测试楼栋3栋。Phase4通过 `phase4.sql` 为维修员新增静态坐标和 `dispatch_record` 推荐记录；`dev-dispatch.sql` 仅填充两项坐标均空的示例楼栋/worker001，并增加worker002电工、worker003水暖开发示例账号。示例坐标不代表实际学校；已有真实坐标、用户密码、评分和业务数据不覆盖。

Phase5通过 `phase5.sql` 增量添加 `chat_message`、`notification` 两表。消息外键绑定订单、发送者和接收者；通知归属单个用户。脚本可重复执行，不更改既有订单或认证结构。

| 身份 | 账号 | 开发密码 |
| --- | --- | --- |
| 学生 | student001 | 123456 |
| 维修人员 | worker001 | 123456 |
| 管理员 | admin001 | 123456 |
| 电工示例人员 | worker002 | 123456 |
| 水暖示例人员 | worker003 | 123456 |

开发账号仅供本地，数据库密码为BCrypt哈希；新增示例账号复制worker001现有哈希，若其密码已改，新增账号继承该密码。账号没有相应档案时业务接口返回友好409错误；档案维护界面不属于本阶段。

## 3. 接口

除原 POST `/api/auth/login` 和 GET `/api/health` 外，接口都要求 Bearer JWT。原退出和当前用户接口保留。

| 方法 | 接口 | 权限 / 用途 |
| --- | --- | --- |
| POST | /api/student/orders | 学生创建，初始WAIT_AUDIT |
| GET | /api/student/orders | 本人订单，分页/状态筛选 |
| GET | /api/orders/{id} | 学生本人/指定维修员/管理员，订单+记录+评价+时间线 |
| GET | /api/admin/orders | 管理员列表，状态/类型/日期筛选 |
| PUT | /api/admin/orders/{id}/audit | 管理员审核通过 |
| PUT | /api/admin/orders/{id}/assign | 管理员人工指定人员，JSON workerId |
| GET | /api/admin/workers | 启用维修人员候选 |
| GET | /api/worker/orders | 仅分配给当前人员的订单 |
| PUT | /api/worker/orders/{id}/accept | 指定维修人员接单 |
| PUT | /api/worker/orders/{id}/start | 开始维修 |
| POST | /api/worker/repair-record | JSON orderId/content/imageUrl，保存记录 |
| PUT | /api/worker/orders/{id}/finish | 至少有一条记录后完成维修 |
| PUT | /api/student/orders/{id}/confirm | 所属学生确认 |
| POST | /api/student/evaluation | JSON orderId/score(1–5)/content，一次评价 |
| GET | /api/admin/dispatch/recommend/{orderId} | 管理员获取并保存本次候选推荐快照 |
| POST | /api/admin/dispatch | 管理员确认，JSON orderId/workerId/recommendationId |
| GET | /api/map/orders | 仅管理员，按status筛选，最多500条及总数 |
| GET | /api/map/workers | 仅管理员，启用维修员静态服务坐标与当前负载 |
| GET | /api/map/buildings | 仅管理员，校园建筑坐标 |
| GET | /api/admin/statistics/overview | 今日报修、处理中、完成率、平均维修时间 |
| GET | /api/admin/statistics/trend | 最近14天新报修与维修结果提交趋势 |
| GET | /api/admin/statistics/types | 故障类型工单数量和比例 |
| GET | /api/admin/statistics/workers | 人员完成量、真实评价均分、当前任务数 |
| GET | /api/orders/{orderId}/messages | 订单学生、当前维修员、管理员读取最近100条 |
| POST | /api/orders/{orderId}/messages | 学生与当前维修员发送文字；JSON content |
| GET | /api/notifications | 当前用户最近50条通知、总数与未读数 |
| PUT | /api/notifications/{id}/read | 仅所属用户标记已读 |
| GET | /api/catalog | 登录用户读取故障类型和楼栋 |
| GET | /api/student/summary | 学生本人基础业务计数 |
| GET | /api/worker/summary | 维修员本人基础计数，today按今日分配事件计算 |
| POST | /api/images | 学生/维修人员，multipart字段file |
| GET | /api/images/{uuid} | 图片上传者或绑定订单参与者，管理员读已绑定图 |

分页参数 `page` 默认1、`size` 默认12且最大100、`status` 可选；管理员 `typeId`、`from`/`to`（YYYY-MM-DD，含起止日期）可选。创建JSON为 typeId/title/description/buildingId/roomNo/priority/imageUrl，priority为LOW/NORMAL/HIGH，图片可不传。先上传获得 `/api/images/{uuid}` 再引用，不能传任意外部图片URL或他人上传。

返回统一 `{code,message,data}`。未登录401、角色错误403、非订单参与者404、状态冲突409、参数/图片错误400。图片成功读取为二进制，前端带授权获取Blob后展示；不公开上传目录。

## 4. 状态与事务

```text
WAIT_AUDIT → 审核 → WAIT_ASSIGN → 指定维修人员（状态仍WAIT_ASSIGN）
→ 接单 ASSIGNED → 开始 PROCESSING → 完成 WAIT_CONFIRM
→ 学生确认 FINISHED → 学生评价 COMMENTED
```

`CREATED`仅保留枚举，当前无草稿业务接口。前端没有任意设置状态的接口。原人工指定流程保留。智能推荐确认由管理员将未指定的WAIT_ASSIGN订单置为ASSIGNED，记录ASSIGN事件，维修人员可直接开始维修。已有人工指定订单不允许再智能派单；维修员只能处理自己的任务。完成必须存在维修记录；确认/评价只能由报修学生执行。

每次操作在事务中锁定订单后验证身份与当前状态。提交记录和图片绑定一起提交/回滚；完成记录时间与完成数一起提交；评价唯一约束且锁定维修员，READ_COMMITTED下计算平均分，防止不同订单并发丢失评分。维修员 `task_count` 在完成维修进入WAIT_CONFIRM时加1，首页“已确认完成”只数FINISHED/COMMENTED，二者含义明确。

JWT算法、有效期、数据库角色校验与token_version撤销沿用Phase2。没有新增JWT、权限框架或客户端身份可信来源。

## 5. 启动

当前电脑的私有运行配置已更新，地址为 [登录页](http://127.0.0.1:5173/login)、[智能派单](http://127.0.0.1:5173/admin/dispatch)、[校园地图](http://127.0.0.1:5173/admin/map)、[健康接口](http://127.0.0.1:8080/api/health)。管理员入口需先登录admin001。MySQL8独立实例为127.0.0.1:13306。

本机数据库已经应用增量迁移。修改后端代码后，先停止运行的后端再编译：

```bash
cd backend
./mvnw verify
```

在项目根目录启动数据库和后端：

```bash
.runtime/start-mysql.sh
.runtime/start-backend.sh
```

另一个终端启动前端：

```bash
cd frontend
npm run dev
```

`.runtime/` 含本机私有配置，不提交或分享；该目录中密钥/数据库配置权限为600。现有系统MySQL服务未改动。

其他电脑使用已有 Java17、Node20.19+或22.12+、MySQL8，无需全局安装Maven：

```bash
mysql -u root -p < sql/init.sql
mysql -u root -p < sql/phase3.sql
mysql -u root -p < sql/phase4.sql
mysql -u root -p < sql/phase5.sql
# 仅在开发环境执行：
mysql -u root -p < sql/dev-users.sql
mysql -u root -p < sql/dev-business.sql
mysql -u root -p < sql/dev-dispatch.sql
```

为应用账号配置最小权限：

```sql
CREATE USER 'campus_repair'@'127.0.0.1' IDENTIFIED BY '自行设置的强密码';
GRANT SELECT ON campus_repair.* TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (token_version) ON campus_repair.user TO 'campus_repair'@'127.0.0.1';
GRANT INSERT, UPDATE ON campus_repair.repair_order TO 'campus_repair'@'127.0.0.1';
GRANT INSERT, UPDATE ON campus_repair.repair_record TO 'campus_repair'@'127.0.0.1';
GRANT INSERT, UPDATE ON campus_repair.repair_image TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.order_event TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.evaluation TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.dispatch_record TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.chat_message TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (read_status) ON campus_repair.chat_message TO 'campus_repair'@'127.0.0.1';
GRANT INSERT ON campus_repair.notification TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (read_status) ON campus_repair.notification TO 'campus_repair'@'127.0.0.1';
GRANT UPDATE (score, task_count) ON campus_repair.worker TO 'campus_repair'@'127.0.0.1';
```

启动后端：

```bash
cd backend
cp -n .env.example .env
# 编辑.env，填写DB配置，并将下述命令生成的密钥填入JWT_SECRET：
openssl rand -base64 32
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
set -a
source .env
set +a
./mvnw spring-boot:run
```

`.env`不会被Spring自动加载，必须导出；共享项目不包含真实密钥。

启动前端：

```bash
cd frontend
npm install
npm run dev
```

前端默认代理8080。修改后端端口时在frontend/.env中设置BACKEND_PROXY_TARGET；`VITE_`变量不能放密钥。


上传默认存放 `backend/uploads/`，可用 `UPLOAD_DIRECTORY` 指定专用持久目录。该目录已忽略，不提交图片数据；正式迁移机器时数据库和图片目录需一起备份。

## 6. 验证

Phase5依照“不要运行长时间命令和测试”，仅执行12秒以内的针对性检查：24项Java核心测试、前端构建、19个SSR路由、40项隔离库HTTP检查。详细证据见 [Phase5验证记录](docs/phase5-validation.md)；历史结果见 [Phase4验证记录](docs/phase4-validation.md)。下面全量回归命令供后续需要时使用，不代表本轮已执行。

```bash
# 先停止运行中的后端再构建jar
cd backend
./mvnw verify
cd ../frontend
npm run build
npm run check:render
npm run check:session
# 8080已启动，开发账号已初始化：
npm run check:auth
```

本机真实HTTP脚本不需要Python第三方包。业务验收应顺序执行，避免共享账号退出撤销干扰：

```bash
# 独立测试库服务为本机18083，由.runtime/start-phase3-check.sh启动：
BACKEND_URL=http://127.0.0.1:18083 python3 backend/scripts/check_repair_scope.py
CHECK_BACKEND_URL=http://127.0.0.1:18083/api npm --prefix frontend run check:repair
```

`check_repair_scope.py` 在任何修改前检查独立端点，导入并执行基础闭环后，添加跨账号/SQL/并发检查；会创建验收订单并保留在独立测试库，临时其他账号在finally清理。`.runtime/`是本机私有配置，不能复制分享。原 `check_auth.py` 会短暂变更开发学生状态/角色再恢复并执行退出撤销，不能对生产运行。普通 `check_repair.py` 可按BACKEND_URL测试本地开发服务，会创建验收订单。

命令行API/状态流程、SSR和构建不能替代真实浏览器视觉、点击交互与浏览器控制台验收；依照约束没有执行GUI自动化。

## 7. 当前未实现

AI助手、WebSocket实时聊天、独立聊天系统、预测模型、实时定位和路线导航未实现。未增加注册、重置密码、档案维护、草稿/取消/拒审/返修等扩展。本阶段一份订单和每条维修记录各支持一张可选图片；未绑定图片清理没有定时任务。

## 8. 下一阶段

先完成真实校园坐标配置和人工浏览器视觉/交互验收，再按既定后续需求继续；不提前进行 Phase 6 全面优化。


## 9. Phase4 推荐与地图

入口：管理员侧边栏“智能派单” `/admin/dispatch`、“校园任务地图” `/admin/map`。订单详情保留人工派单，并提供携带orderId的推荐入口。

评分全部在 `algorithm/DispatchAlgorithm` 中确定性计算。分项0～100，总分保留两位：

| 因素 | 权重 | 规则 |
| --- | --- | --- |
| 技能 | 40% | 精确类别/领域关键词100；综合维修70；其他或未知领域50；不匹配0 |
| 距离 | 30% | WGS84 Haversine公里，100/(1+距离km)；坐标缺失/非法计0并解释 |
| 负载 | 20% | 100/(1+当前未完成任务数)，统计有worker_id的WAIT_ASSIGN/ASSIGNED/PROCESSING |
| 评价 | 10% | 真实历史均分×20；无评价(score=0)中性50 |

`worker.task_count` 保持Phase3累计完成量含义，响应另用 `activeTaskCount` 表示当前负载。总分使用未提前四舍五入的分项加权；候选排序为总分降序、距离升序、workerId升序。技能词条是透明固定规则，不使用AI或随机数。

每次GET推荐对全部启用人员保存独立批次快照，未改变订单；确认POST须携带返回的recommendationId。快照10分钟有效，确认时重新读取人员状态、坐标、技能、负载、历史分与故障类型说明，对每个分项和原始输入说明校验。订单/人员行锁及事务保证确认记录、worker_id、ASSIGNED状态和事件一起写入，重复确认409。

地图是用户选定的本地SVG校园坐标地图，不安装地图SDK、不使用密钥/在线瓦片。支持建筑/订单/人员层、状态筛选、放大缩小、拖动平移、复位、鼠标与键盘选中详情。坐标相同的点展开并用引线连接真实坐标，缺少有效坐标不生成假标记，可在位置列表查看。最多500个订单并显示总数/截断提示。人员位置为静态服务位置，BUSY/AVAILABLE来自当前活动任务数量。正式使用需将示例坐标换成实际WGS84坐标。

短检查脚本（本机开发/隔离环境）：

```bash
npm --prefix frontend run check:map
# 仅隔离库campus_repair_phase4_check_20260930 / 18084；会保存验收工单与推荐记录。
BACKEND_URL=http://127.0.0.1:18084 python3 backend/scripts/check_dispatch.py
npm --prefix frontend run check:dispatch
```

推荐记录使用 `recommendation_batch` 关联一次候选集合，`confirmed=1` 为管理员确认时额外保存的选定快照。推荐分数、解释以及静态坐标输入用于追踪与论文分析；推荐记录维护页面尚未实现；统计驾驶舱在Phase5新增。

## 10. Phase5 驾驶舱、沟通与通知

管理员 `/admin/dashboard` 使用4个统计接口直接读取数据库聚合结果，前端使用本地SVG折线/饼图、CSS横向柱图及原有MapPanel/LocationCard；没有引入图表SDK。今日报修按亚洲/上海自然日统计；“处理中”含ASSIGNED、PROCESSING、WAIT_CONFIRM；完成率为FINISHED与COMMENTED之和除以全部工单；平均维修时间按START到FINISH事件，单位小时。趋势固定最近14日，完成趋势以FINISH事件为准；故障类型包含零订单类别，人员评价均分直接读取evaluation，当前负载统计尚未处理完的已分配订单。无数据时返回0或空系列。

详情页“维修沟通”进入 `/{role}/orders/{id}/messages`。学生与当前负责维修员可发送纯文字；管理员只读；无关账号统一404。未派单不允许发送。消息接口只读最新100条，读取时将当前用户收到的消息标记已读；管理员查看不改变回执。没有实时推送，页面提供手动刷新。

顶部通知面板展示当前账号最近50条、未读总数和已读按钮。学生提交成功、管理员收到新单、审核完成、人工/智能派单完成、维修完成均由订单事务内部事件触发，失败时与状态变更一同回滚。通知面板打开和手动刷新时更新，未做WebSocket或后台轮询。

测试保留JWT、订单状态、图片、智能派单、基础路由以及隔离库核心流程；移除阶段性页面错误/组件调试脚本和重复的异常控制器测试。新增单一 `backend/scripts/check_phase5.py` 覆盖统计、聊天权限、通知触发与已读、智能/人工派单、完成评价和地图冒烟。执行前需启动独立的 `campus_repair_phase5_check_20260930` 库和本机18085服务；脚本会保留该隔离库中的验收工单，不得指向正式数据库。
