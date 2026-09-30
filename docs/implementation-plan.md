> Phase 1 历史实施记录，非最终版本规范。原始设计见 [Phase1架构](phase1-architecture.md)；最终设计见 [当前架构](architecture.md)。

# 基础工程实施计划

**目标：** 完成本次需求限定的第一阶段工程基础。

**设计依据：** [architecture.md](architecture.md)。工作区为空且未初始化 Git，没有已有代码可重构。

## 后端与数据库

- [x] 创建 Maven 工程和 Maven Wrapper，锁定 Java 17、Spring Boot 3.x、MyBatis Plus、MySQL JDBC、Lombok、Validation。
- [x] 先建立统一响应、异常处理的请求级测试，验证缺失实现时失败，再实现 common、全局异常处理及配置。
- [x] 创建分层目录，健康 Controller → Service → `DatabaseProbeMapper.checkConnection(): int`，仅执行 `SELECT 1`。
- [x] 建立 application.yml、环境变量样例、MySQL 8 初始化 SQL 和日志/CORS/分页配置。
- [x] 对正常响应、业务失败、校验失败、非法 JSON、未知异常、404、405、类型转换、缺失参数进行测试；真实启动检查数据库和 CORS。

## 前端

- [x] 创建 Vue 3 / Vite / TypeScript 工程，安装指定依赖，提交锁文件（当前无 Git，只生成文件）。
- [x] 创建 Axios 封装、响应/角色类型、Router 和仅含导航折叠状态的 Pinia Store。
- [x] 创建校园科技风登录表单，本地校验后提示未接入登录；提供与账号表单独立的布局预览入口。
- [x] 建立共享布局组件及 StudentLayout、WorkerLayout、AdminLayout，导航不伪装业务能力。
- [x] 执行类型检查和生产构建；终端验证 dev HTTP、四个页面的 Vue SSR 输出及渲染警告。

## 交付

- [x] README 记录目录、技术栈、环境、初始化、启动方式、验证事实与未实现范围。
- [x] 核查未实现任何禁止模块，确认密码不进入源码、日志与返回内容。

执行决策：需求已明确并授权自主处理，按任务顺序在当前会话实施，不增加设计审批步骤。使用独立 MySQL 8 验证实例，不修改本机 MySQL 9.7 服务。

完成记录：Maven Wrapper verify 12/12 测试通过；MySQL 8.4.9 库字符集及零业务表确认；正常连接、断开返回 503、恢复返回 200、连接失败启动退出均验证；前端安装、严格类型检查、生产构建、dev HTTP、六路由 SSR 渲染通过。只读代码审查未发现需要修复的重要问题。浏览器视觉/交互/控制台验收未进行，遵守无 GUI 自动化约束。
