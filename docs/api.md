# API 契约与异常日志约定

## 2026-11-02 词汇中心正式升级

所有下列接口继续要求本人登录、原有CSRF保护、资源归属和no-store；不调用翻译或AI。单词与词组的收藏、加入学习、手动免复习和暂停互相独立。

### 单词学习v2

- 现有`POST /api/v1/vocabulary/learning/sessions`请求可加`flowVersion:"v2"`；Command增加`attemptId`。写操作保留requestId/expectedVersion/itemId，重放同请求返回原结果，冲突409。旧客户端省略flowVersion仍用v1交互；新词item冻结v2规则，真正旧保存item维持v1；新交互与调度版本相互独立。
- v2 feedback新增HINT、ERROR、PROFICIENT。HINT仅用于复习默写，携带当前正确前缀spelling；ERROR/HINT落库同次尝试证据但保持QUESTION，更新版本；过期attempt拒绝。辅助完成不算通过，进入详情后next回队；干净再练可完成，但本轮失败仍使调度回1天。
- `PROFICIENT`：跳过当前项、全局免复习，不伪造correct事件；管理接口`PUT /learning/words/{id}/proficiency {proficient:boolean}`可恢复原计划，不重写due历史。
- Current新增entryId、learningWordId、attemptId、assisted、proficient、hintCount、hintPrefix、完整词典detail。复习默写题面隐藏英文/音标，后续队列不泄露英文；新学跟打中文释义在整轮结果集中显示。
- `GET /learning/sessions/{id}`读取本人指定轮次；恢复用该id的state ACTIVE，不能按当前选书重新start来冒充恢复。overview.savedSessions新增sourceKey，全部保存轮次可达，范围在轮次内冻结。
- Settings新增autoNext/reviewScope（ALL/CURRENT），以及batchSize（可选整数1—50，默认20，省略更新时保留原值）。新用户默认20/100，已有配置不覆盖；复习目标仅参考，继续处理积压不封顶；新词已完成+预留额度全局共用。看词手动next，跟打逐词确认后连续next，复习默写在释义页手动next。autoNext保留为兼容字段，当前前端不再据此自动切换复习。
- v2间隔1/3/7/15/30/60/90天；旧记录due不重写，旧flow事件标LEGACY，不计入新独立通过口径。旧JSON缺新字段时恢复默认而非解析失败。

### 生词本与学习收藏

- `POST /api/v1/vocabulary/words/from-learning {sessionId,entryId}`只允许收藏本人该轮次实际快照中的词条，原子恢复/创建收藏；重复收藏不重复来源，不更新学习版本或队列。保留原文来源；没有articleId时用独立学习/词书来源记录，绝不伪造文章。
- 原`GET /vocabulary/words`增加q（最多80字符）与status：ALL/UNJOINED/LEARNING/PROFICIENT/DUE/DIFFICULT，服务端分页；WordDto增加可空learning（学习id、personalEnrolled、active、stage、dueAt、proficient）。旧字段保留。
- `GET /vocabulary/words/{id}`保留原sources分页，增加origins（学习/词书来源）和difficulty。取消收藏不删除来源或学习历史，重新收藏恢复同一记录。
- `GET/PUT /learning/words/{id}/difficulty`：PUT `{difficult:true/false/null}`分别手动标记/取消/恢复自动。自动按最近最多5个v2有效item+attempt窗口，错误/辅助达到3次；重复提示不扩成多次attempt。返回difficult/attempts/assistedAttempts/manualOverride，不足5次显示实际次数；难词不等于免复习。
- `/learning/stats`保留旧字段，增加independentReviewPasses、streakDays、longestStreak、recentDays（30天）、difficult；上海日期去重。今天尚未完成时沿用截至昨天的连续记录；没有完成历史不补造，手动跳过不算完成。

### 词组本

- `GET /api/v1/vocabulary/phrases?query=&includeCancelled=false&page=1&pageSize=20`：items/stats/page/pageSize/total。`POST`提交text/meaningZh/note及可选source；本人规范英文词组去重，重复收藏恢复但不覆盖已有个人释义。
- `PATCH /phrases/{id}`编辑中文释义/备注；`DELETE`取消；`POST /{id}/restore`恢复；`PUT /{id}/learning {enrolled?,exempt?}`独立控制学习与免复习。
- 来源为文章版本/句key/真实offset。练习来源额外提供articleId、practiceAttemptId或paperAttemptId+slot；复用既有可见性/交卷权限，服务器重新核对原文选区。多个来源快照保留，取消不删。
- `GET /phrases/practice`读保存轮次，无轮次返回HTTP200、`application/json`和显式`null`；`POST {requestId,mode:RECOGNIZE|WRITE,limit}`仅分配未学或到期且未免复习的词组；`POST /practice/{id}/commands`使用requestId/expectedVersion及action：REVEAL/HINT/ERROR/PASS/NEXT/SAVE/RESUME/DRAFT。
- 词组状态含阶段、dueAt、ruleVersion；独立7阶段1/3/7/15/30/60/90天，失败轮次最终完成回1天。HINT/ERROR/PASS事件与命令持久化并幂等；只有无辅助、无本轮失败的WRITE完成计独立默写，认义自评可完成但不计独立默写。
- 词组收藏/事件/轮次/调度独立，不写入单词events/mastery或占用新词额度。数据库V35—V39为增量迁移，已执行迁移不可改写。

## 2026-10-21 整组人工校对确认

ADMIN `POST /api/v1/admin/practice/sets/{id}/review` 增加 `{expectedLockVersion,kind:"QUESTIONS",reviewed:true}`，不传questionKey。按当前草稿整组验证题目/词库/原文标记后，在一个事务中确认全部题目；题目不完整返回400且不部分写入，版本冲突409，权限/CSRF沿用。reviewed=false撤销全部题目确认。两种操作都撤销来源总确认，不发布、不改题、不接AI；SOURCE和publish继续分别调用。客户端不提交校对哈希。

## 2026-10-20 专项专用原文接口补充

- ADMIN `POST /api/v1/admin/practice/material-sets`：`{requestId,body,metadata,questions,expectedLockVersion:0}`，当前固定CET4/DETAILED；同事务创建专用原文和未校对题组，返回AdminSet。requestId为16–64位有效标识，同用户同内容重放返回原题组，不同内容409。正文须有英文；标题/答案等可暂缺，发布仍须完整校对。
- ADMIN `PUT /api/v1/admin/practice/sets/{id}/material`：相同结构，expectedLockVersion使用题组当前锁版本；仅接受专用原文题组，普通阅读文章复用返回400。原文改动插入不可变版本并撤销校对，未变不更换原文版本，过期锁409。此接口不发布材料。
- AdminSet新增 `materialOnly:boolean`。原有基于articleId/articleVersionId的创建/保存、JSON校验/导入入口继续可用。
- 专项发布、公共目录/详情、新开始练习只依据题组发布版本和状态，不要求原文作为阅读文章发布。阅读列表/推荐排除PRACTICE原文；文章管理保存/发布拒绝专用原文，防止绕过题组锁及校对。ADMIN可读取原文版本，学员只经已发布题组或本人已开始轮次访问专项内容。

所有写接口继续鉴权与CSRF；错误和no-store约定不变。此补充覆盖下文早期「先发布关联文章」前提。

## 2026-10-19 专项正式API

统一前缀 `/api/v1`，沿用身份、CSRF、字段错误与no-store约定。ADMIN写入不接受客户端分数、用户ID或校对hash。所有ID为字符串，分页1起/默认20/最多50。当前只支持CET4/DETAILED写入，其余考试是规划筛选空态。

| 路径（省略统一前缀） | 方法 | 行为/权限 |
| --- | --- | --- |
| `/practice/catalog`、`/practice/sets`、`/practice/facets`、`/practice/sets/{id}` | GET | 公共考试目录/发布题组/联动条件/材料详情；不返回答案，个人状态只对本人提供 |
| `/practice/sets/{id}/attempts` | POST | 登录；`{setVersionId,requestId}`，开始或恢复本人FULL |
| `/practice/attempts/{id}` | GET | 本人冻结轮次；IN_PROGRESS无correctOption/explanation/correct/score，完成后有结果 |
| `/practice/attempts/{id}/draft` | PUT | 本人；`{expectedVersion,answers:[{questionId,choice}],currentQuestionId}`，A–D或null，版本冲突409 |
| `/practice/attempts/{id}/submit` | POST | 本人；`{expectedVersion,requestId,confirmUnanswered}`，整组判分，未答计错需确认 |
| `/practice/attempts/{id}/retry-mistakes` | POST | 本人；`{requestId}`，来源轮次冻结错题子集 |
| `/practice/history`、`/practice/mistakes` | GET | 本人分页；历史IN_PROGRESS/COMPLETED，错题PENDING/RESOLVED |
| `/admin/practice/sets` | GET/POST | ADMIN；管理列表/创建草稿 |
| `/admin/practice/sets/{id}` | GET/PUT | ADMIN；详情/保存；PUT含expectedLockVersion；允许未完成内容，结构仍有界 |
| `/admin/practice/sets/{id}/review` | POST | ADMIN；`{expectedLockVersion,kind,questionKey,reviewed}`，QUESTION或SOURCE，基于已保存内容 |
| `/admin/practice/sets/{id}/publish`、`/unpublish` | POST | ADMIN；`{expectedLockVersion}`，服务端重新检查后发布快照/下架 |
| `/admin/practice/sets/{id}/versions`、`/versions/{revision}` | GET | ADMIN；历史分页/不可变内容预览 |
| `/admin/practice/import/validate`、`/admin/practice/import` | POST | ADMIN；只读校验/幂等导入未校对草稿；实际输入流限制256KiB |

列表条件 `q,exam,readingType,source,topic,year,month,paperNo,page,pageSize`；管理端 `status=DRAFT/PUBLISHED/UNLISTED`、`sort=NEWEST/TITLE/COUNT`，公共端 `state=ALL/IN_PROGRESS/COMPLETED/NOT_STARTED`、`sort=LATEST/TITLE`。游客不能使用私人完成状态。时间只筛真题；无匹配返回真实空列表，facets按其他条件及可见性计算。

草稿/导入结构见 `PracticeDtos.DraftInput/ImportInput`：已有articleId/articleVersionId、examCode、readingType、metadata、1–10个questions；题目4个A–D选项，correctOption可null。metadata含title/titleZh/sourceKind/topic/year/month/paperNo/sourceName/sourceUrl/usageNote，ORIGINAL时间全NULL，PAST发布须来源与使用依据。导入额外requestId，不允许review声明/未知内部属性；校验不写入，确认不覆盖已有同文章题组。保存响应含checks、lockVersion、逐题/来源校对状态。

START/SUBMIT/RETRY/IMPORT的requestId为16–64字符字母数字连字符，同账号全局命名空间，同ID同规范化内容重放原结果，异参数409。草稿乐观版本冲突须显式重新加载，不以新requestId绕过冲突。下架限制新公开开始，已获得旧轮次仅本人可恢复/重练；旧阅读ARTICLE接口拒绝PRACTICE，不能提前取答案。[操作说明](practice-guide.md)、[实际验收](acceptance/2026-10-19-practice.md)。

## 2026-10-19 首页与退出保存（覆盖旧单轮次限制）

