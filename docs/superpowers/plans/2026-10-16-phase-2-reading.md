# Phase 2 Reading Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 打通管理员发布原创文章、学习者阅读与恢复、查词、明确收藏及个人生词本的真实持久化流程。

**Architecture:** 按reading、dictionary、vocabulary模块组织Java业务，复用现有JWT业务principal、MySQL事务及结构化错误。Vue沿用现有布局和反馈组件；每任务完成一个可运行的前后端闭环，文章采用不可变版本，个人进度与收藏在服务端隔离。

**Tech Stack:** Vue3/TypeScript/Pinia、Java21/Spring Boot4.0.8、MyBatis-Plus、MySQL、现有JWT + Redis认证与Element Plus；版本沿用项目锁定值，不新增框架或付费接口。

**Spec:** [已确认阅读方案](../specs/2026-10-15-phase-2-reading.md)，用户于2026-10-16确认。用户已确认并授权当前会话顺序实施；进度以docs/progress.md及本计划勾选为准。

## Global Constraints

- 保持现有黑白灰、玫红#c0324f与浅粉体系；遵循docs/ui-guidelines.md，不改登录注册布局。实际阅读/选词视觉仍需用户确认，设计确认不等于界面验收。
- 至少10篇原创练习，每篇350—650词，学习/生活/科技/文化/职场五类；B1–B2/四级为编辑标注，来源明确为「Lingua Loop原创练习」。至少300个基本词形，配套文中重点词及其屈折形式，不虚构媒体来源或词义。
- 不做AI、复习调度、考试、学习时长、抓取、整句翻译、音频或富文本编辑器；复用认证Redis，不新增阅读Redis缓存或外部翻译依赖。
- 路径/api/v1；ID在JSON中为字符串，UTC存储、上海日期展示。JWT只在内存，CSRF覆盖写请求，服务端核对角色、资源归属和账号版本；保留Redis故障503语义。
- V1—V4不改、不清库；按V5—V9追加迁移，每次同步更新既有SchemaMigrationIT当时应有表清单（8→11→12→14→16→17），不删除原幂等断言。用户/版本/来源外键不级联删除历史；DDL用既有MySQL兼容语法，集成测试只用english_hero_test和项目6381 Redis。
- 开始每任务先git status，保留当前大量未提交基线，不从缺少已批准身份代码的旧HEAD重建工作树。未获要求不自动提交、暂存、合并或发布。
- 先写重要行为的失败测试再实现；Mock、真实MySQL/Redis、构建、内容校对、用户视觉确认分别留证。完成必要工作后再交付审批，不在每个内部任务之间反复询问是否继续。

## Review Focus

1. 下架、开始阅读及再次发布并发时，以事务锁定的发布状态决定权限，不能创建绕过下架的新历史授权；Task2真实并发测试。
2. 跨标签账号变化或文章切换时，节流进度、查词结果和待收藏请求不得跨身份/版本落库；Task3—5的迟到响应及CSRF等待测试。
3. 文章含撇号、连字符、代理对或嵌套词span时，DOM选择必须对应服务端句内UTF-16偏移；不能收藏相邻词或切断字符；Task4—5共享样例及浏览器测试。
4. 发布新版本、下架或取消收藏后，旧来源仍可访问本人快照；不能因“有收藏”泛化为读取他人/任意草稿；Task3/5访问矩阵测试。
5. 同一导入ID并发重试、混合无效内容或响应丢失时，全批原子且幂等；相同ID不同作者/内容不得泄漏既有结果；Task6真实事务测试。

## 文件边界与公共契约

后端下文Java路径均相对 `backend/src/main/java/com/englishhero/`，Java测试相对 `backend/src/test/java/com/englishhero/`；前端路径为项目相对路径。Controller只做绑定/鉴权适配，Service负责规则与事务，Mapper负责带归属条件的SQL，不把业务加入IdentityService。

