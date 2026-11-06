# 四级选词填空独立审查

审查日期：2026-10-21。范围为 `.tool-cache/practice-cloze-implementation/review.diff` 对应的正式 CLOZE 后端、V21、前端 API/store/学习组件/管理端变更；没有把工作区此前未提交内容整体作为本次差异。依据 `AGENTS.md`、正式设计、实施计划及原型验收记录。未修改业务代码、运行服务、安装依赖或提交。

## 结论

本次独立静态审查未发现可确认的 P0–P2 实现缺陷。没有需要修复的代码发现。此结论仅针对已检查的实现，不等同于所有验收项或用户视觉确认完成。

## 关键核对证据

- `backend/src/main/java/com/englishhero/practice/PracticeAttemptService.java:27`：先验证本轮题目归属及题型选项范围，再检查版本；将请求与已存答案合并后验证共享词唯一性，随后才写入。沿用用户行、轮次行锁及事务，移动的清空/填入可以在一个请求中原子处理；DETAILED 仍限制 A–D。
- `backend/src/main/java/com/englishhero/practice/PracticeAttemptService.java:28`：提交先查用户范围内的幂等请求并校验操作哈希，沿用版本检查和未答确认。`:32` 从轮次绑定的发布版本读取词库，未完成时不返回正确答案、解析或成绩；公开题目映射不携带答案。
- `backend/src/main/java/com/englishhero/practice/PracticeAdminService.java:23`：题型创建后不可转换；原文版本改变时不保留逐题校对，词库或单题内容改变通过哈希撤销相应校对和来源确认。`:30` 发布再次校验并冻结类型/词库；版本读取使用独立发布快照。
- `backend/src/main/java/com/englishhero/practice/PracticePublicationChecks.java:16`：仅 CLOZE 哈希纳入词库；DETAILED 保留原哈希路径。`:24` 发布检查共享词库、原文标记、互不重复的正确答案、解析及校对状态。`PracticeClozeRules` 对10空顺序、空 options、A–O 与标记作结构检查。
- `backend/src/main/java/com/englishhero/practice/PracticeAttemptService.java:29`：CLOZE 错题重练读取原轮次冻结版本的完整题组，DETAILED 保留仅错题重练分支。V21 通过新增迁移扩展约束和版本字段，已有版本回填 DETAILED。
- `frontend/src/components/practice/PracticeClozePlayer.vue:204`：手机模态与桌面侧栏切换有独立状态处理；关闭/Escape 有焦点恢复。已用词先确认，再发送清空旧空和填入新空的同批变更；撤销保存受影响空位的旧值。`:325` 手机从词库交卷前关闭模态；`:334` 支持词卡方向键/Home/End。身份切换由 store 清空轮次状态，退出沿用等待保存流程。
- 管理端保留 DETAILED 编辑器、来源信息、JSON 导入、预览、校对和发布历史；CLOZE 新建提供15词和固定10空，已创建题型禁用转换。目录仅开放 CET4/CLOZE，原创入口保持直接列表和生成占位边界。

## 验证证据与限制

本审查没有执行或重复运行测试；以下测试由主执行者运行。本审查读取最终 `.tool-cache/practice-cloze-implementation/full-maven.log`：`:156` 为70项单元测试零失败/错误，`:383` 为3项 `PracticeClozeIT` 零失败/错误，`:1446` 为26项集成检查零失败/错误，`:1451` 为 `BUILD SUCCESS`。因此此前原文单独修改、归属、并发及幂等测试的“待执行”状态已更新为**已执行通过，依据主执行者日志确认**。

前端79项单测、构建、26项构建版Mock回归、补充2项模态/尺寸切换检查，以及真实CLOZE浏览器4项、旧仔细阅读真实浏览器4项的结果和边界，见主执行者的 `docs/acceptance/2026-10-21-practice-cloze.md`；本审查读取该验收记录，不将这些项目表述为审查者亲自运行。

初版 `PracticeClozeIT` 的顺序场景覆盖发布阻止/成功、未交卷答案隔离、重复词拒绝不部分写入、原子移动、未答确认、完整题组重练及冻结词库。初审提出证据不足后，主执行者补充了测试。跟进静态审查确认以下断言有实际验证意义，最终日志确认3项均执行通过：

- `clozePublishesFrozenBankKeepsAnswersPrivateAndRetriesTheWholePassage`：先修改原文而保持词库相同，断言全部题目校对及来源确认失效；重新校对并断言10空/来源已确认后，仅改变词库，再断言校对全部失效；检查旧发布/轮次词库不变。
- `clozeAttemptsEnforceOwnershipAndSerializeCompetingSameVersionSaves`：第二学员读取、保存、提交他人轮次均应404；两个相同 expectedVersion、不同答案的并行保存应分别200/409，持久版本只增长一次且答案与成功响应一致。
- `clozeConcurrentSubmitReplayGradesOnlyOnce`：相同 requestId 并行提交均应200，完成时间/成绩一致；随后重放仍返回同一轮次和完成时间，并直接查询数据库断言仅一条提交请求记录及错题计数未重复增长。

此前提示的词库断言前置状态问题已修正：测试先 `reviewAll(bodyEdited)`，显式断言10项校对及来源确认有效，再仅修改词库。因此现在具有原文修改和词库修改各自独立的失效证据，该验证缺口已关闭。本次跟进仅检查该测试修正及最终日志，产品代码未重新审查。

视觉结论、真实内容核实/导入及本地部署由主执行者负责，本报告不将其记为完成。
