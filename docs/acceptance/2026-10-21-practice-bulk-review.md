# 题组整组人工校对确认

用户要求管理端增加一键校对，避免逐题点击。当前实现为**人工整组确认**：按钮在“全部题目总览与校对”上方，保留逐题勾选；不接AI纠错，不自动改写答案、确认来源或发布。

有编辑时先保存当前草稿，再弹出整组确认说明。题目检查合格后可一次确认全部题目，显示N/N；有缺答案、解析、重复答案/选项、无效词库/原文标记等问题时禁用并提示补齐。来源仍须单独确认，发布仍独立操作。修改内容后沿用原有校对失效规则。

服务端复用ADMIN `/api/v1/admin/practice/sets/{id}/review`，新增 `kind=QUESTIONS`。沿用CSRF、权限、题组版本/事务锁；先检查整组内容再一次写入校对哈希，非法请求不部分确认。metadata来源信息由独立SOURCE确认处理，不能因此绕过最终发布检查。QUESTIONS不接受questionKey，reviewed=false可撤销全部题目确认（界面仍提供逐题撤销）。

实际验证：

- RED：新增DETAILED/CLOZE真实数据库检查均因旧接口拒绝QUESTIONS失败；新增桌面/手机浏览器检查因找不到整组按钮失败。
- `mvnw.cmd -o '-Dtest=PracticeClozeRulesTest' '-Dit.test=PracticeAdminIT,PracticeClozeIT,PracticeImportIT' -Pmysql-it verify`：退出0，5项规则单测、11项专用MySQL/Redis集成检查通过。覆盖ADMIN/普通用户/匿名、旧版本409、无效题目不部分写入、来源和发布不自动执行、CLOZE词库检查及旧流程回归。
- 前端类型/构建退出0；`practice-bulk-review.spec.ts`、`practice-admin.spec.ts`、`practice-editor-recovery.spec.ts`：12项桌面/手机Mock浏览器通过，含确认取消、503失败保留状态与重试、校对统计和缺解析禁用。
- 真实接口 `PracticeClozeBrowserCheck`：退出0，内含4项浏览器流程通过；真实ADMIN使用整组按钮确认10空，修改词库后重新整组确认，独立确认来源并发布；学员1440/768/320px回归通过。测试专用数据清理，不进入日常题库。
- [独立审查](../reviews/2026-10-21-practice-bulk-review.md)无P0–P2发现。
- 8080已更新 `.tool-cache/practice-bulk-review/english-hero-bulk-review.jar`（PID77020），旧JAR保留；4174为最新前端构建。本次无数据库迁移或安装依赖/提交/推送。
- 重启前后真实ADMIN只读核对题组1–3完整响应相同，未替用户勾选校对或发布。日志、修改前快照、只读检查在忽略目录 `.tool-cache/practice-bulk-review/`。

检查入口：[题组3](http://127.0.0.1:4174/admin/practice/3)，点击页面顶部“题目总览”可定位按钮。自动化验证不等同于用户人工完成内容校对；用户点击后的确认才写入当前业务状态。
