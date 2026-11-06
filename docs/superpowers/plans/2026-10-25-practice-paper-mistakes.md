# 整卷错题归集与复习 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本计划建议主代理在当前会话顺序实施；已由用户选择Native主代理顺序实施；仅最后一次独立审查使用子代理。

**Goal:** 已完成整卷自动归集客观错题，支持来源回看、冻结版本重练与订正，不改原卷成绩及单项错题。

**Architecture:** practice模块增加独立整卷错题台账；整卷提交与台账更新同事务。用户主动重练时创建一份有整卷来源的MISTAKES轮次，复用单项保存/交卷/播放器，但按来源更新不同台账。历史归集从真实冻结结果计算。

**Tech Stack:** Vue3/TypeScript/Pinia、Java21/SpringBoot、现有JdbcTemplate/MyBatis-Plus、MySQL8、Flyway、Vitest/Playwright；不新增依赖。

**Spec:** `docs/superpowers/specs/2026-10-25-practice-paper-mistakes-design.md`，用户于2026-10-25回复“确认”批准方案；本实施计划及Native执行已获用户确认。

## Global Constraints

- 整卷台账以 `(userId, setVersionId, questionId)` 去重；不同发布版本独立。
- 单项台账继续独立，单项练习的答对/答错不隐式改写整卷台账，反之亦然。
- 只有用户主动点击重练才创建MISTAKES轮次，不因归集自动制造单项成绩。
- 仔细阅读重做该来源阅读篇中的待订正错题；听力完整重练原25题；选词/匹配完整重练原10题。写作翻译排除。
- 原卷题号根据槽位和原冻结题序计算，不能依赖题干数字或错题子集序号。
- 新增Flyway迁移，不改V19—V26；当前最大V26，拟V27，写迁移前再核对。
- 接口路径 `/api/v1`；保留身份、CSRF、本人归属、锁与幂等。归集失败与原卷交卷一起回滚。
- 专用 `english_hero_test` 和测试Redis验证，不清库、倒退迁移、访问其他数据库或制造开发账号成绩。
- 保留当前所有未提交工作；复用当前工作区中尚未提交的整卷模块，不创建漏掉这些模块的默认分支工作树。不提交/推送无关修改。

## Review Focus

1. 同一题在多份整卷中出错：只一条冻结台账，“本卷错题”仍能按该次原卷的冻结结果找回（任务1/3）。
2. 全组重练带入原先正确的题：答错需正确新增，答对不得伪造错题，也不得改变单项台账（任务2）。
3. 已有同题组活动重练：同来源/版本恢复，否则409且可继续本人旧轮次，不覆盖草稿（任务2）。
4. 历史最后一次答对、同刻交卷和未交卷记录：按完成时间/ID确定状态，排除未完成，不重复回填（任务1）。
5. 仔细阅读只有原第51题错：重练仍显示51，而非1或重复“51. 51.”；回原卷定位不触发作答写入（任务3/4）。

## 文件与接口分工

- 新增 `backend/src/main/resources/db/migration/V27__practice_paper_mistakes.sql`：台账、来源列/约束、历史归集SQL。
- 新增practice包 `PracticePaperMistakeDtos.java`、`PracticePaperMistakeRules.java`、`PracticePaperMistakeMapper.java`、`PracticePaperMistakeLedger.java`、`PracticePaperMistakeService.java`：分别负责契约、题号/范围纯规则、SQL、事务内状态更新、本人列表/重练编排。
- 修改 `PracticePaperAttemptService.java`、`PracticePaperController.java`：首次提交更新台账，新列表/重练路由。
- 修改 `PracticeAttemptMapper.java`、`PracticeAttemptService.java`、`PracticeDtos.java`：可信来源、评分与台账分流、可选来源DTO；原单项行为保持。
- 修改前端 `api/practice-paper.ts`、`api/practice.ts`、`views/PracticePaperLibrary.vue`、`PracticePaperAttemptView.vue`、`PracticeAttemptView.vue`、`components/practice/PracticePaperResult.vue`、`PracticeQuestions.vue`、`PracticeResult.vue`；新增 `components/practice/PracticePaperMistakes.vue`。原听力/选词/匹配播放器复用，不改为另一套重练系统。
- 测试新增 `PracticePaperMistakeIT.java`、`PracticePaperMistakeMigrationIT.java`、`PracticePaperMistakeRulesTest.java`、`frontend/tests/e2e/practice-paper-mistakes.spec.ts`、`frontend/tests/real/practice-paper-mistakes.cjs`；修改既有schema断言和相关夹具清理。

