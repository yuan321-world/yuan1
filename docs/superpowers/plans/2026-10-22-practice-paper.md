# 整卷练习 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking. 建议由主代理直接实施，完成后一次独立审查；用户已确认直接实施；已完成并做一次独立最终审查，不逐任务派发代理。

**Goal:** 已发布题组组卷→普通用户整卷保存→统一提交→只读结果与本人历史，保持现有单项练习。

**Architecture:** practice模块新增独立整卷聚合，引用不可变practice_set_version，不创建七份单项轮次。判分和题型校验复用现有规则，整卷事务负责幂等和统一完成；Vue/Pinia使用独立整卷状态，适配现有阅读/听力播放器。

**Tech Stack:** Vue 3 + TypeScript、Pinia、Java 21 + Spring Boot、JdbcTemplate/MyBatis-Plus、MySQL、Flyway、项目已有Vitest/Playwright。

**Spec:** `docs/superpowers/specs/2026-10-22-practice-paper-design.md`（用户已确认）。

## Global Constraints

- 四级真题整卷：WRITING、LISTENING、CLOZE、MATCH、DETAILED_1、DETAILED_2、TRANSLATION七槽位，客观题25/10/10/5/5共55题。
- 不限时、不自动交卷。确认后允许缺答交卷；主观20000字符上限，不判分，不折算四级标准分。
- 引用冻结版本；整卷记录不插入单项轮次、不计入阅读打卡、不自动迁移演示localStorage。
- `/api/v1`；ADMIN组卷，私人轮次校验本人归属、CSRF、锁版本与请求幂等。
- 当前迁移最新V25，实施时再核对编号；不修改已执行迁移、不清库、不安装依赖。
- 仅项目开发/测试数据库与各自Redis；不创建开发用户模拟成绩、不自动发布未经校对的整卷。
- 保留所有已有未提交修改；本轮不提交，无关内容不覆盖。复用当前工作区，避免丢失尚未提交的题型功能。

## Review Focus

1. 组件改版与下架并发：新开始必须检查可用状态，旧轮次仍读旧版；任务1/2事务测试。
2. 两标签页一页已交卷：另一页未保存作文不能被加载结果覆盖；任务3单元与任务4浏览器。
3. 提交响应丢失或并发重复：同requestId返回同结果，不重复计分/轮次；任务2集成。
4. 空白、超长、非法题ID或跨组件选择：缺答按确认提交，非法输入拒绝并完整回滚；任务2规则/集成。
5. 切换部分销毁audio与组件：听力仍保持同一音频实例，草稿和焦点定位正确；任务4浏览器。

## 公共契约

ID在API中用字符串；数据库BIGINT。槽位类型为上述七个枚举，题号映射1–25/26–35/36–45/46–50/51–55，写作和翻译没有伪造选择题号。行政JSON响应沿用PracticeAdminController.reply，错误沿用BusinessException。

新增PracticePaperDtos：

- `ComponentInput(String slot, String setVersionId)`。
- `AdminInput(Long expectedLockVersion, String title, String examCode, Integer year, Integer month, String sourceNote, List<ComponentInput> components)`；title最长180，sourceNote最长2000，CET4、年份2000–2100、月份1–12且必须匹配来源资料。此为数据验证，不向用户提供12个月的空白筛选。
- `PaperQuery(String exam, Integer year, Integer month, int page, int pageSize)`；页大小1–50，默认20。
- `ResponseInput(String slot, String text, Map<String,String> choices)`；主观只允许text，客观只允许choices；题ID/选项按冻结版本验证。
- `DraftInput(long expectedLockVersion, String currentSlot, String currentQuestionId, List<ResponseInput> responses)`；七槽完整快照，缺项/重复槽拒绝，不将省略内容当清空。
- `SubmitInput(String requestId, long expectedLockVersion, String currentSlot, String currentQuestionId, List<ResponseInput> responses)`。
- `PaperView`安全目录/元数据；`PaperAttemptView`包含id、status、lockVersion、currentSlot/currentQuestionId、组件安全内容和responses。仅COMPLETED返回results/reference/explanation/transcript；ADMIN预览独立鉴权。

