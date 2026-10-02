# API 参考

## 身份与业务接口

除原 POST `/api/auth/login` 和 GET `/api/health` 外，接口都要求 Bearer JWT。原退出和当前用户接口保留。

| 方法 | 接口 | 权限 / 用途 |
| --- | --- | --- |
| POST | /api/auth/login | 公开，username/password，返回token及安全用户信息 |
| POST | /api/auth/logout | 登录用户，递增token_version撤销旧令牌 |
| GET | /api/users/me | 登录用户，读取当前可信身份 |
| PUT | /api/users/me/password | 本人校验旧密码并修改，撤销旧令牌 |
| GET | /api/student/me、/api/worker/me、/api/admin/me | 对应角色身份读取 |
| GET | /api/health | 公开，应用与数据库健康检查 |
| POST | /api/student/orders | 学生创建，初始WAIT_AUDIT；必须提供UUID requestKey |
| GET | /api/student/orders | 本人订单，分页/状态/业务阶段筛选 |
| GET | /api/orders/{id} | 学生本人/指定维修员/管理员，订单+记录+评价+时间线 |
| GET | /api/admin/orders | 管理员列表，状态/类型/日期筛选 |
| PUT | /api/admin/orders/{id}/audit | 管理员审核通过 |
| PUT | /api/admin/orders/{id}/assign | 管理员人工指定人员，JSON workerId |
| GET | /api/admin/workers | 启用维修人员候选 |
| GET | /api/worker/orders | 仅分配给当前人员的订单 |
| PUT | /api/worker/orders/{id}/accept | 指定维修人员接单 |
| PUT | /api/worker/orders/{id}/start | 开始维修 |
| POST | /api/worker/repair-record | JSON orderId/content/imageUrl/requestKey，保存记录 |
| PUT | /api/worker/orders/{id}/finish | 至少有一条记录后完成维修 |
| PUT | /api/student/orders/{id}/confirm | 所属学生确认 |
| POST | /api/student/evaluation | JSON orderId/score(1–5)/content，一次评价 |
| GET | /api/admin/dispatch/recommend/{orderId} | 只读获取当前有效推荐快照 |
| POST | /api/admin/dispatch/recommend/{orderId} | 显式生成推荐；`refresh=true` 重新生成 |
| POST | /api/admin/dispatch | 管理员确认，JSON orderId/workerId/recommendationId |
| GET | /api/map/orders | 仅管理员，按status筛选，最多500条及总数 |
| GET | /api/map/workers | 仅管理员，启用维修员静态服务坐标与当前负载 |
| GET | /api/map/buildings | 仅管理员，校园建筑坐标 |
| GET | /api/admin/statistics/overview | 今日报修、处理中、完成率、平均维修时间 |
| GET | /api/admin/statistics/trend | 最近14天新报修与维修结果提交趋势 |
| GET | /api/admin/statistics/types | 故障类型工单数量和比例 |
| GET | /api/admin/statistics/workers | 人员完成量、真实评价均分、当前任务数 |
| GET | /api/admin/export/orders | 仅管理员，下载工单统计.xlsx |
| GET | /api/admin/export/workers | 仅管理员，下载维修效率.xlsx |
| GET | /api/admin/export/types | 仅管理员，下载故障分析.xlsx |
| GET | /api/orders/{orderId}/messages | 订单学生、当前维修员、管理员按 beforeId/afterId/size 分页读取 |
| GET | /api/orders/{orderId}/messages/context | 轻量读取标题与当前负责人 |
| POST | /api/orders/{orderId}/messages | 学生与当前维修员发送文字；JSON content |
| GET | /api/notifications | 当前用户分页通知，page/size/unread；含未读数与 latestId |
| PUT | /api/notifications/{id}/read | 仅所属用户标记已读 |
| PUT | /api/notifications/read-all | 传 throughId，仅标记该ID以前通知 |
| GET/POST/PUT | /api/admin/manage/** | 管理员分页账号、建档及更新、工作状态、楼栋和故障类型维护 |
| GET | /api/catalog | 登录用户读取故障类型和楼栋 |
| GET | /api/student/summary | 学生本人基础业务计数 |
| GET | /api/worker/summary | 维修员本人基础计数，today按今日分配事件计算 |
| POST | /api/images | 学生/维修人员，multipart字段file |
| GET | /api/images/{uuid} | 未绑定图片仅上传者；已绑定图片按当前订单归属，管理员可读 |

工单分页参数 `page` 默认1、`size` 默认12且最大100；`status` 为原主状态，`phase` 为派生业务阶段。管理员 `typeId`、`from`/`to`（YYYY-MM-DD，含起止日期）可选。创建JSON为 typeId/title/description/buildingId/roomNo/priority/imageUrl/requestKey，priority为LOW/NORMAL/HIGH，图片可不传。先上传获得 `/api/images/{uuid}` 再引用，不能传任意外部图片URL或他人上传。新操作生成新UUID；同次重试复用同一键，同键不同载荷返回409。

返回统一 `{code,message,data}`。未登录401、角色错误403、非订单参与者404、状态冲突409、参数/图片错误400。图片成功读取为二进制，前端带授权获取Blob后展示；不公开上传目录。

## 管理员报表导出

以上三个 `/api/admin/export/*` 接口均要求管理员 Bearer JWT，无筛选参数，返回全量只读快照。成功响应为 `.xlsx` 二进制（不包装在Result中），`Content-Disposition` 包含UTF-8中文文件名及Asia/Shanghai日期，`Cache-Control: no-store`。失败仍返回统一JSON错误；未登录401、非管理员403。前端入口位于Dashboard页头，下载请求超时为60秒，错误文件不会被当作Excel保存。

| 报表 | 字段 |
| --- | --- |
| 工单统计 | 工单编号、报修人、故障类型、报修地点、优先级、当前状态、维修人员、创建时间、完成时间、维修耗时（小时） |
| 维修效率 | 维修人员姓名、完成订单数量、平均评分、平均维修时间（小时）、返工次数、当前任务数量 |
| 故障分析 | 故障类型、数量、占比 |

每份文件包含数据页及「口径说明」页；中文表头加粗、首行冻结、列宽自动调整并限制在可读范围。工单编号按文本保存以避免Excel数字精度损失，用户文字不会生成公式。统计定义如下：

- 工单当前状态采用系统统一业务阶段；完成时间为学生最终确认事件时间，仅已完成/已评价工单展示。旧数据没有确认事件时不猜测时间，保留空值。
- 维修轮次耗时沿用Dashboard的 `MIN(start_time) → MAX(finish_time)`，只包含已完成记录。工单维修耗时为各已完成轮次之和；维修员平均耗时为其负责的已完成轮次平均值，按该轮最终维修记录的人员归属。均以小时保留一位小数，不把审核、派单或验收等待计入。
- 维修员完成数量、平均评分、当前任务数量直接调用原 `StatisticsService.workers`，不改变Dashboard口径；当前任务包括WAIT_ASSIGN、ASSIGNED和PROCESSING。返工次数按验收未通过事件的实际维修员计数，保留多轮历史归属。
- 故障数量及占比直接调用原 `StatisticsService.types`，包含零工单类型，占比沿用一位小数百分比。无有效评分/耗时或总工单数为零时，相应单元格留空。

导出不建表、不写业务数据。工单使用500条游标分页，Excel使用100行流式窗口；生成完毕后再发送文件，最终文件仍需缓存在内存中。本科项目规模下同步生成，未引入后台导出任务。单表最多1,048,575条数据（另占一行表头），超过Excel限制返回413错误，不截断数据。


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
| PUT | /api/admin/orders/{id}/recall | reason、expectedPhase、expectedDispatchRound；维修中另需confirmProcessing=true |

订单列表增加overdue=true/false；详情增加dispatchHistory、轮次、接单/待开工时间、SLA时限、当前预约和版本；overview增加overdueCount、reworkCount及待审核数。聊天POST可带expectedWorkerId，新前端始终传当前负责人，锁内发现更换返回409，保持原content客户端兼容。当前维修员只能读取与自己通信的历史，学生和管理员可分页查看完整历史；当前无负责人时仍可读历史但不能发送。

新智能派单状态仍为ASSIGNED，但acceptedTime为空代表尚未响应，调用accept后才能start；人工方式维持WAIT_ASSIGN直到accept。重复状态转换返回409，不重复写入历史；拒单后原人员因归属解除返回404。预约版本冲突返回409。

统一业务阶段 `WAIT_DISPATCH`、`WAIT_ACCEPT`、`WAIT_START` 由服务器根据主状态、负责人和接单时间计算；待验收应使用返工流程，已完成或已评价工单不能收回。通知在提交后进入有界后台队列，失败只记日志且没有持久补偿；前端应以订单详情作为业务成功依据。
