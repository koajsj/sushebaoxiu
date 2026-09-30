> 本文记录 Phase 1 验证时的状态，不代表当前功能范围。Phase 2 新增认证验证见 [phase2-validation.md](phase2-validation.md)。

# 第一阶段验证记录

验证日期：2026-09-30（Asia/Shanghai）。使用终端、HTTP 和 Vue SSR，无 Computer Use 或 GUI 自动化。

## 后端与数据库

| 验证 | 实际结果 |
| --- | --- |
| Java | 实际使用 Java 17.0.14，编译目标 Java 17 |
| Maven | `./mvnw -B verify`：BUILD SUCCESS，12 项测试，0 失败/错误/跳过 |
| 编译警告 | 开启 `-Xlint:all,-processing` 与 `-Werror`，编译成功，未见 Maven 编译警告 |
| Spring Boot | 3.5.16 实际启动；Tomcat 监听 `127.0.0.1:8080` |
| 数据库 | 独立 MySQL 8.4.9，`127.0.0.1:13306`，未修改已有 MySQL 9.7 服务 |
| 初始化 | `sql/init.sql` 已执行并重复执行成功；`campus_repair` 存在 |
| 字符集 | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| 业务表 | `information_schema.TABLES` 确认表数量为 0 |
| 连接 | 应用启动日志包含 `Database connection verified`，健康接口通过 Mapper 执行 `SELECT 1` |
| 健康接口 | GET `/api/health` HTTP 200，应用和数据库均为 UP |
| 错误响应 | 未知 API 404，不支持方法 405（保留 Allow 头），均为统一响应 |
| 跨域 | 开发来源 preflight 200，返回明确来源且无 credentials；未授权来源 preflight 403 |
| 数据库断开 | 主动关闭独立实例后，健康接口返回 503 和 `数据库暂时不可用`；没有 SQL/JDBC/堆栈细节 |
| 数据库恢复 | 重启独立实例后，健康接口恢复 200 / UP，无需重启应用 |
| 启动失败 | 在单独的 18081 端口，以不可用数据库端口 13307 启动：退出码 1，启动检查失败，未保留该应用进程 |

12 项测试覆盖：成功响应、业务异常、DTO 校验失败及成功、非法 JSON、系统错误不泄露内部细节、数据库不可用的响应映射、405、404、非法路径参数、缺失参数、415。测试辅助 Controller 仅存在于测试代码，不进入应用。

健康接口实际响应：

```json
{"code":0,"message":"成功","data":{"application":"UP","database":"UP"}}
```

数据库断开实际响应：

```json
{"code":50300,"message":"数据库暂时不可用","data":null}
```

数据库故障注入产生预期 WARN/ERROR 日志，不能将这些人为制造的故障误报为正常启动错误。正常首次启动未见非预期应用错误。

## 前端

| 验证 | 实际结果 |
| --- | --- |
| 安装 | `npm install` 退出码 0；103 个依赖包（含传递依赖），安装审计 0 vulnerabilities |
| 类型检查 | `npm run typecheck`，严格 TypeScript 校验通过 |
| 生产构建 | `npm run build` 成功，未见编译或 chunk 体积警告 |
| 开发启动 | `npm run dev` 正常启动，Vite 监听 `127.0.0.1:5173` |
| 页面与模块 HTTP | `/login`、`/student`、`/worker`、`/admin`、main.ts、LoginView、LayoutShell、router 和 favicon 均为 200 |
| 开发代理 | 前端域名 `/api/health` 返回后端健康响应，数据库 UP |
| Vue 渲染 | `npm run check:render`：根路径重定向、登录、三个角色页面、404，共六个路由 SSR 渲染成功 |
| 渲染输出 | 检查对应标题、密码字段、账号字段、独立预览链接，未出现无效对象文本 |
| 警告 | 六个路由未产生 console/Vue SSR 警告；dev 服务日志未见错误 |

SSR 检查复用 Vue 自带的服务端渲染入口和已安装的 Vite，没有增加浏览器、模拟 DOM 或测试框架。检查脚本按 [Element Plus 官方 SSR 说明](https://element-plus.org/zh-CN/guide/ssr) 注入 ID 和 ZIndex，仅服务于终端验证；工程运行方式仍为 Vite 客户端应用。

## 审查与边界

- 独立只读审查已检查源代码、配置、测试、SQL、环境变量样例和忽略规则，未发现需要修复的重要功能问题、凭据泄漏或超范围实现。
- 未进行浏览器中的真实视觉、交互和控制台验收。响应式 CSS 和 SSR 成功不能代替实际浏览器验证。
- 未验证生产部署、正式环境 TLS、真实用户权限、业务模块和业务数据；这些没有在本阶段实现。
- 当前 UI 中三个角色为公开布局预览，未伪装为已完成认证或权限控制。
- 本机私有运行配置和进程日志在 `.runtime/`，Maven/前端构建日志在 `/Users/wyc/.cache/campus-repair/`，均不作为业务源码交付。
