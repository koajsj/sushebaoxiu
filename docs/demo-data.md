# 独立演示数据

演示数据只用于毕业设计展示，不能混入现有业务库。账号、楼栋与工单均为示例；统计接口仍读取数据库，脚本不修改统计公式或伪造历史时间。

## 准备新演示库

需要已有 Java 17、Python 3、MySQL 客户端和已构建的后端 jar，无新增 Python 包。先从项目根目录执行：

```bash
# macOS 多 JDK 环境先选择 Java 17；其他系统设置自己的 JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
cd backend
./mvnw -DskipTests package
cd ..
mkdir -p .runtime
```

准备一个私有 MySQL 管理员客户端配置文件，例如 `.runtime/demo-mysql.cnf`：

```ini
[client]
user=你的本机MySQL管理员账号
password=你的本机MySQL密码
host=127.0.0.1
port=3306
```

文件不要提交到 Git。macOS/Linux 使用 `chmod 600 .runtime/demo-mysql.cnf` 限制读取权限；MySQL 非默认端口时填写实际值。若已使用本机私有 `.runtime/mysql-root.cnf`，可以直接传入该文件。

```bash
python3 scripts/prepare-demo.py --create --mysql-config .runtime/demo-mysql.cnf
```

脚本仅允许本机 MySQL，库名必须以 `campus_repair_demo_` 开头。默认使用当前时间生成新库名；已有库直接拒绝，不覆盖或删除数据。18088 端口已占用也拒绝。初始化复用现有 SQL，随后启动临时后端，调用现有业务 API 创建样例；完成后停止该临时进程。失败会保留隔离库与私有日志供排查，不自动清库。

成功时输出演示目录，例如 `.runtime/campus_repair_demo_20261001_153000/`。其中 `backend.env` 为私有配置，`manifest.json` 记录工单编号、阶段及验证范围。该目录已被 Git 忽略。

## 启动演示

在两个终端中从项目根目录操作，把下方目录替换为脚本实际输出目录：

```bash
# 终端1：演示后端，端口18088；与默认8080展示库分开
set -a
source .runtime/campus_repair_demo_20261001_153000/backend.env
set +a
cd backend
"$JAVA_EXECUTABLE" -jar target/campus-repair-0.0.1-SNAPSHOT.jar
```

```bash
# 终端2：只对本次前端进程指定代理，不修改现有frontend/.env
cd frontend
BACKEND_PROXY_TARGET=http://127.0.0.1:18088 npm run dev
```

确认开发服务器打印的实际地址后打开 `/login`。关闭此前指向其他后端的前端进程，以免浏览器访问旧代理。若本机没有其他前端，一般为 `http://127.0.0.1:5173/login`。

登录账号：`student001`、`admin001`、`worker001`、`worker002`、`worker003`，该全新演示库的密码均为 `123456`。它们只用于本地展示。配置使用提供的本机数据库管理员凭据，不能作为生产配置；搬迁到正式环境时按数据库文档配置独立最小权限账号。

## 样例覆盖

| 场景 | 演示内容 |
| --- | --- |
| 待审核、待派单 | 学生提交及管理员审核，待派单工单已有推荐快照 |
| 待接单、待开工 | 智能/人工派单、维修员响应、学生确认预约 |
| 维修中、待验收 | 已保存的维修记录、当前负责人及工单文字沟通 |
| 返工 | 待安排返工和第二轮维修，真实事件与历史记录保留 |
| 完成、评价 | 学生确认、三名维修员的样例评分与完成量 |
| 审核驳回 | 待学生补充信息，可继续现场演示重提 |
| 超时 | 一个紧急待接单任务，由已有 SLA 扫描产生超时事件及通知 |

脚本创建14份工单。为短时间准备超时样例，**仅临时生成进程**将 HIGH 接单时限缩短为2秒、扫描间隔缩短为1秒；生成的重启配置恢复原默认阈值。已有超时工单保留其实际到期时间，新工单按默认时限处理。主状态仍不因超时而改变。

所有样例产生于实际运行时刻，因此14天趋势的过去日期可能为0；快速执行的平均维修时间可能显示0.0小时。这是样例真实执行耗时，不代表实际校园效率。不得修改时间戳来制造“漂亮曲线”。样例不附带虚构现场照片；浏览器演示时请自行上传无隐私的 PNG/JPEG 照片。

可直接使用待派单样例演示推荐和确认；完整闭环建议现场新建一份工单。候选排序由当前数据库状态计算，不保证某个账号总是第一名。生成阶段的推荐快照可能过期，演示时按提示重新生成即可。