| 单元 | 文件与职责 |
| --- | --- |
| reading | `ReadingDtos.java`请求/响应；`ArticleContent.java`规范化版本正文；`ArticleRules.java`长度/来源校验；`ArticleMapper.java`文章/版本SQL；`AdminArticleService/Controller.java`管理事务；`ReadingMapper/Service/Controller.java`学习访问与开始；`ReadingProgressService.java`进度；`ArticleImportService/Controller.java`导入 |
| dictionary | `DictionaryDtos/Rules/Mapper.java`；`DictionaryProvider.java`边界、`LocalDictionaryProvider.java`精确词形与有限缓存；`DictionaryService/Controller.java`组装本人收藏状态；`DictionaryDataLoader.java`显式本地数据导入 |
| vocabulary | `VocabularyDtos/Mapper/Service/Controller.java`收藏、来源分页及软取消 |
| 通用异常 | 新增 `web/BusinessException.java`与 `BusinessExceptionHandler.java`，延续{code,message,traceId,fieldErrors?}和no-store，不重构身份异常 |
| 前端通信 | `frontend/src/api/business.ts`封装受保护GET/写入；`api/reading.ts`、`admin-articles.ts`、`dictionary.ts`、`vocabulary.ts`限定各自DTO/参数 |
| 页面与局部状态 | `views/ReadingListView.vue`、`ReadingDetailView.vue`、`NotebookView.vue`、`AdminArticlesView.vue`、`AdminArticleEditorView.vue`、`AdminArticlePreviewView.vue`；`reading/useReadingProgress.ts`、`selection.ts`、`useWordLookup.ts`；`components/reading/ArticleBody.vue`、`WordLookupPanel.vue` |

统一类型在Task1定义：`ContentDocument(schemaVersion=1, paragraphs:[{key,sentences:[{key,text}]}])`；`ArticleVersionDto(id,articleId,versionNo,title,titleZh,summary,category,difficulty,content,wordCount,estimatedMinutes,author,sourceType,sourceUrl,usageNote)`；`PageDto<T>(items,page,pageSize,total)`。metadata全为版本快照；version另存categoryId FK，article.category_id表示已发布分类（尚无发布时为初始分类），保存新草稿不提前改变学习端分类，发布时一并切换。`AdminArticleDto`另含status、draftVersionId、publishedVersionId、lockVersion；lockVersion整数递增，绝不接受客户端角色或userId。

正文统一CRLF→LF、NFC、去首尾空白、空行分段；JDK21 `BreakIterator.getSentenceInstance(Locale.ENGLISH)`分句并预览校对，版本内顺序键p0001-s0001。禁止控制符（LF/Tab除外）及未配对代理。contentHash为规范化ContentDocument确定性JSON的SHA-256；编辑另存新版本，不复用句子键跨版本匹配进度。

词数按 `[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*` 非重叠匹配计数，预计分钟max(1,ceil(wordCount/200))。`content/english-token-cases.v1.json`固定跨端样例；选词规则采用同一词边界，查词键NFKC/小写并将弯撇号、词内短连字符映射为ASCII，原文和UTF-16偏移始终保留原样。

公共前端 `businessGet<T>(path,signal?)` 与 `businessWrite<T>(path,body?,method='POST',signal?)` 必须在任何CSRF await之前捕获getGeneration，再传给现有authenticatedJson；禁用身份变化后的旧请求，禁止复制一套刷新逻辑。扩展现有 `write` 只认/account的问题，不扩大其旧公开接口行为。

## Task 1：管理员草稿保存与安全预览

**Files:** Create `V5__reading_articles.sql`（resources/db/migration，后续迁移同目录）；reading/ArticleContent、ArticleRules、ReadingDtos、ArticleMapper、AdminArticleService/Controller；web/BusinessException/Handler。Create前端api/business.ts、admin-articles.ts，AdminArticlesView、EditorView、PreviewView及styles/reading.css。Modify SecurityConfig、router/index.ts、AdminLayout.vue。Test reading/ArticleRulesTest、AdminArticleIT，frontend/tests/unit/business-api.spec.ts、tests/e2e/admin-articles.spec.ts。新增共用测试 `reading/ReadingHttpITSupport.java`，复用TokenTestSupport，独立fixture不调用生产Token/邮箱导出接口。

**Interfaces:** `AdminArticleService.create(SessionPrincipal,DraftInput):AdminArticleDto`、`saveDraft(actor,id,DraftUpdate):AdminArticleDto`、`preview(actor,id,versionId):ArticleVersionDto`；DraftInput包含全部编辑字段和纯文本body，DraftUpdate另带expectedLockVersion。固定分类code为STUDY/LIFE/TECHNOLOGY/CULTURE/WORK，难度B1/B2，sourceType ORIGINAL/THIRD_PARTY。来源完整性及长度一处校验，预览返回规范化后的纯文本快照。

