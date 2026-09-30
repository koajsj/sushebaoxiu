# 校园智能报修管理系统

基于 Vue 与 Spring Boot 的本科毕业设计。当前完成 Phase 1～6：三角色认证、报修闭环、可解释派单、校园坐标地图、真实数据驾驶舱、工单沟通、站内通知及最终审查整理；现已补齐驳回重提、拒单重派、超时提醒、验收返工和维修预约。

学生提交 → 管理员审核 → 推荐维修人员并确认派单 → 维修处理与工单沟通 → 学生确认与评价 → 数据汇总。保留人工派单，不增加AI、实时定位或WebSocket。

## 技术栈与结构

后端：Java 17、Spring Boot 3.5.16、Spring Security、MyBatis Plus 3.5.17、MySQL 8、Maven Wrapper、Lombok、Validation。前端：Vue 3.5.43、Vite 8.3.1、TypeScript、Element Plus、Pinia、Vue Router、Axios。图表与地图使用本地SVG/CSS，不需要地图密钥或额外SDK。本轮没有安装依赖。

```text
backend/
  src/main/java/com/campus/repair/
    common/      Result、分页响应、错误码与异常处理
    config/      数据库、分页、CORS和Security配置
    controller/  HTTP入口
    service/     业务、数据归属、图片、统计、沟通与通知
    mapper/      数据访问与必要行锁/聚合
    entity/      持久化模型
    dto/ vo/     参数校验与安全响应
    security/    JWT、认证服务、Token过滤器
    algorithm/   独立确定性派单评分模型
  src/test/      精简核心单元测试
  scripts/       核心HTTP验收与可选专项检查
frontend/
  src/
    api/ components/ layouts/ router/ store/
    views/ utils/ types/ assets/
  scripts/       SSR、会话竞态、地图与核心HTTP检查
sql/             结构迁移与开发数据
docs/            架构、API、数据库、答辩与验证资料
```

Controller不编写业务或算法逻辑；Service承担权限归属、状态转换和事务；Mapper负责数据库。前端复用LayoutShell、OrderCard、OrderTimeline、MapPanel、ScoreDisplay和通知/图表组件。

## 已完成功能

- 学生：iOS Widget风格首页、五步报修、私有图片上传预览、本人订单分页/筛选、进度时间线、确认与评价。
- 维修人员：工作台、本人任务、接单/开始/记录/完成，以及工单文字沟通。
- 管理员：工单审核、人工派单、四因素可解释推荐、校园地图、Dashboard与沟通记录查看。
- 共用能力：BCrypt登录、JWT与数据库角色校验、刷新恢复、退出撤销、通知及已读、统一异常/参数校验。
- Apple风格：系统字体、浅色背景、蓝色强调、统一圆角与轻阴影、响应式导航、键盘焦点与减少动画支持。

## 数据库与状态

共13张表，见 [数据库说明](docs/database.md) 和 [ER图](docs/architecture.md)。流程增强扩展四张现有表，迁移为 `sql/business-enhancements.sql`；通知解耦迁移为 `sql/notification-after-commit.sql`。最终审查未改数据库结构。保留JWT、角色模型、派单算法与正常主流程，新增REJECTED、REWORK_PENDING两个异常状态。

```text
WAIT_AUDIT → WAIT_ASSIGN → ASSIGNED → PROCESSING
→ WAIT_CONFIRM → FINISHED → COMMENTED
```

人工指定人员后仍为WAIT_ASSIGN，维修员接单进入ASSIGNED；智能派单由管理员确认后直接进入ASSIGNED，但新派单均须维修员明确接受（accepted_time）后才可开始；迁移保留旧订单已接受语义。每次状态操作由后端校验，在事务中锁定订单；评价每单唯一，计算人员均分时保留人员行锁。CREATED仅保留枚举，无草稿接口。

技能40%、距离30%、当前负载20%、历史评价10%。真实输入确定性计算，保存每次推荐快照，确认前复核且10分钟有效。地图使用静态WGS84坐标，支持缩放、平移、重叠标记展开和点击详情；开发坐标明确为示例。

## 最后一轮流程增强

- 审核驳回填写原因；学生编辑原订单后重提，保留ID和事件。
- 未接受的任务可拒单，释放负责人员；再次智能/人工派单保留每轮历史。
- LOW/NORMAL/HIGH的响应/维修SLA集中配置；每分钟检查一次，通知幂等。
- 验收失败交管理员安排原人员返工或重新派单；维修记录按轮次保存。
- 接单后预约上门时间，学生接受或说明调整原因；预约不阻塞维修。

部署前先备份、停止旧后端，再执行新增迁移、`sql/notification-after-commit.sql` 及派单历史字段的UPDATE授权。详见 [本轮实现与验证](docs/business-enhancements.md)。展示库只迁移，测试数据仅写隔离库。

## 启动

### 当前电脑

- 前端：<http://127.0.0.1:5173/login>
- 后端健康：<http://127.0.0.1:8080/api/health>
- MySQL 8私有演示实例：127.0.0.1:13306，数据库campus_repair。

