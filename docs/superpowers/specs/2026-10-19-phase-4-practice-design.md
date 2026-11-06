# 阶段4：四级阅读专项正式设计（正式流程已实现）

2026-10-19。用户已确认导航与筛选原型，并要求整理准确的接口、数据库和复用方案。本文件供集中确认，不代表业务代码已经实现或测试通过。原型以`docs/prototypes/practice-preview.html?preview=entry-clean-4`为准：考试选择页不放「我的练习」「错题本」，对应功能在进入题库后保留。

最新状态：用户确认原型和[集中实施计划](../plans/2026-10-19-phase-4-practice.md)后，正式流程已实现并完成真实后端/浏览器检查；当前证据见[验收](../../acceptance/2026-10-19-practice.md)。管理端以`docs/prototypes/practice-admin-preview.html?preview=admin-review-2`为基准，学习端沿用`practice-workflow-preview.html`；原型保留，正式页面用户视觉确认仍待体验。以下为设计契约，不将设计预期本身当作验证结果。

## 1. 本批交付与边界

交付一条可运行流程：选择大学英语/四级 → 组合筛选题组 → 原文对照作答 → 草稿自动保存/退出续答 → 后端整组判分 → 成绩解析 → 错题重做与历史 → 管理端新增下一组内容。沿用黑白灰/柔和粉色，电脑双栏、手机原文/答题切换，避免导航遮挡。

本批只开放CET4与仔细阅读（DETAILED），每组一篇现有体系中的文章、1–10道四选一单选，样题以5题为目标。题目分类复用MAIN_IDEA/DETAIL/INFERENCE/VOCABULARY/ATTITUDE。管理端规划分类为CET4/CET6/POSTGRAD1/POSTGRAD2（四级、六级、考研英语一、考研英语二），题型联动但未支持项明确标注规划；学习端既有其他规划入口保留。写作、翻译、听力、选词填空、长篇匹配、整套考试和AI本批不实现。长篇匹配不是把单选题型改个名称即可支持，未来另定模型。

阶段4路线图最终要求至少10组经过校对、来源明确的阅读练习。本批先以一组人工审阅的原创题跑通；数量不足10组时只称首批流程交付，不宣称完成整个V1内容验收。原型题仅为候选内容，不直接当作真实真题或自动发布。

## 2. 已有实现与选择

已核对现有QuizService/QuizMapper/QuizRules、AttemptService/AttemptMapper、GuestQuizService、AccountQuizPanel、AdminArticleQuizView与V13/V14。已有文章不可变版本、题目不可变版本、草稿、发布、逐题判分、历史、归属检查，可复用；`/practice`目前仍为PendingFeatureView。

选择：**共用文章与题库结构，区分题目用途，新增专项作答聚合。**

- 不复制identity_user、reading_article、reading_article_version、dictionary或生词系统。
- reading_quiz新增用途ARTICLE/PRACTICE，旧记录默认ARTICLE；reading_quiz_version/reading_question继续共用。发布时复用QuizRules的题干/选项/答案/解析校验；专项编辑器使用同一QuestionInput契约，现有文章编辑器和阅读界面保持原样，不为复用表单重构旧页面。
- 原ARTICLE公开/游客/账号题目与逐题提交接口明确限定ARTICLE用途，包括以quizId直接开始的入口；旧轮次仍只能引用原ARTICLE题目。不能通过猜专项quizId绕过整组交卷拿答案。
- 新PracticeAttemptService处理整组提交，不调用会立即返回答案且联动CheckinService的旧逐题提交服务。文章配套练习、游客体验和打卡规则保持原行为。
- 不采用“只改按钮后循环提交旧接口”的方案；也不新建第二套文章和题目表。用途隔离会触及旧QuizMapper查询，必须有旧功能与绕过攻击的回归。

## 3. 学习端页面与权限

路由建议：

| 路由 | 用途 |
| --- | --- |
| `/practice` | 考试类别与级别选择；已保存任务可显示继续入口，不新增已删除的个人菜单 |
| `/practice/cet4` | 四级题库，内部tab为全部题组/我的练习/错题本 |
| `/practice/attempts/:id` | 本人作答；提交后同一轮次展示成绩与解析 |