### Task 1：历史与新整卷的事务归集

**Interfaces:**

- `PracticePaperMistakeLedger.recordPaper(PracticePaperMapper.Run run, List<PracticePaperDtos.ResponseInput> responses, Instant completedAt): void`，调用方已锁定本人和轮次；按冻结组件真实题目计算正误。
- `PracticePaperMistakeMapper.assess(Assessment assessment): void`；`Assessment`包含user、setVersion、quizVersion、question、slot、originalNumber、sourcePaperAttempt、reviewAttempt、correct、at。paper/review来源恰好一个，第一份错误原卷来源永久保存。
- DTO `PaperMistake`：questionId、setId、setVersionId、sourceAttemptId、slot、originalNumber、paperTitle、readingType、status、wrongCount、stem、submittedChoice、correctOption、explanation。ID为字符串。
- `PracticePaperMistakeRules.originalNumber(String slot,int zeroBasedIndex): int`，槽位起号LISTENING=1、CLOZE=26、MATCH=36、DETAILED_1=46、DETAILED_2=51，并按25/10/10/5/5限定索引；非法主观槽位或越界报输入错误。`fullReview(String readingType): boolean`仅LISTENING/CLOZE/MATCH为true。

- [ ] 在PracticePaperMistakeRulesTest先写原题号与复习范围测试：DETAILED_2索引0为51、索引4为55；LISTENING索引24为25；主观/越界拒绝；三类完整重练为true，DETAILED为false。运行并记录缺少规则实现的RED，再实现规则并通过。
- [ ] 在专用库集成写 `paperSubmissionCollectsWrongAndMissingExactlyOnce`：55题混合错/空、两主观已填，只归集客观错/空；同requestId重放、两个并发提交不重复计数。
- [ ] 写 `failedCollectionRollsBackPaperSubmission`：台账更新中途抛错，轮次仍IN_PROGRESS，result_json与台账均无半提交。
- [ ] 运行 `./mvnw.cmd -o verify -Pmysql-it '-Dit.test=PracticePaperMistakeIT' '-Dtest=PracticePaperMistakeRulesTest'`，记录因归集缺失产生的失败，不把环境错误当RED。
- [ ] 新增V27：`practice_paper_mistake`唯一键(user_id,set_version_id,question_id)，PENDING/RESOLVED与wrong_count>0约束、本人/冻结quiz/题目的复合外键。添加practice_attempt可空 `source_paper_attempt_id`、`source_paper_slot`，外键检查同用户来源、单项来源与整卷来源不可同时存在，保留当前活动唯一键。
- [ ] 在同一迁移用MySQL8 JSON_TABLE展开已完成整卷的客观result_json，再与冻结组件、题目身份核对；按completed_at、attemptId确定首次错误与最近状态，统计错误次数。仅有历史错误才建行，未完成和主观排除。回填INSERT对已存在键不覆盖，以便安全验证重放；不更新任何原作答/结果JSON。
- [ ] 写 `historicalBackfillKeepsLatestStateAndFrozenVersions`：用受测试用户所有的历史夹具执行迁移中同一段归集SQL，覆盖错→对、错→错、同时间ID顺序、新版本独立。再次执行不加次数；Flyway再次migrate执行0次。不得为测试清库或down-migrate；SQL测试读取V27明确标记的回填语句，避免测试另写一份不同算法。
- [ ] 实现mapper与ledger。正确题仅更新已存在记录，错误题upsert；服务端用户锁串行化状态顺序。`PracticePaperAttemptService.submit`在首次完成事务中调用ledger，幂等重放路径不调用。数据库时间和记录时间使用同一完成时刻。
- [ ] 重跑上述命令及SchemaMigrationIT；schema表数量由现53核实后更新为54，同时检查旧题组/单项/整卷夹具未被迁移改写。相关测试清理先删本人新台账、再删来源轮次，避免外键失败；不得放宽归属过滤。

### Task 2：有来源的重练与订正

**Interfaces:**

