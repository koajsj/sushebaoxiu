# 校园智能报修管理系统

基于 Vue 3 和 Spring Boot 3 的本科毕业设计项目。学生提交报修后，管理员审核并确认人工或智能派单，维修人员处理工单，学生验收和评价。系统还提供预约、驳回重提、拒单重派、多轮返工、工单沟通、通知、数据驾驶舱和校园坐标地图。

## 在这台电脑上启动

这台电脑已有私有 MySQL 8 配置和 `.runtime/` 启动脚本。**以下命令均从项目根目录开始**；打开两个终端，分别运行：

```bash
# 终端 1：先启动 MySQL，再以前台方式启动后端
./.runtime/start-mysql.sh
./.runtime/start-backend.sh
```

```bash
# 终端 2：启动前端
cd frontend
npm run dev
```

打开 <http://127.0.0.1:5173/login>。访问 <http://127.0.0.1:8080/api/health> 检查后端与数据库；响应中的 `application` 和 `database` 均应为 `UP`。后端占用当前终端，按 `Ctrl+C` 停止；前端同理。端口已被占用时，先检查是否已有服务在运行，不要重复启动。

本机脚本依赖**已构建的** `backend/target/campus-repair-0.0.1-SNAPSHOT.jar`。首次启动或修改后端源码后，先停止旧后端，再从项目根目录执行：

```bash
cd backend
./mvnw -DskipTests package
cd ..
./.runtime/start-backend.sh
```

`.runtime/` 包含本机路径和私有配置，已被 Git 忽略；其他电脑请使用下面的通用步骤。

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
cd backend
set -a
source .env
set +a
./mvnw spring-boot:run
```

首次运行 Maven Wrapper 可能下载项目已有依赖。启动后访问 <http://127.0.0.1:8080/api/health>；如果数据库连接失败，后端会直接启动失败。macOS 上如有多个 JDK，可在启动前运行 `export JAVA_HOME=$(/usr/libexec/java_home -v 17)`。

### 4. 启动前端（终端 2）

```bash
cd frontend
npm ci
npm run dev
```

打开 <http://127.0.0.1:5173/login> 登录。`npm ci` 只需首次安装依赖或锁文件变化后运行；以后直接 `npm run dev`。前端开发服务器默认把 `/api` 代理到 `127.0.0.1:8080`；后端端口变更时，复制 `frontend/.env.example` 为 `frontend/.env` 并修改 `BACKEND_PROXY_TARGET`。不要把密钥写入任何 `VITE_` 变量。

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
| 后端提示缺少 `DB_PASSWORD` 或 `JWT_SECRET` | 确认已填写 `backend/.env`，并在**同一个终端**执行 `source .env` 后启动。 |
| `./mvnw` 使用了错误的 Java 版本 | 运行 `java -version`；macOS 可设置 `JAVA_HOME` 指向 Java 17。 |
| 页面可打开，但请求 `/api` 失败 | 先访问后端 `/api/health`，再检查前端代理目标是否指向后端实际端口。 |
| 运行 `npm run dev` 提示找不到 `vite` | 在 `frontend/` 执行一次 `npm ci`，再启动前端。 |
| 5173 或 8080 端口被占用 | 检查是否已有前端或后端进程；结束旧进程后再启动。 |
| 能登录，但部分业务页面无数据 | 确认结构脚本和三个本地演示数据脚本已按顺序执行。 |

## 项目结构与技术栈

```text
backend/   Spring Boot API、认证授权、业务服务、MyBatis Plus、Maven Wrapper
frontend/  Vue 页面、路由、Pinia 状态、接口封装、公共组件
sql/       数据库初始化、增量迁移和本地演示数据
docs/      架构、API、数据库、流程与答辩资料
```

后端使用 Java 17、Spring Boot 3.5.16、Spring Security、MyBatis Plus 3.5.17、MySQL 8、Validation 和 Lombok；前端使用 Vue 3、Vite、TypeScript、Element Plus、Pinia、Vue Router 和 Axios。图表及地图由本地 SVG/CSS 绘制，不需要地图密钥。后端按 `controller → service → mapper` 分层，派单评分位于独立 `algorithm` 模块。

学生只能访问自己的工单，维修人员只能处理当前分配给自己的任务，管理员负责审核和派单。JWT 用于身份认证，订单状态由后端控制。通知在核心事务提交后独立写入；写入失败会记录日志，不回滚已完成的业务。

## 功能与资料

- 学生：五步报修、私有图片、我的工单、事件时间线、驳回重提、预约确认、验收返工、评价。
- 维修人员：工作台、接单或拒单、预约、维修记录、返工处理、工单沟通。
- 管理员：审核、人工或可解释智能派单、收回重派、账号及基础资料维护、数据驾驶舱、校园坐标地图、沟通记录查看。
- 共用：登录与三角色权限、本人修改密码、分页通知、待接单/待开工/维修超时扫描、统一错误和参数校验。

智能派单按技能 40%、距离 30%、当前负载 20%、历史评价 10% 计算，管理员确认后生效。地图使用静态坐标，演示坐标不代表真实学校位置。

详细资料：[系统架构与流程](docs/architecture.md) · [API](docs/api-reference.md) · [数据库和迁移](docs/database.md) · [答辩演示](docs/defense-guide.md) · [最终加固与验证](docs/final-hardening-review.md)。

## 构建与限制

需要构建时，分别在 `backend/` 运行 `./mvnw -DskipTests package`，在 `frontend/` 运行 `npm run build`。最新的针对性验证和实际覆盖范围见 [最终加固记录](docs/final-hardening-review.md)；不要将服务端渲染检查当作浏览器视觉验收。

工单沟通在页面可见时每15秒增量刷新，通知支持分页、未读筛选与全部已读；地图无实时定位或路线导航。每张工单和维修记录各支持一张可选图片。通知使用有界后台队列，写入失败会记日志，但没有持久重试队列；进程退出或队列拒绝可能漏通知。登录失败限流仅在当前进程内生效。生产部署还需要真实校园坐标、HTTPS、独立环境配置、数据库与图片目录备份，以及人工浏览器验收。
