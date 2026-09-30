> 本文为 Phase 1 历史设计。当前认证与页面设计见 [Phase 2 设计](superpowers/specs/2026-09-30-auth-design.md) 和根目录 README。

# 第一阶段：基础工程

范围以本次用户需求为准：只完成初始化、配置、工程规范和基础验证，不提前实现业务模块。

## 结构与接口

- `backend`：Java 17 / Spring Boot 3.x Maven 单模块，根包 `com.campus.repair`，按 common、config、controller、service、mapper、entity、dto、vo、utils、security 分层。
- `frontend`：Vue 3 / Vite / TypeScript，Element Plus 表单和导航，Pinia 只存放非敏感 UI 状态，Vue Router 提供登录及三个角色的布局预览。
- `sql/init.sql`：幂等创建 `campus_repair`，使用 MySQL 8 的 `utf8mb4` 字符集，不创建业务表或种子业务数据。
- `GET /api/health`：统一响应 `{code,message,data}`；通过 service 和 MyBatis mapper 执行 `SELECT 1`，成功返回应用、数据库状态。数据库不可用时 HTTP 503，隐藏数据库错误细节。
- 启动时检查数据库连接并失败退出，避免把未连接数据库的启动判为成功。

## 基础规范

- 环境变量提供连接信息，无仓库内密码；SQL 由开发者显式执行，应用不自动建库或改表。
- 异常处理覆盖业务异常、参数校验、请求格式、404、405 和系统异常；客户端不返回堆栈及原始内部异常信息。
- Controller 只负责 HTTP 协议，健康检查由 service 执行；空层保留 package-info 或 .gitkeep。
- 跨域来源可配置，开发默认 localhost / 127.0.0.1:5173，不启用携带凭据或通配来源。
- 登录 UI 只进行本地必填校验和提示，不请求登录接口、不保存密码、不创建 token、不实施路由鉴权。
- 三角色页面明确标记为布局预览，只保留导航和空状态。
- 页面采用深蓝、青绿色、中文系统字体，桌面与移动布局，低动效及键盘焦点样式。

## 验证边界

Maven 编译与异常处理测试、Java 17 启动、真实 MySQL 8 连接和 HTTP/CORS 验证；npm 安装、TypeScript、生产构建、Vite dev HTTP 与 Vue 服务端渲染检查。按用户要求不使用 Computer Use 或 GUI 自动化；不把服务端渲染等同于浏览器视觉验收。