筛选放URL query：source、readingType、topic、state、year、month、paperNo、q、sort、page。可组合、清空、删除单项，返回恢复上下文；手机筛选面板具备关闭、焦点恢复和独立滚动。公共目录页码从1开始，默认20条，最大50，不一次加载全库。

来源ORIGINAL/PAST。ORIGINAL不存考试年份/月/套次；PAST有真实来源与使用依据时才录入这些字段，选项由已发布数据产生，不编造年份覆盖。当前可选题型只有DETAILED；未实现类型标为规划而不发布成可答题组。排序LATEST/TITLE，使用稳定id兜底。同一过滤条件的总数和列表使用同一可见性规则，侧栏计数排除该组自身条件、保留其他条件。

状态：该set有任一未提交轮次为IN_PROGRESS；否则有FULL完成轮次为COMPLETED；否则NOT_STARTED。题库层面状态不替代轮次历史。错题重做未完成可以保留并单独恢复。

游客可浏览公共目录与公开原文预览，点击正式开始或个人tab调用现有统一登录弹窗，登录后保留级别、筛选与开始意图。本批专项作答要求登录以便可靠保存；原文章模块游客基础练习不受影响。目录接口游客不能借state等条件读私人信息：state非ALL返回401；公开响应不包含个人记录。

## 4. 作答、保存、交卷与并发

开始时冻结practice_set_version、article_version、quiz_version及本轮题目ID。新FULL只允许当前发布且来源文章仍公开的题组；本人旧轮次始终按冻结版恢复，下架/改题不删除历史。已发布专项版本可指向审核时选定的文章不可变版本，不跟随后来文章发布自动换内容；文章下架则公共目录和新FULL不可用。

每个用户/题组/练习种类（FULL或MISTAKES）最多一个未提交轮次，允许不同题组或种类分别保存，不锁住整个首页。同类型重复开始恢复旧轮次及其冻结版本；不同requestId的并发开始也只创建一个。错题重做冻结源完成轮次中的错题子集，不能由客户端传任意questionId；只有同一题组/版本的错题才能组成一轮。

草稿每次选择或清除后合并保存，约400ms防抖，同一轮次串行发送；题号跳转、切页和退出前flush。PUT只接受当前用户本轮题目ID和A–D/null，携带expectedVersion；成功后才显示“已保存”。自动保存失败保留本地选择、显式重试；退出保存失败不丢弃选择、不伪装已退出保存。导航守卫在未保存时提示重试或明确放弃本地未保存选择，已落库部分保留。

同时保存最后浏览题号，范围必须在本轮题目内；字号等纯阅读偏好可本地保存。服务端草稿可跨设备恢复；身份变更清空内存与本账号缓存，迟到响应不能覆盖其他账号。多标签页使用乐观版本；冲突加载最新服务器记录并保留本地未确认项供用户确认，不静默覆盖。

统一交卷：先flush所有草稿，再打开确认面板；有未答题时说明“按错题计入”，允许返回检查。POST submit携带expectedVersion、requestId、confirmUnanswered；未答未确认返回明确错误。服务器锁本人轮次，从冻结题目读取正确选项，在同一事务写submitted_choice、correct、COMPLETED、正确数和错题投影。

提交前：题目响应仅题干/四个选项/题型/ID，不能包含正确选项、解析、correct布尔或评分信息；article版旧接口同样不能获取PRACTICE题目的判分。提交后才返回本轮全部解析。未答按错误计入，分数只显示正确数/总数与正确率，不冒充四级标准分。

同一requestId同请求返回原关联轮次或完成结果，异请求409；开始请求重放可返回该轮次最新状态，但不创建新轮次。重复交卷只完成一次，不追加第二份成绩或错题事件。COMPLETED后相同提交重放可取已有结果，任何改答案请求409；并发草稿/交卷按版本与锁顺序解决。身份校验、范围校验、判分与幂等均在后端，不接受客户端用户ID或score。

## 5. 错题与重做

按本人+不可变question_id区分错题；新发布版本的问题即使key相同也不自动合并。首次错误或未答后进入PENDING；重做正确后RESOLVED，原始错答和成绩永不删除。再一次有效完成的练习答错可重新进入PENDING。投影只在有效交卷时更新，不因查看、选项草稿或重复请求改变。

