# 阶段3个人生词学习与到期复习实施计划

执行状态：2026-10-19已按用户批准完成个人来源学习/复习合批交付。验收见[实际结果](../../acceptance/2026-10-19-vocabulary-personal-learning.md)；下文任务保留原计划，测试覆盖以验收记录为准，不把每条计划描述视为独立已执行用例。词书和/stats词汇扩展继续保留后续边界。


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 本批由当前会话实施，不自动派发代理。

**Goal:** 将已确认HTML接到真实个人生词，交付加入学习、认词/选义、逐字拼写、恢复、间隔复习和本轮结果的完整全栈流程。

**Architecture:** 复用公共dictionary_entry、个人user_word及来源、认证与CSRF。MySQL持久化学习状态、轮次和反馈；Pinia负责当前页面和输入交互。所有成功判定、归属、排期与幂等在后端处理；逐字提示在前端即时进行。

**Tech Stack:** Vue 3 + TypeScript + Pinia；Java 21 + Spring Boot；现有JdbcTemplate业务访问模式；MySQL + Flyway。不加依赖。

**Spec:** [阶段3方案](../specs/2026-10-19-phase-3-vocabulary-design.md)，以用户最新确认的融合认词、逐字拼写及本计划复习补充为准。

## 状态与交付范围

2026-10-19用户已确认原型，要求继续、不要拆太小步骤；再次询问复习设计。本计划把原方案个人学习和到期复习合为一个交付批次，内部测试任务不逐项打断用户。真实词书导入及词汇统计页随后处理，不删其规划。当前只完成检查与计划，以下复选框均未执行。

## Global Constraints

- 保留Vue 3 + TypeScript、Java 21 + Spring Boot、MySQL模块化单体及既有认证/生词收藏/阅读功能。
- 接口均在`/api/v1`；服务器取得用户身份，不接受客户端userId、复习日期、阶段或是否答对。
- 新增Flyway迁移，不修改已执行迁移、不清库；原型不进入生产构建。
- 间隔规则`fixed-v1-binary`，1/3/7/14/30天；Shanghai自然日统计，时间持久化Instant。
- 没有真实记录就显示空态，不预置个人学习记录，不把词汇练习算入阅读打卡。
- 每批最多20词；每日新增默认20、复习目标50；目标不是积压复习的硬上限。
- 本批仅个人生词。词书入口保留并明确未开放，不把离线字典冒充四级词书。

## Review Focus

1. 反馈已落库但响应丢失：相同命令重试返回原结果，不重复排期（任务1、2）。
2. 同账号双标签页：旧版本操作冲突后恢复服务器当前卡片，不覆盖新结果（任务2、3）。
3. 中途换号：清除旧账号卡片、输入和未发送意图，旧响应不能填入新账号页面（任务3）。
4. 收藏取消/字典变化：学习历史和当轮快照保留，用户可单独暂停学习（任务1、3）。
5. 输入法、粘贴和释义切换：只校验已提交文本；错误清空、完整正确只提交一次，失败不跳词（任务3）。

## 文件边界

后端新增`backend/src/main/java/com/englishhero/vocabulary/learning/`中的LearningController、LearningService、LearningMapper、LearningDtos、ReviewSchedule，均单独文件。复用VocabularyMapper的归属/用户锁与DictionaryMapper；不重构原收藏服务。新增`backend/src/main/resources/db/migration/V16__vocabulary_learning.sql`（实施前再次核对最大版本）。

前端新增`frontend/src/api/vocabulary-learning.ts`、`frontend/src/stores/vocabulary-learning.ts`、`frontend/src/vocabulary/spelling.ts`、`frontend/src/views/VocabularyView.vue`、`frontend/src/components/vocabulary/{LearningCard,LearningQueue,LearningSettings,LearningResult}.vue`及`frontend/src/styles/vocabulary.css`。只增量修改router/index.ts的/vocabulary组件、NotebookView.vue的加入入口与取消收藏说明。

