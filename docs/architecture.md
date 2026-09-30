# 最终系统架构与毕业设计说明

以当前Phase1～6源码为准，保留单后端、单前端、三角色及既有状态机。所有图均为文档中的Mermaid源，可在支持Mermaid的Markdown阅读器中查看或导出，不安装绘图工具。

## 系统架构图

```mermaid
flowchart LR
    Browser[Vue3 / TypeScript 页面] --> Router[Router Guard / Pinia]
    Router --> Axios[Axios / Bearer Token]
    Axios --> Security[Spring Security / TokenFilter]
    Security --> Auth[JWT校验 / 数据库账号状态与角色]
    Security --> Controller[Controller / DTO校验 / Result]
    Controller --> Service[Service / 订单归属 / 事务与行锁]
    Service --> Algorithm[DispatchAlgorithm 四因素评分]
    Service --> Mapper[MyBatis Plus / 聚合SQL]
    Mapper --> DB[(MySQL8 / 13张表)]
    Service --> Files[(私有图片目录)]
    Service --> Events[订单事件与通知]
    Events --> Mapper
```

登录是唯一公开认证入口，健康检查公开；其他接口需JWT。角色路径由Security限制，订单、聊天、图片、通知进一步按数据归属验证。角色以数据库当前值为准，禁用账号或token_version变化可拒绝旧令牌。客户端角色与路由保护负责用户体验，不能代替服务端权限。

响应统一为`{code,message,data}`，图片为受限二进制响应。分页最大100，聊天最近100条、通知最近50条、地图最多500单。数据库事务包含状态、事件、图片绑定、必要通知等写入；失败整体回滚。

## 功能结构图

```mermaid
flowchart TD
    System[校园智能报修系统] --> Student[学生端]
    System --> Worker[维修人员端]
    System --> Admin[管理员端]
    System --> Shared[共用能力]
    Student --> S1[首页 / 五步提交 / 图片预览]
    Student --> S2[本人订单 / 时间线 / 确认评价]
    Worker --> W1[工作台 / 本人任务]
    Worker --> W2[接单 / 开始 / 记录 / 完成]
    Admin --> A1[审核 / 人工派单]
    Admin --> A2[智能推荐与确认 / 静态校园地图]
    Admin --> A3[数据驾驶舱 / 沟通只读]
    Shared --> C1[认证 / 权限 / 通知与已读]
    Shared --> C2[订单内学生与当前维修员文字沟通]
```

前端统一系统字体、蓝色强调、浅色留白、圆角和轻阴影；组件复用而非后台模板。路由按需加载；地图、图表使用SVG/CSS。通知与聊天没有轮询或WebSocket，提供手动刷新。

## ER图

```mermaid
erDiagram
    USER ||--o| STUDENT : profile
    USER ||--o| WORKER : profile
    BUILDING o|--o{ STUDENT : residence
    STUDENT ||--o{ REPAIR_ORDER : submits
    REPAIR_TYPE ||--o{ REPAIR_ORDER : classifies
    BUILDING ||--o{ REPAIR_ORDER : location
    WORKER o|--o{ REPAIR_ORDER : assigned
    REPAIR_ORDER ||--o{ REPAIR_RECORD : records
    WORKER ||--o{ REPAIR_RECORD : performs
    REPAIR_ORDER ||--o| EVALUATION : rated_once
    STUDENT ||--o{ EVALUATION : rates
    REPAIR_ORDER ||--o{ ORDER_EVENT : timeline
    USER ||--o{ ORDER_EVENT : actor
    USER ||--o{ REPAIR_IMAGE : uploads
    REPAIR_ORDER o|--o{ REPAIR_IMAGE : binds
    REPAIR_ORDER ||--o{ DISPATCH_RECORD : snapshots
    WORKER ||--o{ DISPATCH_RECORD : candidate
    REPAIR_ORDER ||--o{ CHAT_MESSAGE : conversation
    USER ||--o{ CHAT_MESSAGE : sender
    USER ||--o{ CHAT_MESSAGE : receiver
    USER ||--o{ NOTIFICATION : receives
    USER {
        bigint id PK
        string username UK
        string password
        string role
        int status
        bigint token_version
    }
    STUDENT {
        bigint id PK
        bigint user_id FK,UK
        string student_no UK
        bigint building_id FK
        string room_no
    }
    WORKER {
        bigint id PK
        bigint user_id FK,UK
        string skill_type
        decimal score
        int task_count
        int status
        decimal longitude
        decimal latitude
    }
    BUILDING {
        bigint id PK
        string name UK
        string type
        decimal longitude
        decimal latitude
    }
    REPAIR_TYPE {
        bigint id PK
        string name UK
        string description
    }
    REPAIR_ORDER {
        bigint id PK
        bigint student_id FK
        bigint type_id FK
        bigint building_id FK
        bigint worker_id FK
        string title
        string description
        string room_no
        string image_url
        string priority
        string status
        datetime create_time
        datetime update_time
    }
    REPAIR_RECORD {
        bigint id PK
        bigint order_id FK
        bigint worker_id FK
        string content
        string image_url
        datetime start_time
        datetime finish_time
    }
    EVALUATION {
        bigint id PK
        bigint order_id FK,UK
        bigint student_id FK
        int score
        string content
        datetime create_time
    }
    ORDER_EVENT {
        bigint id PK
        bigint order_id FK
        bigint actor_id FK
        string action
        string status
        datetime create_time
    }
    REPAIR_IMAGE {
        string id PK
        bigint owner_id FK
        bigint order_id FK
        string content_type
        datetime create_time
    }
    DISPATCH_RECORD {
        bigint id PK
        bigint order_id FK
        bigint worker_id FK
        decimal skill_score
        decimal distance_score
        decimal load_score
        decimal rating_score
        decimal total_score
        string reason
        string recommendation_batch
        int confirmed
        datetime create_time
    }
    CHAT_MESSAGE {
        bigint id PK
        bigint order_id FK
        bigint sender_id FK
        bigint receiver_id FK
        string content
        int read_status
        datetime create_time
    }
    NOTIFICATION {
        bigint id PK
        bigint user_id FK
        string title
        string content
        int read_status
        datetime create_time
    }
```