以服务器完成事务顺序作为最新反馈顺序（用户写锁串行），不按浏览器时间覆盖。可在错题页筛选待订正/已订正，默认待订正；查看当时答案解析指向原source_attempt。重做新建MISTAKES轮次，保留source_attempt_id；新完成轮次不改源结果，正确率只按本次子集计算。

已下架题组的本人历史错题允许按已获得的旧版本重做；不借此读取其他用户/从未作答的旧版本。无法公开新开始和本人历史恢复是不同权限。题库历史、错题、作答页全部no-store、认证与资源归属检查。

专项练习不会写阅读完成、每日阅读打卡或词汇排期，也不合并进当前阅读统计数字。专项统计先放题库内真实成绩，不另建没有数据支撑的首页卡片。

## 6. 数据库增量设计

实际最新迁移为V18；实施前再次确认，计划V19增量，不改已执行迁移、不清库。

现表reading_quiz增加`usage`，默认ARTICLE、CHECK为ARTICLE/PRACTICE；唯一索引quiz_article_version由(article_id,article_version_id)增量替换为(article_id,article_version_id,usage)。复合外键quiz_article_identity等保持。为同一文章版本保存两类题组，保留旧ID和全部历史。

| 新表 | 主要字段与约束 |
| --- | --- |
| `practice_set` | id、article_id、exam_code(CET4)、reading_type(DETAILED)、draft_article_version_id、draft_quiz_id、draft_metadata_json、published_version_id、status(DRAFT/PUBLISHED/UNLISTED)、revision、lock_version、created_by/updated_by与时间；unique(article_id,exam_code,reading_type) |
| `practice_set_version` | id、set_id、version_no、article_id/article_version_id、quiz_id/quiz_version_id、发布metadata_json、published_at、created_by；unique(set_id,version_no)、unique(set_id,id)、unique(id,quiz_version_id)；联合FK检查文章版本和quiz版本归属，所有发布版本不可变 |
| `practice_attempt` | id、user_id、set_id/set_version_id、quiz_version_id、kind(FULL/MISTAKES)、source_attempt_id可空、status(IN_PROGRESS/COMPLETED)、correct_count提交前NULL、total_count、lock_version、current_question_id、started/completed_at；生成列active_set_id在IN_PROGRESS为set_id否则NULL，unique(user_id,kind,active_set_id)；unique(user_id,id)、unique(id,quiz_version_id)；联合owner FK防跨账号源轮次，(set_version_id,quiz_version_id)FK绑定冻结版本 |
| `practice_answer` | attempt_id、quiz_version_id、question_id、ordinal、draft_choice、submitted_choice、is_correct提交前NULL、submitted_at；PK(attempt_id,question_id)，unique(attempt_id,ordinal)，(attempt_id,quiz_version_id)和(quiz_version_id,question_id)双联合FK；只存本轮子集 |
| `practice_request` | user_id、request_id、operation、request_hash、attempt_id或set_id、response_version；unique(user_id,request_id)，关联本人attempt或导入set。START/SUBMIT/RETRY引用attempt，IMPORT引用set；CHECK约束引用种类。重放验证相同操作和内容，不存Token或密钥 |
| `practice_mistake` | user_id、question_id、set_id/set_version_id、source_attempt_id、latest_attempt_id、status(PENDING/RESOLVED)、wrong_count、first_wrong_at、last_completed_at；PK(user_id,question_id)，索引(user_id,status,last_completed_at,question_id)，来源关联本人完成记录；为投影，历史仍以答案表为事实 |

metadata字段：title/titleZh、sourceKind、topic、year/month/paperNo、sourceName/sourceUrl/usageNote、reviewedBy/reviewedAt。范围与发布校验由后台共享业务执行；ORIGINAL的考试时间全NULL，PAST缺来源和使用依据不能发布。仅保存内容使用说明，不把管理员填写的说明视为平台已完成法律授权核查。

题组草稿允许保留缺题干、未选答案、缺解析等未完成内容，仅先做字段类型、长度、题数和题目标识等有界结构校验；完整QuizRules在发布和人工确认校对时执行，不用旧文章题组保存接口阻止未完成专项草稿落库。专项重复选项额外按去首尾空格并忽略大小写检查，不改变旧阅读题组规则。

