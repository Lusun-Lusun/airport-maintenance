# AeroCare 机场机电设备维修管理系统

本机运行的开发与演示系统，Java 17 / Spring Boot 3.3.5 / MyBatis / Spring Data Redis / Vue 3.5.13。嵌入式 H2 文件数据库免安装；浏览器静态资源全部本地提供。仅监听 127.0.0.1:8080。

## 启动

标准环境需要 Java 17、Maven 3.6.3 或更新版本。前端静态资源已包含在仓库中，无需安装 Node.js；Redis 为可选组件。当前 Windows 开发环境的便携式 JDK、Maven 位于 .runtime/tools，构建依赖位于 .runtime/m2；.runtime 已被 Git 排除，克隆仓库不会包含这些工具和依赖。

本地已准备 .runtime 时，在项目目录执行 ./build.ps1 构建并运行测试；若 Windows 沙箱阻止 Java 读取 Maven JAR，可执行 python scripts/portable_build.py（需要 Python 3 和已准备的 .runtime，运行同一套测试，但不执行 Maven 打包；首次使用可能下载 JUnit Console）。执行 ./start.ps1 后打开 http://127.0.0.1:8080，执行 ./stop.ps1 停止服务。

服务在隐藏窗口后台运行，日志为 server.log / server-error.log，PID 位于 server.pid。另一台电脑安装 Java 17 和 Maven 后，在项目目录运行 mvn verify，再运行 java -jar target/maintenance-1.0.0.jar 即可。也可以直接复制 jar 到有 Java 17 的电脑运行。

### 从 GitHub 克隆并运行

```powershell
git clone https://github.com/Lusun-Lusun/airport-maintenance.git
cd airport-maintenance
mvn verify
java -jar target/maintenance-1.0.0.jar
```

启动后打开 http://127.0.0.1:8080，前台运行时按 Ctrl+C 停止服务。mvn verify 会编译、执行测试并生成可运行 JAR。

### 数据初始化

首次启动自动装载 30 台设备、12 名维修人员、120 条已验收历史工单。文件存储在 data/airport.mv.db，重启保留操作。测试使用独立的内存库，不覆盖演示库。若需重置演示库，请先停止服务，再备份并移走 data 目录。

## 业务路径

设备台账 → 录入巡检 → 异常提交产生本地消息 → 每 3 秒补偿 → 自动推荐人员或升级通知 → 开始维修 → 维修完工 → 验收通过或退回 → 统计。

优先级：影响航班运行一律 P1；否则关键度 1/2/3 对应 P2/P3/P4。推荐人员须在岗、具备精确技能标签、处于同区域且当前负载小于容量，按负载占比升序、人员编号升序确定。人工派单仍校验这些约束。待验收工单仍占用人员容量，验收通过后释放。

巡检表和 outbox 在同一数据库事务写入；异常巡检请求必须携带 requestKey，同键同内容重放返回原记录，同键不同内容拒绝。消费者将工单插入与消息 DONE 在同一事务提交；work_order.inspection_id 唯一，重复补偿不重复创建。异常持续重试，记录次数与最近错误。派单通过数据库锁串行保护负载检查；本交付以单进程为运行范围。

主管通知是持久化站内通知，可标记已读；网页每 5 秒刷新。

## Redis

默认关闭，直接从数据库算统计。使用 Redis 时先启动本机 Redis，再 ./start.ps1 -Redis。Spring 配置支持环境变量 SPRING_DATA_REDIS_HOST、SPRING_DATA_REDIS_PORT、SPRING_DATA_REDIS_PASSWORD。固定时间窗口统计缓存 10 秒，缓存失败回退数据库；业务一致性不依赖缓存。修改数据最多在 TTL 到期后反映到已缓存的统计中。

## 指标与数据

datasets/ 为合成数据与独立期望值，生成种子 20260801。120 个修复时长从 [1,2,3,4,6,8] 小时取样，每条验收额外等待 1 小时，历史区间无重叠。UTC 窗口 [2026-08-01,2026-11-01)，92 天，30 台设备总暴露 66240 小时。该数据验证软件逻辑，不能代表机场实际可靠性、维修效率或推荐效果。

MTTR：完工时间落入窗口的工单，累加从异常巡检到最后一次维修完工的时长，再除样本数；包括派单等待。退回工单清空完工时间，重新完工后再纳入。

可用率：按设备把异常巡检至验收通过的停机区间截取到窗口并求并集，1 − 停机小时 /（当前设备数 × 窗口小时）。未验收及待转单异常持续至窗口结束。无样本时 MTTR 返回 null。设备假设整个窗口在役，暂未实现投退役与计划停机日历。

reports/ 为实测验证结果；reports/junit 为 JUnit XML；标准 Maven 环境可在 target/site/jacoco/index.html 生成覆盖率；scripts/evaluate.py 可复核接口、业务链路并运行短时本机只读压测。压测受本机负载影响。

## API

GET /api/health、/equipment、/technicians、/inspections、/orders、/messages、/notifications、/audit、/stats?from=...&to=...。

POST /api/equipment 新增设备；POST /api/inspections 提交巡检；POST /api/orders/{id}/action 传 action（assign/start/repair/accept/reject）、note、technicianId；POST /api/messages/retry 立即补偿；POST /api/notifications/{id}/read 标记已读。字段示例见脚本和 Vue 前端。

## 交付范围

五个核心模块及本地消息、站内通知、审计、可重复测试。当前是单机演示服务，共享管理工作台，尚未实现登录/RBAC、独立维修与验收人员隔离、附件、完整设备编辑删除、人员管理编辑、生产 MySQL 迁移脚本、消息指数退避与告警、多实例消费部署。生产上线需要补齐这些事项并验证运行环境。仅本机演示默认无登录，不应直接暴露公网。

开源调研与依赖许可见 THIRD_PARTY.md，详细评价见 reports/评价报告.md。