沿用全部学习接口与身份/CSRF/幂等规则，无新用户或词典体系。

- overview新增`savedSessions:[{id,kind,mode,status,phase,passed,total,sourceTitle}]`与`reservedNew`。只列本人当前来源NEW及全局REVIEW保存摘要；reservedNew为所有未完成NEW轮次的未通过项数。source新增可空wordCount。unlearned排除其他来源轮次占用词，due排除任何未完成轮次占用词；stats.due仍为全部到期事实。
- POST `/sessions`按NEW+当前来源或REVIEW+GLOBAL优先恢复保存轮次，保留原mode/题目/释义。其他ACTIVE存在时409。新学和复习按settings.batchSize开组，默认20、可设1—50词；不足时按实际可分配数量开始，每日目标仅作参考。保存轮次保留原题量，已预留的词不会重复分配。所有学会的新词按同一规则安排复习。
- state PAUSED即「退出并保存」，收到成功后才回首页；失败留原卡按原requestId重试。恢复ACTIVE仍需没有其他ACTIVE。GET `/session`优先ACTIVE，否则最新PAUSED或null；首页继续入口取overview对应来源摘要。
- PUT `/source`允许PAUSED时切换，ACTIVE时先退出。GET `/wordbooks`包含已发布书和本人有未完成NEW的下架书；仅该所有者可切回下架书恢复，不分配新词。
- NEW/REVIEW分配均排除未完成轮次已占用的学习词；用户写锁串行，数据库约束每scope一个未完成轮次、账号最多一个ACTIVE。私人归属/CSRF/版本与命令重试规则保持。
- 拼写前缀仅本标签页sessionStorage按账号/轮次/题目/版本保存，恢复迁移合法前缀；新标签页或存储受限可能重输字母。轮次、反馈、释义由服务端持久化。

新版页面为词书栏、双卡、方式弹窗、今日进度/目标弹窗、折叠词管理。以下先前单轮次说明均以本节覆盖。


## 2026-10-19 参考词书与词汇统计

新增认证/no-store接口，仍在`/api/v1/vocabulary/learning`前缀下，写入保留CSRF，私人数据均取当前身份：

| 方法与子路径 | 契约 |
| --- | --- |
| GET `/wordbooks` | 已发布词书数组：id/title/dataVersion/sourceUrl/notice/originalCount/availableCount/missingPhoneticCount/excluded/status/learned；learned为本人已学习过数量，非永久掌握 |
| GET `/source` | `{kind:PERSONAL或BOOK,bookId,title,available,reason}`；无个人设置默认PERSONAL |
| PUT `/source` | `{kind,bookId?}`；BOOK需已发布或本人有保存轮次，其他未知/下架404 WORD_BOOK_UNAVAILABLE；PERSONAL不带bookId；有ACTIVE时先退出，PAUSED可切换；重复选择原来源幂等 |
| GET `/stats` | `{today,weekStart,days:[{date,newLearned,reviewed}],totalLearned,totalReviewPasses,due,paused,completedRounds,nextDueAt}`；上海周一至周日、七天有界查询，不接受客户端统计数字 |

原overview增加source摘要，不删旧字段。NEW按服务端选中的来源取词，每组按batchSize分配，每日新增目标仅用于显示进度；BOOK按书内顺序懒分配，选择不生成数千条个人行。PERSONAL只取personal_enrolled=true；个人主动加入书中已有词仅设置该标记，不改active/stage/dueAt。REVIEW始终跨本人全部激活来源，不受新词来源切换或书下架影响。书下架不能开新词任务，已开始轮次仍可继续。旧四级参考词表下架，四级乱序版和核心词汇版仍可选择。

统计只读V16学习/事件/轮次：totalLearned为stage>0的本人去重词条，暂停保留历史；totalReviewPasses为REVIEW成功反馈词次；日newLearned/reviewed为对应kind的唯一成功item数；completedRounds为COMPLETED轮次。失败重练/请求重放不增加第二次通过。日期窗口由Clock和Asia/Shanghai转UTC，读取不补写打卡或造任务。阅读和词汇统计独立请求/错误/重试，页面聚焦及上海跨日刷新。

参考词书为固定ECDICT cet4标签，本地开发、非官方大纲：原始3849、可学3846，3个缩略词明确排除，源数据10词缺音标；来源/版本/hash/排除详情见content/vocabulary/ecdict-cet4-local。公共导入默认关闭，显式开关english-hero.vocabulary.cet4-reference.enabled；无在线收费API。

## 2026-10-19 个人生词学习与到期复习

新增认证前缀`/api/v1/vocabulary/learning`；沿用JWT身份、CSRF和业务错误字段，全部no-store。本人身份取服务端，不接受userId、客户端排期或correct标志；收藏接口保持兼容。

| 方法与子路径 | 输入或结果 |
| --- | --- |
| GET `/overview` | candidates/unlearned/due/nextDueAt、上海日newToday/reviewToday、settings、sessionId |
| GET `/candidates?page=1&pageSize=20` | 本人活动收藏分页，wordId/lemma/meaning/joined/paused/unavailableReason |
| POST `/words` | `{wordIds}`，1..20个本人活动收藏；同entry去重，不覆盖已有阶段 |
| GET `/words?page=1&pageSize=20` | 本人已加入词，含active/stage/dueAt；最大pageSize=50 |
| PATCH `/words/{id}` | `{active}`暂停/恢复；未结束轮次中的词返回409 |
| PUT `/settings` | `{newGoal:1..100,reviewGoal:1..200}`，无设置默认20/50，复习目标非硬上限 |
| GET `/session` | 本人未完成轮次或JSON字面量null |
| GET `/sessions/{id}` | 本人轮次或完成结果；他人404 |
| POST `/sessions` | `{requestId,kind:NEW或REVIEW,mode:RECOGNITION或SPELLING}`；最多20词，NEW受当日剩余额度限制，REVIEW只取到期词并按due_at/id排序；同scope保存轮次优先恢复 |
| POST `/sessions/{id}/feedback` | `{requestId,expectedVersion,itemId,action,optionId?,spelling?,hadSpellingError?}`；认词接受KNOWN/UNKNOWN/CHOICE；拼写只接受完整英文、忽略大小写，服务器判定 |
| POST `/sessions/{id}/next` | `{requestId,expectedVersion,itemId}`，只允许释义状态推进，失败回队尾 |
| POST `/sessions/{id}/state` | `{requestId,expectedVersion,status:ACTIVE或PAUSED}`，保留题目/释义 |

空结果写操作和无轮次读取均为HTTP200、application/json、字面量null，不能返回空HTTP200。requestId为16..64位字母/数字/连字符（正常用UUID）；同ID同参数返回原响应，同ID异参数409。无任务409 LEARNING_EMPTY，旧版本/非法状态409 LEARNING_CONFLICT，参数错误400 VALIDATION_ERROR并给字段错误。

SessionDto字段id/version/kind/mode/status/phase/current/queue/passed/total/results；status为ACTIVE/PAUSED/COMPLETED，phase为QUESTION/DEFINITION。成功反馈立即排期并保存释义，next仅切卡；不认识/错选看释义后回队尾。认词题和队列不提前显示中文答案，不含correctOptionId；中文干扰项不足时只提供认识/不认识。词典和来源来自当轮不可变快照。

拼写题提供本人当前spellingTarget作逐字辅助，页面不渲染英文答案，最终仍后端精确校验；该模式用于学习，不承诺保密考试。前缀/提示保存在本人本标签页缓存；合成输入结束后校验，输错清空、完整拼对发一次反馈，错误不跳词。hadSpellingError仅降低排期，不把客户端尝试数当权威成绩。网络失败保留编号和重试入口，离开返回亦可恢复；换号清空旧状态。

fixed-v1-binary间隔1/3/7/14/30天，stage5以后保持30天；本轮出错后最终通过重置stage1/隔1天。单词复习以 Asia/Shanghai 的自然日期加天，到期日凌晨05:00开放，持久Instant。已有未来单词复习时间经V41对齐至同一到期日05:00，已到期词继续可用。GET无学习副作用，取消收藏不取消学习，词汇不计阅读打卡。当前仅个人生词，词书与/stats词汇扩展未开放。

## 2026-10-17 参考译文第三小阶段：覆盖十篇原创

版本tools.translation结构保持不变，编辑参考目录dataVersion=1.1覆盖10篇原创、60段及13条已准备句级译文。仍按标题和规范化正文完全匹配，不给修改后的版本套用旧译文；原接口会按该版本真实paragraph/sentence key返回。来源继续“项目编辑参考译文（待人工校对）”，已做独立AI复核但未冒充人工校译。

纯英文默认、英中段落对照、全文中文和完整段落选区复用同一数据；仅已有13条准备句可单独匹配，任意句子的在线翻译未启用。其余7篇源稿的译文随JAR备好，不自动发布草稿或写阅读记录；公开详情仍只返回已发布当前版本，私人旧版规则不变。下方旧“3篇18段6句”为此前范围。

## 2026-10-17 词典第二小阶段：保持原查询/收藏接口

GET /api/v1/dictionary/lookup?word=...的found/candidates/collection结构不变。精确词头与显式词形优先，无结果才进入最多16个词形候选的单次词头批查，并用词性、正向变化及随包已知范式验证；覆盖常规复数/时态/比较级及有限不规则形式。强制双写和已知范式不接受runing/stoped/goed/childs/gived/admited等错拼；不宣称完整英语词法分析。

用户选择ECDICT本地开发离线子集，开启english-hero.dictionary.ecdict.enabled后在启动事务补充既有公共词典表：10,000词头/23,112词形，保留原319内容/ID，合并去重10,006词头。无外部API、按次费用或第三方密钥；原词条优先，部分原词仍无音标。外部词条sourceNote标识ECDICT、MIT仓库声明和混合上游来源；许可记录见content/dictionary/ecdict-local/NOTICE.md。既有公共有界LRU的manifest包含原词库及补充hash，未缓存私人collection。

第三方词条definitionEn允许空字符串，前端省略无数据的英文释义，不编造双解或音标。英/美按钮调用浏览器en-GB/en-US合成语音，实际口音取决于本机语音库，不是供应商真人音频；例句为当前文章真实原句。无可靠数据的词根/短语/近义词栏目不显示。收藏/取消/恢复继续校验身份、版本和完整原文偏移，新词及还原词都必须对应词典真实稳定entryId。

## 2026-10-17 精读详情第一小阶段接口

新增 GET /api/v1/reading/articles/{articleId}/versions/{versionId}/tools，可公开读取已发布当前版本；本人已有阅读记录才能访问旧版/下架版，草稿不可访问，沿用ReadingService.version的400/404规则。返回no-store：
- related：至多3篇其他已发布文章，当前分类优先、发布时间/ID稳定排序。
- previous/next：按文章ID顺序的已发布相邻文章或null；不包含草稿。
- keywords：原文完全匹配项目原创目录时的编辑重点词，含entryId/lemma/forms/phonetic/senses/sourceNote，不是考试词书或用户生词率。
- translation：available/source/paragraphs[{key,text}]/sentences[{key,sourceText,text}]。当前准备3篇原创的18段及6句参考译文，明确待人工校对；标题与规范化原文不一致则为空。无外部服务调用，不宣称任意文本可翻译。

