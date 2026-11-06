# 阶段4专项练习集中实施计划

2026-10-20用户纠正后的实施补充：专项原文独立发布，已移除下文早期「文章须公开」前提。发布只检查原文版本归属/题目/来源/校对；新开始由题组发布状态决定。专用原文默认直接录入，仅可选复用阅读文章。见 `docs/acceptance/2026-10-20-practice-material.md`；下文保留原实施历史。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. 实际执行状态与证据记录于正式验收和任务台账，以下细目保留设计参考。

**Goal:** 按已确认学习端和管理端原型，交付题组草稿→逐题校对发布→题库→保存续答→整组交卷→解析/错题重做/历史的一条真实全栈流程。

**Architecture:** 共用文章与现有quiz/version/question，新增ARTICLE/PRACTICE用途隔离及六张专项关系表。专项整组作答不调用文章逐题提交，不写阅读打卡或词汇排期。正式数据和并发约束在服务端，Pinia仅保存交互与尚未确认的本地输入。

**Tech Stack:** 保持现有Vue 3 + TypeScript + Pinia、Java 21 + Spring Boot、MyBatis-Plus/MySQL模块化单体，以及项目现有JdbcTemplate Mapper风格；不新增框架或替换pnpm。

**Spec:** [正式设计](../specs/2026-10-19-phase-4-practice-design.md)；管理端以[已确认HTML](../../prototypes/practice-admin-preview.html?preview=admin-review-2)为准，学习端以[方案检查版](../../prototypes/practice-workflow-preview.html)为准。

**Status:** 2026-10-19，用户确认后已在当前目录集中实现任务1–4，完成真实数据库/浏览器及独立审查修复；任务5本地交接结果见正式验收。未提交/推送。细粒度示例测试按实际HTTP职责合并，以下实施细目保留作设计参考，不声称每个示例方法名或命令逐字执行。实际证据以[验收](../../acceptance/2026-10-19-practice.md)为准。

## Global Constraints

- 接口路径使用`/api/v1`，沿用JWT身份、CSRF与业务字段错误；密码、Cookie、Token、AI密钥不得输出/入Git。
- 每组一篇材料，1–10道A–D四选一；题目类型MAIN_IDEA/DETAIL/INFERENCE/VOCABULARY/ATTITUDE。
- 首批仅CET4/DETAILED开放，CET6/POSTGRAD1/POSTGRAD2及其他题型为明确规划，不创建假支持数据。
- 来源ORIGINAL/PAST；原创year/month/paperNo全NULL，真题需真实来源及时间/使用依据。本批不引入真实四六级内容、不伪造年份，不自动发布演示题。
- 公共及私人分页从1开始，默认20/最多50；管理端额外支持原型5/10/20条每页和COUNT排序。
- 只新增V19增量迁移，不改V1—V18、不清库；只用english_hero_dev/english_hero_test与现有专用Redis配置。
- 保留文章管理/文章练习/游客题组/打卡/背单词及其入口，旧界面不为抽组件而重构；用途隔离是旧阅读后端的必要最小改动。
- 原型独立保留，不进入Vue构建；黑白灰/柔和粉色与确认布局延续。未知数量显示真实空态，不硬编码成果或个人记录。
- 每个任务按失败测试→确认失败→实现→同组检查通过推进；交付按以下连续流程集中执行，不把内部任务拆成逐项用户确认。

## Review Focus

1. 由旧quizId/旧游客接口获取专项答案：任务1覆盖全部旧入口，专项交卷前无答案字段。
2. 草稿不完整也应可保存；改题不能保留旧校对：任务1分离结构校验与发布校验，并验证逐题hash失效。
3. 保存时继续输入/清空/切题或身份切换：任务2的串行队列不能覆盖较新的本地输入，失败退出保留可重试状态。
4. 下架/更新期间恢复旧任务、不同来源错题并发：任务2/3固定版本，恢复不跨题组/账号/源版本串用。
5. 请求超时后重试与同ID异参数：任务1/2/3/4验证幂等、并发唯一及冲突，不能二次发布或重算错题。

