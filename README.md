# 校园智能报修管理系统

基于 Vue 3 和 Spring Boot 3 的本科毕业设计项目。学生提交报修后，管理员审核并确认人工或智能派单，维修人员处理工单，学生验收和评价。系统还提供预约、驳回重提、拒单重派、多轮返工、工单沟通、通知、数据驾驶舱和校园示意地图。

## 产品能力

| 使用者 | 从进入系统到完成任务 |
| --- | --- |
| 学生 | 五步报修 → 查看真实进度 → 工单沟通 → 验收或返工 → 评价 |
| 维修人员 | 任务工作台 → 接单/拒单 → 预约 → 维修记录 → 提交结果 |
| 管理员 | 运营驾驶舱 → 审核 → 智能/人工派单 → 收回重派 → 统计与 Excel 导出 |

系统以订单为中心，保留多轮维修历史和事件时间线。蓝色强调、轻量内容面、系统字体和短动画共用同一套 Design System；图表使用本地 SVG/CSS；地图使用虚构校园图片底图与 SVG 中文标签，不需要地图密钥。

**快速入口**：[本机一键启动](#本机一键启动推荐) · [停止项目进程](#停止项目进程) · [手动排错启动](#在新电脑上从零启动) · [独立演示数据](docs/demo-data.md) · [8～10分钟演示](docs/defense-guide.md) · [产品验收清单](docs/product-acceptance.md)

## 系统截图

真实浏览器截图尚待人工采集，当前不把参考图或校园素材作为运行截图。[截图采集规范](docs/screenshots/README.md)列出登录、Dashboard、智能派单、学生端和维修端五个画面及隐私检查。补齐后可直接放入本节展示，不需要改代码。

## 核心设计

- **可解释派单**：技能40%、静态距离30%、当前负载20%、历史评价10%；查看快照只读，显式生成批次，管理员确认后派单。
- **业务可靠性**：后端状态校验与行锁、请求键去重、多轮派单/返工历史；通知在提交后写入，失败不回滚业务。
- **订单级权限**：三角色 JWT 授权；聊天和私有图片按当前订单归属访问，重派后撤销旧维修员权限。
- **统一数据口径**：Dashboard 和报表复用统计服务；缺少评分或维修时长时显示“暂无数据”或留空，不伪造指标。

数据库使用 MySQL 8 / utf8mb4，共13张表。[ER图和架构图](docs/architecture.md)、[表职责与安全迁移](docs/database.md)、[完整API](docs/api-reference.md)保持独立维护，便于论文引用与审查。

## 本机一键启动（推荐）

在这台 Mac 上，双击项目根目录的 [start-local.command](start-local.command)。脚本会检查 Java、Node/npm、Maven Wrapper 和 MySQL；必要时启动项目专用的 `127.0.0.1:13306` MySQL，核对 `campus_repair` 数据库及迁移结构，构建并启动后端和前端，等待真实健康接口与页面响应，然后打开 <http://127.0.0.1:5173/login>。启动窗口会保持打开；双击 [stop-local.command](stop-local.command) 或在启动窗口按 `Ctrl+C` 可停止本次启动的前后端，**不会关闭 MySQL**。重复双击会复用已就绪的服务。

此 Mac 默认使用被 Git 忽略的 `.runtime/backend.env`。若创建了 `backend/.env.local`，会优先使用它；可参照 [示例配置](backend/.env.local.example) 填写，密码和 JWT 密钥不要提交。原有 `backend/.env` 指向不同的 MySQL 端口，**一键启动不会读取它**。本机配置只允许数据库 `127.0.0.1/campus_repair`；后端和 Vite 也只监听 `127.0.0.1`，后端启用 `local` profile。旧库缺迁移时脚本会提示，不会覆盖已有表；仅目标数据库完全不存在且有本机 MySQL 管理配置时才按现有脚本顺序初始化。

构建和运行日志在 `logs/backend-build.log`、`logs/backend.log`、`logs/frontend.log`；运行 PID 在 `.run/`。这些文件均被 Git 忽略。端口被其他进程占用时脚本会报告 PID，不会结束它。首次使用需要已有 Java 17+、符合前端要求的 Node/npm 和本地 MySQL；项目自带 Maven Wrapper，不必单独安装 Maven。以下手动命令保留为排错备用。

## 在新电脑上从零启动

需要 **Java 17、MySQL 8、Node.js 20.19+（20 系列）或 22.12+**，以及 npm。项目自带 Maven Wrapper，**无需单独安装 Maven**。以下命令适用于 macOS/Linux 的终端；先进入项目根目录。可以用 `java -version`、`node -v`、`mysql --version` 检查已有环境。

### 1. 启动 MySQL 并初始化数据库

先启动你电脑上的 MySQL 8 服务。新库按下面顺序执行结构脚本；最后三个 `dev-*` 脚本只用于**本地演示**，提供测试账号、人员档案和示例坐标。已有数据的数据库升级前请先备份，并参照 [数据库迁移说明](docs/database.md)。

```bash
mysql -u root -p < sql/init.sql
mysql -u root -p < sql/phase3.sql
mysql -u root -p < sql/phase4.sql
mysql -u root -p < sql/phase5.sql
mysql -u root -p < sql/business-enhancements.sql
mysql -u root -p < sql/notification-after-commit.sql
mysql -u root -p < sql/final-hardening.sql

# 仅本地演示环境执行
mysql -u root -p < sql/dev-users.sql
mysql -u root -p < sql/dev-business.sql
mysql -u root -p < sql/dev-dispatch.sql
```

每条命令会单独提示输入 MySQL 管理员密码。若 MySQL 不在默认端口，在**每条命令**的 `mysql` 后加入 `-h 127.0.0.1 -P 实际端口`。脚本会创建 `campus_repair`；应用不会自动建表。应用数据库账号可按 [最小权限示例](docs/database.md#最小应用权限) 创建。

### 2. 配置后端

```bash
cp backend/.env.example backend/.env
openssl rand -base64 32
```

编辑 `backend/.env`：将 `DB_HOST`、`DB_PORT`、`DB_USERNAME`、`DB_PASSWORD` 改为实际 MySQL 连接信息，并把上一步生成的随机值填入 `JWT_SECRET`。`DB_NAME` 保持 `campus_repair`，除非你明确修改了数据库名。`.env` 是本机私有文件，不要提交或分享。后端不会自动读取 `.env`，启动命令会先将其导出为环境变量。

### 3. 启动后端（终端 1）

```bash
./scripts/dev.sh backend
```

脚本从 `backend/.env` 加载配置，使用 Maven Wrapper 直接运行当前源码，不需要先打包 JAR；首次运行可能下载项目已有依赖。启动后访问 <http://127.0.0.1:8080/api/health>；如果数据库连接失败，后端会直接启动失败。macOS 上如有多个 JDK，可在启动前运行 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`。

### 4. 启动前端（终端 2）

```bash
cd frontend
npm ci
cd ..
./scripts/dev.sh frontend
```

打开 <http://127.0.0.1:5173/login> 登录。`npm ci` 只需首次安装依赖或锁文件变化后运行；以后直接 `./scripts/dev.sh frontend`。前端开发服务器默认把 `/api` 代理到 `127.0.0.1:8080`；后端端口变更时，复制 `frontend/.env.example` 为 `frontend/.env` 并修改 `BACKEND_PROXY_TARGET`，随后重启旧前端进程。不要把密钥写入任何 `VITE_` 变量。

两个启动命令都在前台运行，按 `Ctrl+C` 停止。端口上已有可用服务时，脚本会报告现有进程并直接退出；若端口被占用但接口不可用，会提示进程号，不会擅自结束进程。开发期间不要在仍运行旧 JAR 的同时执行 `package`，以免旧进程加载被覆盖的文件；上述 Maven 开发启动方式无需打包。

### 本地演示账号

执行上述三个 `dev-*` 脚本后可使用以下账号。密码仅用于本地演示，数据库中存储的是 BCrypt 哈希；不要在正式环境运行开发数据脚本。

| 角色 | 账号 | 密码 |
| --- | --- | --- |
| 学生 | `student001` | `123456` |
| 维修人员 | `worker001` | `123456` |
| 管理员 | `admin001` | `123456` |
| 其他示例维修人员 | `worker002`、`worker003` | `123456`（新库默认值） |

## 常见启动问题

| 现象 | 检查方法 |
| --- | --- |
| 后端提示数据库连接失败 | 先确认 MySQL 正在运行，再核对 `.env` 中的地址、端口、账号、密码及数据库名；应用启动时会验证连接。 |
| 后端提示缺少 `DB_PASSWORD` 或 `JWT_SECRET` | 确认已填写 `backend/.env`，再从项目根目录执行 `./scripts/dev.sh backend`；本机私有环境使用 `ENV_FILE=.runtime/backend.env`。 |
| `./mvnw` 使用了错误的 Java 版本 | 运行 `java -version`；macOS 可设置 `JAVA_HOME` 指向 Java 17。 |
| 页面可打开，但请求 `/api` 超时 | 先用 `./scripts/dev.sh backend` 检查现有后端的健康接口与登录校验；异常时停止旧后端并重新启动。再检查前端代理目标是否指向后端实际端口。 |
| 运行 `npm run dev` 提示找不到 `vite` | 在 `frontend/` 执行一次 `npm ci`，再启动前端。 |
| 5173 或 8080 端口被占用 | 运行对应的 `./scripts/dev.sh frontend` 或 `./scripts/dev.sh backend`；可用时直接复用，异常时根据提示中的 PID 找到并停止旧进程。 |
| 能登录，但部分业务页面无数据 | 基础脚本只提供账号/档案/坐标；若要展示不同阶段工单，使用[独立演示数据](docs/demo-data.md)。 |

## 在这台电脑上启动

日常使用请双击根目录的 `start-local.command`。下面是排错时的传统手动启动方式，**以下命令均从项目根目录开始**；打开两个终端，分别运行：

```bash
# 终端 1：先启动 MySQL，再以前台方式启动后端
./.runtime/start-mysql.sh
ENV_FILE=.runtime/backend.env ./scripts/dev.sh backend

```

```bash
# 终端 2：启动前端
./scripts/dev.sh frontend
```

打开 <http://127.0.0.1:5173/login>。访问 <http://127.0.0.1:8080/api/health> 检查后端与数据库；响应中的 `application` 和 `database` 均应为 `UP`。本机使用 `.runtime/backend.env` 指向现有 MySQL；它与 `backend/.env` 可能连接不同端口或使用不同 JWT 密钥，不要混用。修改后端源码后停止旧后端，再运行上面的启动命令即可，不需要重新打包 JAR。

`.runtime/` 包含本机路径和私有配置，已被 Git 忽略；其他电脑请使用上面的「在新电脑上从零启动」。

## 停止项目进程

一键启动的服务直接双击 [stop-local.command](stop-local.command)；它只会停止 `.run/` 记录且核对为本项目的前后端进程，MySQL 保持运行。也可在仍打开的启动窗口按 `Ctrl+C`。手动启动的服务分别在原终端按 `Ctrl+C`；`dev.sh` 检测到端口已有服务时只会报告并复用，运行该命令的终端不会控制原进程。找不到原终端时，再按下面步骤确认进程归属后停止。

在 macOS/Linux 上，从项目根目录检查监听端口；后端若设置了 `SERVER_PORT`，将 `8080` 换成实际端口：

```bash
lsof -nP -iTCP:5173 -sTCP:LISTEN  # 前端 Vite
lsof -nP -iTCP:8080 -sTCP:LISTEN  # 后端 Spring Boot
```

记下输出中的 `PID`，逐个运行 `ps -p PID -o pid,ppid,command`，**确认命令及路径确实属于本项目**，再分别执行 `kill PID`（把 `PID` 替换为查到的数字）。`kill` 默认发送正常退出信号，不要用 `kill -9`、`pkill java`、`pkill node` 或按端口盲目批量结束，以免影响其他项目。若前端或后端由 npm/Maven 父进程启动，子进程退出后父进程仍在，可根据 `PPID` 再核对并停止对应的本项目父进程。

**仅在这台电脑**，若还要关闭 `.runtime/start-mysql.sh` 启动的独立 MySQL（端口 `13306`），先确认没有其他任务使用它，再执行：

```bash
"$HOME/.cache/campus-repair/tools/mysql-8.4.9-macos15-arm64/bin/mysqladmin" \
  --defaults-extra-file="$PWD/.runtime/mysql-root.cnf" shutdown
```

这条命令通过本机私有配置正常关闭数据库，不会删除数据。其他电脑上的 Homebrew、系统服务或共享 MySQL，应使用各自的服务管理方式；只想停止本项目页面时，无需关闭 MySQL。最后再次运行上面的 `lsof` 命令检查 `5173`、`8080`；如关闭了本机独立数据库，也检查 `lsof -nP -iTCP:13306 -sTCP:LISTEN`。没有输出即表示相应端口已不再监听。

## 项目结构与技术栈

```text
backend/   Spring Boot API、认证授权、业务服务、MyBatis Plus、Maven Wrapper
frontend/  Vue 页面、路由、Pinia 状态、接口封装、公共组件
sql/       数据库初始化、增量迁移和本地演示数据
docs/      架构、API、数据库、流程与答辩资料
scripts/   开发启动入口与独立演示库准备工具
```

后端使用 Java 17、Spring Boot 3.5.16、Spring Security、MyBatis Plus 3.5.17、MySQL 8、Validation 和 Lombok；前端使用 Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router 和 Axios。图表由本地 SVG/CSS 绘制，地图使用本地图片底图与 SVG 交互标记，不需要地图密钥。后端按 `controller → service → mapper` 分层，派单评分位于独立 `algorithm` 模块。

学生只能访问自己的工单，维修人员只能处理当前分配给自己的任务，管理员负责审核和派单。JWT 用于身份认证，订单状态由后端控制。通知在核心事务提交后独立写入；写入失败会记录日志，不回滚已完成的业务。

## 功能与资料

- 学生：五步报修、私有图片、我的工单、事件时间线、驳回重提、预约确认、验收返工、评价。
- 维修人员：工作台、接单或拒单、预约、维修记录、返工处理、工单沟通。
- 管理员：审核、人工或可解释智能派单、收回重派、账号及基础资料维护、数据驾驶舱、Excel报表导出、校园示意地图、沟通记录查看。
- 共用：登录与三角色权限、本人修改密码、分页通知、待接单/待开工/维修超时扫描、统一错误和参数校验。

智能派单按技能 40%、距离 30%、当前负载 20%、历史评价 10% 计算，管理员确认后生效。地图使用虚构校园导览底图，工单按楼栋名称匹配示意位置，维修员按静态配置匹配楼栋；不显示真实地址或经纬度，不代表实时位置。详见[地图素材说明](docs/campus-map-asset.md)。

详细资料：[系统架构与流程](docs/architecture.md) · [API](docs/api-reference.md) · [数据库和迁移](docs/database.md) · [答辩演示](docs/defense-guide.md) · [最终加固与验证](docs/final-hardening-review.md)。

### 管理员导出报表

管理员登录后进入 `/admin/dashboard`，在页头选择「工单统计」「维修效率」或「故障分析」，点击「导出报表」。生成期间禁止重复点击；失败会显示提示并允许重试。下载文件为包含校园日期的 `.xlsx`，使用 Apache POI 生成，具有中文表头、自动列宽、冻结首行和「口径说明」工作表。

报表读取全量现有业务数据，不修改数据库，也不受 Dashboard 最近14天趋势范围限制。维修效率的完成量、平均评分、当前任务数以及故障数量和占比直接复用 Dashboard 统计。维修时间按已完成维修轮次计算，缺少有效时间或评分时留空；详细字段及边界见 [报表接口说明](docs/api-reference.md#管理员报表导出)。无需额外数据库迁移。

## 构建与限制

需要构建时，分别在 `backend/` 运行 `./mvnw -DskipTests package`，在 `frontend/` 运行 `npm run build`。本轮交付验证和待人工验收项见 [产品验收记录](docs/product-acceptance.md)；历史加固证据见 [最终加固记录](docs/final-hardening-review.md)；不要将服务端渲染检查当作浏览器视觉验收。

工单沟通在页面可见时每15秒增量刷新，通知支持分页、未读筛选与全部已读；地图无实时定位或路线导航。每张工单和维修记录各支持一张可选图片。通知使用有界后台队列，写入失败会记日志，但没有持久重试队列；进程退出或队列拒绝可能漏通知。登录失败限流仅在当前进程内生效。生产部署还需要真实校园坐标、HTTPS、独立环境配置、数据库与图片目录备份，以及人工浏览器验收。