- [x] 写规则/HTTP失败测试：160标题、500简介、160署名、20,000正文、2,000单句边界；缺来源说明、第三方非http/https URL、非法枚举/代理对失败；输入HTML只显示文字。普通用户管理API403、匿名401、无CSRF403；当前actor字段不接收客户端userId。
- [x] 在backend运行 `./mvnw.cmd test '-Dtest=ArticleRulesTest'`，Expected: 新类缺失或断言失败，记录真正红灯。
- [x] 实现V5：category五行及article/version表，版本号/归属复合唯一键、文章两指针复合FK；首次插入文章→版本→草稿指针同事务。saveDraft行锁+expectedLockVersion比较，产生新快照并只改草稿指针，冲突409 ARTICLE_VERSION_CONFLICT；不提供硬删除。加入异常映射和仅ADMIN管理路由。
- [x] 实现管理列表/筛选、创建、纯文本编辑、保存与预览；字段错误在表单内，草稿离开有未保存确认；不引入富文本。测试 `businessWrite` 等待CSRF期间切换账号后零业务发送，再实现封装。前端给出当前草稿版本/锁值，预览不能调用学习接口获取草稿。
- [x] 运行 `./mvnw.cmd verify -Pmysql-it '-Dit.test=AdminArticleIT'`，前端unit/build及admin-articles Mock浏览器。Expected: 全绿；真实MySQL证明并发保存一胜一409、错误版本FK拒绝、失败事务无半篇文章。更新docs/api.md和database.md已实现部分，不提前列成全阶段已完成。

## Task 2：发布、列表与单篇开始阅读闭环

**Files:** Create V6__reading_progress.sql；reading/ReadingMapper、ReadingService/Controller；extend AdminArticleService/Controller、ReadingDtos、SecurityConfig。Create前端api/reading.ts、ReadingListView、ReadingDetailView、ArticleBody.vue；Modify管理页、router/index.ts、router/navigation.ts、views/LoginView.vue；Create router/safe-redirect.ts。Test reading/ReadingAccessIT、frontend/tests/unit/safe-redirect.spec.ts、tests/e2e/reading-flow.spec.ts。

**Interfaces:** `publish(actor,articleId,expectedLockVersion)`、`unpublish(...)`返回AdminArticleDto；`ReadingService.list(actor,ArticleQuery):PageDto<ArticleSummaryDto>`、`start(actor,id):StartResult(version,progress,created)`、`version(actor,articleId,versionId):ReadingDetailDto`。ProgressDto(id,articleId,articleVersionId,sentenceKey,offset,status,lockVersion,updatedAt,completedAt)，status=READING/COMPLETED；初始首句/0、lockVersion=0。ArticleSummaryDto含完整来源摘要及本人当前发布版本的进度摘要，不含草稿正文。

- [x] 写访问/并发测试再运行 `./mvnw.cmd verify -Pmysql-it '-Dit.test=ReadingAccessIT'`，Expected: 当前缺发布/start/list接口失败。断言草稿不可读、普通用户403、两用户start互不共享、同用户同版本重复start一条记录，首次201/已有200。
- [x] V6建立progress唯一(user_id,article_version_id)、版本文章复合FK与本人历史索引；发布/下架/保存/start都先锁article行，再读指针。当前发布版本可读但GET不创建进度；带version入口遇到progress=null时先start并核对返回版本，若发布指针已变则提示重新选择、不混用旧正文和新进度；旧版本必须有本人progress，管理员使用独立preview。无权/不存在统一404；下架新start返回409 ARTICLE_UNAVAILABLE。下架与start的两种锁顺序都有真实测试，不只测顺序请求。
- [x] 实现列表categoryId/标题keyword/page/pageSize，page从1、默认20/上限50、keyword≤80，标题LIKE转义用户%/_，排序published_at DESC,id DESC。publish完整校验后原子切指针并递增锁；unpublish保留快照/历史，重复或并发操作按expectedLockVersion处理。
- [x] 接入/admin/articles发布/下架确认、/reading及/reading/:articleId。列表加载/空/无匹配/失败分开；详情先start，再渲染ArticleBody纯文本与来源；管理端仍只管数据。safe-redirect只允许已知本地路径、正整数文章ID（最多19位）及version同格式、sentence=p0001-s0001式键、start/end为0—2000整数的定位query，拒绝协议相对、外链、反斜杠、未知query/嵌套路由和角色越权。
- [x] 后端目标IT、前端unit/build、reading-flow桌面/320px Mock及真实测试库浏览器。Expected: 管理员发布后学习者可见并阅读，草稿/他人版本拒绝，来源/字数正确、无横向滚动和导航遮挡。打开实际页面收集视觉反馈，记录“待用户确认”，不替用户写通过。

