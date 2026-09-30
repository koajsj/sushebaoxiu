# Phase4 实现与验证记录

日期：2026-09-30（Asia/Shanghai）。本轮遵守“尽量少安装东西”“不使用Computer Use/GUI自动化”“不要运行长时间命令和测试”。没有新增项目依赖或安装工具。所有检查分别设置6～12秒上限；数据库短命令设置2～5秒上限。

## 已实现

独立多因素DispatchAlgorithm、推荐候选快照、管理员事务确认、保留人工指定/维修员接单。ADMIN专用地图接口与SVG校园坐标地图；推荐卡、评分动画、地图层/筛选/缩放/平移/键盘选择/详情卡、错误和无数据处理。详见README第9节与设计/执行计划。

## 数据保护

主库campus_repair迁移前通过既有MySQL8客户端保存私有备份 `.runtime/phase3-before-phase4.sql`（600权限）。JWT/数据库配置仍为600，未写入可分享文件。

新增dispatch_record表（候选/确认快照、批次、各分项/总分/说明/时间），worker新增可空longitude/latitude。主库与隔离库campus_repair_phase4_check_20260930都应用phase4.sql与dev-dispatch.sql，重复执行后所有11表逐表内容哈希一致。原user表结构和原业务表未更改；开发示例新增worker002/003，已有密码/角色/任务数据不覆盖，真实坐标不覆盖。

应用仅新增dispatch_record的INSERT权限，读取沿用SELECT；未授予用户密码写权限或DDL权限。业务测试全部使用18084隔离后端；主库保留此前Phase3验收订单，未在主库新增本轮验收工单。

## 实際验证结果

| 检查 | 结果与耗时 | 证据 |
| --- | --- | --- |
| Maven离线package，针对性JUnit | 14项全通过，编译/打包成功，4.622秒 | .runtime/phase4-package-final.log |
| 算法 | 11项：A>B、权重、技能、Haversine、空/非法坐标、无评价、负载及快照原始输入 | 同上 |
| 派单服务边界 | 3项：行锁后重校有效期、各分项校验、秒精度不得生成未来时间 | 同上 |
| 后端实际HTTP/SQL | 46项通过，2.47秒；完整智能派单后维修/确认/评价 | .runtime/phase4-http.log |
| 前端实际API+Pinia+Router | 10项通过，3.38秒；角色隔离、确认、地图、刷新登录、维修、评价、退出 | .runtime/phase4-frontend-http.log |
| 地图纯函数 | 坐标合法性、东西/南北投影、重合点分离、空/单点、平移边界通过 | .runtime/phase4-map-green.log |
| 生产地图组件事件 | Vue内存Renderer：标记数量、同址位置、真实click/Enter处理与选中状态，SSR正确详情卡；无Vue警告 | .runtime/phase4-map-components.log |
| 生产派单页面函数 | 4项通过：旧响应不得覆盖当前订单，失败刷新清空推荐，确认成功后刷新失败正确反馈，冲突清空旧快照 | .runtime/phase4-dispatch-views.log |
| 页面SSR | 15项通过，包括/admin/dispatch、/admin/map；无Vue/console警告 | .runtime/phase4-render.log |
| 前端最终生产构建 | TypeScript检查与Vite构建成功，无编译警告 | .runtime/phase4-build-final.log |
| 地图容量边界 | 隔离库501临时订单返回500项与真实总数；临时行finally已删除 | 本轮命令结果 |
| 运行状态 | 主后端8080健康接口application/database均UP；5173登录/派单/地图返回HTML 200 | 本轮最后HTTP检查 |

后端46项覆盖无token401、学生/维修员403、未审核不可推荐、候选排序与权重、每候选持久化、每次新批次、快照与人员归属、账号/档案禁用、坐标变化、推荐过期、累计完成量不作为负载、同一订单并发确认仅一次成功、唯一确认记录/事件、任务出现在指定人员列表、原manual assign/accept、静态坐标来源、状态筛选、缺失坐标不伪造及评分明确说明。

## 审查与修复

完成一次独立只读源码/SQL审查，未运行测试或GUI。两项P2均通过失败检查再修复：评分使用的故障类型说明加入原始输入快照；取得所有行锁后重新检查10分钟有效期。额外加入各分项逐一比较。

真实HTTP首次并发检查发现MySQL DATETIME(0)将毫秒四舍五入，新推荐可能短暂晚于当前时间而被拒。数据库CAST与JUnit已复现；DispatchService统一截断至秒后14项针对性检查和46项HTTP全通过。并未修改JWT或核心报修状态枚举。

## 未验证与限制

- 本轮没有执行完整Maven verify、原Phase1～3全量单元/HTTP回归、图片上传回归或压力测试。旧Phase3测试记录仅作历史证据；本轮实际执行的是上述短检查。
- 没有实际浏览器或GUI验收。SSR、内存Renderer事件、API检查不代表视觉、触摸拖动、动画流畅度或真实浏览器控制台已经通过。
- 地图是用户选择的本地校园坐标地图，使用开发示例WGS84坐标；不是在线瓦片地图、实时GPS或路线导航。需要配置实际校园/人员静态服务坐标。
- 订单地图最多显示500项，并显示总数和筛选提示；人员/建筑没有额外行政维护页面。
- dispatch_record会保存每次候选结果及确认快照；未额外实现分析Dashboard、导出/清理任务或记录管理模块。
- 智能确认直接到ASSIGNED，工人可开始维修；旧人工指定仍保留接单环节。管理员确认的两种入口区别在页面说明和README中。
- Phase5数据分析Dashboard、工单聊天、通知增强、AI模型与实时定位均未实现。

下一步：人工浏览器验收与实际WGS84坐标配置；在需要且获准运行较长检查时补完整回归，然后按既定Phase5需求推进。