新增 GET 同一版本前缀 /words?page=1&pageSize=10，要求认证且本人有该版本reading_progress，验证articleId与versionId属于本人记录；失败401/404。PageDto.items为{word:WordDto,source:SourceDto}，按当前用户活动生词与该版本来源筛选后计数/分页（size最大50），词条去重。userId不从客户端取值。全局笔记本与收藏POST接口不变，服务器仍验证完整单词/原文位置/entryId及CSRF。

公开工具响应不包含私人word来源；个人词汇独立请求。读取工具不创建进度；保存生词仍要求显式操作，失败不伪造已存状态。前端合成朗读不是词典真人音频，当前音标未准备时明确显示暂缺。


## 2026-10-17 阅读历史筛选修正

GET /api/v1/reading/history 现在支持 keyword（最长80，英文标题/中文译名/简介）、categoryId、difficulty（B1/B2）、sort（recent默认、newest、short、long）和现有page/pageSize。在数据库对本人所读的不可变版本筛选、计数后分页，保留本人旧版及下架记录；不依赖前端过滤当前页，不暴露其他用户记录。newest按文章发布时间，其余同值以最近阅读时间、记录ID稳定排序；非法参数400。原接口不带新参数时行为兼容。无需迁移。

此规则取代下文历史记录中的“阅读历史禁用筛选”。游客仍需登录查看本人历史。


## 2026-10-16 阅读增量接口（当前生效）

本节取代下文历史版本中“阅读必须登录”的限制；认证 API、JWT + Redis 和 CSRF 规则不变。以下路径均以 /api/v1 为前缀，响应 no-store。

- 公开 GET /reading/categories、/reading/recommendation、/reading/articles、/reading/articles/{id}。列表支持 keyword（最长80，匹配英文标题/中文译名/简介）、categoryId、difficulty（B1/B2）、sort（newest/short/long）、page、pageSize（默认20、最多50，界面每页6篇）。参数无效返回400及字段信息；筛选不影响独立推荐。
- GET /reading/articles/{id}/versions/{versionId}：游客仅可访问已发布的当前版本；旧版/下架版仍要求登录并拥有该版本阅读记录。草稿不公开。游客正文与列表 progress 为 null，读取不创建记录。
- GET /dictionary/lookup 允许基础公开查词，沿用现有有限单词词典与长度规则；游客 collection=null，不加载私人收藏。
- 私有 GET /reading/favorites：与全部列表相同的组合筛选和后端分页。GET /reading/favorites/states?ids=1,2：最多50个有效文章ID，返回本人已收藏ID集合。
- 私有 PUT /reading/articles/{id}/favorite、DELETE 同路径：设置/取消收藏，幂等，身份来自服务端 principal。新增只允许已发布文章；取消允许下架文章，草稿不可操作。写入仍要求 CSRF。
- /reading/history、POST /reading/articles/{id}/start、PUT /reading/progress/{versionId}、个人生词保存仍要求认证及归属校验。未认证私人接口401；无权访问正文按现有404约定处理。

游客收藏触发现有登录弹窗；同一页面登录成功后重放明确的文章收藏意图，失败局部重试。历史按最近阅读排序，不套用文章列表筛选。游客保存生词先登录，再重新选词，避免跨身份重放过期选词上下文。


## 阶段2：管理员草稿（已交付）

2026-10-16：新构建已实现GET /api/v1/admin/articles/categories、GET /admin/articles（status/categoryId/keyword/page/pageSize）、POST /admin/articles、GET /admin/articles/{id}、PUT /admin/articles/{id}/draft及GET /admin/articles/{id}/preview?versionId=。以上省略路径统一前缀/api/v1，仅ADMIN，写操作CSRF。创建201，保存/预览200；版本冲突409 ARTICLE_VERSION_CONFLICT，参数错误结构化fieldErrors，未知/无权文章404。当前4174/8080已切换阶段2构建，完整结果见阶段2验收记录。

DraftInput为categoryId/title/titleZh/summary/difficulty/body/author/sourceType/sourceUrl?/usageNote；更新额外expectedLockVersion。保存产生不可变版本，不发布；预览正文纯文本。字数与预计分钟来自规范正文，不是学习统计。

## 2026-10-16：JWT + Redis认证

本节覆盖下方历史Session契约中的认证方式，其余用户名、邮箱、资料、密码字段不变，前缀仍为 `/api/v1`。

| 接口 | 当前行为 |
| --- | --- |
| GET /auth/csrf | `{token,headerName:"X-CSRF-TOKEN"}`，masked JSON令牌，HttpOnly EH_CSRF Cookie、Path=/api/v1，不创建HttpSession |
| POST /auth/login | `{username,password,rememberMe}` → 200 AuthTokens，设置EH_REFRESH并删除历史EH_SESSION及旧CSRF |
| POST /auth/refresh | 无凭证请求体，读取刷新Cookie → 200 AuthTokens，原子轮换刷新Cookie |
| GET /auth/me | Authorization: Bearer；200原UserDto |
| POST /auth/logout | CSRF+可信Origin/Referer；合法刷新Cookie或有效Bearer撤销本次登录；无凭证幂等204，清Cookie |
| account/admin | 原业务字段，Bearer+写操作CSRF，实时检查账号、角色和归属 |

AuthTokens为 `{user:UserDto,accessToken:string,tokenType:"Bearer",expiresIn:number}`。expiresIn最多600秒，且不超过本次登录绝对期限。Token只留前端内存，不进入URL、持久浏览器存储或日志。

EH_REFRESH为host-only、HttpOnly、SameSite=Lax、Path=/api/v1/auth，生产Secure；普通登录无持久Max-Age，记住登录不超过原登录起7天的剩余时间。普通空闲2h、绝对24h；受保护请求核对MySQL启用/版本/角色和Redisfamily，刷新不重置绝对期限。

登录、刷新、退出除CSRF外必须有明确允许的Origin或同源Referer；缺失/跨源403 CSRF_INVALID，不信任转发来源头。所有原写接口继续CSRF。登录/退出清旧CSRF，下次写入重新GET；普通Bearer请求不轮换CSRF。

有效签名、账号及family均有效而只有Access过期，才返回401 ACCESS_TOKEN_EXPIRED；前端共享一次刷新并最多重试一次。其他401、403、503、超时不得自动重放写入。已消费刷新重放撤销本family，随机错误秘密不撤销他人登录。Redis不可达503 AUTH_SERVICE_UNAVAILABLE，不只验签旁路、不伪退出。

改密/找回继续在MySQL递增security_version；全部旧凭证失效，即使Redis清理失败。邮箱公开端点忽略无关旧Bearer，防枚举/限流不变。配置与恢复见 [认证配置](auth-jwt-redis-setup.md)。

日期：2026-10-14。系统状态与基础异常/请求追踪已实现；真实数据库联调和业务错误仍分阶段验收。

## 2026-10-15：发送状态补强

阶段2阅读/词典/收藏接口仍在[设计草案](superpowers/specs/2026-10-15-phase-2-reading.md)中，本文件以下为已实现接口，不宣称新业务API已可调用。

公开接口的requestState=ACCEPTED只表示请求已受理，绝不包含匹配结果或投递结果。不匹配请求不发邮件、不作废已有真实证明；仍保留相同响应结构及限流，执行同形的数据库和异步排队流程，响应不等待SMTP。公开找回没有投递状态查询接口。

账户绑定在登录+当前密码检查通过后，提前检查邮箱占用，409 EMAIL_TAKEN定位email字段，不进入发送队列。状态查询只允许当前用户、当前security_version、BIND用途；未知、非本人、非BIND或无效UUID统一404 EMAIL_REQUEST_NOT_FOUND，未登录401。SMTP_ACCEPTED仅表示SMTP服务接受，不保证收件；FAILED包括发送失败或证明已禁用，EXPIRED表示到期，NOT_SENT用于未发送/历史状态，不能视为成功。状态读取不消耗或延长验证码。

前端请求中阻止重复点击；有效ACCEPTED响应后按resendAfter显示「再次请求」，429按Retry-After；网络/格式错误/503不伪造正常冷却，也不自动重放POST。账户状态最多自动查询15次、间隔2秒，可手动重新检查；更改邮箱、新请求或离开页面取消旧查询并忽略迟到响应。发送失败不清除真实服务端限流。

## 当前接口：系统状态

`GET /api/v1/system/status`，无请求体、无查询参数。阶段 0 允许匿名 GET，成功与查询失败均不缓存（Cache-Control: no-store）。

200 响应：

```json
{"status":"UP","service":"english-hero","database":"UP"}
```

数据库查询异常、元数据缺失或标识不正确时返回 503：

```json
{"status":"DOWN","service":"english-hero","database":"DOWN"}
```

字段均为字符串，status/database 只使用 UP/DOWN；service 固定为 english-hero。不返回数据库地址、账号或底层异常。

这些分支已有代码与单元/MVC测试记录；2026-10-14已完成真实数据库200/UP及浏览器联通验证。数据库迁移在应用启动时失败会使应用启动失败，与已启动服务返回 503 是两种不同场景。

local profile 允许 GET `/v3/api-docs` 和 Swagger UI 资源；prod profile 关闭文档。阶段1新增身份接口见下节，其他未实现API仍默认拒绝。

## 当前前端请求层

- 仅允许 `/api/v1/` 下的同域路径，使用 same-origin credentials。
- 请求总超时 5000ms；支持调用者取消；非 2xx 抛出带 HTTP status 的 ApiError。
- 系统状态页校验 DTO，200/DOWN 不显示成功，HTML 回退不视为合法 API 响应。
- 真实数据库测试和浏览器 Mock 状态测试分别记证据。

## 后续接口设计模板

每个接口记录：方法/路径、目的、调用角色、资源归属、参数及验证、响应示例、HTTP 状态、错误码、分页排序、幂等性、事务、状态转换和测试点。

JSON 字段使用 camelCase；Java 实体不直接作为跨模块或浏览器契约。分页接口在实际列表阶段统一 page/size/排序及上限，不预设不需要的全站接口。重复反馈、收藏或交卷的唯一性策略随对应阶段设计确定。

## 基础异常、请求追踪与日志：已实现

不改变上面的状态 API 契约，状态接口 503 仍返回 SystemStatusResponse。其他业务异常采用统一结构：

```json
{"code":"INTERNAL_ERROR","message":"服务暂不可用，请稍后重试","traceId":"服务端生成的请求标识"}
```

后续业务字段校验失败可附 `fieldErrors`，字段名对应请求字段，不回显密码或敏感值。4xx 明确告诉用户如何修正；500 不把 SQL、连接串或堆栈放入响应。

阶段 0 已实现服务端生成的 X-Request-Id（不信任客户端输入），与错误 JSON 的 traceId 和日志对应。未知 checked/runtime 内部异常及显式5xx异常统一返回500和脱敏结构；框架非5xx状态保留。现有系统503/DOWN契约、Security拒绝与CSRF状态经测试保持。业务字段错误、401/403等完整业务响应在阶段1及后续用例中实现。

日志记录请求标识、方法/路径、HTTP 状态、耗时及必要错误类别。服务端内部保留排错信息，客户端看到可恢复提示；不记录密码、Cookie、Token、请求/响应全文或原始连接配置。查询数据库失败可以记录类别，不把每次状态探测都当成新的高严重度事件。