## 文件与契约分工

新增后端包`com.englishhero.practice`，admin/catalog/attempt三个职责，通过下面固定DTO互通；新增测试留在`com.englishhero.reading`包内复用包私有ReadingHttpITSupport，不为此扩大生产可见性。

| 文件 | 职责 |
| --- | --- |
| `backend/src/main/resources/db/migration/V19__practice.sql` | 用途列/索引、六表与外键/唯一/CHECK约束 |
| `backend/src/main/java/com/englishhero/practice/PracticeDtos.java` | 本计划全部HTTP records及分页/过滤条件 |
| `.../practice/PracticeDraftRules.java`、`PracticePublicationChecks.java` | 有界可缺项草稿规范化、汇总发布问题/规范化hash |
| `.../practice/PracticeQuizMapper.java`、`PracticeSetMapper.java` | 固定PRACTICE用途读写共享题目表、题组/版本/目录持久化 |
| `.../practice/PracticeAdminService.java`、`PracticeAdminController.java` | 草稿/单题校对/发布/下架/版本/导入 |
| `.../practice/PracticeCatalogService.java`、`PracticeCatalogController.java` | 公共目录/facets与本人题组状态 |
| `.../practice/PracticeAttemptMapper.java`、`PracticeAttemptService.java`、`PracticeAttemptController.java` | 私人轮次、草稿、幂等、整组判分、历史和错题 |
| `frontend/src/api/practice.ts`、`admin-practice.ts` | DTO与公共/私人/ADMIN请求，复用business/publicReadingGet |
| `frontend/src/stores/practice-attempt.ts` | 身份隔离、400ms防抖、串行flush/失败重试/冲突保留 |
| `frontend/src/practice/admin-draft.ts`、`catalog-query.ts` | 管理端当前题/问题定位/表单状态，URL条件解析与清空 |
| `frontend/src/views/{AdminPracticeSetsView,AdminPracticeEditorView,PracticeEntryView,PracticeLibraryView,PracticeAttemptView}.vue` | 独立正式管理列表/编辑及学习端入口/题库/作答 |
| `frontend/src/components/practice/{PracticeFilters,PracticeReader,PracticeQuestions,PracticeResult,PracticeMistakes,PracticeQuestionEditor,PracticeQuestionOverview,PracticePublicationChecks,PracticeAdminPreview}.vue` | 按职责承接确认布局，不把整个应用塞进单个组件 |
| `frontend/src/styles/practice.css` | 全部规则限于专项根类，避免影响旧页面 |

以上`.../practice/`均相对于`backend/src/main/java/com/englishhero/`。旧文件修改限定在QuizMapper用途、SecurityConfig窄公共GET白名单、router/AdminLayout新入口、test helper清理和相关文档。旧AdminArticleQuizView、AccountQuizPanel、词汇业务文件不改。

### 固定DTO与HTTP约定

ID对外均为string、后端解析正long；时间ISO Instant，所有选择A–D或null。原型整数answer转换为correctOption，不把HTML演示对象直接当服务端模型。