- `PracticePaperMistakeService.retry(SessionPrincipal actor, String sourceId, RetryInput input): PracticeDtos.Attempt`；`RetryInput(String requestId,String slot)`。
- `PracticeAttemptMapper.PaperSource(Long attemptId,String slot)`，`paperSource(long reviewAttemptId)`及`attachPaperSource(long reviewAttemptId,long sourceAttemptId,String slot)`，不重构既有Row的全部构造器。
- `PracticeDtos.PaperReviewSource(String attemptId,String slot,Map<String,Integer> originalNumbers)`；Attempt新增可选reviewSource字段，原构造器继续兼容。
- `PracticePaperMistakeLedger.recordReview(PracticeAttemptMapper.Row review, List<QuizMapper.Question> questions, Instant completedAt): void`，QuizMapper使用 `com.englishhero.reading.quiz.QuizMapper`，不另造同名题目类。

- [ ] 写 `paperReviewPreservesFrozenContextAndOriginalNumbers`：原卷第51题错时，只产生1题仔细阅读重练，映射51；LISTENING/CLOZE/MATCH分别25/10/10，不创建七个FULL轮次。
- [ ] 写 `paperReviewUpdatesOnlyPaperLedger`：原错题答对RESOLVED，新错/空题PENDING并累计；原卷分数/结果JSON及原单项practice_mistake逐字段保持。未交卷的重练DTO不暴露答案/解释。
- [ ] 写 `reviewReplayAndActiveConflictPreserveDrafts`：重复命令返回同轮次；同冻结版本同整卷来源可恢复；单项来源或不同版本活动重练409，返回本人可继续轮次信息，旧草稿不变。
- [ ] 写 `foreignOrForgedPaperReviewIsRejected`：未登录401，他人来源/音频404；未完成来源、无待复习题、非法slot、客户端伪造版本/题ID不能创建轮次。
- [ ] 运行任务1的集成/规则命令，记录未实现重练路由/来源行为的RED。
- [ ] 实现retry：校验本人已完成原卷、槽位对应冻结版本、原卷错题与当前台账交集；仔细阅读选择待订正子集，其余完整题组。用户锁下复用practice_request幂等，操作可沿用RETRY但请求hash包含PAPER_RETRY、sourceId、slot，防止跨命令重用；冲突检查在创建前完成。
- [ ] 评分分流：保留 `PracticeAttemptMapper.grade(row,qs,now)` 原行为；新增重载允许跳过单项台账更新。`PracticeAttemptService.submit`按数据库可信paperSource选择是否更新单项表，并在相同事务调用recordReview。不通过客户端标志选择台账。
- [ ] Attempt DTO通过冻结组件原题序生成originalNumbers；PracticeAttemptService仅依赖ledger，retry编排service可依赖AttemptService.get，不建立循环service依赖。来源下架后仍可复习本人冻结内容，音频沿用既有票据鉴权。
- [ ] 重跑集成与规则，另运行原 `PracticePaperIT#paperLifecycleIsAtomicPrivateAndFrozen` 及单项Cloze/Match/Listening相关IT，确认旧权限、判分、重练与事务行为保持。

### Task 3：整卷错题栏目、结果入口与原卷定位

**Interfaces:**

- `PracticePaperMistakeService.list(SessionPrincipal actor, Query query): PageDto<PaperMistake>`；Query含exam、year、month、readingType、status、sourceAttemptId、page、pageSize，沿用既有分页边界1–50。
- Controller新增GET `/practice/paper-mistakes`、POST `/practice/paper-attempts/{id}/retry-mistakes`；旧路径契约不变。
- 前端 `paperMistakes(query,signal)`、`retryPaperMistakes(id,{requestId,slot})`；api/practice.ts的Attempt新增可选reviewSource。
- 新组件PracticePaperMistakes输入items/busy，发出review/item、view/item；列表数据、AbortController/epoch与命令requestId由PracticePaperLibrary持有。