配置以本地示例、local/prod profile 和忽略提交的真实配置区分。补充规范无需新的日志平台或框架，复用 Spring Boot 日志能力。修改契约时同步前端类型、OpenAPI、相关测试及此文；不添加包裹层改变已有成功响应。

## 阶段1：身份与账户（2026-10-15）

具体输入约束及安全边界见 [身份方案](superpowers/specs/2026-10-14-phase-1-identity.md)。UserDto 为 `{id:string,username:string,displayName:string,roles:(USER|ADMIN)[],email:string|null,emailVerified:boolean}`，邮箱仅返回当前账号本人，不返回hash、securityVersion或数据库实体。

| 方法与完整路径 | 请求 | 成功响应 | 权限 |
| --- | --- | --- | --- |
| GET /api/v1/auth/csrf | 无 | 200 `{token,headerName}` | 公开 |
| POST /api/v1/auth/register | username/password/displayName?/email/challengeId/code | 201 UserDto，不自动登录 | 公开+CSRF，仅创建USER |
| POST /api/v1/auth/login | username/password/rememberMe? | 200 AuthTokens | 公开+CSRF+可信来源，默认普通登录 |
| GET /api/v1/auth/me | 无 | 200 UserDto | 登录 |
| POST /api/v1/auth/logout | 无 | 204 | 刷新Cookie或有效Bearer；无凭证幂等，CSRF+可信来源 |
| PATCH /api/v1/account/profile | displayName | 200 UserDto | 本人+CSRF |
| POST /api/v1/account/password | currentPassword/newPassword | 204并注销 | 本人+CSRF |
| GET /api/v1/admin/identity | 无 | 200 UserDto | ADMIN |

所有身份响应不缓存。前端每次写操作先获取CSRF，使用返回的masked token和X-CSRF-TOKEN header；不把Cookie或身份存入localStorage。登录和退出清除旧CSRF，下一次写入重新获取；Cookie由浏览器管理。认证检测到禁用、版本过时、闲置或绝对超时返回401；idle过期后的匿名写请求可能先因缺少CSRF返回403，因此403不能直接等同于退出。

用户名规范化小写且数据库唯一，重复注册409；注册拒绝整个admin前缀（去首尾空白、不区分大小写）及root/system，返回400，管理员创建和登录不受此前缀限制；角色、userId等未知字段400。账户更新只取服务端principal id。昵称与改密在事务中执行，改密使用security_version条件更新，竞争更新409；旧凭据401，修改成功所有旧会话在下一请求失效。

业务错误：`{code,message,traceId,fieldErrors:{字段:修正提示}}`；fieldErrors仅业务校验等响应存在，可能为空。400 VALIDATION_ERROR/BAD_REQUEST；401 BAD_CREDENTIALS/UNAUTHENTICATED；403 CSRF_INVALID/FORBIDDEN；409 USERNAME_TAKEN/ACCOUNT_CHANGED；500 INTERNAL_ERROR。不返回输入原值。数据库不可用不降级放行。

管理员无公开创建/提权接口，通过本地一次性bootstrap创建；重复用户名不能覆盖、重设密码或提权。文章/题库管理接口尚未实现。

## 阶段1补充：邮箱验证与密码找回

保留用户名登录，认证已迁移JWT + Redis，邮箱不可直接登录。旧账号email为空，仍可登录并在账户设置完成首次验证绑定；本阶段不提供换绑已验证邮箱。

| 方法与完整路径 | 请求 | 成功响应 | 权限 |
| --- | --- | --- | --- |
| POST /api/v1/auth/email-codes | purpose:REGISTER或RESET，email，RESET额外username | 202 `{requestState:"ACCEPTED",challengeId,expiresIn:300,resendAfter:60}` | 公开+CSRF |
| POST /api/v1/auth/password-reset | username/email/challengeId/code/newPassword | 204，不自动登录；全部旧会话下次请求失效 | 公开+CSRF |
| POST /api/v1/account/email-codes | email/currentPassword | 202同上 | 登录本人+CSRF+当前密码 |
| GET /api/v1/account/email-codes/{challengeId} | 无 | 200 `{state:"QUEUED"或"SMTP_ACCEPTED"或"FAILED"或"EXPIRED"或"NOT_SENT"}` | 仅本人BIND证明+当前安全版本；no-store |
| POST /api/v1/account/email | email/currentPassword/challengeId/code | 200 UserDto | 登录本人+CSRF+当前密码 |

邮箱去首尾空白并统一小写；首版接受ASCII邮箱，最多254字符、local部分64字符，校验域名及禁止头注入。数据库唯一。验证码6位数字，5分钟有效，5次错误失效；重新发送禁用同用途、同邮箱旧码。用途、邮箱、用户及安全版本全部在服务端绑定，challengeId为随机UUID，不能凭challengeId取得验证码。

发送限制为同邮箱跨用途60秒1次、滚动小时5次、滚动24小时10次，IP每小时20次；登录按用户名+IP每15分钟25次、IP每15分钟100次，包含成功尝试，无永久锁号。绑定的当前密码验证也受登录尝试预算约束。不信任客户端转发IP头。限流持久化并原子计数，429 `RATE_LIMITED` 带 `Retry-After`（秒）。未知/不匹配/未验证/禁用账号的找回发送与真实请求返回同样202结构、有效期及冷却，不投递邮件；邮件异步发送，不同步探测收件箱是否存在。

无SMTP或HMAC配置时发送返回503 `EMAIL_UNAVAILABLE`，不会返回验证码或伪造发送成功。异步接受不保证最终投递，SMTP失败禁用本次证明。验证码错误、过期、用途或用户不匹配、重复使用均为400、code字段提示。新增409 `EMAIL_TAKEN`、`EMAIL_ALREADY_BOUND`。CSRF与未知字段拒绝规则保持。验证尝试事务先结束，再在独立账号事务中锁定、复验、消费证明和写入账号，避免连接池嵌套等待；任何账号写入失败均回滚消费，错误尝试计数不会被业务回滚抹掉。

## 阶段2：文章与本人阅读进度（任务1—3已实现）

管理接口均需ADMIN：GET /api/v1/admin/articles/categories；GET/POST /api/v1/admin/articles；GET /{id}；PUT /{id}/draft；GET /{id}/preview?versionId；POST /{id}/publish、/{id}/unpublish。发布/保存/下架带expectedLockVersion，冲突409 ARTICLE_VERSION_CONFLICT。新草稿201，其余200。文章版本不可变，来源元数据随版本保存。

学习接口需登录：GET /api/v1/reading/categories、/articles（categoryId/keyword/page/pageSize）；POST /articles/{id}/start（首次201，重复200）；GET /articles/{id}/versions/{versionId}。下架新start409 ARTICLE_UNAVAILABLE；旧版/下架只有本人已有进度可读，无权与不存在均404。GET不授予历史权限。

PUT /api/v1/reading/progress/{versionId}：{sentenceKey,offset,completed,expectedLockVersion}，offset为当前不可变句子的UTF-16位置，不能切断代理对。仅本人已开始记录可保存；条件更新冲突409 READING_PROGRESS_CONFLICT，未授权404 READING_NOT_FOUND。完成只能显式标记，后续位置更新不撤销完成。GET /api/v1/reading/history?page=1&pageSize=20返回PageDto<ReadingHistoryDto>，摘要来自本人原阅读版本而非最新发布版本，按updated_at DESC,id DESC分页。写接口均保留Bearer+CSRF；响应no-store，ID字符串。

阶段2任务4—5（已交付）：GET /api/v1/dictionary/lookup?word=studies需登录，单个完整英文词≤64字符；精确词条优先，仅显式form映射，不猜去s/ed。200 {found,candidates}，未命中found=false，故障503 DICTIONARY_UNAVAILABLE。候选含entryId/lemma/phonetic?/senses/sourceNote/collection；collection仅当前用户实时组装，不进公共缓存。缓存最多512词、命中24小时/未命中5分钟，数据摘要变化清缓存。

POST /api/v1/vocabulary/words：{articleVersionId,sentenceKey,startOffset,endOffset,entryId}，首次201，重复或恢复200 {word,created}。服务端根据本人progress恢复不可变原句，校验UTF-16完整词边界和合法词典候选；拒绝客户端userId/原句/释义。GET /words?page&pageSize返回本人激活收藏；GET /words/{id}?page&pageSize返回{word,sources:PageDto}；DELETE /words/{id}软取消204（需CSRF），不删来源。无权词条404 WORD_NOT_FOUND；下架后本人已有历史仍能收藏。并发收藏/取消按本人锁排序，锁后使用当前读，避免旧事务快照重复插入。原记录和来源ID恢复时复用。

阶段2任务6：仅ADMIN的POST /api/v1/admin/articles/import/validate接受{schemaVersion:1,articles:DraftInput[]}，返回{valid,fieldErrors,itemCount}且不写入；POST /import/confirm?clientImportId=UUID再次校验并在同一事务创建批次/全部草稿，201 {batchId,articleIds}。最多20篇、请求体完整UTF-8最多1MiB，含分块请求；超限413 IMPORT_TOO_LARGE，未知JSON字段/语法错误400。校验字段路径如articles[2].title，上限100项。相同clientImportId+管理员+规范化全payload摘要重试返回同一结果；不同作者或内容409 IMPORT_CONFLICT且不泄漏已有ID。唯一冲突后在新事务读取并发结果，不在失败事务中继续。导入不发布、不抓取外部URL。

阶段2任务7：GET /api/v1/reading/recommendation（需登录）返回{date,article}，date为上海自然日，article为空时null。按已发布文章ID升序，用floorMod(上海日期epochDay,数量)选择；同日同集合稳定，下架立即排除。推荐查询不授予历史阅读权限，开始阅读仍独立校验。前端首页故障回退列表，不伪造今日任务/复习/打卡。
# 2026-10-17 查词增量：近义词与短语（当前）

补充（音标/选中短语）：candidate.phonetic在原字段为空时由已有本地ECDICT补充，新增可空phoneticSourceNote说明来源，原音标优先，不写数据库。公开GET `/api/v1/dictionary/phrase?text=in%20advance`返回200 `{found,phrase:{text,translationZh}|null,sourceNote}`，no-store；读取已有23,591条去重表达，需本地ECDICT开关。大小写、多空白及弯引号规范化后精确匹配2—6词/最多100字符，未匹配或超出短语形状返回found=false；空白或超过2000字符400 text字段错误。带身份时校验当前身份，匿名可读，无私人数据或写入。前端优先准备的完整句段译文，不把任意文本当词条ID或调用外部翻译。

现有公开GET `/api/v1/dictionary/lookup?word=beginning`的candidate新增只读字段：`synonyms:[{partOfSpeech,meaningEn,words:string[]}]`、`phrases:[{text,translationZh}]`、`lexicalSourceNote:string`。无资料返回空数组；原entryId/lemma/senses/sourceNote/collection与错误契约保持，前端兼容无新增字段的旧响应。游客collection仍null，私人状态不进入公共数据。