测试新增backend的vocabulary/learning单元和IT、frontend/tests/unit/vocabulary-learning.spec.ts及vocabulary-spelling.spec.ts、frontend/tests/e2e/vocabulary-learning.spec.ts；原阅读/笔记本测试保留。

## 契约与数据决策

### 数据

六张新表，不修改旧收藏结构：

- `vocabulary_learning_setting`：user_id主键，new_goal(1..100)、review_goal(1..200)。无记录使用20/50，GET不创建。
- `vocabulary_learning_word`：id/user_id/entry_id，unique(user_id,entry_id)，active、stage(0..5)、due_at、last_passed_at、rule_version、revision；索引(user_id,active,due_at,id)。entry_id外键复用字典。取消收藏不删它。
- `vocabulary_learning_session`：id/user_id、kind(NEW/REVIEW)、mode(RECOGNITION/SPELLING)、status(ACTIVE/PAUSED/COMPLETED)、phase(QUESTION/DEFINITION)、version、current_item_id、创建/完成时间。用户写锁下保证最多一个未结束轮次；暂停也占用这一轮，恢复不重新抽题。
- `vocabulary_learning_item`：session_id/learning_word_id、position、passed、failure_count、内容/来源/中文选项快照、最近反馈；unique(session_id,learning_word_id)及unique(session_id,position)。重排position取当前最大值+1，轮次最多20项，锁保护更新。
- `vocabulary_learning_event`：user_id/session_id/item_id、反馈类型、判定、实际答复、阶段/到期前后快照、rule_version、created_at，保留失败与最终通过历史。
- `vocabulary_learning_command`：user_id/request_id唯一、命令类型、规范化请求hash、响应快照、created_at。用于start/feedback/next/pause/resume的幂等；相同ID不同参数409。重试先查命令，再校验版本。

私人表有用户外键；轮次项和事件关联使用复合归属键或服务校验防串用户。SQL约束限制枚举和阶段/目标范围。事务锁顺序固定为identity_user用户行→session→item→learning_word；先锁用户再生成新轮次、写命令及推进阶段。全部写入同一事务。

### API与DTO

均需认证、no-store，写接口沿用CSRF；沿用业务错误结构，400参数、404不属于本人/不存在、409版本或命令冲突。

- `GET /vocabulary/learning/overview`：真实候选/未学/到期数、nextDueAt、当日新增/复习通过数、目标、可恢复轮次摘要；GET不推进记录。
- `GET /vocabulary/learning/candidates?page=1&pageSize=20`：本人活动收藏，附joined/paused；最大50，不一次加载全部。
- `POST /vocabulary/learning/words`：`{wordIds:string[]}`，1..20项，全部验证本人活动收藏，按entry去重；重复加入保留已有阶段；任一非法整批回滚。
- `PATCH /vocabulary/learning/words/{id}`：`{active:boolean}`暂停/恢复；当前轮次包含的词返回409并提示完成本轮后调整，避免静默丢词。
- `GET /vocabulary/learning/words?page=1&pageSize=20`：已加入记录，含未学/阶段/到期和暂停状态，支持暂停后找到恢复入口。
- `PUT /vocabulary/learning/settings`：`{newGoal:number,reviewGoal:number}`字段错误就地返回。
- `GET /vocabulary/learning/session`：本人未完成轮次或null；完成轮次可由`GET /vocabulary/learning/sessions/{id}`取本轮结果。
- `POST /vocabulary/learning/sessions`：`{requestId,kind,mode}`，新词仅stage0且受当日剩余新增额度限制；复习仅due_at<=now，按due_at/id稳定排序，每批最多20。已有未完成轮次返回它，不并行生成第二轮。无候选返回明确空态，无假任务。
- `POST /vocabulary/learning/sessions/{id}/feedback`：`{requestId,expectedVersion,itemId,action,optionId?,spelling?,hadSpellingError?}`，action为KNOWN/UNKNOWN/CHOICE/SPELLING；CHOICE服务器验证当轮选项，SPELLING服务器精确忽略大小写匹配。hadSpellingError只能降低本轮后续排期，不能声明成功；不把前端上报次数作为权威统计。
- `POST /vocabulary/learning/sessions/{id}/next`：`{requestId,expectedVersion,itemId}`，只允许DEFINITION推进。失败项移至队尾，成功项移出待学；队列空才完成轮次。响应SessionDto。
- `POST /vocabulary/learning/sessions/{id}/state`：`{requestId,expectedVersion,status:PAUSED|ACTIVE}`；暂停不删除当前释义或队列。