`PracticePaperRules.validateComponents`输出规范七槽顺序；`validateResponses`输出规范答案快照；`grade`输出组件正确数、总数、未答数及逐题结果。题目显示可转为现有Attempt的组件适配对象，但不把整卷ID当成单项ID请求接口或音频票据。

### Task 1: 管理组卷、发布与公开目录

**Files:** 新增backend practice包`PracticePaperDtos.java`、`PracticePaperRules.java`、`PracticePaperMapper.java`、`PracticePaperAdminService.java`、`PracticePaperCatalogService.java`、`PracticePaperAdminController.java`、`PracticePaperController.java`；新增迁移`V26__practice_paper.sql`（编号先核对）；新增`PracticePaperRulesTest.java`、`PracticePaperIT.java`；修改已有`SchemaMigrationIT.java`（用rg定位确切路径）。

**Interfaces:** Mapper注入JdbcTemplate，不访问其他@Repository代理的实例字段。AdminService提供create/save/get/review/publish/unpublish/versions，参数为SessionPrincipal、id、上述DTO或expectedLockVersion；CatalogService.list(PaperQuery, SessionPrincipal)、get(String id)。Controller对应设计的管理/公开路由。

- [x] 先写规则测试：完整七槽通过，重复/缺项/错类型/客观数量异常/不同考试年月拒绝。集成测试复用PracticeSubjectiveIT的专用库、TokenTestSupport及真实HTTP鉴权方式，夹具前缀paper_，独立清理，不依赖来源缓存。
- [x] 运行`./backend/mvnw.cmd -f backend/pom.xml -o '-Dtest=PracticePaperRulesTest,PracticePaperIT' test`，记录预期未实现失败，不以环境报错代替RED。
- [x] 迁移建paper/version/component/attempt/response/request六表。版本号及版本槽唯一，component引用冻结set_version；attempt有owner复合唯一、本人+进行中paper生成列唯一；response复合引用attempt对应paper版本及其槽；request按本人+操作+UUID唯一。提交状态/分数条件CHECK，版本和管理员外键，paper发布版必须属于该paper。
- [x] 实现规范化、ADMIN鉴权和乐观更新。改草稿重置来源确认；发布在事务内锁定组件set行，再校验仍PUBLISHED且引用版本属于该set。保留来源说明而非自动拼同套号。公开目录仅PUBLISHED，返回可用状态/原因，不泄漏题目参考；年月选项从实际发布目录计算。
- [x] 增加admin GET `/papers/components`候选接口，按考试/年月和类型返回已发布安全标题、版本、题数；需ADMIN，不让前端遍历所有题库。放入管理Controller且避免动态id路由冲突。
- [x] 真实库验证匿名/USER管理拒绝、旧锁409、未确认发布拒绝、v1不被v2覆盖、下架隐藏目录，迁移重复执行0次；更新表数量基线（当前47→53，以实际新增六表核对）。全部GREEN后再进入任务2。

### Task 2: 本人轮次、统一草稿与事务提交

**Files:** 新增`PracticePaperAttemptService.java`、`PracticePaperContentService.java`；扩展Task1 Mapper/Controller/Rules/IT；必要时新增`PracticeAnswerRules.java`给单项与整卷共用选择校验，定向修改`PracticeAttemptService.java`；音频票据扩展`PracticeAudioController.java`、`PracticeAudioService.java`。

**Interfaces:** AttemptService.start(SessionPrincipal,String,PracticeDtos.StartInput)、get(SessionPrincipal,String)、save(SessionPrincipal,String,DraftInput)、submit(SessionPrincipal,String,SubmitInput)、history(SessionPrincipal,PaperQuery)。ContentService.loadComponent(versionId, revealReferences)返回组件安全内容，由已鉴权调用方决定是否解锁。新增POST `/practice/paper-attempts/{id}/audio-ticket`，从本人冻结听力组件获得asset；不借用单项attemptId，沿用音频票据有效期和资源鉴权。