synonyms来自随包WordNet3.0，按实际候选词头与已有释义词性筛选，最多6组，每组最多6词，英文meaningEn用于说明词义，不能视为与全部中文释义一一对应或任意语境可互换。phrases来自固定ECDICT原数据，每候选最多5条，完整单词匹配；仅已有`english-hero.dictionary.ecdict.enabled=true`时提供，否则[]。查询不写词汇/来源/进度。无新增接口、外部请求或数据库迁移；来源与许可见`content/dictionary/lexical-extras/NOTICE.md`。

# 2026-10-18 写作积累阶段A（当前增量）

素材绑定articleId及articleVersionId，沿用文章版本访问。字段content={expression(300),meaningZh(600),keyPhrases(1—8项/每项120),structure(1000),exampleEn(2000),exampleZh(2000),scenarios(1—8项/每项120),sortOrder(整数0—9999)}，均纯文本。每版本最多50条非归档素材。

- GET `/api/v1/reading/articles/{article}/versions/{version}/writing-materials`：公开可访问版本已发布素材数组[{id,articleId,articleVersionId,content,revision}]，按发布快照sortOrder/id排序；不返回草稿/锁/管理员信息。旧版或下架文章仅本人已有阅读历史可读，其他404。
- GET/POST `/api/v1/admin/articles/{article}/versions/{version}/writing-materials`：ADMIN列表/创建，创建201，字段直接放body，不包content。返回AdminWriting{id,articleId,articleVersionId,draft,published,status,revision,lockVersion}。published可null，status为DRAFT/PUBLISHED/UNLISTED。
- PUT `/api/v1/admin/writing-materials/{id}`：相同内容字段加expectedLockVersion，仅保存草稿；已发布快照不变。
- POST 同路径`/publish`、`/unpublish`：{expectedLockVersion}，200；发布将校验过的完整草稿原子复制为发布快照并增加revision；下架保留内容。
- DELETE 同路径：{expectedLockVersion}，204软归档，不物理删除。归档后管理和公开列表不展示，后续变更404。

所有管理写操作保留Bearer+CSRF、服务层ADMIN检查及行锁/锁版本校验；403无管理权限，404不存在/版本不匹配，409 WRITING_CONFLICT保留本地输入并提示重新加载；字段错误400/fieldErrors。不接受客户端userId。所有响应no-store、ID字符串。阶段A不提供个人素材收藏或仿写接口，避免把原型演示当生产保存。


# 2026-10-18 写作积累阶段B

以下个人接口均要求认证，从 SessionPrincipal 获取用户，响应 no-store；写操作复用 Bearer、CSRF 与身份有效性校验，不接受 userId。公开素材列表仍只包含公开内容。

- GET `/api/v1/reading/articles/{article}/versions/{version}/writing-materials/states`：本人该版本已收藏素材ID到true的映射，版本权限沿用阅读接口。
- GET `/api/v1/writing-materials/{id}/personal`：Personal={materialId,articleId,articleVersionId,expression,available,favorite,material,imitation}。material 为当前公开 PublishedMaterial 或 null；imitation={text,materialRevision,snapshot,lockVersion} 或 null。下架/不可访问仅本人已有有效收藏或仿写可查看，其他404。
- PUT `/api/v1/writing-materials/{id}/favorite`：{materialRevision}；返回Personal；重复收藏幂等。DELETE 同路径，无body，204软取消，已有收藏下架后仍可取消。
- PUT `/api/v1/writing-materials/{id}/imitation`：{text,materialRevision,expectedLockVersion}；text去首尾空白，1—2000字符且包含英文字母。首次expectedLockVersion=0、materialRevision=当前素材修订；修改使用本人原仿写修订/锁版本。返回Personal，保存成功锁版本递增。
- GET `/api/v1/account/writing-materials?view=favorites|imitations&page=1&pageSize=12`：本人分页 PageDto，pageSize 最大50，按个人更新时间倒序；两个视图不混入个人生词。

400字段错误；401未认证；404不存在/不可访问；409 WRITING_CHANGED=素材依据变化，IMITATION_CONFLICT=仿写并发变化，WRITING_UNAVAILABLE=素材已下架或不可访问。错误保留输入，重新加载最新记录不会覆盖草稿。素材更新后修改既有仿写仍保留最初参考快照；下架后既有仿写只读，拒绝新增收藏/仿写和修改。
# 2026-10-18 阅读扩展C：题组与临时体验

题组绑定现有articleId/articleVersionId，每版本一个；1—10道四选一题。管理保存草稿与公开发布分离，所有API no-store，ID字符串，错误沿用结构化fieldErrors。题目均纯文本，不接受HTML渲染。

- GET `/api/v1/admin/articles/{a}/versions/{v}/quiz`：ADMIN当前题组或JSON null。AdminQuiz={id,articleId,articleVersionId,draft:QuestionInput[],published:PublicQuiz|null,status,revision,lockVersion}。status DRAFT/PUBLISHED/UNLISTED。
- PUT 同路径：{questions,expectedLockVersion}；首次期望0、返回锁1；编辑传最新锁。QuestionInput={key,type,stem,options:string[4],correctOption,explanation,sortOrder}。key为1—64位字母/数字/下划线/连字符且组内唯一，type为MAIN_IDEA/DETAIL/INFERENCE/VOCABULARY/ATTITUDE；题干最多2000、每选项1000且不得重复、解析4000字符；正确选项A—D，顺序整数0—9999。保存与发布均校验。
- POST `/api/v1/admin/reading/quizzes/{id}/publish`、`/unpublish`：{expectedLockVersion}，返回AdminQuiz；发布新增不可变题组版本/题目并更新公开指针，绝不改写旧题；下架保留历史。所有管理写入ADMIN+CSRF+当前锁。
- GET `/api/v1/reading/articles/{a}/versions/{v}/quiz`：PublicQuiz或JSON null（可访问文章未配题/已下架题组）。PublicQuiz={id,versionId,articleId,articleVersionId,questions:[{id,key,type,stem,options}]}；**不返回correctOption/explanation/draft/管理锁**。文章旧版本与下架访问沿用本人阅读历史规则，其他404。
- POST 同公开路径`/guest-sessions`：{quizVersionId}，201 {token,expiresIn:600}。校验当前可访问且仍发布同题组版本，版本变化409 QUIZ_CHANGED。使用现有CSRF，已登录旧版阅读可携带现有Bearer；仍仅为临时体验，不写个人成绩。
- POST `/api/v1/reading/quiz-guest/submit`：{token,questionId,choice}。200 Feedback={questionId,choice,correct,correctOption,explanation,completed,correctCount,total}；**仅返回刚提交题目的答案/解析**。逐题有序，同选项重试不重复计入，提交后改选/越题409 QUIZ_SUBMISSION_CONFLICT，不属本会话题目404。正确数仅完成全组时返回有效总分；中途correctCount=0占位，不代表当前成绩。

临时凭证随机256bit，10分钟固定TTL，仅body与前端内存；服务端Redis键使用SHA256摘要。独立`${auth.namespace}:reading-guest:`前缀，不写学习表、日期或用户成绩；刷新首版不恢复体验。已开始的体验可在题组更新/下架后按原冻结版提交，新体验必须通过当前访问/发布检查。每remoteAddr开启10次/小时、提交120次/分钟，429 QUIZ_RATE_LIMIT；不信任任意X-Forwarded-For。反向代理部署须另行核对可信代理/IP策略，本地代理可能共享地址限额。

凭证无效/过期410 QUIZ_SESSION_EXPIRED，Redis故障503 QUIZ_SERVICE_UNAVAILABLE，草稿并发409 QUIZ_CONFLICT，非法字段400。前端失败保留当前选项、提供局部重试；体验过期显式重新开始，不冒充个人保存或打卡。正式账号轮次、草稿恢复、成绩与重做在阶段D实施，打卡在E实施。
# 2026-10-18 阅读扩展D：账号轮次、恢复与成绩

全部路径在`/api/v1/reading`下，要求认证；用户取SessionPrincipal，写操作CSRF，响应no-store。不接受客户端userId/正确答案/正确数/完成日期。公共题干接口和C游客体验继续保留，游客结果不会自动转成正式成绩。

- GET `/articles/{a}/versions/{v}/practice`：State={published:PublicQuiz|null,current:Attempt|null,first:Summary|null,latest:Summary|null}。current优先本人进行中，否则最近轮次；first/latest为该文章版本的首次/最近完成成绩。打开页面只读取，不创建轮次。文章版本访问复用已有阅读历史规则。
- POST `/quizzes/{id}/attempts`：{quizVersionId,clientRequestId:UUID}，200创建或恢复。每用户/题组至多一个IN_PROGRESS，已有进行中继续其冻结版；无进行中时需当前可访问且已发布的指定题组版本，否则409 QUIZ_CHANGED/QUIZ_UNAVAILABLE。同一用户的相同clientRequestId始终返回原轮次；恢复已有轮次的不同请求ID也记录映射，原轮次完成后重试不会创建新轮次。请求ID用于另一题组409。
- GET `/attempts/{id}`：本人Attempt，其他用户404，支持已下架资料的本人既有轮次。Attempt={id,quizId,quizVersionId,articleId,articleVersionId,attemptNo,status,correctCount,total,lockVersion,startedAt,completedAt,quiz:PublicQuiz,answers:SavedAnswer[]}。status IN_PROGRESS/COMPLETED，correctCount在完成前为null；所有题干始终无答案。
- PUT `/attempts/{id}/answers/{questionId}`：{choice:A|B|C|D|null,expectedLockVersion}，保存/清除未提交草稿。非空选择有界，题目必须属于该轮次冻结版；变化才增锁，过期锁409 ATTEMPT_CONFLICT。已提交题不能更改。
- POST 同路径`/submit`：同body但choice必填。服务端按冻结题目判分，须按未提交题目顺序提交；提交后不可改选。同答案重复提交即使锁版本已过期仍返回当前轮次，不重复计分；不同答案/越题409。最后一题与正确数/COMPLETED/服务端完成时间在同事务保存。
- GET `/articles/{a}/versions/{v}/practice/history?page=1&pageSize=6`：本人PageDto<Summary>，按轮次ID倒序，pageSize最大50。Summary为Attempt元信息（不含quiz/answers/lockVersion）；不返回他人的记录。

SavedAnswer={questionId,draftChoice?,submittedChoice?,correct?,correctOption?,explanation?,submittedAt?}；空项省略，**仅提交后提供该题correct/correctOption/explanation/submittedAt，未提交题不含这些字段**。未来题答案不会混入普通恢复响应。轮次按不可变题组版本保存，不随管理员后续改题重判。

草稿选项变化时保存，失败保留本地选择和局部重试。并发冲突加载最新记录保留尚未提交选择；若其他页面已提交，显示真实锁定结果并说明本地选择未提交。题组发布变化与轮次冲突分别重载正确资源。同账号重新验证保留选择，换账号清理，不写浏览器持久存储。重做明确确认，原成绩与首次记录保留。当前不完成打卡（E尚未实施）。

## 阅读扩展阶段E：每日打卡与统计（2026-10-18）

以下均认证本人、Cache-Control:no-store；无客户端用户ID/日期/分数或直接打卡写接口。