## Task 3：进度保存、本人历史与旧版本恢复

**Files:** Create reading/ReadingProgressService.java；extend ReadingMapper/Service/Controller、ReadingDtos；Create frontend/src/reading/useReadingProgress.ts；Modify ReadingDetailView、ReadingListView、api/reading.ts。Test reading/ReadingProgressIT、frontend/tests/unit/reading-progress.spec.ts、tests/e2e/reading-progress.spec.ts。

**Interfaces:** `save(actor,versionId,ProgressUpdate(sentenceKey,offset,completed,expectedLockVersion)):ProgressDto`，PUT /reading/progress/{versionId}；`history(actor,page,pageSize):PageDto<ReadingHistoryDto>`，GET /reading/history。HistoryDto包含有权限的文章/版本摘要和progress，历史入口带 `/reading/:id?version=:versionId`；定位query仅锚点信息，不能替代服务端权限。`useReadingProgress`提供setPosition、markCompleted、flush、retry、reloadLatest、dispose；保存状态为idle/saving/saved/unsynced/conflict。

- [x] 写测试：边界offset0/句长可保存，未知sentence/越界/代理对中间偏移400；未start不得PUT；他人记录404；同lockVersion并发一胜一409 READING_PROGRESS_CONFLICT；completedAt只首次显式完成写入，滚动completed=false不撤销已完成。
- [x] 跑后端目标IT及前端reading-progress单元，Expected: 缺保存/历史与同步状态实现而失败；再实现归属检查+条件UPDATE，0更新区分无权/冲突而不泄漏他人资源。历史按updated_at DESC,id DESC，只返回本人记录，不依据收藏推导授权。
- [x] 前端句子进入视口后节流2秒，offset取句内合法UTF-16定位（不能可靠计算时0），恢复时定位相应Range；同页单写入队列，保存中变动合并下一次，响应只确认其发送版本。离开/visibilitychange尽力flush，卸载取消定时器/请求；不靠unload、不用无Bearer的sendBeacon、不自动重放网络失败写入。
- [x] 测试慢响应、失败、401/503、切文章、账号代次变化、明确完成及409：旧请求不把新进度标已保存，冲突只能用户选择重载，未同步可重试；重新登录/刷新仅恢复入库位置。旧版A阅读后发布B/下架，A历史仍可读，未开始的另一用户不能读A。
- [x] 运行 `./mvnw.cmd verify -Pmysql-it '-Dit.test=ReadingProgressIT,ReadingAccessIT'`、前端目标unit/build/浏览器。Expected: 全绿；记录跨版本/下架矩阵和网络失败状态，更新接口文档。

## Task 4：本地词典与桌面/手机查词

**Files:** Create V7__local_dictionary.sql、dictionary模块上述文件；Modify backend/pom.xml打包词典数据；`content/dictionary/entries.v1.json`、`content/english-token-cases.v1.json`。Create前端api/dictionary.ts、reading/selection.ts、useWordLookup.ts、WordLookupPanel.vue；extend ArticleBody/ReadingDetailView。Test dictionary/DictionaryRulesTest、DictionaryLookupIT、frontend/tests/unit/reading-selection.spec.ts、dictionary.spec.ts、tests/e2e/reading-lookup.spec.ts。

**Interfaces:** `DictionaryProvider.lookup(normalizedWord):List<DictionaryCandidate>`；`DictionaryService.lookup(actor,word):LookupDto(found,candidates)`，候选entryId/lemma/phonetic?/senses/sourceNote/collection，Task4尚未提供收藏模块，collection=null且不显示收藏按钮，Task5接入CollectionStateProvider后组装真实状态。`selection.ts`返回SelectionContext(articleId,versionId,sentenceKey,startOffset,endOffset,surfaceForm)或明确invalid原因；lookup(signal)可以取消。