- `Metadata(title,titleZh,sourceKind,topic,year,month,paperNo,sourceName,sourceUrl,usageNote)`：year 2000..服务器Clock当前年，month 1..12，paperNo 1..9；标题200/中文标题500/来源500/URL2000/说明2000字符上限。
- `DraftInput(articleId,articleVersionId,examCode,readingType,metadata,questions,expectedLockVersion)`，后端questions复用`QuizDtos.QuestionInput`。未选答案存null，stem/options/explanation允许空字符串；前端专项类型`PracticeQuestionInput = Omit<QuizQuestionInput,'correctOption'> & {correctOption:string|null}`，旧阅读TS类型不改。结构限制1..10题/恰好四个选项/key唯一/枚举及文本长度。考试只允许CET4/DETAILED写入，规划过滤返回空态。
- `AdminSet(id,articleId,articleVersionId,examCode,readingType,metadata,questions,reviewedQuestionKeys,sourceReviewed,status,revision,lockVersion,article,checks)`；article复用现有`ReadingDtos.ArticleVersionDto`，后端由ArticleMapper.VersionRow映射；前端用现有`api/admin-articles.ts`的ArticleVersion，不改旧接口。仅ADMIN可见答案。
- `ReviewInput(expectedLockVersion,kind,questionKey,reviewed)`，kind QUESTION/SOURCE，QUESTION必须有当前key，SOURCE不带key。review请求不能带题目内容；一次只一项。
- `PublicationCheck(path,questionKey,message)`，path如`questions[q_key].options`/`metadata.year`，以key定位，不用排序后可能过期的数组下标。`AdminSet`附当前checks列表供前端汇总，发布时后端重新计算。
- `SetSummary(id,setVersionId,metadata,examCode,readingType,questionCount,publishedAt,personalState,currentAttemptId)`。游客不含personalState/currentAttemptId，答案和解析永不进入公共摘要。
- `StartInput(setVersionId,requestId)`；`DraftAnswersInput(expectedVersion,answers:[{questionId,choice}],currentQuestionId)`；`SubmitInput(expectedVersion,requestId,confirmUnanswered)`；`RetryInput(requestId)`。
- `Attempt(id,setId,setVersionId,quizVersionId,kind,sourceAttemptId,status,version,currentQuestionId,total,article,questions,answers,correctCount,startedAt,completedAt)`。IN_PROGRESS题目严格使用无答案`PublicQuestion`，答案仅含draftChoice；COMPLETED另返回每题submittedChoice/correct/correctOption/explanation，未答为null/false。
- `Page<T>(items,page,pageSize,total)`复用ReadingDtos.PageDto；API返回直接JSON，不新增响应包裹层。`PracticeQuery(exam,readingType,source,topic,state,year,month,paperNo,q,sort,page,pageSize)`，admin另有status。
- `Facets`按当前条件返回source/readingType/topic/state及真实PAST时间选项和匹配数，计算某组时只排除该组自身条件；排序枚举公开LATEST/TITLE，admin NEWEST/TITLE/COUNT，均以id稳定兜底。
- `ImportInput(requestId,articleId,articleVersionId,examCode,readingType,metadata,questions)`，单组JSON上限256KiB/10题，不接受review内部字段。validate无副作用；confirm仅DRAFT，重复同请求返回同set，同ID异内容409。

错误码沿用字段错误`VALIDATION_ERROR`，专项新增`PRACTICE_CONFLICT`、`PRACTICE_SET_CHANGED`、`PRACTICE_REVIEW_REQUIRED`、`PRACTICE_UNANSWERED_CONFIRMATION_REQUIRED`、`PRACTICE_REQUEST_CONFLICT`、`PRACTICE_ALREADY_EXISTS`、`PRACTICE_NO_MISTAKES`；本人资源不存在或他人资源均404。重复建组409响应可附`existingSetId`供明确打开，不覆盖草稿。

## Task 1：真实管理草稿→校对发布→公共目录（首条纵向流程）

**Files:** 创建上述V19、DTO/rules/quiz/set mapper、admin/catalog service/controller及管理列表/编辑和入口/题库Vue；修改`reading/quiz/QuizMapper.java`、`config/SecurityConfig.java`、`frontend/src/router/index.ts`、`frontend/src/layouts/AdminLayout.vue`。测试新增`backend/src/test/java/com/englishhero/reading/{PracticeRulesTest,PracticeAdminIT,PracticeIsolationIT,PracticeCatalogIT}.java`与`frontend/tests/e2e/practice-admin.spec.ts`。

**Interfaces:** 复用`QuizRules.questions(QuizDtos.Input)`及public `ArticleMapper.article(long,boolean)`/`version(long,long)`，当前身份由IdentityService取。产出`PracticeAdminService.create/get/save/review/publish/unpublish/versions/version`及`PracticeCatalogService.catalog/sets/facets/detail`，对应正式设计路由。