- GET `/api/v1/reading/checkins/week`：`today`、`weekStart`（ISO日期），`days:[{date,checked}]`（上海周一到周日7天）、`todayChecked`、`totalDays`、`qualifiedArticles`、`completedReadings`（读完文章版本数）、`completedAttempts`（完整练习轮数）。只读，不补打卡。
- GET `/api/v1/reading/articles/{articleId}/versions/{versionId}/checkin`：`readCompleted`、`practiceCompleted`、`hasQuiz`（当前已发布配套）、`articleQualified`（整篇文章一次性资格）、`qualifiedDate`（无则null）、`todayChecked`。沿用阅读版本访问规则及本人身份；错误局部重试，不影响正文。

已有PUT阅读进度、POST逐题提交响应保持不变；只有未完成→已完成的有效动作才在同事务内核对资格。两条件必须同一文章版本：本人COMPLETED阅读+完整正式轮次，不设正确率门槛。曾发布的冻结旧轮次仍可完成。游客不生成资格；仅打开/滚动/标记读完不等于全部完成。

按Clock服务端instant转Asia/Shanghai，最终条件达成时计日，不补前日。每天最多一条，同篇文章终身最多一次；同日第二篇也消耗文章资格，但不加一天。重做不改变已有资格日期或首个当日来源。上线前D历史GET/同答案重试不补卡；未获得E资格的文章可通过新一轮真实完成首次达成。首页不恢复今日计划/打卡大卡，详情状态和`/stats`展示真实统计。无题文章明确提示不具备两条件。
# 2026-10-21 专项选词填空扩展

沿用 `/api/v1/admin/practice` 与 `/api/v1/practice`，新增 CET4/CLOZE。既有 DETAILED 请求不要求新增字段。独立原型不作为 API 数据。

- 草稿/导入增加 `wordBank: string[]`，最多15个词，每词最多100字符；空串可存草稿。专项原文录入请求增加 `readingType`，省略为 DETAILED。创建后题型不可变。
- CLOZE `questions` 固定10项，题序26–35；`type="CLOZE"`、`stem="26"` 至 `"35"`、`options=[]`、稳定唯一key；`correctOption` 为 A–O 或草稿null，解析最多4000字符。原文对应位置用 `{{26}}` 至 `{{35}}`。
- 发布版本、公开详情和作答增加 `readingType`、`wordBank`；词库为冻结快照。公开详情和未交卷作答不包含正确答案/解析。
- 草稿答案 PUT 继续使用 `expectedVersion` 和 `answers:[{questionId,choice}]`。CLOZE接受A–O/null，DETAILED仍A–D/null；同词不能重复填入。移动必须一次提交清空旧空及填入新空，服务端先验证合并后的完整选择再写入；失败不部分保存。
- 发布检查原文10个合法且唯一标记、15个非空不重复词、10个唯一答案及逐空解析/校对和来源。`body`、`wordBank` 错误可直接定位；导入仅为草稿。
- 改原文/词库后重新校对全部空位；改某空答案/解析后重新校对该空与来源。旧仔细阅读校对哈希兼容。
- CLOZE `retry-mistakes` 返回完整冻结10空题组，便于保持原文和共享词库语境；DETAILED保留仅错题重做。



# 2026-10-21 四级长篇匹配扩展

沿用专项管理、目录和作答接口，新增 CET4/MATCH，创建后题型不可转换。DETAILED/CLOZE 请求保持兼容。

- 专项材料 `body` 以空行分段，每段以连续 `[A]`、`[B]` 等标记开头，1–26段且正文非空；漏标、重复、不连续及未空行分隔的第二个段首标记均阻止校对/发布。字母随文章版本冻结，不推测未标记原文。
- MATCH 固定10题，数组顺序显示36–45。`type="MATCH"`、完整陈述 `stem`（最多2000字符）、`options=[]`、`wordBank=[]`；`correctOption` 为原文实际存在的A–Z字母，未完成草稿可null。每题解析最多4000字符。同一段可以作为多题正确答案。
- 草稿 PUT `answers:[{questionId,choice,flagged?}]` 增加可选布尔 `flagged`。省略时保留已有标记，显式false撤销；仅改标记也计入乐观版本。MATCH choice 为冻结材料中实际存在的字母/null，允许重复使用。DETAILED仍A–D，CLOZE仍A–O且不能重复词。
- 服务端整批校验后原子保存，继续强制归属/CSRF/版本；完成后拒绝答案和标记编辑。交卷前只返回公共题干、草稿答案和标记，交卷后才含正确答案与解析；标记不影响判分。
- `retry-mistakes` 对MATCH与CLOZE均重练冻结完整10题，避免丢失语境；DETAILED保持仅错题重做。错题条目增加可选 `readingType` 便于前端准确说明重练范围。
- 管理端逐题/整组人工校对和独立来源确认沿用原接口，检查原文标记/答案引用/陈述/解析；JSON导入仍只创建草稿。原文变化撤销题目校对，发布不会改写既有轮次或成绩。


# 2026-10-21 四级整套听力

在既有专项管理与作答接口增加 CET4/LISTENING；内部兼容字段 `readingType=LISTENING`，学习目录使用 `section=listening`，不加入阅读题型筛选。省略 section 仍为 reading。

- ADMIN `POST /api/v1/admin/practice/audio-assets`：multipart字段file，完整MP3，默认80MiB；鉴权、CSRF、扩展名/媒体头和流式大小校验。返回id/originalName/byteSize/sha256/mediaType，不返回磁盘路径。头识别不能替代完整解码和人工预听。
- ADMIN `POST /api/v1/admin/practice/audio-assets/{id}/playback-ticket`；本人 `POST /api/v1/practice/attempts/{id}/audio-ticket`：返回url/expiresAt。管理员限本人上传资产，学员限本人轮次的冻结资产；Redis票据有效10分钟、仅限该资产，前端不持久保存。
- `GET/HEAD /api/v1/practice/audio/{id}?ticket=...`：媒体请求用票据验证，支持单Range/206和HEAD；越界/非法Range为416，不支持多Range。无票据/过期/跨资产拒绝，不开放静态文件目录。
- ADMIN `POST /api/v1/admin/practice/listening-sets`、`PUT /api/v1/admin/practice/sets/{id}/listening`：沿用MaterialInput的requestId/body/metadata/questions/expectedLockVersion，增加listening。通用材料接口亦支持LISTENING。配套原文只能为PRACTICE专用材料，禁止复用公开阅读文章；通用草稿和JSON导入同样校验。
- `listening={audioAssetId,groups:[{key,title,questionKeys}],audioReviewed}`。草稿最多50题，每题LISTENING/A–D四选一；发布至少1题，分组顺序恰好覆盖所有题目。客户端不能直接设置已校对；复用review接口增加kind=AUDIO，题目与来源仍独立确认。导入只建草稿，引用当前管理员已上传的资产，不接路径或远程URL。
- 换音频/分组撤销相关校对；发布版冻结音频引用、分组、配套原文和题目。缺音频/预听确认、题干/选项/答案/解析、重复选项、来源或校对时禁止发布。
- 作答DTO新增listening/playback；草稿PUT可选 `playback={positionSeconds,rate}`，有限位置0–21600秒、倍速0.5–2。省略保留旧值，与答案/标记/当前题共用expectedVersion事务。媒体更新约5秒合并；显式退出/交卷前暂停并保存，刷新恢复暂停。
- LISTENING公开详情和IN_PROGRESS轮次均不返回配套原文、正确答案或解析；交卷后才返回。重练完整冻结套组；下架阻止新开始，保留已有本人轮次。阅读题量和既有重做规则不变。

验收和未验证边界见 `acceptance/2026-10-21-practice-listening.md`。技术可用不表示已导入真实听力。


# 2026-10-22 写作与翻译主观题

> 后续整卷接口见文末2026-10-23章节；主观单项接口及其判分边界保持。

新增四级WRITING/TRANSLATION，目录`section=writing|translation`，省略section仍为阅读（兼容`all`用于跨栏目查询）。公开SetDetail增加`subjective={prompt,requirements,minWords,maxWords,minutes}`，不含reference/notes；公开材料不含参考。AdminSet/PublishedVersion仅主观题增加完整subjective，旧客观响应不增加null字段。

- ADMIN `POST /api/v1/admin/practice/subjective-sets`，`PUT /api/v1/admin/practice/sets/{id}/subjective`：`{readingType,metadata,subjective,expectedLockVersion}`，subjective增加reference/notes。元数据同原题组，翻译minWords/maxWords归一为空。仅创建或修改草稿，不自动发布。`GET`、版本、publish/unpublish沿用通用接口；review增加`kind=CONTENT`，另`SOURCE`确认。内容/参考/材料改动撤销相关确认，无绕过校对一键发布。
- `POST /api/v1/practice/sets/{id}/subjective-attempts`：`{setVersionId,requestId}`，仅已发布主观题；返回已有进行中稿或新建。客观attempts入口拒绝主观类型。
- `GET /api/v1/practice/subjective-attempts/{id}`：仅本人，返回`id,setId,setVersionId,parentAttemptId?,title,readingType,prompt,answerText,wordCount,status,version,startedAt,updatedAt,completedAt`；只有COMPLETED才包含reference/notes。不返回score/correctCount/quizVersion。
- `PUT .../{id}/draft`：`{expectedVersion,answerText}`，空文允许，最多20000 UTF-16字符，保留换行。服务器与客户端同英文词正则`[A-Za-z]+(?:['’‐‑-][A-Za-z]+)*`。
- `POST .../{id}/submit`：`{expectedVersion,requestId,confirmWordRange}`，从已保存稿提交；无英文词400，超建议词数且未确认400/PRACTICE_WORD_RANGE_CONFIRMATION_REQUIRED；重复同命令幂等，版本/内容冲突409/PRACTICE_CONFLICT。完成后禁止原地保存。
- `POST .../{id}/revise`：`{requestId}`，仅本人已完成稿，新建同冻结版本的预填稿，保留旧提交。若已有不同来源进行中稿则409，防止覆盖。
- `GET /api/v1/practice/subjective-history`：个人分页，支持同目录考试/来源/题型/年月/主题/搜索/state/section过滤，按开始时间倒序。返回状态/词数/时间，无参考和分数。

所有私人/后台接口保持鉴权、CSRF、归属和no-store；跨用户轮次404。下架阻止新开始，已有本人稿及修订使用原冻结版本。主观表和命令不复用客观attempt外键，不记阅读打卡。

## 2026-10-25 整卷错题归集与复习

接口以下均带 `/api/v1`；须真实登录，写请求保留 CSRF，本人资源校验沿用现有规则。

