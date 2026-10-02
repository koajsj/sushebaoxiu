# 2026-10-02 发布前收口验证

本记录对应本轮工作树；先前 UI 与官方素材修改一并交付。本轮只修复确认的上传竞态、验收脚本隔离与过期断言，没有再次进行视觉重构。历史阶段报告不代表当前验证结果。

## 环境与数据边界

- 使用 `LOCAL_NO_OPEN=1 LOCAL_NO_PAUSE=1 ./start-local.command` 启动现有本机 MySQL、local profile 后端与前端。最后恢复该环境，三角色通过前端代理完成真实登录及只读 API 检查。
- 所有业务写入验收和 Maven 数据库并发测试使用新建的 `campus_repair_deep_check_*` 隔离库，HTTP 后端端口为 18086。未重建、清空或迁移既有业务库。
- 既有库与完整 migration 新建库的列定义、索引和约束一致；两库 21 组外键关联检查没有孤儿记录。
- 既有库 12 张业务表（不含账号表）在验收前后校验和相同；既有账号未执行禁用、改角色或改密码测试。
- 私有配置、凭据、日志、进程文件、隔离数据库证据和构建产物保存在被忽略的位置，不提交。

## 确认问题与最小修复

| 等级 | 触发条件与根因 | 修复与证据 |
| --- | --- | --- |
| P2 | 图片上传未结束时切换 Session、工单或维修轮次；原组件无身份/上下文校验，晚到结果可以回写新草稿 | 增加同步失效版本、AbortController、卸载取消及工单/轮次上下文；原实现下两个回归检查失败，修复后通过 |
| P2 | 原认证验收脚本默认连接业务后端，并硬编码更新业务库账号；增强验收也未强制隔离 | 强制显式隔离库和端口；SQL 库名校验及仅存在于隔离库的随机账号探针证明 HTTP/SQL 指向同一库，错误配置在业务变更前退出 |
| P3 | 增强验收脚本沿用旧账号、缺少当前必需幂等键、以 GET 生成推荐、SLA 等待短于默认扫描周期，活动工单断言遗漏人工待接单 | 仅更新测试准备与断言；真实增强验收 60 项通过，未修改产品算法、状态机或 SLA 配置 |

未确认 P0/P1。以上范围外没有为了增加修复数量而改动代码。

## 代码审查范围

重新检查认证与禁用账号、角色和工单归属、状态转换锁、幂等键、派单快照、预约冲突、图片配额与文件授权、多轮维修、SLA 扫描、AFTER_COMMIT/独立事务、通知唯一键、分页排序、批量关联查询、Excel 与异常日志。

前端检查 Session/请求版本保护、路由切换、AbortController、上传与图片资源、聊天轮询/已读、通知分页、草稿保存、Dialog 状态、定时器/监听清理，以及当前视觉 diff。保留现有保护；服务端业务源码、SQL migration、路由、Store、权限、算法和依赖清单没有变化。

## 实际执行结果

| 检查 | 结果 |
| --- | --- |
| Java 17 Maven `./mvnw -B -ntp -Djava.awt.headless=true verify` | BUILD SUCCESS；43 测试，0 失败/错误/跳过；其中 4 项真实 InnoDB 并发测试 |
| `npm run build` | 通过；包含 `vue-tsc --noEmit` 与 Vite production build |
| `npm run check:render` | 23 路由及相关状态面板渲染通过；为 SSR 检查 |
| `npm run check:session` | 8 项通过 |
| `node scripts/check-deep-races.mjs` | 10 项通过；包括上传、Chat、Notification、Draft 竞态 |
| `npm run check:map` / `npm run check:export` | 分别 4 / 5 项通过 |
| `node scripts/check-visual.mjs` | Logo、四张场景图、响应式资源及失败 fallback 检查通过；五张原图哈希与用户提供文件一致 |
| `CHECK_BACKEND_URL=http://127.0.0.1:18086/api npm run check:auth` | 17 项真实 HTTP/前端认证与路由保护检查通过 |
| `CHECK_BACKEND_URL=http://127.0.0.1:18086/api npm run check:core` | 15 项真实核心 API/前端数据流检查通过 |
| `backend/scripts/check_auth.py` | 56 项真实 HTTP/数据库认证检查通过 |
| `backend/scripts/check_enhancements.py` | 60 项真实业务检查通过 |
| `backend/scripts/check_release.py` | 19 项真实并发、待开工 SLA 与 Excel 检查通过 |
| 隔离负向检查 | 未设置环境、错误端口、业务库名及 HTTP/SQL 配对错误均拒绝；`python -O` 不会绕过入口隔离保护；探针账号清理完成 |
| `git diff --check` | 通过 |

认证脚本会撤销其测试账号 Token，故共享隔离库账号的认证、核心和业务 HTTP 检查串行执行。首次 Maven 测试在 macOS 字体路径退出，添加上述 headless 参数后完整通过；不是跳过 Excel 测试。

真实业务覆盖提交/驳回/重提、人工派单、智能推荐/确认、拒单/重派、接单、预约、开工、验收失败/返工、多轮记录/评价、管理员收回、旧维修员失权、Chat/Notification、图片访问、三个 Excel 导出、重复请求与竞争。默认调度器实际验证接单、待开工、维修三类 SLA；测试仅修改隔离工单到期时间。

## 提交安全与未验证边界

待提交内容检查真实私有密码、JWT Secret、Token、私钥及个人绝对路径；仅保留公开演示账号与示例占位配置。`.runtime`、`.env`、logs/PID、uploads、target/dist、node_modules、Codex 本地配置与 IDE 元数据被忽略。最终以 Git staged 扫描及 push 后 HEAD/远端一致性检查为准。

未进行 GUI/Computer Use 或浏览器自动化：1920/1366/1280/窄窗口的实际裁剪、地图文字、控件 focus、Dialog、键盘操作和浏览器下载交互仍需人工确认。SSR、源码检查及真实 HTTP 下载不替代这些检查。未进行生产部署、TLS 验收、持续负载测试或故障注入；没有创建 Release 或部署。