- 写失败测试：`incompleteDraftPersistsButCannotPublish`断言空题干/null答案可存草稿、发布400且逐项checks；`legacyEndpointsRejectPracticeQuiz`断言旧公开/游客/管理员文章quiz/quizId开始入口均不能访问PRACTICE，旧ARTICLE原测试仍能通过；`independentQuizzesShareArticleVersion`断言ARTICLE/PRACTICE可并存。
- 在backend运行`./mvnw.cmd -Dtest=PracticeRulesTest test`、`./mvnw.cmd -Dit.test=PracticeAdminIT,PracticeIsolationIT,PracticeCatalogIT verify -Pmysql-it`，确认因新增行为缺失而失败，记录退出码；不把编译错误当行为失败。缺测试库时先按README准备，不清库绕过。
- 实施V19：reading_quiz加usage默认ARTICLE、先建旧FK所需非唯一前缀索引，再将唯一键替换为(article_id,article_version_id,usage)。新增设计六表；为set/version/article/quiz及attempt/source/answer设联合归属外键；practice_request增加set_id，按operation约束结果引用；active_set_id唯一(user,kind,active_set_id)。不得修改旧attempt表或现有数据内容。
- 旧QuizMapper.article/get/create默认ARTICLE；新增PracticeQuizMapper固定PRACTICE，题目版本/题目发布仍写原表。PracticeSetMapper负责分页COUNT/EXISTS查询，查询与总数共用条件构建，参数化q并转义LIKE通配符，sort白名单；不一次取全表在Java筛选。
- 实施结构规范化、完整checks和服务端review hash。发布调用QuizRules并追加忽略大小写的重复选项检查；保存删除已变questionKey校对，总确认遇内容/顺序/来源变化失效；review先验证当前已保存对象、单项完整性，再更新确认和lockVersion。review变动后总确认撤销。旧阅读规则不改。
- 发布事务按文章→quiz→set锁定，检查当前文章公开状态及所选版本归属，生成不可变quiz/set版本并推进revision；下架保留快照。`PracticeAdminService.publish(actor,setId,expectedLockVersion)`在锁后重新完整验证，不接受客户端checks/score/reviewed布尔替代。
- 前端实现确认表格/手机卡片、搜索/联动组合筛选/更多条件/排序分页；编辑原文/当前题目/题号问题导航、全部题目总览、检查定位、预览/历史/下架。切题保留Pinia/组件草稿；勾校对先flush草稿保存，再单独review请求，失败保留内容且不显示已校对。新建同文章冲突明确打开已有题组；不造多个独立题库。
- 增补并跑通`reviewHashesInvalidateOnlyChangedQuestions`、`sourceReviewCannotBypassQuestionReview`、`reviewedJsonCannotBypassChecks`、`publicationFreezesOldVersion`、`catalogCountsRespectVisibilityAndOtherFilters`；HTTP断言ADMIN/CSRF/no-store、规划不可发布、原创日期NULL、真题缺来源不可发布。重复保存旧lockVersion409、旧版不变；新建并发一组。
- 运行新admin Mock浏览器检查与旧`reading-quiz.spec.ts`/`admin-articles.spec.ts`，桌面/手机关键布局可操作。此时已有真实数据库管理→发布→公共目录闭环；不以Mock替代上述HTTP/MySQL检查。

## Task 2：开始→自动保存/退出续答→整组交卷→成绩解析

**Files:** 创建PracticeAttemptMapper/Service/Controller、`frontend/src/stores/practice-attempt.ts`、PracticeAttemptView/Reader/Questions/Result；新增`PracticeAttemptIT.java`、`PracticeConcurrencyIT.java`、`frontend/tests/unit/practice-attempt.spec.ts`、`frontend/tests/e2e/practice-attempt.spec.ts`。

**Interfaces:** 消费任务1的不可变set/quiz/article版本。产出`start(actor,setId,StartInput):Attempt`、`get(actor,attemptId):Attempt`、`saveDraft(actor,attemptId,DraftAnswersInput):Attempt`、`submit(actor,attemptId,SubmitInput):Attempt`；Pinia暴露`load(id)`、`choose(questionId,choice)`、`navigate(questionId)`、`flush():Promise<boolean>`、`retrySave()`、`submit(confirmUnanswered)`、`clear()`。

