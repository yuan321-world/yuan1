# DeepSeek 主观题学习反馈实施计划

2026-10-26：用户已批准，任务1–4代码与离线验证完成；任务5的原创样本、HTTP Stub及配置/验收记录完成，真实模型校准仍待本地密钥与实测预算。详见 [最终验收](../../acceptance/2026-10-26-practice-ai-review.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让本人已提交的单项/整卷作文与翻译获得可恢复、可追溯的DeepSeek学习反馈。

**Architecture:** practice/review职责隔离，MySQL持久任务与限额账本，服务端官方HTTP适配器；短事务领取与预留，事务外外呼，条件更新结果。Vue/Pinia复用一个反馈组件，原判分与参考入口保持。

**Tech Stack:** Vue3/TypeScript/Pinia、Java21/Spring Boot4、MySQL/Flyway、现有HTTP与测试工具；不增AI SDK/消息中间件。

**Spec:** [设计及2026-10-26细化](../specs/2026-10-25-practice-ai-review-design.md)。用户认可原型并选择DeepSeek；本计划已于2026-10-26获准，代码已实施，V29已应用专用测试库/开发库；真实API未外呼。

## Global Constraints

- `/api/v1`、服务端本人鉴权与CSRF；单项/整卷均须COMPLETED且有非空主观响应。
- 默认关闭；建议Flash非思考、非流式JSON、8000输出token、128KiB响应、5秒连接/120秒总超时。
- 输入现有20000字符上限；输出评分0–15，不折算考试分，不覆盖原作、原卷与历史结果。
- 建议用户日10次、每来源最多3尝试/间隔10秒、每用户活动1、全局worker2；日$1预留总额/请求$0.05，须用户确定后开启。
- 密钥仅后端本地配置/环境变量；生产日志无原文、上游body或凭证。真实调用不进入自动测试。
- 只新增V29（执行前核对最高迁移避免冲突）；保留所有旧迁移及脏工作区，不提交不相关内容。

## Review Focus

1. 多标签/并发申请同来源，必须复用任务且一次外呼；失败重试是新尝试并可能再次收费。
2. 外呼成功但本地结果写入前崩溃，不可自动重发；未知结果保留预留并提示用户。
3. 单项和整卷ID碰撞、他人ID、槽位伪造、旧轮次，必须读正确冻结响应并拒绝越权。
4. 上游空JSON、截断、无usage、虚构引文、重复原句、多语种/注入内容，不能展示错误评分或HTML。
5. 日界/用户切换/组件卸载，预算日期与结果归属不能串，晚响应不能覆盖新用户状态。

---

### Task 1: 持久任务与本人来源申请API

**Files:**
- Create: `backend/src/main/resources/db/migration/V29__practice_subjective_review.sql`
- Create: `backend/src/main/java/com/englishhero/practice/review/PracticeReview{Controller,Service,Mapper,Dtos,SourceResolver}.java`（五个文件）
- Modify: `backend/src/main/java/com/englishhero/practice/PracticeSubjectiveService.java`、`PracticePaperService.java`（必要时仅增加受控冻结来源读取方法，拒绝复制判分逻辑）
- Test: `backend/src/test/java/com/englishhero/practice/PracticeReviewIT.java`
- Document: `docs/database.md`、`docs/api.md`

**Interfaces:** `create(userId,CreateReview):ReviewDto`、`get(userId,id):ReviewDto`、`history(userId,sourceType,attemptId,slot):List<ReviewDto>`；`resolve(userId,sourceType,attemptId,slot):FrozenReviewSource`。DTO/路径与设计一致，JSON字段名用设计定义。

- [ ] 编写并运行失败IT：匿名401/他人404、进行中400、类型/槽位不符400；单项/整卷同数字ID不混用；有效本人来源202且正确冻结版本/原文哈希。
- [ ] 补并发IT：同requestId20次并发、同源不同requestId并发仅一个活动任务；同键异请求409；成功已有结果复用；失败最多3次、10秒冷却；申请不改原轮次DTO。
- [ ] 实现三表及约束、来源读取和原子申请；同requestId重放先查归属及原请求哈希，再处理额度，不能二次扣额。任务状态QUEUED/RUNNING/SUCCEEDED/FAILED，错误码安全枚举。
- [ ] 使用专用english_hero_test执行 `./mvnw.cmd -o verify -Pmysql-it -Dit.test=PracticeReviewIT`；记录RED/GREEN与原业务保持。同步API草案转实际契约，不跑开发迁移。

### Task 2: DeepSeek HTTP适配与结果校验

**Files:**
- Create: `backend/src/main/java/com/englishhero/practice/review/DeepSeekReviewClient.java`、`PracticeReviewProperties.java`、`PracticeReviewResultValidator.java`、`PracticeReviewPrompt.java`
- Create: `backend/config/application-ai.example.yml`（空密钥占位）
- Modify: `backend/src/main/resources/application.yml`（关闭默认值）
- Test: `backend/src/test/java/com/englishhero/practice/review/DeepSeekReviewClientTest.java`、`PracticeReviewResultValidatorTest.java`

**Interfaces:** `DeepSeekReviewClient.review(FrozenReviewSource):ProviderReviewResponse`；响应含经校验结果、安全失败类别及usage。client不写业务表。`validate(source,content):ReviewResult`；prompt固定版本`subjective-review-v1`、rubric`learning-15-v1`。

- [ ] 编写本地HTTP Stub失败测试：Bearer存在但日志不含值；官方参数与json示例、thinking disabled、禁用tools、无账号字段；200有效响应转DTO。
- [ ] 对空content、非法/截断JSON、finish_reason=length、401/402/429/5xx、慢响应、超128KiB写失败用例；每次调用最多一条HTTP请求，不隐式重试或自动转Pro。
- [ ] 校验score整数0–15、3维度枚举、批注≤20、遗漏≤10、每段说明≤1000字符、修改稿≤20000字符、总评≤2000字符；引用按quote+1起occurrence定位UTF-16区间，翻译sourceQuote也必须匹配源文。字段错误或引用无法定位则整次安全失败，不给“成功但虚构”的反馈。记录使用量异常，不伪造usage。
- [ ] 实现适配/提示/校验与长度边界；用户输入作为数据隔离，输出不使用v-html；不展示reasoning_content。单元全部通过，无真实网络与凭证。

### Task 3: 任务领取、费用预留和中断恢复

**Files:**
- Create: `backend/src/main/java/com/englishhero/practice/review/PracticeReviewWorker.java`、`PracticeReviewBudget.java`
- Modify: Task1 Mapper/Service，Task2 Properties；注册受控scheduler及有界执行器，不使用通用无界线程池。
- Test: `backend/src/test/java/com/englishhero/practice/PracticeReviewWorkerIT.java`、`backend/src/test/java/com/englishhero/practice/review/PracticeReviewBudgetTest.java`

**Interfaces:** `claimNext(now):Optional<ReviewJob>`、`complete(id,leaseToken,response)`、`fail(id,leaseToken,error)`；只有lease持有者能结算。budget `reserve(userId,day,taskId)`、`settle(taskId,usage)`、`releaseUnsent(taskId)`；金额以十进制或整数微美元保存，不用浮点。

- [ ] 写失败测试：双worker竞争一次调用；并发预算不透支；Asia/Shanghai日界按预留日结算；仅未发送任务可无条件释放。
- [ ] 测试领取后崩溃、外呼成功但结果提交前崩溃、180秒lease失效：FAILED/OUTCOME_UNKNOWN、不自动外呼、保留预留；旧worker迟到不能覆盖新状态。QUEUED10分钟过期释放，禁用后不领取。
- [ ] 写测试：usage缺失保留$0.05；有usage按已记录价格保守结算；不可识别模型/价格或超过输入上界拒绝发出；已成功重复读取与幂等重放不消耗预算。
- [ ] 实现每秒领取、全局2并发、120秒整次超时；网络不占数据库事务。失败仅用户主动申请新尝试，不调用上游取消/无幂等保证的自动重试。
- [ ] 运行Task1–3专用库回归、既有 `PracticePaperIT` 和 `PracticeSubjectiveIT`（执行前核实类名），核对既有并发提交、权限与原成绩。无付费调用。

### Task 4: 共用Vue反馈区与账号隔离

**Files:**
- Create: `frontend/src/api/practice-review.ts`、`frontend/src/stores/practice-review.ts`、`frontend/src/components/practice/PracticeReviewPanel.vue`、`frontend/src/styles/practice-review.css`
- Modify: `frontend/src/views/PracticeSubjectiveAttemptView.vue`、`PracticePaperAttemptView.vue`
- Test: `frontend/tests/unit/practice-review.spec.ts`、`frontend/tests/e2e/practice-review.spec.ts`

**Interfaces:** `loadHistory(source)`、`requestReview(source,requestId,consentVersion)`、`refreshReview(id)`；source明确带type/attemptId/slot。保持auth generation/epoch取消迟到结果模式；每来源独立状态，不把当前草稿当旧提交。

- [ ] 写RED用例：草稿禁用、提交后申请、首次发送说明确认、处理中按钮禁用、刷新恢复、切换单项/整卷与W/T、旧轮次历史、登录用户切换迟到请求丢弃。
- [ ] 双端Mock覆盖429限额、服务关闭/未配置、失败重试及未知结果“重试可能再次计费”、成功3标签与中英定位；注入HTML仅文本显示、分数始终学习参考。
- [ ] 用共用组件移植已认可原型紧凑反馈，置作答下方；整卷仅COMPLETED显示W/T反馈入口，右侧题号卡不改。首次说明确认后该请求携版本，取消不申请；参考范文/译文保持。
- [ ] 每3秒轮询当前可见的活动任务，终态或卸载/隐藏页暂停；返回恢复，退出清空，防重复点击保留requestId直到服务端状态明确。
- [ ] 运行相关unit、桌面1440/手机320 E2E、类型与构建；检查无横向溢出/导航位置跳变。Mock结果不能称真实AI通过。

### Task 5: 原创样本校准、配置说明和最后交接

**Files:**
- Create: `content/ai-review/original-calibration.json`（10作文+10翻译及人工预期，不含用户私文）
- Create: `docs/ai-review-setup.md`、`docs/acceptance/2026-10-26-practice-ai-review.md`（实施时以实际日期命名）
- Modify: `docs/progress.md`、`docs/README.md`、`docs/decisions.md`

- [ ] 样本至少覆盖无错误但表达不同、重复引文、遗漏、超短文/超长文、中文夹杂、指令注入与可选润色。预期写清哪些是错、哪些可接受；不以模型自动评分自证。
- [ ] 先在专用库、HTTP Stub完成20样本与本人/跨用户/历史全流程，核对原卷成绩及题库快照不变；完整运行后端相关测试、前端单元/E2E/构建，记录退出码。
- [ ] 提供忽略的本地配置填写步骤，不要求聊天粘贴密钥。用户确定日预算、数据说明及实测费用范围后，再开仅本地小样本联调；无密钥或无授权则明确记“真实AI未验证”。
- [ ] 实测记录实际model、token用量、延迟/错误率、已知语法纠正、误判/遗漏；人工审阅20份，无虚构引文与隐私串线。无错误样本不强制制造纠错，超时不重试计费。质量不足则记录并调整，不直接对外开放。
- [ ] 独立审查或有记录的逐项自审后修复并回归。只有用户确认开放范围后才配置开发环境开关；迁移前保留只读业务快照，部署后核对，不用开发作答夹具。无自动Git提交/推送。

## 执行前待确定

建议先按Flash/非思考与每应用日$1、每用户日10次开发可配置限额；这些是提案，不代表已允许花费。用户审阅计划后再实施；真实调用时另确认可用密钥和允许的小样本费用范围。对外开放前补齐实际账号的数据政策核对与用户说明。