- [x] 写RED：普通USER开始/恢复同一进行中整卷；其他USER404；55题分型合法答案保存、主观文本/当前位置刷新恢复；进行中递归无正确项/参考/原文字段；音频票据归属拒绝。
- [x] 写RED：CLOZE重复选项拒绝、MATCH允许复用段落、跨组件questionId与无效选项拒绝，超20000字符拒绝；确认提交空白部分成功、总题数仍55、无主观分数。
- [x] 实现start事务检查组件当前发布状态，生成七个response并冻结paper_version；读取已有本人轮次不受组件后来下架影响。草稿逐项校验完整快照和currentQuestionId归属，更新锁版本；不改任何单项轮次表。
- [x] 提交锁定attempt，同一事务验证最终答案、判分、冻结结果及状态。幂等命令摘要包含id和完整规范载荷；成功请求先检查幂等结果再判旧锁，相同请求成功复用、不同载荷409；非业务异常不吞，保证回滚。
- [x] 集成用双并发请求验证只完成一次、同UUID变载荷409、第二次新UUID不能改完成答案；测试注入持久化失败确保所有response及attempt回滚；新练习不覆盖旧完成版。
- [x] 运行任务1命令及`PracticeSubjectiveIT,PracticeClozeIT,PracticeMatchIT,PracticeListeningIT`相关真实库回归，确认单项成绩/错题行为未改。此时不部署、不写开发学习轮次。

### Task 3: 前端API与串行保存状态

**Files:** 新增`frontend/src/api/practice-paper.ts`、`frontend/src/stores/practice-paper.ts`；新增`frontend/tests/unit/practice-paper.spec.ts`。

**Interfaces:** API导出listPapers/getPaper/startPaper/getPaperAttempt/savePaperDraft/submitPaper/listPaperHistory和管理create/save/review/publish/unpublish/versions/listComponents，类型对应公共契约。Store导出usePracticePaperStore，含load(id)、setText(slot,text)、setChoice(slot,questionId,choice)、navigate(slot,questionId)、flush()、submit()、recoverySnapshot、loadError/saveError及提交不确定状态。

- [x] 单元先RED：约900ms防抖、写请求串行，保存期间新编辑最终保留；提交先flush、固定UUID重试；慢响应不能回写覆盖较新输入。
- [x] 实现Pinia草稿与组件进度；服务器冲突进入显式恢复状态，保留完整本地快照，禁止自动重提覆盖服务器，允许复制文本。账号退出清理内存，不写原型localStorage。
- [x] 单元先RED后GREEN：409服务端已完成仍保留本地作文/译文；断网flush不清dirty；读取新轮次时旧请求不能污染新状态；重复点击交卷仅一次请求。
- [x] `pnpm --dir frontend test:unit tests/unit/practice-paper.spec.ts`及`pnpm --dir frontend typecheck`退出0；不因本任务重写其他store。

### Task 4: 管理与学习正式界面

**Files:** 新增`AdminPracticePapersView.vue`、`AdminPracticePaperView.vue`、`PracticePaperLibrary.vue`、`PracticePaperAttemptView.vue`、`components/practice/PracticePaperResult.vue`、`styles/practice-paper.css`；修改`router/index.ts`、`views/PracticeLibraryView.vue`、`views/AdminPracticeSetsView.vue`。新增`tests/e2e/practice-paper.spec.ts`。

**Interfaces:** 保留`/practice/cet?...&section=paper`栏目，挂载PracticePaperLibrary避免请求旧单项目录；`/practice/paper-attempts/:id`受登录保护；管理`/admin/practice/papers`及`/admin/practice/papers/:id`（new）保持ADMIN守卫。组件使用Task3 API/store，不自行提交单项轮次。