- 写失败测试`startFreezesVersionsAndOmitsAnswers`、`draftSupportsNullAndCurrentQuestion`、`submitRequiresUnansweredConfirmation`、`submissionGradesFrozenVersionAtomically`；断言未知/外组questionId拒绝、未交卷JSON无correctOption/explanation/correct/score、空题按错计、已完成不可改答案。
- 运行`./mvnw.cmd -Dit.test=PracticeAttemptIT,PracticeConcurrencyIT verify -Pmysql-it`确认失败，再实现开始/恢复、答案快照、当前题号及乐观版本。读写均验证当前用户归属，私人响应no-store；所有用户ID从身份取。新FULL仅公开文章+发布题组可开始，已有本人旧轮次不因下架失去访问。
- 写事务READ_COMMITTED，锁顺序固定：开始文章→quiz→set→user→attempt；草稿/交卷从user开始且不再反锁文章/set。START/SUBMIT/RETRY的requestId为16..64字符字母数字连字符，hash包含operation/路径及完整规范化请求，同ID异请求409；重复同请求返回原轮次最新状态/同一成绩。
- 保存只更新本轮最多10个唯一questionId、A–D/null和合法currentQuestionId；实际无变化不加version。整组交卷锁本人轮次，冻结submitted_choice/逐题判分/总数/完成时间/错题投影同事务；不调用AttemptService逐题提交或CheckinService。已完成重复交卷不写第二份成绩。
- 写Pinia失败测试`flushKeepsEditsTypedDuringInflightSave`、`clearedChoiceSurvivesRetry`、`failedExitKeepsLocalChoices`、`accountChangeDropsLateResponse`、`submitRetriesSameRequestId`；明确断言失败时choice保留且状态非“已保存”，后续更改排队而非被响应覆盖。
- `pnpm --dir frontend test:unit -- practice-attempt.spec.ts`确认失败，再实现400ms防抖+单轮次串行队列，questionId键控dirty集合；返回版本更新时只清除被该请求确认且值未再次改变的项。切题/退出/交卷前await flush；失败禁止假退出/假交卷，提供重试；身份与epoch保护所有迟到响应。
- 409支持加载最新服务器版并保留未确认本地选择，用户明确后按新版本保存，不自动覆盖其他页面。登录过期保持同账号本地编辑并重新验证；换账号立刻清空。页面导航守卫提供重试或明确放弃本地未保存项；服务端已保存项不删除。
- 增补真实HTTP并发测试：同用户/set/kind不同requestId并发只一IN_PROGRESS；不同题组各自能保存；并发保存/交卷结果一致；重复交卷一次完成一次错题更新；他人/游客资源拒绝、跨设备从新HTTP客户端读取保存结果。上述Java及Vitest检查全部通过。
- 实现双栏/手机原文答题切换、题号/上一题下一题/清空选项、退出保存、整组确认及未答计错说明、结果解析；浏览器Mock覆盖慢响应/失败重试/切题/刷新/路由离开/身份切换，保持其他模块入口可用。

## Task 3：我的练习、错题重做、旧版本恢复

**Files:** 扩展PracticeAttemptMapper/Service/Controller、PracticeLibraryView/PracticeMistakes及practice.ts；新增`PracticeMistakeIT.java`、`frontend/tests/e2e/practice-history.spec.ts`。

**Interfaces:** `history(actor,query):Page<AttemptSummary>`、`mistakes(actor,query):Page<Mistake>`、`retryMistakes(actor,sourceAttemptId,RetryInput):Attempt`。Mistake含questionId/setVersionId/sourceAttemptId/latestAttemptId/status/wrongCount及本人已提交时的解析；不能混用新版本题目的答案。

