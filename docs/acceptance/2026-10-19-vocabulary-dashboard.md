# 背单词首页与退出保存验收

用户确认dashboard及exit-save-2原型后授权正式实现。保留原认词/选义、逐字拼写、释义主动继续、队尾重练、词书、目标、加入/暂停词条、分页、阅读/认证与统计。首页改词书栏、学新词/复习双卡、今日进度/设置弹窗、开始方式弹窗和折叠管理。只显示真实数据，无演示词或虚构统计进入正式页面。

## 退出与恢复

退出并保存收到后台PAUSED成功响应后回首页；失败保留卡片/输入与原请求重试。新词按来源、复习按全局保存，恢复保留原模式/快照/释义/队列。每个账号最多一ACTIVE，每scope最多一未完成；新分配排除其他未完成轮次占用的词。新词分配扣减今日已通过和所有保存NEW未通过项，避免切来源占多份额度。旧任务跨日或目标调低不会被删除。

本人保存的下架词书仍列在选书面板，可切回恢复；无该保存轮次的其他用户不能选它，且下架书不产生新词任务。词汇历史与阅读打卡不混计。

V18只增量扩展session来源快照与生成列唯一约束，不改旧迁移、不清库。旧未完成轮次按原先锁定来源回填。拼写字母仍仅本标签页缓存，轮次/反馈由数据库持久化；不保证跨设备恢复未提交字母。

## 实际验证

| 检查 | 结果与证据 |
| --- | --- |
| 后端完整单元/集成 | Maven实际Java21.0.12.1，`mvnw.cmd -o test failsafe:integration-test failsafe:verify -Pmysql-it`退出0；65单元、92专用MySQL/Redis真实HTTP集成，零失败/错误/跳过。日志`.tool-cache/dashboard-full-backend-final.log` |
| 审查修复定向 | 两项新增用例先失败（额度返回200而应409，下架目录缺保存书），修复后17项词汇定向集成通过，日志dashboard-review-red/green.log |
| 前端单元 | 19文件67用例通过，退出0；dashboard-unit-final.log |
| 类型/生产构建 | `pnpm --dir frontend build`退出0；dashboard-build-final2.log，JS index-D5d5cyFr.js |
| 浏览器回归 | 受控5174，dashboard/学习/词书统计/Notebook/导航/查词/打卡/登录弹窗60项通过，退出0；dashboard-browser-final3.log。另追加下架书选回桌面/手机2项通过：dashboard-unlisted-browser.log |
| 响应式 | 1440、320px新首页/弹窗截图已查看；既有768px词汇回归通过，无横向溢出。截图dashboard-live-desktop/mobile、dashboard-progress-desktop/mobile |
| 本地运行 | 8080新进程PID76428，`.tool-cache/dashboard-runtime/english-hero-dashboard.jar`；开发库由V17增量至V18，日志server.log确认；服务和数据库UP。4174/vocabulary为200，游客私人overview为401 |

浏览器使用Mock，不冒充真实账号浏览器闭环。真实HTTP/数据库权限、隔离、并发、保存/恢复等由后端集成单独验证。最终用户视觉确认待体验。

## 失败记录与审查

最初新后端测试因全局openId返回旧轮次失败，新UI测试因不存在双卡失败，均已观察失败后实现通过。浏览器测试曾因alert选择不唯一、未等退出响应即reload及旧按钮名失败，修正测试与新交互对应后通过。

广域回归两次出现既有登录测试桌面填密码与Element Plus @opened聚焦竞争，单独重跑通过；未改认证业务。测试helper改为等待dialog进入过渡完成后填写，完整60项随后通过，无固定sleep或跳过用例。初次文档patch因标题不匹配未应用，核对标题后重新写入。

独立只读审查见[记录](../reviews/2026-10-19-vocabulary-dashboard.md)。两P2均补失败回归修复；占用词概览与可分配查询一并对齐。所有无关本地修改保留，未提交/推送/合并，原型保留不进入生产构建。
