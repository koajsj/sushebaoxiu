# API 参考

## 身份与业务接口

除原 POST `/api/auth/login` 和 GET `/api/health` 外，接口都要求 Bearer JWT。原退出和当前用户接口保留。

| 方法 | 接口 | 权限 / 用途 |
| --- | --- | --- |
| POST | /api/auth/login | 公开，username/password，返回token及安全用户信息 |
| POST | /api/auth/logout | 登录用户，递增token_version撤销旧令牌 |
| GET | /api/users/me | 登录用户，读取当前可信身份 |
| GET | /api/student/me、/api/worker/me、/api/admin/me | 对应角色身份读取 |
| GET | /api/health | 公开，应用与数据库健康检查 |
| POST | /api/student/orders | 学生创建，初始WAIT_AUDIT |
| GET | /api/student/orders | 本人订单，分页/状态筛选 |
| GET | /api/orders/{id} | 学生本人/指定维修员/管理员，订单+记录+评价+时间线 |
| GET | /api/admin/orders | 管理员列表，状态/类型/日期筛选 |
| PUT | /api/admin/orders/{id}/audit | 管理员审核通过 |
| PUT | /api/admin/orders/{id}/assign | 管理员人工指定人员，JSON workerId |
| GET | /api/admin/workers | 启用维修人员候选 |
| GET | /api/worker/orders | 仅分配给当前人员的订单 |
| PUT | /api/worker/orders/{id}/accept | 指定维修人员接单 |
| PUT | /api/worker/orders/{id}/start | 开始维修 |
| POST | /api/worker/repair-record | JSON orderId/content/imageUrl，保存记录 |
| PUT | /api/worker/orders/{id}/finish | 至少有一条记录后完成维修 |
| PUT | /api/student/orders/{id}/confirm | 所属学生确认 |
| POST | /api/student/evaluation | JSON orderId/score(1–5)/content，一次评价 |
| GET | /api/admin/dispatch/recommend/{orderId} | 管理员获取并保存本次候选推荐快照 |
| POST | /api/admin/dispatch | 管理员确认，JSON orderId/workerId/recommendationId |
| GET | /api/map/orders | 仅管理员，按status筛选，最多500条及总数 |
| GET | /api/map/workers | 仅管理员，启用维修员静态服务坐标与当前负载 |
| GET | /api/map/buildings | 仅管理员，校园建筑坐标 |
| GET | /api/admin/statistics/overview | 今日报修、处理中、完成率、平均维修时间 |
| GET | /api/admin/statistics/trend | 最近14天新报修与维修结果提交趋势 |
| GET | /api/admin/statistics/types | 故障类型工单数量和比例 |
| GET | /api/admin/statistics/workers | 人员完成量、真实评价均分、当前任务数 |
| GET | /api/orders/{orderId}/messages | 订单学生、当前维修员、管理员读取最近100条 |
| POST | /api/orders/{orderId}/messages | 学生与当前维修员发送文字；JSON content |
| GET | /api/notifications | 当前用户最近50条通知、总数与未读数 |
| PUT | /api/notifications/{id}/read | 仅所属用户标记已读 |
| GET | /api/catalog | 登录用户读取故障类型和楼栋 |
| GET | /api/student/summary | 学生本人基础业务计数 |
| GET | /api/worker/summary | 维修员本人基础计数，today按今日分配事件计算 |
| POST | /api/images | 学生/维修人员，multipart字段file |
| GET | /api/images/{uuid} | 图片上传者或绑定订单参与者，管理员读已绑定图 |

分页参数 `page` 默认1、`size` 默认12且最大100、`status` 可选；管理员 `typeId`、`from`/`to`（YYYY-MM-DD，含起止日期）可选。创建JSON为 typeId/title/description/buildingId/roomNo/priority/imageUrl，priority为LOW/NORMAL/HIGH，图片可不传。先上传获得 `/api/images/{uuid}` 再引用，不能传任意外部图片URL或他人上传。

返回统一 `{code,message,data}`。未登录401、角色错误403、非订单参与者404、状态冲突409、参数/图片错误400。图片成功读取为二进制，前端带授权获取Blob后展示；不公开上传目录。


## 流程增强接口

| 方法 | 接口 | 内容与限制 |
| --- | --- | --- |
| PUT | /api/admin/orders/{id}/reject | reason必填，WAIT_AUDIT → REJECTED |
| PUT | /api/student/orders/{id}/resubmit | 创建报修同字段，仅本人REJECTED；原ID → WAIT_AUDIT |
| PUT | /api/worker/orders/{id}/reject | reason必填，仅当前未接受的任务；释放负责人 → WAIT_ASSIGN |
| PUT | /api/student/orders/{id}/acceptance-fail | reason必填，仅本人WAIT_CONFIRM → REWORK_PENDING |
| PUT | /api/admin/orders/{id}/rework | mode=ORIGINAL或REDISPATCH；下一维修轮次 |
| PUT | /api/worker/orders/{id}/appointment | start/end为校园本地时间，version为详情当前值；需已接单 |
| PUT | /api/student/orders/{id}/appointment | version、accepted；拒绝时reason必填 |

订单列表增加overdue=true/false；详情增加dispatchHistory、轮次、接单时间、SLA时限、当前预约和版本；overview增加overdueCount、reworkCount。聊天POST可带expectedWorkerId，新前端始终传当前负责人，锁内发现更换返回409，保持原content客户端兼容。当前维修员只能读取与自己通信的历史，学生和管理员可查看完整最近100条。

新智能派单状态仍为ASSIGNED，但acceptedTime为空代表尚未响应，调用accept后才能start；人工方式维持WAIT_ASSIGN直到accept。重复状态转换返回409，不重复写入历史；拒单后原人员因归属解除返回404。预约版本冲突返回409。
