# 专项整组人工校对独立审查

审查日期：2026-10-21。范围仅为 `PracticeAdminService.review` 新增 QUESTIONS 分支、前端校对 API kind 联合类型、编辑页 `reviewAll`/`bulkReviewBlocked`、总览按钮及指定新增测试。后端服务和编辑页对照 `.tool-cache/practice-bulk-review/*.before.*`，未以整个脏工作区作为本次变更。未修改业务代码、运行测试、安装依赖或提交。

## 结论

未发现可确认的 P0–P2 问题，没有需要修复的代码发现。本功能记录管理员对整组题目的人工校对确认，未引入 AI 改题或自动发布。

## 代码证据

- `backend/src/main/java/com/englishhero/practice/PracticeAdminService.java:24`：新增分支沿用服务端实时 ADMIN 校验、文章/题组/题目锁、expectedLockVersion 检查及事务。`SecurityConfig.java:67` 继续保护 `/api/v1/admin/**`；原 POST review 路径仍使用既有 CSRF 配置，无新增豁免。前端通过 `businessWrite` 发送 CSRF 头及身份代际检查。
- `backend/src/main/java/com/englishhero/practice/PracticeAdminService.java:26`：QUESTIONS 要求无单题 key。确认前先对整组检查，再为全部题目生成与逐题相同的校对哈希；任一内容问题在修改持久状态前抛错，最终只调用一次 `sets.review`。撤销分支清空题目校对。两条路径均撤销来源确认，且不调用 publish、不生成发布版本。
- `backend/src/main/java/com/englishhero/practice/PracticeAdminService.java:28`：仅排除来源确认、来源元数据和已有校对缺失提示；题干/选项/答案/解析及 CLOZE 词库、标记、答案唯一性检查保留。来源和题目确认可以分别完成，完整发布仍沿用原发布检查。
- `frontend/src/views/AdminPracticeEditorView.vue:49`：按钮禁用条件与服务端检查分类一致，内容不全或已有全部校对时不可整组确认。`:50` 对本地编辑先保存并使用返回的最新 lockVersion，随后显示明确人工确认文案；取消不发送整组校对请求，保存失败也不会继续确认。
- `frontend/src/views/AdminPracticeEditorView.vue:52`：busy 期间禁用现有编辑控件，防止请求期间继续改内容。流程在保存后、确认后及响应落地前检查身份代际、账号、页面 epoch 和卸载状态；账号或页面变化后不继续发送后续确认或覆盖新状态。错误不乐观勾选，原有失败重试/冲突恢复路径保留。
- `frontend/src/components/practice/PracticeQuestionOverview.vue:7`：新增整组按钮与数量提示，原逐题复选框和编辑入口保留。`frontend/src/api/admin-practice.ts:12` 仅扩展 kind 为 QUESTIONS，原 QUESTION/SOURCE 契约保留。真实 CLOZE 浏览器测试改为实际点击整组确认按钮，然后单独确认来源并发布。

## 测试证据和边界

审查者读取测试源码与主执行者日志，没有重复执行测试：

- `PracticeAdminIT.wholeGroupReviewIsAtomicVersionedAndDoesNotPublish` 覆盖非管理员403/匿名401、整组成功只增加一次锁版本、来源仍未确认、DRAFT/revision0、旧版本409，以及一题缺解析时拒绝且校对/锁版本不变。
- `PracticeClozeIT.wholeClozeReviewChecksSharedBankAndAllAnswersBeforeConfirming` 覆盖10空整组确认、来源和发布独立，以及缺共享词时400且无部分校对或锁版本变化。其他内容检查的保留通过上述服务端调用路径静态核对，不能把此单项测试说成逐一运行所有非法输入组合。
- `frontend/tests/e2e/practice-bulk-review.spec.ts` 覆盖取消不请求、503不勾选、再次确认成功、全组完成后禁用按钮、来源仍未确认、没有发布请求，以及删除解析后阻止整组确认。
- 主执行者 `.tool-cache/practice-bulk-review/green.log` 最终 `BUILD SUCCESS`，5项规则单测及11项集成测试零失败/错误（含4项 PracticeClozeIT、5项 PracticeAdminIT、2项 PracticeImportIT）。`browser.log` 记录桌面/手机12项通过，包含新增整组确认及旧管理/恢复回归。`build.log` 有最终构建完成记录。这些结果由主执行者执行，本审查只读取证据。

新浏览器测试没有专门模拟身份切换或脏编辑自动保存期间的竞态，这两部分在本次为静态路径审查；不记录为独立动态覆盖。真实浏览器最终结果和用户视觉确认以主执行者本轮验收记录为准。