- 写失败测试`mistakeProjectionFollowsTransactionOrder`、`retryUsesOnlyFrozenSourceMistakes`、`unlistedHistoryRemainsPrivateAndUsable`：错→对变RESOLVED、后续有效错再PENDING；GET/草稿不更新错题，重放不增wrongCount，源成绩不变，跨源题目不可合并。
- 运行`./mvnw.cmd -Dit.test=PracticeMistakeIT verify -Pmysql-it`确认失败，再实现本人分页及服务器选错题子集。FULL/MISTAKES分别一个未完成任务；同类旧任务存在优先恢复，返回源信息，不静默替换为另一源版本。无错题409。
- 完成事务在同一user锁内更新questionId投影，按完成事务顺序而非客户端时间决定最新状态。提交未答也进入错题；RESOLVED不删历史；当前题库状态IN_PROGRESS优先，其次完成FULL，否则NOT_STARTED。
- 题库内保留全部题组/我的练习/错题本及待订正/已订正过滤，入口不新增用户已删除的两个个人按钮；已保存任务有继续入口。游客点击私人tab/正式开始用现有统一登录弹窗并恢复原筛选与意图。
- 增补测试发布新版本后旧题及成绩/错题不变、文章/题组下架后仅本人可恢复/重做已获得旧版本、别人无法猜ID读旧版、两组/两kind并存与顺序稳定分页；Java+浏览器相关检查通过。

## Task 4：单组JSON校验/导入与重复请求恢复

**Files:** 扩展PracticeAdminService/Controller、PracticeSetMapper请求记录、admin-practice.ts和管理列表导入弹窗；新增`PracticeImportIT.java`、`frontend/tests/e2e/practice-import.spec.ts`。

**Interfaces:** `validateImport(actor,ImportInput):ImportValidation(valid,fieldErrors,itemCount)`、`importDraft(actor,ImportInput):AdminSet`，POST `/admin/practice/import/validate`和`/import`。validate忽略requestId幂等写入，confirm绑定requestId和规范化内容hash；两者均验证ADMIN/CSRF/大小/文章版本。

- 写失败测试`validationHasNoWrites`、`importIsUnreviewedDraftAndIdempotent`、`duplicateArticleDoesNotOverwriteDraft`：校验前后表计数不变；导入无发布版本/校对；同ID同内容同set，同ID异内容409，同文章不同requestId也不覆盖。
- 跑`./mvnw.cmd -Dit.test=PracticeImportIT verify -Pmysql-it`确认失败，再按256KiB/1..10题/现有文章版本校验，拒绝规划考试/不支持题型/外部答案hash或review声明；未知属性按明确schema给字段错误，不加载外部文件/URL。
- confirm按文章→quiz→set→user锁定并写practice_request IMPORT+set_id，唯一约束兜底；仅复制输入元数据/题目到草稿，不复制文章、不写用户作答。已有关联题组返回existingSetId冲突并提供打开入口，禁止自动覆盖。
- 浏览器检查非法JSON、修改后需重新校验、字段问题定位、确认按钮状态、网络响应丢失用原requestId重试、导入后仍需人工逐题校对；Java及Mock浏览器检查通过。

## Task 5：完整验收、独立审查与本地交接

**Files:** 更新ReadingHttpITSupport中的FK清理顺序及SchemaMigrationIT的迁移断言；新增`frontend/tests/e2e/practice-real.spec.ts`、`docs/acceptance/2026-10-19-practice.md`、`docs/practice-guide.md`；同步docs/api.md、database.md、README.md（文档入口）、progress.md、decisions.md。

**Interfaces:** 消费前四项完整流程；使用现有脚本和README命令，不更改测试框架或关闭沙箱。已确认现有ReadingHttpITSupport包私有，新Practice*IT置于相同包；清理只删除本测试创建的记录，按request/mistake/answer/attempt/set发布指针/set_version/set/quiz顺序解除关系，不触及其他项目库。