- [x] 管理页提供全部整卷、新建、七槽已发布版本选择、来源说明、预览、校对、发布/下架与版本列表；沿用锁版本反馈。现有管理列表新增组卷入口，不删除新建/导入单项功能。
- [x] 整卷栏目仅全部试卷/我的练习，默认折叠年月，真实目录聚合筛选；查询更新保留滚动和旧结果直到新结果返回，不插入加载行导致页面跳动。无内容时明确没有已发布整卷，不冒用旧题组。
- [x] 按已确认HTML落地作答布局。复用Cloze/Match/Listening现有受控播放器，必要新增可选audioTicketProvider而保持单项默认行为；Listening用v-show保持audio实例，阅读选题保留局部位置。主观输入保留紧凑尺寸与字数。
- [x] 交卷前列未答、空白和作文建议字数；确认后调用store.submit。成功结果只读、参考解锁，点击错题/未答定位；返回目录保留筛选；再练创建新轮次。
- [x] Mock浏览器先RED后GREEN：1440/320px四部分切换与同片段多题、音频DOM实例保持、保存失败重试、冲突保留文本、取消/确认交卷、结果/历史，键盘焦点与dialog取消；无溢出/重复题号。
- [x] 路由离开与同路由换ID统一等待flush；失败阻止静默离开、提示复制。运行定向E2E及`pnpm --dir frontend build`，再跑旧听力/主观/目录相关E2E。

### Task 5: 真实联调、审查与交接

**Files:** 新增`docs/acceptance/2026-10-22-practice-paper.md`；更新`docs/api.md`、`docs/database.md`、`docs/practice-guide.md`、`docs/README.md`、`docs/progress.md`；扩展PracticePaperIT内真实浏览器夹具或新增`frontend/tests/real/practice-paper.cjs`，不含凭据。

- [x] 启用项目专用测试Redis；Maven `-v`输出实际Java21再执行整卷与受影响真实库测试。测试库原创夹具构造完整55题，管理员发布测试卷，普通USER在1440/320px从目录开始→各部分保存→刷新恢复→统一交卷→参考→历史；第二USER不能读取。
- [x] 审阅当前任务差异并进行一次独立最终审查，修复实际发现后只复验受影响检查；不逐文件/逐任务派发。若用户不授权独立审查则先保持工作成果并说明未独立审查，不能伪称已审。
- [x] 记录全部命令、退出码、真实库/Mock/构建边界，更新计划复选框、验收和进度。只用本轮文件做差异检查，不暂存/提交历史未追踪功能。
- [x] 开发部署前抓旧题组/版本和目录只读基线；部署新JAR与dist，执行新迁移，确认旧内容不变、健康接口可用。可保存候选组卷草稿（需保留翻译套号说明），不自动发布；真实内容组卷校对/发布须明确记录，不在开发账号写演示成绩。
- [x] 展示正式管理入口供确认，说明尚未发布时用户目录为空的原因。AI批改、六级和其他新阶段不在本计划。

## 执行命令与记录

PowerShell先`. ./scripts/use-workspace-tools.ps1`，Maven实际JAVA_HOME为Java21并将其bin置PATH前。点号-D参数整体加引号；现有测试配置/密钥只由忽略文件加载，不输出。缓存日志使用`.tool-cache/practice-paper-implementation/`。Playwright依据现有E2E_EXTERNAL_SERVER/E2E_PORT或测试夹具随机端口配置，不能把Mock通过写成真实数据库联调。

计划自查：覆盖七槽发布、来源冲突、冻结版本、音频鉴权、本人保存、并发/回滚、完整快照、缺答交卷、恢复副本、历史与正式紧凑界面；本计划没有安装依赖、生成真题或覆盖旧记录的步骤。2026-10-23完成；命令与实际限制见同日整卷验收及忽略缓存ledger。