- [x] 写词形/缓存/选词测试再运行目标unit，Expected: 缺实现失败。样例包含can't、learner’s、well-being、study/studies、空选区、跨句、两词、64/65字符、带emoji前缀的UTF-16位置；拒绝未配对代理及标点外围混入。不能硬猜去s/ed，精确lemma优先、其他仅显式form映射，允许多候选。
- [x] 实现V7 entry/form唯一约束、显式DictionaryDataLoader（固定本地数据路径/版本，不开放用户上传入口），启动时先完整校验再事务装载；同版本同摘要不重复，同版本不同内容失败；system_metadata独立key保存词典manifest版本/摘要，事务串行更新，entry以lemma更新并保留ID，不删除已有收藏引用。pom同时保留原resources并将../content/dictionary打包到classpath:dictionary，不依赖运行工作目录找文件。初始化用dataVersion=0.1的小的真实校对子集，Task6以1.0扩为保留初始词形的≥300超集；单元用自己的fixture，词典不足不冒充内容验收通过。
- [x] 实现仅公共词典结果的应用内缓存：最多512个规范词键，命中24h、未命中5min，Clock可测、替换数据版本使旧缓存失效；不缓存collection/user。未命中200 found=false；提供器故障503 DICTIONARY_UNAVAILABLE，不写词条/收藏。sourceNote为原创编辑说明，音标无可靠校对可省略。
- [x] 前端桌面单词点击/选择，手机原生选择后明确查词按钮；从同一句DOM文本节点计算偏移，缓存有效selection再打开面板，不干扰复制/滚动。面板适配视口、焦点进入、Escape关闭返焦；选择变化/卸载Abort且generation丢弃迟到结果，失败只在局部展示。
- [x] 运行 `./mvnw.cmd verify -Pmysql-it '-Dit.test=DictionaryLookupIT'`、前端选择/请求单元和桌面/320px浏览器。Expected: 未命中、故障、取消和多候选可操作；查词前后数据库收藏为零。Task4不添加提前可用的收藏按钮，Task5交付后再启用。

## Task 5：收藏、来源与个人生词本

**Files:** Create V8__collected_vocabulary.sql、vocabulary模块上述文件；Create dictionary/CollectionStateProvider.java并接VocabularyService实现；extend DictionaryService。Create前端api/vocabulary.ts、NotebookView.vue；Modify WordLookupPanel、router/index.ts、router/navigation.ts。Test vocabulary/VocabularyIT、frontend/tests/unit/vocabulary.spec.ts、tests/e2e/notebook.spec.ts。

**Interfaces:** `collect(actor,CollectInput(articleVersionId,sentenceKey,startOffset,endOffset,entryId)):CollectResult(word,created)`；`list(actor,page,pageSize):PageDto<CollectedWordSummary>`；`detail(actor,id,page,pageSize):CollectedWordDetail`（词条+来源分页）；`cancel(actor,id):void`。CollectionStateProvider按actor+entryIds实时查询，永不进入公共词典缓存。

- [x] 写失败测试：选中原文映射合法entry才可收；伪造原句/释义/userId未知字段400；无本人progress、错版本/句子、偏移越界、伪候选、他人word均拒绝；两用户同词不共享。运行目标IT，Expected: 无收藏接口/表而失败。
- [x] V8建立user_word唯一(user_id,entry_id)和source唯一(word_id,version_id,sentence_key,start,end)，来源文章归属/代理对/完整词边界由服务端恢复和校验，句子snapshot取不可变版本。对本人word行加锁或等效原子upsert，收藏、来源、激活同事务；不吞掉非预期SQL错误。
- [x] 实现201首次创建/200重复及恢复，重复点击同位置一条来源、不同位置保留；cancel仅本人、软取消幂等204，不删来源。测试collect/cancel并发按同一word锁线性化，再次收藏复用ID，不创建调度任务。下架后已有本人快照可收藏；草稿不能借来源越权读取。
- [x] 接入明确收藏按钮、loading/区域错误/成功Toast、/notebook分页/来源详情与返回快照锚点；取消用现有MessageBox确认。当前候选或身份改变后禁止发送旧收藏，等待CSRF期间切账号零业务写入；更新收藏状态只影响当前用户。
- [x] 运行 `./mvnw.cmd verify -Pmysql-it '-Dit.test=VocabularyIT,DictionaryLookupIT,ReadingAccessIT'`、前端unit/build/notebook浏览器。Expected: 并发重复只一个word、同位置一个source，用户/版本隔离、恢复、取消拒绝及词典缓存不串收藏状态全绿。