- GET `/practice/paper-mistakes`：`exam`（默认 CET4）、`year`、`month`、`readingType`（LISTENING/CLOZE/MATCH/DETAILED）、`status`（默认 PENDING，可选 RESOLVED）、`sourceAttemptId`、`page`、`pageSize`（1–50）。返回 `{items,page,pageSize,total}`；条目包含 `questionId,setId,setVersionId,sourceAttemptId,slot,originalNumber,paperTitle,readingType,status,wrongCount,stem,submittedChoice,correctOption,explanation`。首次错误原卷来源保留；指定本人原卷时，按该次冻结错题身份筛选，重复出现在其他卷中的同版题仍能查到。他人来源404，未完成来源400。
- POST `/practice/paper-attempts/{id}/retry-mistakes`：`{requestId,slot}`。仅本人已完成原卷的五个客观槽位；服务端选择冻结组件版本和待订正题，客户端不能指定题号/版本。slot缺失、null、空白、未知或主观槽位返回400 `VALIDATION_ERROR`，包含 `fieldErrors.slot`，不创建重练/幂等记录；同requestId修正后仍可申请（2026-10-25补修）。听力25题、选词/匹配各10题完整重练，仔细阅读仅待订正子集。无待订正题409 `PRACTICE_NO_MISTAKES`。同题组已有整卷来源且同版本的活动重练可恢复；单项来源或其他版本冲突409 `PRACTICE_REVIEW_ACTIVE`，`fieldErrors.activeAttemptId` 可继续已有本人轮次，草稿不覆盖。
- 返回既有 Attempt DTO，整卷来源轮次额外包含 `reviewSource:{attemptId,slot,originalNumbers:{questionId:number}}`，编号对应原卷1–55。未交卷时仍无参考答案和解析；保存、交卷、音频沿用既有单项端点与本人鉴权。整卷来源重练须走本端点，旧单项 retry 路由不接受它。
- 整卷交卷与独立错题台账同事务，错/空归集，答对订正已有记录；幂等重放不累加。只有主动重练才创建一份有来源的 MISTAKES 轮次。重练不更新单项错题表，不改原卷作答/正确数。查看或保存草稿不订正；全组重练产生的新错题可继续按该冻结组件复习。写作、翻译仍不判对错。
- V27 回填已有已完成整卷，按完成时间、轮次ID及组件顺序确定历史最新状态；V28收紧来源槽非空约束。归集不制造单项FULL成绩。原 V26 说明保留历史边界，独立整卷错题以本节为准。验收见 `acceptance/2026-10-25-practice-paper-mistakes.md`。

## 2026-10-23 四级整卷（正式）

- ADMIN `/api/v1/admin/practice/papers` GET列表/POST创建，`/{id}` GET/PUT草稿，`/{id}/preview` GET受保护组成及参考，`/{id}/review|publish|unpublish` POST `{expectedLockVersion}`，`/{id}/versions` GET冻结版本。`/components?exam=CET4&year=2024&month=6` GET已发布候选。AdminInput=`{expectedLockVersion?,title,examCode,year,month,sourceNote,components:[{slot,setVersionId}]}`。修改草稿撤销组合确认；发布校验7槽、同考试年月、题型与25/10/10/5/5题数、组件可用性。
- 公开 GET `/api/v1/practice/papers`，GET `/papers/{id}`；query exam=CET4|CET6、year/month、page默认1/pageSize默认20最大50。目录含items/total/page/pageSize和实际years/months计数，PaperView包含可用状态及原因，登录本人可获currentAttemptId；草稿不公开。
- 本人 POST `/practice/papers/{id}/attempts` `{paperVersionId,requestId}`，开始或恢复已有未完成轮次；GET `/practice/paper-attempts/{id}`；PUT其`/draft` `{expectedLockVersion,currentSlot,currentQuestionId,responses}`；POST其`/submit`加`requestId,confirmUnanswered:true`，最终完整快照与判分在一个事务完成。GET `/practice/paper-history`本人分页；POST `/practice/paper-attempts/{id}/audio-ticket`只授权本人冻结听力资产，票据仍10分钟。
- responses必须完整七槽，各项`{slot,text,choices}`，slot=`WRITING,LISTENING,CLOZE,MATCH,DETAILED_1,DETAILED_2,TRANSLATION`。主观text最多20000字符、choices空；客观text空、choices按冻结题ID和题型验证，可缺答，不接受外来题ID；选词字母不可重复，匹配可重复实际存在的段落。返回Attempt含components安全内容及responses，参考/解析/听力原文只在COMPLETED解锁（ADMIN预览除外）。
- 身份/CSRF/资源归属沿用现有规范；他人轮次404，旧锁/命令冲突409，非法输入400，下架或组件不可用409；相同请求与载荷重试返回同结果。缺答交卷需显式确认，不算四级标准总分，主观不判对错，不插入单项轮次/错题/阅读打卡。已有轮次可继续旧版，下架只阻止新开始。


## 2026-10-23 听力定位附加信息

既有单项/整卷/管理员音频播放票据除url、expiresAt外附加sha256（对应已授权不可变音频资产的内容指纹）。无新增公开资源读取权限或票据用途。客户端仅在指纹与题组题号均匹配已核对映射时显示片段播放器；不将题干、原文或答案提前解锁。


## 2026-10-23 整卷目录查询提速

GET `/api/v1/practice/papers` 响应契约不变：从已发布冻结元数据筛选和分页，组成题目计数/状态及本人进行中轮次按当前页批量读取，匿名不读私人轮次。仍实时反映组成题组下架，详情和开始操作保持原权限与冻结版本校验；不引入跨用户缓存或提前返回答案。

## 写作与翻译AI学习反馈（2026-10-26）

所有路径以 `/api/v1` 为前缀，已实现；不是官方评分或原卷分数接口。本人鉴权/CSRF沿用现有业务接口，返回no-store。功能默认关闭，未配置时申请503 PRACTICE_AI_UNAVAILABLE；历史读取不依赖上游可用性。

- POST `/practice/subjective-reviews`：`{requestId:UUID,sourceType:SUBJECTIVE|PAPER,attemptId,slot:WRITING|TRANSLATION,consentVersion:"2026-10-26"}`，返回202 ReviewDto。本人已完成轮次、有效槽位、非空英文作答；其他来源404，未完成/题型/说明版本错误400。服务端读冻结题目/作答，不接受客户端原文、用户ID或任意上游URL。
- 同用户UUID同命令返回已有任务，异命令409 PRACTICE_REVIEW_CONFLICT。同来源/响应哈希的活动或成功任务复用；其他活动任务409 PRACTICE_REVIEW_ACTIVE。次数/尝试/冷却/总预算耗尽429，分别为PRACTICE_REVIEW_LIMIT、PRACTICE_REVIEW_COOLDOWN、PRACTICE_AI_BUDGET。重复请求不重复预留预算或发出请求。明确未外呼的QUEUE_EXPIRED不消耗外呼尝试数（审查补修）。
- GET `/practice/subjective-reviews/{id}`：本人任务ReviewDto，他人/不存在404 PRACTICE_REVIEW_NOT_FOUND。
- GET `/practice/subjective-reviews?sourceType=...&attemptId=...&slot=...`：本人该冻结来源最近20条，倒序。单项修改稿与父轮次分开读取，不跨轮评价。
- ReviewDto：`id,sourceType,attemptId,slot,status,errorCode,createdAt,completedAt,model,promptVersion,rubricVersion,submittedText,result`；状态QUEUED/RUNNING/SUCCEEDED/FAILED。submittedText是当次冻结原文；result仅成功返回。敏感凭证、上游原始body/异常/思考内容不返回。
- result：`score`整数0–15、`summaryZh/summaryEn`、三个`dimensions[{name,level:GOOD|FAIR|IMPROVE}]`、`annotations[{kind,severity:ERROR|SUGGESTION,quote,occurrence,replacement,reasonZh,reasonEn,sourceQuote?}]`、`omissions[{sourceQuote,reasonZh,reasonEn,suggestion}]`、`revisedText`。引用必须真实存在，occurrence从1计数。空输出、截断、越界分数/引用失败不展示评分，标记INVALID_OUTPUT。
- 任务错误类别还包括QUEUE_EXPIRED、OUTCOME_UNKNOWN、PROVIDER_AUTH、PROVIDER_BALANCE、PROVIDER_LIMIT、PROVIDER_UNAVAILABLE、UNAVAILABLE。外呼与DB事务分离，异常不自动再次调用；未知结果保留费用预留，主动重试可能再次计费。未发出的10分钟排队过期释放预留，180秒运行租约失效标为未知。
- 仅用于主动申请后的学习反馈；不替换原题参考、原卷判分/正确数/历史作答。模型默认Flash非思考，不支持联网或工具。真实AI质量和实际消费需另行小样本验证。
# AI 批改费用状态补充（2026-10-26）

本人批改记录 GET/列表及创建响应新增 `budgetStatus` 和 `budgetAmountMicrocny`，不改变既有鉴权、错误码和结果字段。状态为 RESERVED（处理中占用）、SETTLED（按可信用量保守估算结算）、RELEASED（本地预留释放）或 UNKNOWN（费用待确认，保留预留）。金额是人民币微元的本地额度占用，向上取整，不是 DeepSeek 实扣账单。尚无这些字段的旧前端夹具兼容显示。

反馈内容无效时仍返回FAILED、result=null；若官方成功响应含允许的Flash型号及范围合法的整数用量，则保留input/output tokens并结算，而非占住整笔预留。发送前的不可用/输入过长、官方400/401/402/422/429明确拒绝会释放本地预留；403及5xx、响应损坏/超时、缺失或不可信用量继续保留。失效租约及重复结算不改写账本。不自动重发、不重算旧未知记录、不清空历史费用。PRACTICE_AI_BUDGET仍为429，提示可用额度不足且可能存在预留，不再声称一定已实际花满。


## 2026-10-27 留言墙正式接口

统一 `/api/v1`，成功响应 no-store；沿用 JWT、CSRF 和项目错误格式。匿名可读公开 messages、详情和 comments；其余写操作、mine、requests、notifications 必须真实登录，admin/wall 还须服务端 ADMIN。ID 使用正整数字符串，cursor 为倒序ID，pageSize 1–50。

- `GET /wall/messages?category&keyword&cursor&pageSize`、`GET /wall/messages/{id}`、`GET /wall/messages/{id}/comments`：仅公开可见内容；隐藏/删除返回404。
- `POST /wall/messages`：`{requestId,kind,category,color,text,title,preferredPosition:{x,y}}`。普通 MESSAGE 分类 STUDY/CHECKIN/ENCOURAGE；正文1–1000 Unicode字符，公告标题1–80。颜色 yellow/pink/blue/green/purple/orange。公告 ANNOUNCEMENT 仅ADMIN。
- `GET /wall/mine` 返回本人含隐藏的留言、状态和作者可见原因；不含管理内部备注。`DELETE /wall/messages/{id}`、`DELETE /wall/comments/{id}` 使用 `{requestId,expectedVersion}`，仅作者。
- `GET /wall/requests/{UUID}` 只查本人幂等结果；同命令UUID同载荷重放，异载荷409；未知结果先查询再复用UUID重试。
- `PUT /wall/messages/{id}/like {liked}` 为期望状态。`POST /wall/messages/{id}/comments {requestId,text}` 正文1–500。`POST /wall/reports {requestId,targetType,targetId,reason,detail}`，targetType MESSAGE/COMMENT，reason SPAM/UNFRIENDLY/PRIVACY/OTHER，detail最多500；同用户同目标待处理举报复用，不自动隐藏。
- `GET /admin/wall/messages?status&flagged&cursor&pageSize`、`GET /admin/wall/messages/{id}`、`GET /admin/wall/comments?messageId`、`GET /admin/wall/reports` 提供管理上下文；`POST /admin/wall/messages`发布公告。
- `POST /admin/wall/messages/{id}/moderation` 及 comments 对应路径：`{requestId,expectedVersion,action,text,title,authorReason,internalNote,reportIds}`。action EDIT/HIDE/RESTORE/DELETE/KEEP；评论不支持EDIT。作者原因1–200、内部备注最多500，最多50个明确选中且属于目标的待处理举报。旧版本409、删除不可恢复。内容、举报处理、审计、通知同事务；KEEP不发通知。
- `GET /wall/notifications` 返回 items/nextCursor/hasMore/unreadCount/readBoundary；`PUT /wall/notifications/{id}/read`、`PUT /wall/notifications/read-all {throughId}` 仅本人。throughId为本轮列表边界，后到通知不被一并已读；canOpen仅目标当前公开时为true。