逐题校对保存在draft_metadata_json的服务端review字段中：questionKey→题干/题型/选项/答案/解析的规范化hash，不含排序；总确认绑定文章版本、来源元数据和有序题目内容hash。更新草稿由后端比较并撤销已变题目和总确认，移动题目保留本题校对、撤销总确认。专用review接口一次只确认/撤销一题或文章来源，更新管理员/时间与lock_version；保存/导入JSON不能写入review内部字段。前端勾选前先保存内容，再确认服务端当前版本。发布必须同时具有每题有效hash和总确认，不只信任客户端总布尔值。

旧QuizMapper的article/get/create入口默认限定ARTICLE；专项通过独立PracticeQuizMapper访问相同三张表并固定PRACTICE条件。旧AttemptMapper及V14活动轮次约束保持不变，不把FULL/MISTAKES加入旧作答记录。新增关系表用联合外键约束set/文章/quiz/version和本人源轮次归属；迁移时保留旧FK所依赖的(article_id,article_version_id)前缀索引，再替换旧唯一索引。

锁顺序统一文章→quiz→practice_set→user→attempt→answer；仅需要草稿/提交的事务从user开始且不再反向锁文章/题组。发布不锁用户；历史访问读不可变快照，不获取后续新版本题目的答案。开始和发布使用当前读检查状态，避免MySQL可重复读旧快照的既有问题，写事务沿用READ_COMMITTED。

## 7. 接口边界

路径均在`/api/v1`，沿用现有响应/字段错误/认证/CSRF约定，PRIVATE接口no-store。命名在实施计划中固定，本节给出完整业务边界。

| 方法/路径 | 契约概要 |
| --- | --- |
| GET `/practice/catalog` | 可用考试/级别/题型与开放状态；只读真实目录，不造年份数量 |
| GET `/practice/sets` | exam=CET4及组合条件、page/pageSize；PageDto，游客无私人state；登录时可带本人状态 |
| GET `/practice/facets` | 与目录相同可见性和条件，返回各组选项/匹配数；年份/月/套次仅从PAST已发布元数据取 |
| GET `/practice/sets/{id}` | 当前公开题组元数据与原文预览，不返回答案或解析 |
| POST `/practice/sets/{id}/attempts` | `{setVersionId,requestId}`；开始/恢复FULL；发布变化409 SET_CHANGED |
| GET `/practice/attempts/{id}` | 本人冻结文章、题目、草稿、浏览位置和状态；COMPLETED才含判分/解析；他人404 |
| PUT `/practice/attempts/{id}/draft` | `{expectedVersion,answers:[{questionId,choice}],currentQuestionId}`；最多10项、只改本轮题；实际无变化不增加版本 |
| POST `/practice/attempts/{id}/submit` | `{expectedVersion,requestId,confirmUnanswered}`；整组原子判分，重放不重复 |
| GET `/practice/history` | exam/set/status等条件，本人分页；查看结果用attempt详情 |
| GET `/practice/mistakes` | exam/set/status等条件，本人分页，含对应已完成来源的解析与原作答 |
| POST `/practice/attempts/{id}/retry-mistakes` | `{requestId}`，源轮次本人且已完成，由服务器选错题；同题组/版本的MISTAKES已有轮次优先恢复，无错题409 |
| GET/POST `/admin/practice/sets` | ADMIN列表/创建草稿，关联现有文章版本，不复制文章 |
| GET/PUT `/admin/practice/sets/{id}` | ADMIN读取/编辑元数据和PRACTICE题目草稿，expectedLockVersion防覆盖 |
| POST `/admin/practice/sets/{id}/review` | `{expectedLockVersion,kind:QUESTION或SOURCE,questionKey?,reviewed}`，逐题或文章来源校对，无批量确认 |
| GET `/admin/practice/sets/{id}/versions`、`/versions/{revision}` | ADMIN分页历史及当次不可变原文/题目/来源预览，不读取当前草稿替换旧版 |
| POST `/admin/practice/sets/{id}/publish`、`/unpublish` | ADMIN与版本校验，发布冻结文章/quiz版本/元数据；下架保留历史 |
| POST `/admin/practice/import/validate` | ADMIN校验单组JSON的articleId/version、metadata/questions，不写库；有界文件与逐字段错误 |
| POST `/admin/practice/import` | ADMIN明确确认后导入为草稿，重复请求不重复建组；不自动发布、不写账号成绩 |