## Task 6：批量导入、10篇原创内容及配套词典

**Files:** Create V9__reading_import_batches.sql、reading/ArticleImportService/Controller、ArticleImportDtos.java、ImportBodyLimitFilter.java；`content/reading/original-b1-b2.v1.json`、`content/README.md`；extend dictionary数据/loader、AdminArticlesView、api/admin-articles.ts；Create components/reading/ArticleImportPanel.vue。Test reading/ArticleImportIT、content/ContentQualityTest、frontend/tests/e2e/article-import.spec.ts。

**Interfaces:** `validate(actor,ImportPayload):ImportValidation(valid,fieldErrors,itemCount)`无写入；`importDrafts(actor,clientImportId,ImportPayload):ImportResult(batchId,articleIds)`。Payload schemaVersion=1、articles为DraftInput数组；表单JSON解析错误与articles[2].title等逐项错误展示。clientImportId通过confirm接口query UUID传入，body与validate完全同形；1MiB按UTF-8完整body字节校验（含chunked流，不只看Content-Length），最多20篇，错误字段数上限100、超额另给简要说明。

- [x] 写并运行目标IT，Expected: 尚无导入接口失败。覆盖1MiB边界、非法JSON/版本/未知字段、21篇、混合无效批次零写入；并发同ID同作者同payload只一批，冲突payload或不同作者统一409 IMPORT_CONFLICT、不泄漏文章ID。
- [x] 规范化后的完整payload（包含所有编辑字段，不含clientImportId）以固定字段顺序JSON取SHA-256。批次、文章/快照/指针同事务；依赖唯一client_import_id处理并发，唯一冲突在新事务读取已提交结果，不能在已回滚事务里继续查询。validate只做校验，confirm重新校验，不信任前端validate结果；不接受发布状态、不发网络抓取请求。
- [x] 管理端文件选择→逐条问题/校验结果→明确确认导入草稿，禁重复提交；网络失败重试复用clientImportId，改文件生成新ID。大文件前端先拒绝、后端仍独立限制。导入后逐篇预览/发布，绝不导入即发布。
- [x] 编写五类各2篇原创350—650词、对应中英标题/简介/署名/来源/使用说明/重点词；词典扩至≥300基本词形和所有重点词映射。数据文件记录schemaVersion、dataVersion、编辑说明和重点词清单；新增shared实例校对词形/原句/义项一致，避免复制无授权媒体内容。
- [x] ContentQualityTest检查数量、五类、词数、来源字段、词典唯一性、所有重点词/形式可查和offset样例；它不宣称官方B1/B2评级或代替人工语言校对。跑完整内容/导入测试和浏览器，Expected: 全绿；实际校对记录在content/README.md，未校对逐项标待完成，不能以300条占位解释凑数。

## Task 7：今日推荐、首页接入、完整验收与服务交接

**Files:** Create reading/RecommendationService.java、frontend/tests/e2e/reading-real.spec.ts、docs/acceptance/phase-2.md；extend ReadingController/Service、ReadingListView、api/reading.ts、HomeView.vue；Update docs/api.md、database.md、progress.md、README.md、decisions.md及本计划勾选状态。

**Interfaces:** `recommend(actor):RecommendationDto(date,article)`，上海LocalDate，按已发布文章ID升序集合、floorMod(date.toEpochDay(),size)选择；空集合article=null。同日集合不变稳定，下架立即排除；返回推荐不授予start权限。首页阅读按钮使用推荐或回退/reading，只有外刊精读和笔记本新入口启用，其他功能和真实空计划/打卡不改。