上述路径实际前缀均`/api/v1`。SessionDto包含id/version/kind/mode/status/phase、最多20条中文队列项、当前QuestionDto或DefinitionDto、uniquePassed/total。识别题不发送correctOptionId；中文选项由保存的选项ID映射判分，干扰项中文归一后不得重复或等于当前任一义项，候选不足明确降级为认识/不认识。快照确保刷新不换题。

拼写逐字本地校验需要当前标准词形，QuestionDto仅给本人当前题的spellingTarget，作为学习辅助数据，不作为考试保密承诺；页面不在中文题面或队列渲染英文。最终全文仍由后端匹配，错误提交留原词。不能用前端`correct:true`代替判定。缺失中文/非支持的含空格词条明确不可练及原因，不悄悄消失。

### 复习与恢复

ReviewSchedule.next(stage, hadFailure, now)返回nextStage/dueAt/ruleVersion。stage0首次通过为stage1/隔1天；stage1..4复习通过依次stage2..5/隔3、7、14、30天；stage5以后保持30天。本轮任何UNKNOWN/错选/拼写错误，最终通过均stage1/隔1天。间隔按Shanghai本地日期加天保留当地时刻，再转Instant。Clock注入可控。未最终通过不推进阶段，也不把用户卡在“完成”状态。

成功反馈事务立即排期一次并进入DEFINITION；next仅切卡。服务器保留释义与结果，刷新不重复提交反馈。每个用户当前词条同一轮只通过一次；不同模式共享阶段，未结束不能切换模式新开一轮。

Pinia保存界面，后端保存事实。正确拼写前缀/提示开关仅按userId+sessionId+itemId+version存sessionStorage，恢复前必须先确认身份和服务端题目；账号变化清空内存和本标签缓存。输错整词清空；输入法合成只在结束时校验；精确拼对发一次反馈请求，网络失败保留输入和requestId，提供重试，不能提前显示成功。释义仍手动下一个。

## 任务1：加入学习与真实首页（一个全栈验收点）

**Interfaces:** LearningService.join(actor,wordIds)、overview(actor)、candidates(actor,page,size)、learningWords(actor,page,size)、setActive(actor,id,active)、settings(actor,input)。DTO按上方契约定义；前端api使用businessGet/businessWrite，不另建认证封装。

- [ ] 先写LearningEnrollmentIT：A收藏可加入；A不能加B收藏；重复入队只有一行；取消收藏后阶段/来源不丢；游客401/缺CSRF403；非法目标400；GET无写入。运行`./mvnw.cmd verify -Pmysql-it -Dit.test=LearningEnrollmentIT`记录真实失败。
- [ ] 新增迁移/Mapper/Service/Controller；身份复用IdentityService.current与SessionPrincipal，不改收藏接口语义。
- [ ] 替换/vocabulary占位，接真实概览、候选分页、加入和暂停/恢复、目标设置；Notebook保留原查看来源/取消按钮，新增加入学习入口。游客仍走统一登录。
- [ ] 新增前端测试“空收藏不伪造任务、加入失败可重试、分页和字段错误、换号不呈现旧数据”；运行定向单测/E2E并执行EnrollmentIT，区分Mock与真实数据库结果。