提交/题库变更/数据冲突分别用明确业务错误码与重试入口，不使用浏览器alert。新题组缺失404；已有本人冻结轮次不会因公共题组下架而变为404。基本JSON导入复用QuizRules与文章归属校验，原文章导入服务继续保留。第一版只导入单组元数据/题目，并引用已有articleId/version，不另造文章导入管线；unique(article_id,exam_code,reading_type)配合事务保证重复导入不创建重复组，已有不同草稿返回冲突由管理员明确选择编辑，不静默覆盖。

## 8. 前端与管理端文件责任

建议新增`frontend/src/api/practice.ts`、`stores/practice-attempt.ts`、`views/PracticeEntryView.vue`、`PracticeLibraryView.vue`、`PracticeAttemptView.vue`、`AdminPracticeSetsView.vue`、`AdminPracticeEditorView.vue`、`components/practice/{PracticeFilters,PracticeSetRow,PracticeReader,PracticeQuestions,PracticeResult,PracticeMistakes}.vue`及专项样式。复用WordLookupPanel、词典API、统一登录、消息反馈与布局，不复制整套阅读详情工具栏或生词管理。

不改AdminArticleQuizView的交互或抽取它的模板。新增专项专用编辑组件，复用现有QuestionInput契约、WordLookupPanel、统一登录、业务请求和反馈工具；原后台入口/权限不变，增加「专项题组」导航与预览。2026-10-20按用户纠正：默认在专项中直接录入原文，专用原文不进入阅读发布；保留已有文章版本可选复用及原JSON导入。录入步骤：填原文/分类/来源→录题和解析→预览→人工逐题校对→只发布题组。默认新导入只草稿。

旧阅读后端仅补用途隔离及验证，不改变逐题判分/打卡行为。正式新建与导入都执行同文章/考试/题型唯一约束；原型允许多个演示草稿的限制不带入生产实现。

后端新增`com.englishhero.practice`目录，按catalog/admin/attempt职责组织Service/Mapper/DTO/Controller，仍为模块化单体。只对旧QuizMapper补用途隔离与共用编辑校验，不改旧AttemptService的评分和CheckinService规则。SecurityConfig只开放明确公共GET，不开放整个practice前缀。

题库query、页码与tab保留；身份变化清空私人store和中止请求。自动保存有队列、服务端版本和失败重试；不能把Pinia或localStorage当持久业务数据库。字号本地可选，答题和统计以数据库为准。

## 9. 集中实施顺序与验收

设计确认后编写精确实施计划，再按完整纵向流程集中执行：

1. 用途隔离/迁移与真实回归；一组草稿录入→预览→发布→公共目录和筛选，可运行地验证后台与前台。
2. 同一题组开始→保存/清除→退出/刷新/换设备恢复→后端整组交卷→结果，补权限/并发/幂等测试。
3. 错题投影/重做/历史、元数据更新/下架后恢复、导入校验，保持旧功能。
4. 独立审查、真实MySQL/Redis/HTTP集成、前端单测/构建、1440/768/320浏览器验证，更新API/数据库/使用/进度文档，再更新本地服务。

必测：ARTICLE原接口完全不变且无法访问PRACTICE题；交卷前所有可访问接口无答案；不同账号和游客拒绝私人数据；重复开始/交卷只一轮/一结果；草稿冲突和离线保留；清空选项可持久化；未答确认/计错；暂停多题组不互相锁死；发布后改题不改历史；旧错题只按旧版本判分；下架后本人恢复/重做且他人不能读旧版本；筛选/计数/分页一致，原创不冒充真题日期；旧查词/登录/阅读打卡/词汇不受影响。

本文件说明预期，实际已创建V19及正式流程，测试/本地运行结果以验收为准；独立审查修复已记录。现有未提交工作全部保留，没有提交/推送/部署外网。真实题源与至少10组人工校对内容另行处理，不把基础流程完成记为内容目标完成。