- [x] 先写Clock跨上海午夜、空集合、稳定集合、下架/发布变更测试，再运行目标单元，Expected: 缺RecommendationService失败；实现并接入列表/首页，不以文章点击制造学习统计。
- [x] 跑 `./mvnw.cmd verify -Pmysql-it`、前端全部unit/typecheck/build、桌面和320px完整Mock浏览器；在独立测试库/6381 Redis后端与本地SMTP收集器运行真实阅读闭环。Expected: 退出0，记录真实数目、跳过原因及每类证据，不沿用阶段1数字。
- [x] 真实流程：测试管理员创建/预览/发布→普通用户读→保存/重新登录恢复→查词→收藏/生词本→另一用户隔离→发布新版本/下架后本人历史→取消恢复；验证刷新令牌、CSRF、身份变化仍按认证规范工作。测试夹具只清理本轮创建的记录；任何历史依赖不通过DELETE CASCADE绕过。
- [x] 按选定执行方式做整阶段独立评审，覆盖上方五项Review Focus；修复重要问题并重跑受影响验证。打开实际阅读/管理/手机选词界面与内容清单，用户未确认视觉/内容时分列“自动验证完成、人工确认待完成”。
- [x] 执行前后端同契约切换前备份可确认的运行构建和配置路径，先测试迁移；MySQL DDL可能部分提交，Flyway失败按实际状态修复/新增迁移，不假称rollback可撤销DDL，不改已执行文件或清库。保留现有QQ凭据及可联网后端运行方式；不发真实测试邮件。最终保持4174/8080/6380体验服务可用，按授权正式导入仅创建草稿，发布仍需管理员明确动作。
- [x] 更新验收/进度/接口/数据/内容校对和未完项；说明未做AI/复习/考试，阶段3另需授权。只交付本阶段变更，不自动提交整个脏工作区。

## 验证命令与执行约定

根目录先 `. ./scripts/use-workspace-tools.ps1`、`. ./scripts/use-java21.ps1`，`backend/mvnw.cmd -version`确认实际Java21；项目test Redis先 `./scripts/project-redis.ps1 -Environment test -Action start`。Maven命令在backend目录，前端命令在根目录。依赖下载、CIM进程归属或SMTP联网受沙箱限制时走明确工具审批，不改全局权限。

每任务RED记录真实失败，GREEN运行其目标IT及相关单元；构建命令 `pnpm --dir frontend build`包含类型检查。Mock浏览器采用 `E2E_EXTERNAL_SERVER=1 E2E_PORT=4174 pnpm --dir frontend exec playwright test <目标文件> --workers=1`；真实联调另起8081/4175、允许源明确4175、测试数据库与Redis及本地SMTP2525/2526，设置E2E_REAL_BACKEND/E2E_MAILBOX_URL。禁止并行运行共享数据库、dist或Playwright报告的测试。

测试ReadingHttpITSupport自行持有按client分组的Bearer与Cookie，导入TokenTestSupport和测试数据库配置，不继承包不可见的identity/TokenHttpITSupport；fixture登记操作者、文章/版本/个人记录，按FK依赖顺序精确清理。本阶段新增SchemaMigrationIT预期表数，在已有V4专用测试库验证升级、17表/索引/复合FK及二次启动无新增；完整空库V1→V9只在另有明确授权且已隔离的空环境时验证，当前不新建服务器、不清现有测试库，未具备则如实列未运行。不要让旧“8表”断言掩盖迁移或直接删原测试。

## 自审与交接

覆盖映射：管理/版本/来源→Task1—2；恢复/下架/本人历史→Task2—3；选词/词典/cache→Task4；收藏/来源/取消→Task5；导入/原创与300词→Task6；推荐/首页/全量测试/视觉与内容确认→Task7。五项Review Focus均有具体归属测试；个人状态不会进入公共缓存，所有异步写入保留认证代次。

迁移V5文章、V6进度、V7词典、V8收藏、V9导入；相邻任务类型名和API只以上方Interfaces为准。本文仅编写并自审计划，未运行新阶段测试、执行迁移、编写正文/词典或改动运行服务。

建议沿用当前会话顺序实施（Native），最后一次独立评审：文章版本、进度与词条来源接口紧密依赖，同一实现者逐步接入可减少交接误差。用户已批准阅读设计；本计划请先审阅，确认计划和执行方式后再实施。
