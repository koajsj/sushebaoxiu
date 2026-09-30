# Phase 4 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 可解释推荐、管理员确认及校园坐标地图，保持核心闭环。
**Architecture:** 纯算法 + 独立事务Service + 现有认证与组件。
**Tech Stack:** 现有 Java17/Spring Boot/MyBatis Plus/MySQL/Vue3/TypeScript/SVG；零新增依赖。
**Spec:** docs/superpowers/specs/2026-09-30-dispatch-map-design.md

## Global Constraints
- 保留人工派单、JWT、状态枚举与原有接口。
- 不安装工具、不使用GUI、不实现Phase5。
- 仅ADMIN访问地图和推荐，所有评分后端生成。

## Review Focus
- 重复与并发确认仅一次成功；事务失败不留下部分订单或确认记录。
- 缺坐标、无历史分、负载变化、账号禁用清晰处理。
- 同坐标多Marker可逐个选择；无坐标不出假Marker。
- 切换订单或刷新失败不得使用旧推荐提交。
- 迁移重复执行不覆盖已有用户/业务/真实坐标。

### Task 1: 独立评分算法
Files: algorithm/DispatchAlgorithm.java, test/algorithm/DispatchAlgorithmTest.java
Produces: Input(typeName,description,skillType,orderLongitude,orderLatitude,workerLongitude,workerLatitude,activeTaskCount,rating), Scores(skillScore,distanceScore,loadScore,ratingScore,totalScore,distanceKm,reason)
- [x] 写A>B、权重、缺坐标、技能、Haversine与负载测试，运行RED。
- [x] 实现纯算法，运行JUnit GREEN。

### Task 2: 增量模型与接口
Files: sql/phase4.sql, sql/dev-dispatch.sql; entity/WorkerEntity.java, DispatchRecordEntity; DispatchRecordMapper; DispatchService/MapService; DispatchController/MapController; DTO/VO。
Consumes: Task1算法与已有OrderAccessService/Mapper。
Produces: recommendList with recommendationId; confirm(orderId,workerId,recommendationId); map orders/workers/buildings。
- [x] 写隔离HTTP检查（401/403、快照、过期、输入变化、两次确认、禁用、完整智能闭环），未实现接口RED。
- [x] 实现坐标增量迁移、快照、行锁确认与地图权限/截断。
- [x] 隔离数据库迁移与HTTP GREEN，登录/权限/维修/评价关键回归；不重跑旧全量回归。

### Task 3: 复用组件与页面
Files: types/dispatch.ts, api/dispatch.ts; ScoreDisplay/WorkerRecommendCard/MapPanel/LocationCard; DispatchView/MapView; router/LayoutShell/OrderTimeline/OrderDetailView targeted links; dispatch-map.css。
Consumes: Task2API；map projection纯工具。
Produces: /admin/dispatch与/admin/map。
- [x] 先写projection检查并验证缺少实现RED；再补重合点/实际Vue选择/详情/SSR和路由检查。
- [x] 实现双栏推荐、评分动画、SVG地图、状态筛选/加载/错误/刷新/无数据。
- [x] typecheck/build、SSR与前端API流程GREEN。

### Task 4: 收尾验证与报告
- [x] 依用户最新限制，执行12秒上限的Maven针对性package、8～10秒上限的短回归，迁移幂等、秘密与警告检查；不执行完整verify和全量旧回归。
- [x] 独立代码审查与必要修复验证。
- [x] 更新README、Phase4验证记录/评分规则/边界/启动说明；报告仅实际验证。

## Execution ledger
无Git仓库，原工作区直接增量修改，不创建Git、不提交/重构。用户明确授权实施，按developer自主执行要求不增加设计审批环节。使用隔离数据库验证；主库迁移前私有备份。

Task 1: 测试预期算术修正：100×.4+100×.3+50×.2+80×.1=88，首次误写90；算法未修改。

Task 1: complete — DispatchAlgorithmTest 10/10 GREEN。

Task 2: complete — 主库私有备份；两库11表迁移/示例脚本重复执行完全不变；46 HTTP/SQL检查2.47秒通过。ADMIN权限、禁用/过期/变化、并发一成功一409、完整智能闭环与人工assign/accept验证。
Task 3: complete — 地图纯投影与实际Vue内存Renderer点击/键盘/详情验证；4项真实页面函数竞态/失败检查；15 SSR页面无警告；10前端实际Pinia/Router/API检查3.38秒通过。最终生产构建成功。
Task 4: complete within current user verification boundary — JUnit11算法+3服务边界=14/14，Maven离线package4.622秒；独立静态审查两项P2均RED→GREEN修复；额外MySQL DATETIME(0)四舍五入误拒推荐已复现并统一秒精度。501临时订单验证地图500上限，临时数据已删除。无新增依赖。

## Rulings and limits
- 用户“不要运行长时间命令和测试”优先于最初完整回归计划：只执行每项6～12秒上限短检查；完整verify、原30项单元测试与Phase1～3全量认证/图片回归本轮未重跑。
- 无GUI；实际浏览器视觉、鼠标拖动、动画性能与控制台留人工验收。内存Renderer验证生产事件处理函数，不计作浏览器验收。
- 保留累计task_count，当前负载独立聚合；智能确认直接ASSIGNED，原人工指定仍需accept。状态枚举与JWT未改。
- SQL示例脚本首次遇到同表INSERT SELECT列名歧义；源表别名消除歧义，最终脚本事务化且两库幂等验证通过。
- 独立审查P2：类型说明未追踪、人员行锁等待后过期；均已验证修复。无遗留高/中审查问题；未做第二次独立审查。