## 任务2：两种学习与到期复习（一个全栈验收点）

**Interfaces:** ReviewSchedule.next(int stage,boolean hadFailure,Instant now)；LearningService.start(actor,input)、session(actor)、feedback(actor,id,input)、next(actor,id,input)、setSessionState(actor,id,input)、sessionResult(actor,id)。所有变更返回版本化SessionDto。

- [ ] ReviewScheduleTest先覆盖固定Clock下stage0→1/1天、1→2/3天、2→3/7天、3→4/14天、4→5/30天、5→5/30天、任意失败重置1天；LearningSessionIT覆盖NEW不取到期词、REVIEW只取到期词、20词上限、无任务、到期排序、上海跨日、单词同轮唯一完成。
- [ ] 在LearningSessionIT增加响应丢失重放、同requestId异参数409、两个并发start共一轮、并发同版本反馈只成功一次、B访问A轮次404、字典更新后旧题快照稳定、暂停恢复释义；先运行确认失败。
- [ ] 实现快照题目、反馈事务、幂等命令、成功排期/失败队尾和恢复。所有查询有本人边界；GET不创建轮次。用服务端选项和拼写判定，拒绝乱mode/action/item/超长文本等参数。
- [ ] Vue接入融合认词/中文选义及中文拼写，保留原型语音/提示/设置和手动下一词；加载/失败局部反馈，按钮防重复；本轮结果来自服务器，不把中途出错统计冒充最终已学。
- [ ] 运行ReviewScheduleTest、LearningSessionIT及前端定向测试；复习与新词都实际走完一轮，验证刷新后当前释义仍在、不多排期。

## 任务3：输入、身份与阅读回归，统一交付

**Interfaces:** `validateSpellingPrefix(input:string,target:string):'EMPTY'|'PREFIX'|'INVALID'|'COMPLETE'`位于frontend/src/vocabulary/spelling.ts；store异步写操作保持requestId直到明确成功/冲突，识别身份代次丢弃旧响应。

- [ ] 单元测试逐字apple正确、首字母错/中间错整词清空、退格合法、大小写、粘贴超长、输入法合成、完整拼对只发一次；接口失败保持原词且不显示虚假释义。先失败，再实现。
- [ ] E2E覆盖两个模式、新词与复习、故意出错回队尾、最后1词失败可暂停、释义刷新、断网重试、两个标签冲突、切号清空及登录失效；1440/768/320px检查无横向溢出/导航遮挡/焦点丢失。无可靠数据不凑假选项。
- [ ] 用项目专用真实MySQL/Redis跑数据隔离/并发/幂等/跨日IT；不以Mock替代。按README先加载工具与Java21脚本，`backend/mvnw.cmd -version`确认实际JDK21，再`mvnw.cmd test`及`verify -Pmysql-it`；前端`pnpm --dir frontend test:unit`、`build`及相关Playwright学习/笔记本/阅读/认证回归。记录退出码、数量、跳过和环境阻塞。
- [ ] 更新docs/api.md、database.md、progress.md、README入口、decisions.md的调度及收藏边界；新增docs/acceptance/2026-10-19-vocabulary-personal-learning.md，列出Mock/真实联调/构建/视觉验收差别。用户检查完整闭环后，再处理真实词书和词汇统计扩展。

## 自审与执行交接

已覆盖新词和真实到期复习，不再拆成许多用户确认点。逐字错误由前端即时反馈，服务端独立校验最终答案；这属于学习而非保密考试。保留字典来源/许可及原阅读打卡规则。当前未执行迁移、未写业务代码、未运行本计划测试，不引用HTML通过来声称正式功能完成。

推荐在当前会话集中实施，保持现工作区及其他任务修改，不创建默认分支工作树丢掉未提交的阅读依赖。不自动提交整个脏工作区；需要提交时只审查本批文件并单独处理Git审批。用户已确认执行；当前保持工作区，不做Git集成操作。
