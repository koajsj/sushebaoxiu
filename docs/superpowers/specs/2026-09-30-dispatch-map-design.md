# Phase 4 智能派单与校园坐标地图

## 范围与兼容
保留 Phase 1~3 JWT、SecurityConfig、OrderStatus、人工 assign/accept 与完整闭环。新增独立 DispatchAlgorithm、DispatchService、MapService 与薄 Controller。无新依赖，无 GUI 自动化，无 Phase 5 功能。
人工派单继续 WAIT_ASSIGN → 维修员 accept → ASSIGNED；新智能确认接口由管理员明确确认，使用已有 WAIT_ASSIGN → ASSIGNED 边，记录 ASSIGN 事件，维修员随后 start。不更改状态枚举或现有接口。
worker.task_count 是累计完成量，不能用作当前负载；当前负载从已指定 WAIT_ASSIGN、ASSIGNED、PROCESSING 订单实时聚合，响应命名 activeTaskCount。

## 可解释评分
所有分项 0~100，总分四舍五入两位。技能按故障名称/描述关键词映射照明电工、给排水、门窗家具、空调设备；精确故障名称或领域标签匹配100，综合维修70，未知领域或其他问题50，不匹配0。词条与结果均写明原因。
距离使用 WGS84 静态坐标 Haversine 公里，distanceScore=100/(1+distanceKm)，缺少/非法坐标为0并明确提示，不编造距离。
loadScore=100/(1+activeTaskCount)。历史均分1~5映射 ratingScore=score*20；无评价(score=0)中性50。总分=0.4技能+0.3距离+0.2负载+0.1评价。排序总分降序、距离升序、workerId升序，无随机。
只推荐启用的 WORKER 账号与启用 worker 档案。没有可用人员返回空列表。推荐仅针对无维修员的 WAIT_ASSIGN。

## 快照与确认
新增 dispatch_record，保留每次推荐所有候选分项、原因、批次与确认标记。原因包含技能输入、坐标、当前负载与历史分等原始输入，支持论文追溯。
GET 推荐生成独立批次写入记录，不修改订单。POST 确认携带 orderId/workerId/recommendationId，校验记录归属与未确认、10分钟有效期。订单和维修员行锁、READ_COMMITTED事务；重新计算选定候选，原始输入说明或任何分项变化返回409重新推荐。写确认快照、worker_id、ASSIGNED与ASSIGN事件原子提交。重复/并发确认仅一笔成功，禁用人员不可派单。前端不传分数。

## 坐标与权限
worker新增可空longitude/latitude静态服务位置。building已有坐标保留；独立dev脚本仅为两项均空的开发样例设置明确示例坐标，不覆盖实际数据。地图服务三个GET接口 orders/workers/buildings 仅 ADMIN（服务显式校验，复用现有权限链）；不向学生或维修员暴露全校任务或人员位置。
订单地图可状态筛选，返回最多500条与总数及截断提示，包含缺少坐标订单便于说明。人员地图仅启用人员，返回静态坐标与从当前任务推导的工作状态，无GPS。

## 前端
/admin/dispatch 左订单队列/信息右推荐卡，候选分项/权重/评分动画/原因/确认按钮，确认后清空旧快照并反馈。支持路由query.orderId从详情直达，保留人工派单详情入口。
/admin/map 大地图与侧边详情，SVG等距经纬度投影、不加载在线瓦片、无密钥。建筑与工单坐标共享位置时展开标记并用引线连接实际点；支持筛选、缩放、平移、复位和键盘点击。明确“示例校园 · 静态坐标”，无坐标不渲染Marker并提示。ScoreDisplay/WorkerRecommendCard/MapPanel/LocationCard复用；尊重prefers-reduced-motion，清理动画帧。

## 验收
JUnit算法A>B、权重/边界/空坐标/中性评价/技能词条与距离。隔离数据库真实HTTP推荐快照/越权/并发确认/输入变化/派单后工人start→record→finish→confirm→evaluate、旧人工闭环回归。前端构建、SSR、真实API与Pinia/router回归、地图投影/选中详情程序化组件验证；不用 GUI，真实浏览器视觉/点击留人工验收。