在项目根目录分别启动数据库、后端，前端另开终端：

```bash
.runtime/start-mysql.sh
.runtime/start-backend.sh
```

```bash
cd frontend
npm run dev
```

`.runtime/`只用于当前电脑，含私有配置，不提交、不分享。修改后端代码后先停止旧后端，再运行 `cd backend && ./mvnw -o -DskipTests package`；不要在运行的jar文件上覆盖构建。

### 其他电脑

需要已有Java 17、Node 20.19+或22.12+、MySQL 8。使用Maven Wrapper，无需全局安装Maven。

1. 按 [数据库说明](docs/database.md) 初始化结构、可选开发数据和最小权限账号。
2. 复制backend/.env.example为backend/.env，填写连接与随机JWT_SECRET。运行 `openssl rand -base64 32` 生成密钥；真实密钥不要写入源码。
3. 确认 `java -version` 为17且JAVA_HOME正确。macOS可用 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`；其他系统设置当地JDK目录。
4. 启动后端：

```bash
cd backend
set -a
source .env
set +a
./mvnw spring-boot:run
```

5. 首次获取前端依赖再启动：

```bash
cd frontend
npm ci
npm run dev
```

`.env`需要导出，Spring不会自动读取该文件。前端默认代理8080；更换端口时用frontend/.env中的BACKEND_PROXY_TARGET。VITE_变量不可存放密钥。

默认图片在backend/uploads，可用UPLOAD_DIRECTORY指定持久目录。日志滚动保留7天/总量100MB，错误响应隐藏内部堆栈。交付时备份数据库和图片目录，排除`.runtime/`、`.env`、日志、node_modules及个人数据。

## 开发账号

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 学生 | student001 | 123456 |
| 维修人员 | worker001 | 123456 |
| 管理员 | admin001 | 123456 |
| 电工示例人员 | worker002 | 123456 |
| 水暖示例人员 | worker003 | 123456 |

仅供本地演示；密码以BCrypt哈希保存。新增示例人员继承worker001当前密码。档案缺失返回友好错误，档案维护UI不在现有范围。

## 必要验证与维护

最新一轮执行后端离线构建、图片权限与通知的3项针对性单元验证、前端生产构建、19路由SSR、7项会话/交互/错误回归及地图逻辑检查，并在隔离库验证真实流程与前端API闭环。结果见 [最终审查记录](docs/final-review.md)；[Phase6验证记录](docs/phase6-validation.md)保留为此前证据。没有执行全量历史专项套件或GUI自动化，SSR不能替代人工浏览器视觉/交互/控制台检查。

短验证入口：

```bash
# 先停止运行中的后端，再构建；已有缓存时可用-o离线
cd backend
./mvnw -o -Dtest=ImageServiceTest,NotificationServiceTest test
./mvnw -o -DskipTests package
```

```bash
cd frontend
npm run build
npm run check:render
npm run check:session
npm run check:map
```

本机隔离库验收在18085，数据库campus_repair_phase5_check_20260930；脚本会保存验收工单和图片，禁止指向展示/正式库。启动该隔离后端后顺序执行，避免共享账号退出撤销相互干扰。超时专项验收需仅在隔离实例设置 `APP_SLA_SCAN_DELAY_MS=1000`，完成后恢复默认60000：

```bash
.runtime/start-phase5-check.sh
python3 backend/scripts/check_enhancements.py
npm --prefix frontend run check:core
```

验收脚本使用现有Python标准库及前端依赖，无新增测试包。历史认证/越权并发/推荐失效专项脚本保留供对应代码发生变化时使用，不纳入默认执行。移除了重复的前端阶段HTTP脚本，合并为check:core；测试边界见 [维护策略](docs/phase6-validation.md)。

## 交付资料

- [当前系统架构、功能结构、ER、核心流程与创新点](docs/architecture.md)
- [API参考](docs/api-reference.md)
- [数据库与迁移、最小权限、备份](docs/database.md)
- [答辩展示流程与人工检查清单](docs/defense-guide.md)
- [最新最终审查与验证结果](docs/final-review.md)
- [此前Phase6审查及验证结果](docs/phase6-validation.md)

Phase1～5设计与验证文档保留为历史记录，最终行为以当前源码和上述交付资料为准。

## 当前限制

- 沟通与通知手动刷新，无实时推送；消息最多展示最近100条，通知最近50条。
- 地图是静态校园坐标图，不提供在线瓦片、实时GPS或路线导航；演示坐标需替换为真实校园坐标。
- 每张工单/每条维修记录支持一张可选图片。未绑定图片没有自动清理任务。
- 未实现注册、密码重置、档案维护、取消、AI、预测模型、独立聊天、App或小程序。
- 通知在AFTER_COMMIT后独立写入，失败记录日志但无持久重试；进程在提交与通知落库之间退出时可能漏通知。
- 没有提供现成生产部署配置；需要人工完成真实校园数据、HTTPS与环境配置及浏览器验收。

后续只补充交付前人工验收、真实坐标和论文材料，不增加未规划功能。