默认北京日界：留言10条/日且间隔30秒，评论60条/日且间隔5秒，举报10条/日。429包含Retry-After；同命令重放不重复扣额。公开DTO不暴露用户名、邮箱、举报人、内部备注。公告只支持受限 `**加粗**`，普通留言纯文本，不解析HTML。

## 2026-10-27 用户管理接口

统一 `/api/v1/admin/users`，真实ADMIN、服务端再次查角色/账号状态；写请求保留CSRF/来源检查，成功no-store。禁止返回密码、password_hash、验证码、Cookie或Token。

- `GET ?keyword&status&role&page&pageSize`：keyword最多100码点，ID精确匹配或账号/昵称字面包含（转义LIKE通配符）；status ALL/ENABLED/DISABLED；role ALL/USER/ADMIN，USER指不含ADMIN的学习者。page1起、最多1000000，pageSize默认20、1–50。返回items/page/pageSize/total。
- `GET /{id}`：详情；列表白名单id/username/displayName/roles/enabled/version/createdAt/protectedAccount，详情增加maskedEmail/emailVerified。version为既有账号安全版本，邮箱只显示遮罩。
- `PUT /{id}/status {requestId,expectedVersion,enabled,reason}`：标准UUID、整数版本、真正boolean、去首尾1–200 Unicode码点原因，拒绝非法UTF16/NUL。所有ADMIN和本人禁止修改。旧版本或状态无变化409；目标不存在404，越权403，未登录401。成功返回requestId/targetId/enabled/version/completed。
- `GET /requests/{requestId}`：仅当前管理员的已完成结果，未知404。同actor UUID同载荷重放，不同载荷409。未知网络结果应先查再复用UUID，不能换UUID盲重试。
- `GET /audit?userId&cursor&pageSize`：userId可选，cursor为倒序正整数ID，pageSize1–50；items/nextCursor/hasMore。记录id、actor/target的id与账号、beforeEnabled/afterEnabled、reason/createdAt。只读、仅管理员。

状态变更、安全版本+1、命令回执与审计同事务。停用后旧access/refresh与新登录不可用，恢复再递增版本并要求新登录；不删除学习或留言，不自动隐藏发言，不终止已受理AI任务。停用提交前已鉴权在途请求可能完成。无账号删除/角色修改/管理员代重置接口，自助邮箱重置保持。

## 2026-10-28 阅读选区实时翻译

与通用AI助手独立。前端点击优先查已有参考译文/本地短语，未命中且登录时才请求；选择或拖动不调用模型。单词继续原查词。

- 认证 `POST /api/v1/reading/selection-translations {requestId,articleId,versionId,text}`：标准UUID、正整数ID、文本1–1000码点，翻译至少包含一个英文字母片段（允许词内子串），语法解析仍至少两个英文词，必须来自本人可访问的该文章版本，空白标准化，跨句段以空格连接。
- 认证 `GET /api/v1/reading/selection-translations/{id}`：仅本人任务，否则404。
- 认证 `GET /api/v1/reading/selection-translations/requests/{uuid}`：本人创建请求回执，未知404。未知网络结果先查询，再复用相同UUID，不盲目生成新请求。

响应Task：id/text/status/translation/model/errorCode/budgetStatus/estimatedCostMicrocny；status QUEUED/RUNNING/SUCCEEDED/FAILED，budgetStatus RESERVED/SETTLED/UNKNOWN/RELEASED。成功no-store。相同用户/版本/标准化选文的成功或活动任务复用，新的UUID映射到原任务；不同选文复用同UUID409。新请求用户50次/北京日、间隔2秒、至多1条活动任务；缓存不重复计费。

复用practice_ai_daily_budget同一行锁与人民币日限额，每任务本地预留5000微美元，CNY估算向上折算约0.033334元，不等于供应商扣费；可信用量按现有Flash单价结算，明确未发送释放，未知保留。模型输入仅选中文本，输出JSON translation纯文本；max_tokens1024，输出体128KiB限制，40秒请求超时，不自动重试。单翻译worker并发、60秒lease、排队120秒过期释放、运行超时保留未知预留；排队时账号停用不再发出。

前端轮询最多60秒，关闭/换选区/换账号取消本页等待，不能据此当作服务器任务取消；后续通过回执查结果。失败后用户明确点击重试才允许新请求并提示费用。无密钥/账户资料发往模型。

### 2026-10-29 扩展：语法解析

上述POST增加可选mode：TRANSLATION（省略时默认，兼容已有缓存）或GRAMMAR；其他值400。同一UUID换模式409；不同UUID的缓存按用户、版本、文本和模式分别复用。现有50次日限、2秒间隔、本人读取和单活动任务对两种操作合并计算。

Task增加mode与analysis（翻译时null）。解析成功translation=null；analysis包含scopeNote、mainClause[{quote,role,explanation}]、clauses[{quote,role,explanation}]、clauseNote、grammarPoints[{quote,explanation}]。所有quote必须是选文精确子串，中文字段/条目数量/总长度服务端受控；不返回HTML。结构验证不能保证机器语法判断绝对准确。

解析仅显式点击“解析”发出，不查本地短语、不顺带请求翻译；最多1000码点且至少两个英文词，版本/权限规则同翻译。DeepSeek输出最多3072tokens，预留10000微美元（约0.066667元），与其他AI任务共用原人民币1元项目日限额；8192输入/3072输出已知用量成本最多6144微美元。翻译仍1024tokens/5000微美元。失败、未知结果与可信用量结算沿用原规则；缓存复用不重复调用。

### 2026-10-29 百度翻译替换

TRANSLATION改为百度通用文本翻译，model=baidu-general；新增sourceCharacters返回源文Unicode字符数，GRAMMAR为0。翻译缓存增加BAIDU前缀，与旧DeepSeek译文隔离。相同UUID旧请求只供本人历史查询；新的百度请求不回退到DeepSeek。所有权限/CSRF/来源/幂等/限流规则保持。

百度翻译不参与practice_ai_daily_budget，reserved/settled_microusd为0，estimatedCostMicrocny=0仅表示不使用该金额账本，不是供应商账单断言。budgetStatus在百度任务表示字符配额状态：RESERVED待完成、SETTLED成功保留、RELEASED明确未发送释放、UNKNOWN不确定保留。界面不展示百度“AI金额预留”。

项目共用reading_baidu_monthly_quota，默认每北京时间月100万源文字符，跨月排队转移到实际启动月后再调用；缓存/回执不再计数。并发按月行锁，超过返回429 BAIDU_CHARACTER_QUOTA。源文1–1000码点，HTTPS POST表单，40秒/128KiB界限，无自动重试。语法解析和主观AI批改继续原DeepSeek与每日金额规则。

### 四六级原文共用翻译

POST selection-translations 输入新增可选 practiceAttemptId 或 paperAttemptId+slot（不能混用）。slot仅LISTENING/CLOZE/MATCH/DETAILED_1/DETAILED_2；有练习上下文时mode只允许TRANSLATION。服务端通过本人练习/整卷快照取得原文，核对articleId/versionId/选文，不能用客户端文本代替原文证明。单项听力未交卷及整卷未交卷返回409 PRACTICE_TRANSLATION_LOCKED；他人轮次404，不匹配版本/槽位400。该授权在缓存及UUID重放前执行；上下文进入请求载荷hash，本人版本/选文译文缓存仍共用。

认证POST `/api/v1/reading/selection-translations/reference` 使用同样输入（无需requestId）返回 `{text,source}`，无精确对照时均为null。权限与原文证明同上，CSRF/no-store；只查已有整句/整段参考译文，不创建任务或扣字符。前端顺序为精确参考→离线短语→百度，选择本身不触发翻译。词典查词保留。

界面只在英文原文标记范围响应，选项/中文解析/输入控件不纳入；填空跨按钮选区不开放翻译。单项阅读可用、听力原文交卷后开放，整卷句段翻译交卷后开放。全部复用现有百度队列/月字符配额及本次字符数显示，不新增语法或AI批改调用。

2026-10-29选词补充：词内子串也保留显式“翻译”。前端点击后先查本地单词中文释义，缺失时沿用百度任务和本人版本文本缓存；不在拖选/释放时自动发百度请求。词内子串不建立完整词典收藏来源；正常完整单词查词入口保留。

### 2026-10-29 固定双语词条例句

GET /api/v1/dictionary/lookup 的 candidate 新增 examples:[{text,translationZh}]（最多2条，无数据为[]）、exampleSourceNote。按实际候选lemma读取随包原创固定资料，不依赖正在阅读的文章，不调用翻译/AI，不写数据库，不把公共例句混入用户collection。保持其他字段与完整词条、词形匹配/收藏归属。固定例句与前端当前文章原句分栏，待补充真实显示。首批116原创双语句对、395词头、456关联，非全词库覆盖；近义词/短语继续既有来源和显示规则。

## 2026-11-06 词汇中心主动查词

- GET `/api/v1/dictionary/search?q=app` 公开只读、no-store。q支持单词或2—10词短语，规范化空格及大小写，最多80字符，不接受通配符或句子标点。返回 `{items:[{entryId,lemma,partOfSpeech,definitionZh,kind,canonicalLemma,relatedEntryIds}]}`，最多10项。kind为WORD/FORM/PHRASE；FORM显示实际词形并关联原词条；PHRASE使用 `phrase:` 前缀标识，不能传给单词收藏接口。精确词头优先，单词/词形与短语混合展示；短语按完整词边界、常用示例及组成词频代理排序。结果仅取本地词典。
- POST `/api/v1/vocabulary/words/from-dictionary` 需登录及CSRF，输入 `{word,entryId}`。验证entryId属于该查询的本地词条，按本人收藏锁新建或恢复收藏，返回原CollectResult。来源DICTIONARY在V43增量允许，记录不依赖阅读版本或轮次，不自动加入学习。
- 学习feedback新增LOOKUP动作，仅v2复习默写的QUESTION阶段允许，沿用本人资源、expectedVersion、itemId、attemptId与requestId幂等检查。记assisted及一次失败证据，不泄露字母提示、不推进阶段。前端在建议、详情或最近记录即将显示当前词之前先保存该记录，失败时不展示结果，重试复用原命令。
- 查词弹窗的最近记录按账号保存于当前浏览器，最多8项。首版实现英文查词、详情、朗读、收藏和查词辅助，未收录显示空状态，不自动请求外部翻译。

词组扩充：复用 GET `/api/v1/dictionary/phrase?text=...` 读取离线中文释义；多词表达通过已有 POST `/api/v1/vocabulary/phrases` 收藏到词组本，重复收藏保留原个人释义/笔记和学习状态，不自动加入学习。最近词组在展示前刷新关联词条ID；建议、详情及历史涉及当前复习词（含大小写和已知词形）时先保存LOOKUP辅助。单词查询 `/lookup` 的原64字符完整单词规则保持。