USER.password保存BCrypt哈希。ER图列出核心字段，完整字段以sql/*.sql为准。worker_id/坐标/未绑定图片的order_id可为空；每单评价唯一。关联删除被外键限制，当前没有通用删除业务接口。

## 核心流程图

```mermaid
flowchart TD
    Submit[学生提交及图片绑定] --> Audit[WAIT_AUDIT 管理员审核]
    Audit --> Wait[WAIT_ASSIGN 待派单]
    Wait --> Choice{管理员选择派单方式}
    Choice --> Manual[人工指定人员 / 状态保持WAIT_ASSIGN]
    Manual --> Accept[维修人员接单]
    Accept --> Assigned[ASSIGNED]
    Choice --> Recommend[真实数据评分 / 保存候选快照]
    Recommend --> Confirm[管理员确认 / 复核快照和当前数据]
    Confirm --> Assigned
    Assigned --> Response[维修员明确接受 / accepted_time]
    Response --> Start[PROCESSING 开始维修]
    Start --> Record[保存文字与图片维修记录]
    Record --> Finish[WAIT_CONFIRM 提交维修结果]
    Finish --> StudentConfirm[FINISHED 学生确认]
    StudentConfirm --> Evaluation[COMMENTED 一次评价]
    Assigned -.-> Chat[订单双方沟通 / 管理员只读]
    Submit -.-> Notify[提交 / 审核 / 派单 / 完成事务通知]
    Audit -.-> Notify
    Assigned -.-> Notify
    Finish -.-> Notify
    Evaluation --> Statistics[真实数据库统计与地图展示]
```

重复审核、派单、开始、完成、确认或评价在当前状态不匹配时返回409，不重复写事件/评价。时间线只展示真实事件，并按维修轮次折叠历史，不根据状态补造节点。

## 智能派单与创新点

`totalScore = skillScore × 0.4 + distanceScore × 0.3 + loadScore × 0.2 + ratingScore × 0.1`。

| 因素 | 规则 |
| --- | --- |
| 技能 | 精确故障类别/领域关键词100；综合维修70；其他或未知领域50；不匹配0 |
| 距离 | WGS84 Haversine公里距离，100/(1+距离km)；无合法坐标计0并解释 |
| 负载 | 100/(1+当前已分配未处理完任务数)，不使用累计task_count冒充负载 |
| 历史评价 | 真实历史均分×20；无评价中性50 |

候选按总分降序、距离升序、id升序确定性排序。每次推荐保存完整批次及解释；10分钟内确认并重新检查原始输入、各分项、可用状态和订单。人工决策保留，算法不直接越过管理员操作。

可作为本科设计创新点讨论：

1. **可解释的人机协同派单。** 评分规则透明，推荐记录支持追踪和论文分析；不是AI模型，未做预测或最优路线证明。
2. **空间化校园服务展示。** 静态坐标映射、重叠点展开、状态图层与详情联动；无需SDK或密钥，坐标数据缺失时不制造位置。
3. **可追溯业务闭环。** 订单事件、维修记录、评价、绑定聊天及事务通知形成完整证据链。
4. **轻量交付。** 单体分层、组件复用、本地SVG、短而有效的验收入口；本轮减少冗余查询并保留安全边界。

## 统计口径

自然日为Asia/Shanghai。今日报修按create_time；处理中为ASSIGNED/PROCESSING/WAIT_CONFIRM/REWORK_PENDING；完成率为FINISHED+COMMENTED / 全部工单；平均维修时间按每个订单、维修轮次的记录开始至结果完成时间，不含审核或等待确认时间。趋势最近14天，完成曲线按FINISH事件；人员完成数量按学生已确认状态，评分直接读取evaluation。上述口径在页面描述中保留，不把“提交维修结果”和“学生确认”混为同一指标。

## 最终优化边界

本轮只修改冗余查询、前端竞态/错误处理、展示与交付资料。Phase6最终优化保留JWT、权限模型、派单公式和地图逻辑；后续异常协同流程仅增加必要状态与附加信息，见business-enhancements.md。详见[审查及验证](phase6-validation.md)与[答辩指南](defense-guide.md)。