- 基线在实现前运行现有后端`test`、MySQL`verify -Pmysql-it`与前端`test:unit/build`，记录实际退出码。若已有失败，先调查并隔离原因，不能把既有失败改写为本次通过；不得清库、关闭权限或删除旧测试。
- `. ./scripts/use-workspace-tools.ps1`、`. ./scripts/use-java21.ps1`后检查`./backend/mvnw.cmd -version`实际Java21。全部后端单元/真实MySQL+Redis HTTP集成、前端单测/类型/生产构建通过；必要命令如下，逐次记录结果和未运行项。

```powershell
# 根目录准备；后端测试必须以backend为工作目录加载./config
. ./scripts/use-workspace-tools.ps1
. ./scripts/use-java21.ps1
./backend/mvnw.cmd -version
# 在backend工作目录分别执行
./mvnw.cmd test
./mvnw.cmd verify -Pmysql-it
# 回到根目录
pnpm --dir frontend test:unit
pnpm --dir frontend typecheck
pnpm --dir frontend build
pnpm --dir frontend test:e2e practice-admin.spec.ts practice-attempt.spec.ts practice-history.spec.ts practice-import.spec.ts
pnpm --dir frontend test:e2e reading-quiz.spec.ts reading-attempt.spec.ts reading-checkin.spec.ts vocabulary-learning.spec.ts vocabulary-dashboard.spec.ts navigation.spec.ts
```

- 新专项浏览器测试在1440/768/320px运行，768px在专项spec内用`test.describe`和`test.use({viewport})`追加，不改旧项目全局配置。实查表格/手机卡片、原文/编辑/导航、总览与校对、键盘焦点/弹窗关闭、保存失败/冲突/恢复、版本/下架/导入；无横向溢出/pageerror/非预期失败请求，截图人工查看。
- 新增`practice-real.spec.ts`使用项目专用真实测试服务/已建测试账号和真实数据，不复用Mock身份冒充权限验证；真实HTTP IT另验证用户归属、CSRF、答案隔离、并发、冻结版本和幂等。真实端口/库配置仅在忽略本地配置中，不输出凭据；未启动真实服务时明确未运行，不能跳过后声称通过。
- 对稳定结果做一次独立Reviewer审查，重点检查用途绕过/HTTP泄漏、跨账号、串行保存、校对失效、下架恢复/版本、同ID异参数、FK与清理范围。修复重要问题并只重跑相关检查；审查和最终证据保存在验收文档。
- 更新本地8080/4174前确认当前进程归属和新JAR/dist；保留旧运行包以便恢复，不杀不明进程。仅迁移专用开发库V19，不导入真实题或自动发布候选。健康/新API/游客可见目录与私有401、旧阅读/词汇可用验证后打开正式页面供用户体验。
- 同步API/数据库/使用/进度/取舍/验收：区分Mock、真实库、构建及用户视觉确认，逐条记命令/退出码/通过数量/未完成项。只审查本任务差异，不暂存/提交已有无关修改，不自动进入后续内容批次。

## 计划自审与当前证据

本计划覆盖正式设计§1—9及管理端确认的四部分，逐题hash/未完成草稿/历史预览/导入幂等结果引用已补齐，不把原型总布尔校对搬入正式鉴权或发布校验。每个任务均有接口、写入范围和失败→成功验证；任务1即包含管理端/后端/公共目录的纵向流程，后续接完整个人训练链路。

2026-10-19实际完成：基线65后端单元/92集成/67前端单测与构建；实现V19和六表、全部专项流程；最终65后端单元/101真实集成、72前端单测/类型/构建、88项Mock浏览器回归，以及真实后端驱动4项浏览器流程。独立Reviewer四项重要问题已修复并回归。Rules/Isolation/Catalog/Concurrency测试按Admin/Attempt/Mistake/Import九个HTTP用例合并；测试清理通过新子类而不改旧helper。详情和本地交接见正式验收。

内容验收保持独立：至少10组经过人工校对/来源明确的题目仍是阶段4最终目标；本批交付业务闭环不等于真实四六级题源准备或全部内容完成。原型5道原创题只是候选，不自动写入开发平台并发布。