- [ ] 写后端 `paperMistakeListFiltersOwnedFrozenHistory`：年月/状态/题型/分页准确；同题多份整卷去重后，sourceAttemptId仍按指定原卷冻结错题ID找得到；他人sourceAttemptId404，不能靠过滤结果推断他人记录。
- [ ] 写双端E2E `paperMistakeTabKeepsFilterLayoutAndSourceContext`：新增错题本栏目，已有考试/日期筛选保留；切换状态不重建侧栏/弹窗，旧响应不覆盖新查询。
- [ ] 写 `paperReviewShowsOriginal51AndReadonlySourceJump`：重练标题/题号为原51且无重复数字；回原卷打开已交卷DETAILED_2并定位正确题，导航只读且不改变原保存状态。
- [ ] 跑后端目标测试和 `pnpm --dir frontend test:e2e tests/e2e/practice-paper-mistakes.spec.ts`，记录缺少入口和题号映射的RED。
- [ ] 实现列表mapper用冻结metadata筛选与分页；本卷范围先加载本人冻结结果中最多55个客观错题键再查询台账，不仅匹配首次来源列。新增整卷tab、题型/状态筛选、卡片来源及重练范围按钮、失败重试与409继续入口，所有重练命令复用requestId直到结果确认。
- [ ] PracticePaperResult在原卷有客观错题时提供“复习本卷错题”；目标URL为原整卷目录section=paper、tab=mistakes、sourceAttemptId=该轮次，保留年月。PracticePaperAttemptView解析slot/questionId仅对已加载的合法冻结组件应用一次，不因后续导航反复跳回，不发草稿写请求。
- [ ] PracticeAttemptView显示reviewSource与返回整卷错题本入口；PracticeQuestions/PracticeResult支持可选originalNumbers用于仔细阅读题号，缺省行为不变；仅在带来源映射时去掉题干已有重复编号。现有听力/选词/匹配完整题组本来使用原题号，保持播放器行为。
- [ ] 运行上述双端E2E与相关单元，通过后 `pnpm --dir frontend build`。对已有单项错题、整卷保存恢复、目录折叠与日期计数回归，不为纯装饰新增重复单元测试。

### Task 4：真实来源全栈验收与交接

**Files:** 扩展 `backend/src/test/java/com/englishhero/practice/PracticePaperMistakeIT.java`，新增 `frontend/tests/real/practice-paper-mistakes.cjs`；同步docs/api.md、database.md、practice-guide.md、progress.md、README.md及本日acceptance记录。

- [ ] 在真实来源测试副本下新增可选浏览器流程：临时管理员发布七部分整卷，普通测试用户故意错/漏听力、选词、匹配和两篇阅读；只在专用库写入。
- [ ] 实际1440/320视口分别验证：交卷归集→筛选本卷→原卷定位→正确重练范围→保存刷新→交卷订正→已订正列表；同版本重复错题累计、新版独立、原卷分数不变。断言实际视口并查看截图，不把声明宽度误计手机验收。
- [ ] 对当前真实库历史归集算法做只读预检并保存待归集数、状态分布及原结果/冻结响应指纹；测试库迁移、回填、权限和回滚通过后，才按项目现有启动命令应用开发库迁移，不创建模拟成绩。运行后核对原结果、单项错题、题库和冻结内容指纹保持；只新增派生台账。
- [ ] 串行运行真实库IT，避免共用测试库并发清理；执行23项既有保存/音频相关单元与新增有意义用例、目标双端E2E、类型构建和真实源浏览器，保存命令/退出码/结果。
- [ ] 更新接口文档，明确整卷现在归集独立错题、不创建单项FULL轮次；说明既有历史、版本去重、重练范围及来源冲突提示。验收区分全量数据一致性、代表性浏览器、Mock和用户视觉确认。
- [ ] 检查本任务差异、未完成项与风险，更新进度；不暂存/提交已有无关文件，不自动进入六级或AI阶段。

## 自审记录和执行交接

已逐项对照确认方案：历史回填、来源类型、版本去重、原卷筛选、题号、活动冲突、幂等、权限、双端与旧功能回归均有任务归属。新台账为派生数据，不迁移或覆盖原用户成绩。任务1→2→3依赖同一来源和DTO，建议Native：主代理当前会话顺序实施，最后针对稳定差异做一次审查。

计划状态：四项任务已完成，本地服务及V27/V28已更新；实际命令、结果和边界以 `docs/acceptance/2026-10-25-practice-paper-mistakes.md` 为准。一项低影响手工API空slot边界遗留，未冒称所有输入均通过。细项复合断言部分由多项测试共同验证，测试命名以当前源文件为准。
