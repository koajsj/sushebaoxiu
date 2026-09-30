# Phase 2 用户认证与权限设计

目标为真实账号登录、三角色隔离、刷新恢复与退出闭环，保留 Phase 1 分层和健康接口，不实现 Phase 3 业务。

## 数据与接口
- `user` 表：id、username（唯一、区分大小写）、password（BCrypt）、real_name、phone、role（STUDENT/WORKER/ADMIN）、status（1启用/0禁用）、create_time、update_time；增加 token_version 用于持久化撤销登录凭证。
- Entity/Mapper/UserService/UserController 分层；password 不进入响应对象，登录 DTO 不自动生成包含密码的 toString。
- POST /api/auth/login 输入 username/password，返回 token、expiresAt、user、role。
- GET /api/users/me 返回当前用户。GET /api/student/me、/api/worker/me、/api/admin/me 仅验证对应角色身份，不返回业务数据。
- POST /api/auth/logout 原子递增 token_version，撤销该账号此前所有 token，前端成功后清除本地登录状态。

## 认证
- Spring Security 无状态安全链；JWT 使用 Spring Security JOSE/Nimbus 的 HS256 签名与校验，不自行实现加密。
- JWT_SECRET 为必填随机 Base64 密钥，解码后至少32字节；issuer固定、exp必填、有效期默认2小时；拒绝过期、伪造、错误issuer/算法及错误用户版本。
- 每次带 token 请求查询用户，禁用/删除/角色变化即时生效。ROLE_STUDENT/ROLE_WORKER/ROLE_ADMIN 按路径隔离，无管理员跨端隐式豁免。
- 健康接口与登录公开，其他接口需登录；认证401、越权403保持统一 Result。CORS 保持精确来源并增加 Authorization 头；不使用 cookie/session。
- 相同登录错误避免透露账号是否存在；未知账号也执行 BCrypt 检查；禁用用户不给token。

## 前端
- 视觉参数：DESIGN_VARIANCE=5 / MOTION_INTENSITY=3 / VISUAL_DENSITY=2，服务入口优先清晰、留白和克制反馈；使用Apple风格网页视觉，不宣称官方原生材料。
- 保留 Vue/Element Plus，零新增前端依赖；浅色 Apple 风格网页，系统字体、蓝色强调、宽留白、自然 CSS 动画。
- Pinia 维护 token/user/恢复状态；localStorage 仅存 token（用户角色保存在 Pinia，每次刷新重新获取可信用户信息），密码不持久化。
- Axios 自动带 Bearer，401使会话失效；路由守卫等待恢复，未登录进入 /login，角色不匹配进入自身首页。
- 登录失败/加载/退出失败均有反馈；退出失败不假称服务端已撤销，可重试。
- 不添加注册、密码重置、用户管理、刷新token、业务仪表盘。

## 验收边界
真实 MySQL8/HTTP验证账号密码、JWT与3×3权限、禁用/撤销；Java17 Maven全套测试；前端类型/构建、Node中真实Pinia/路由/Axios代码的状态流程与SSR渲染。遵守禁止GUI自动化，因此浏览器实际视觉、鼠标交互及浏览器控制台保留为人工验收。
