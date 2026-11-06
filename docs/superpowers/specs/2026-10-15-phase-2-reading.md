# 阶段2：文章阅读、查词与生词收藏设计

日期：2026-10-15（Asia/Shanghai）。状态：**用户已于2026-10-16确认设计；[逐任务实施计划](../plans/2026-10-16-phase-2-reading.md)已编写，待审阅，尚未开始功能实现或数据库迁移。** 用户已同意继续整理阶段2详细方案，并明确选择首批原创英语短文、偏四级/B1–B2。本文不代表阶段2功能已实现或验收通过。

## 1. 目标、现状与边界

后续调整：用户确认JWT + Redis迁移，已于2026-10-16完成自动验收及本地切换，见 [认证验收](../../acceptance/2026-10-15-jwt-redis-auth.md)。阅读实施依赖新的Bearer/CSRF/Redis family鉴权；本设计现已确认，实施计划待审阅；当前仍未授权跳过计划直接写阅读业务。

交付流程：管理员创建文章草稿 → 预览、发布 → 学习者浏览与阅读 → 查词 → 明确收藏 → 在个人生词本查看来源；刷新或再次登录可恢复阅读位置和收藏。管理员下架不删除历史。

依赖：阶段0工程与阶段1身份系统、JWT + Redis/CSRF/角色鉴权；当前数据库已到V4。首页布局已优化，阅读/词汇/练习仍为占位页，管理端已有独立布局和账户设置，尚无文章维护能力。没有现成文章、词典或收藏数据库模块。

保持Vue3/TypeScript/Pinia、Java21/Spring Boot、MyBatis-Plus、MySQL模块化单体，不换认证机制、包管理器或UI组件库。遵循docs/ui-guidelines.md及现有黑白灰、玫红#c0324f与浅粉视觉。原始原型不改，演示数据不代替业务持久化。

本阶段不实现背词复习算法、错词本、考试、学习时长统计、AI解释、任意网页抓取、整句在线翻译或复杂富文本编辑器。不接入付费服务、新的阅读Redis缓存、Python或音频系统；继续使用现有认证Redis。

## 2. 选择与内容准备

| 方案 | 取舍 |
| --- | --- |
| 原创短文 + 本地词典 + 手工校对词形（推荐） | 可离线运行、内容和释义来源可说明，先完成闭环；本地词典覆盖范围需明确 |
| 同时接在线词典/翻译 | 覆盖更广，但引入凭据、网络、配额和外部服务依赖，当前不作为验收前置 |
| 直接导入媒体全文并做AI分析 | 超出本阶段和用户选定内容来源，不采用 |

按用户选择准备至少10篇原创英文短文，目标每篇350—650词，主题为学习、生活、科技、文化、职场五类。B1/B2与四级相关性为编辑标注，不宣称官方评级或真题。

每篇具备中英标题、简介、主题、编辑难度、英文正文、作者/编辑署名、来源类型、使用说明及重点词清单。明确标注「Lingua Loop原创练习」，不伪称BBC等媒体文章；原创内容允许无外部来源URL。第三方内容日后录入时必须提供真实来源和使用依据。

首批本地词典至少300个经校对的基本词形，并包含文中重点词及对应屈折形式；它是明确范围的初始词典，不宣称任意词均可查。中文释义、词性、必要音标及形式映射随项目数据文件交付，版本/编辑来源有说明。未命中有独立状态，不编造释义、不硬套去s/ed规则。

10篇文章以带schemaVersion的数据文件交付，经管理端校验导入为草稿，再预览发布；不借Flyway自动发布内容或伪造阅读记录。文本和释义的实际编写/校对在实现阶段完成，本设计不把尚未存在的数据算作交付。

## 3. 学习端交互

### 3.1 列表 `/reading`

沿用胶囊导航和页面宽度。展示分类、标题关键字搜索、分页及今日推荐；文章卡片包含标题、简介、难度、词数、预计阅读时间和真实来源。词数/预计时间由正文计算，预计时间明确标为估算，不是已学习时长。

只返回已发布文章；加载、无内容、无匹配、失败和重试分别展示。搜索仅检索标题，关键字最长80字符，页大小默认20、上限50。词数按统一英文分词规则计算，预计分钟为max(1,ceil(wordCount/200))，规则与数据版本记录在实现中。用户阅读状态来自本人记录，未开始不得显示虚假完成进度。

今日推荐按服务端上海日期和可用文章集合确定性选择；同日集合未变时结果稳定，下架内容立即剔除，空集合返回null。不引入个性化算法，不承诺文章集合变化后推荐仍固定。

### 3.2 详情 `/reading/:articleId`

进入新阅读时由服务端确认文章仍已发布，并创建/复用当前版本的本人阅读记录；正文仅渲染服务端规范化的纯文本段落与句子，不执行文章HTML。顶部展示完整来源、难度、词数，正文采用Georgia等现有衬线字体、舒适行宽和行距，保留悬浮导航滚动间距。

「继续阅读」带已有版本定位；从本人历史入口进入 `/reading/:articleId?version=...`，恢复该不可变快照及位置。版本由服务端校验，不允许拼接版本ID读取草稿。

阅读位置按句子锚点与偏移保存，段落变化后节流约2秒提交；展示「保存中/已保存/尚未同步」，失败可重试。离开前尽力提交但不只依赖unload，不伪称断网已保存。成功响应前不清除未同步状态；刷新只恢复已入库位置。

完成阅读由明确的「标记已读」操作记录，不用滚动到底冒充已读或生成学习时长。每个文章版本保留独立进度，完成状态不因向上滚动自动撤销。并发页面采用服务端lockVersion条件更新；冲突提示加载最新进度，不静默覆盖另一页。

### 3.3 查词

桌面支持单词点击和选词；手机保留系统文本选择，并提供明确的「查词」按钮，不强行阻止复制和页面滚动。阶段2只查单个英文词，支持词内连字符/撇号，最长64字符；整句或跨句选择提示「请选中一个英文单词」。

使用现有风格的轻量弹窗，小屏靠下展示，受视口约束；打开后焦点进入可操作区，Escape/关闭返回原触发位置。展示原文词形、基本词形、词性、中文释义及可选音标；多种合法词形候选须明确选择，不能无依据猜测词义。

查词是只读操作，**绝不自动收藏**。未找到、超时、服务失败显示在查词区域；失败不阻止继续阅读。切换选择或离开页面取消旧请求，迟到结果不覆盖新选择。未命中不生成伪造词条，允许重新选词或重试；手工创建词条不在首版范围。

### 3.4 收藏与生词本 `/notebook`

查词结果提供「收藏生词」；收藏须由用户点击，服务端验证所选词条确为原文词形的合法候选，并从文章版本恢复原句和位置，不信任客户端提交的释义/原句/用户ID。

同一用户、同一基本词形只保留一个收藏词条；不同原文形式或文章出现位置分别保留来源。同一位置重复点击幂等，返回已有记录，不创建学习任务。请求进行中阻止重复点击，成功轻量Toast，失败在当前区域可恢复。

生词本可分页查看基本词形、释义、原文形式、原句、文章版本和来源，来源链接回到有权限的快照位置。取消收藏要求确认，只软取消本人收藏，不删除来源或未来学习历史；再次收藏复用原记录。阶段3再使用这些记录建立复习队列，本阶段不预建调度表。

首页仅将已完成的外刊精读及笔记本入口接到真实页面，并让原阅读按钮读取今日推荐或回退列表；错词本、留言墙及其他待开发功能仍不启用。今日计划、打卡和复习按钮继续保持诚实空状态，不能用阅读点击伪造学习统计。

## 4. 管理端与文章生命周期

管理端新增「文章管理」，保留独立布局与账户设置，不加入学习功能。页面包括列表、创建/编辑草稿、预览、发布、下架和JSON导入。分类首版为五个固定分类，只提供文章分类选择，不增加无关分类运营后台。

正文先用纯文本编辑及预览，不引入富文本依赖。后端规范化换行、分段和分句，生成版本内稳定句子标识；管理端预览可校对分句结果。标题160字符、简介500字符、署名160字符，正文上限20,000字符，单句上限2,000字符；第三方来源URL只接受http/https。前后端共同校验，服务端为准。

文章状态DRAFT/PUBLISHED/UNLISTED：

- 新文章保存为草稿，不对学习者开放。
- 每次明确保存产生不可变版本快照；编辑已发布文章只更新draftVersionId，当前publishedVersionId不受影响，直至再次发布。
- 发布要求草稿完整、来源说明有效且expectedLockVersion匹配；原子切换发布指针，版本内容不原地改写。
- 下架需确认，撤出列表/推荐并拒绝新阅读；已有本人阅读记录可继续访问其原版本，收藏来源仍存在。首版不做硬删除。
- 管理员并发保存/发布/下架返回409冲突，不覆盖别人的更新；取消编辑或网络失败不能自动发布。

JSON导入：schemaVersion=1，单文件最多1MiB/20篇；先校验并展示逐条字段问题，再确认创建草稿。确认时后端重新完整校验，单批全成全败；clientImportId+规范化payload摘要防重复导入，相同请求返回已有结果，同ID不同内容409。导入不抓取URL、不执行脚本、不接受发布状态或任意账号字段。

## 5. 数据设计（拟新增，不是当前表）

新增Flyway迁移从V5开始，按实现闭环拆分；V1—V4不改、不清库。下表为当前阶段最小关系设计，实施计划再明确逐文件迁移顺序。ID使用BIGINT并在JSON中返回字符串，时间为UTC，显示按上海日期；文本使用utf8mb4，正规化英文检索键用ASCII或明确二进制排序。

| 拟新增表 | 核心字段、约束与索引 |
| --- | --- |
| reading_category | id、code VARCHAR32 UNIQUE、name VARCHAR64、sort_order；首批五类 |
| reading_article | id、category_id FK、status VARCHAR16 CHECK、draft_version_id、published_version_id、lock_version INT、created_by/updated_by FK、created_at/updated_at/published_at；索引(status,category_id,published_at,id) |
| reading_article_version | id、article_id FK、version_no INT、title/title_zh、summary、difficulty、content_json JSON、content_hash CHAR64、category_id FK（版本分类快照）、word_count、estimated_minutes、author、source_type、source_url、usage_note、created_by FK、created_at；UNIQUE(article_id,version_no)，另UNIQUE(article_id,id)供复合归属约束 |
| reading_progress | id、user_id FK、article_id、article_version_id、sentence_key、position_offset INT、status、lock_version、started_at/updated_at/completed_at；UNIQUE(user_id,article_version_id)，索引(user_id,updated_at,id)，复合FK保证版本属于文章 |
| dictionary_entry | id、lemma VARCHAR64 UNIQUE、phonetic、senses_json JSON（词性/释义）、source_note、data_version、updated_at；只存校对后的本地词典 |
| dictionary_form | normalized_form VARCHAR64、entry_id FK、form_type；UNIQUE(normalized_form,entry_id)，支持同形多候选；精确基本词形优先，禁止不受控猜词 |
| user_word | id、user_id FK、entry_id FK、active BOOLEAN、created_at/updated_at；UNIQUE(user_id,entry_id)，索引(user_id,active,updated_at,id) |
| user_word_source | id、user_word_id FK、article_version_id FK、sentence_key、start_offset/end_offset、surface_form、sentence_snapshot；UNIQUE(user_word_id,article_version_id,sentence_key,start_offset,end_offset) |
| reading_import_batch | id/client_import_id UUID UNIQUE、created_by FK、payload_hash CHAR64、item_count、created_at；创建批次及全部文章在同一事务，相同ID的重放核对操作者与摘要 |

分类作为版本快照保存；编辑已发布文章的草稿分类不提前影响当前发布列表，发布时一并切换文章分类。reading_article的两个版本指针采用(article_id,version_id)对应本文章版本的复合FK（草稿首次创建时指针可空），避免引用其他文章版本。其他正文、来源、枚举和长度约束在服务端集中校验；外键不级联删除用户历史。

content_json保存只读段落/句子数组及版本内句子ID。收藏位置采用句内UTF-16起止偏移，Java String与浏览器一致；校验范围、代理对边界及原文匹配。原句、基本词形和出处由服务端决定，用户ID一律取服务端验证的业务principal。

用户收藏和来源写入同一事务，唯一约束处理并发去重；新增来源时重新验证父收藏归属与原文访问权限。软取消不清空来源。文章保存快照和指针更新同事务；已发布快照不可更新。进度写入不改变文章内容或其他用户记录。

首版应用内有限词典缓存（拟上限512词，命中TTL24小时、未命中5分钟），缓存只存公共词典结果、不混入个人收藏状态；数据版本变化清理旧缓存，不新增缓存框架。保留DictionaryProvider边界，外部翻译服务以后通过Java适配，不在本阶段配置真实外部服务。

## 6. API契约草案

统一 `/api/v1`，保留JWT + Redis鉴权、CSRF、no-store私有响应、traceId及结构化fieldErrors。业务异常增加自己的类型与映射，不把reading/dictionary业务塞进IdentityService，也不重构已验证身份流程。

| 方法/路径 | 输入与响应概要 | 边界 |
| --- | --- | --- |
| GET /reading/categories | 分类数组 | 登录 |
| GET /reading/articles | categoryId/keyword/page/pageSize；{items,page,pageSize,total} | 仅已发布，摘要无全文草稿 |
| GET /reading/history | page/pageSize；本人版本与进度分页 | 登录，仅本人已开始记录，不暴露他人历史 |
| GET /reading/recommendation | {date,article:null或摘要} | 登录，日期取服务端 |
| POST /reading/articles/{id}/start | 无用户/版本参数；{articleVersion,progress}，首次201/已有200 | 锁定发布状态后创建/复用本人记录，CSRF |
| GET /reading/articles/{id}/versions/{versionId} | {articleVersion,progress} | 当前发布版本或本人已开始的旧版本；草稿/他人记录不可读 |
| PUT /reading/progress/{versionId} | sentenceKey/offset/completed/expectedLockVersion；返回持久进度 | 当前用户已开始，CSRF，乐观锁 |
| POST /dictionary/lookup | word；{found,candidates:[entryId,lemma,phonetic,senses,sourceNote,collection]} | 登录+CSRF，只读语义、不创建收藏；collection为本人收藏状态，在公共词典缓存之外查询/组装 |
| GET /vocabulary/collected | page/pageSize；词条与来源分页摘要 | 仅本人active收藏 |
| GET /vocabulary/collected/{id} | 释义及分页来源 | 仅本人，非本人同不存在404 |
| POST /vocabulary/collected | articleVersionId/sentenceKey/startOffset/endOffset/entryId；收藏及是否新增 | 本人阅读快照、词形候选校验；201新增/200已存在；CSRF |
| DELETE /vocabulary/collected/{id} | 204，重复取消幂等 | 仅本人、软取消、CSRF |
| GET /admin/articles | status/categoryId/keyword/page/pageSize | 服务端ADMIN |
| POST /admin/articles | 草稿字段；201草稿与lockVersion | ADMIN+CSRF |
| GET /admin/articles/{id} | 当前草稿、发布指针、lockVersion | ADMIN |
| PUT /admin/articles/{id}/draft | 完整草稿+expectedLockVersion；新版本及新锁值 | ADMIN+CSRF，冲突不覆盖 |
| GET /admin/articles/{id}/preview | versionId；只读预览 | ADMIN，不能冒充学习者正文接口 |
| POST /admin/articles/{id}/publish | expectedLockVersion；新发布状态 | ADMIN+CSRF，发布前完整校验 |
| POST /admin/articles/{id}/unpublish | expectedLockVersion；新状态 | ADMIN+CSRF，下架不删除历史 |
| POST /admin/articles/import/validate | schemaVersion/articles；逐条字段结果 | ADMIN+CSRF，不写文章 |
| POST /admin/articles/import | 同批JSON body+query clientImportId；{batchId,articleIds} | ADMIN+CSRF，复验、事务及幂等 |

校验字段名保持结构化，如title、sourceUrl、articles[2].title。批量导入逐条问题在表格对应位置展示；单篇字段错误在输入下方。通用错误延续{code,message,traceId,fieldErrors?}，不依赖中文文本判断类别。

拟定错误：400 VALIDATION_ERROR；401 UNAUTHENTICATED；403 FORBIDDEN/CSRF_INVALID；404 ARTICLE_NOT_FOUND/READING_NOT_FOUND/WORD_NOT_FOUND；409 ARTICLE_UNAVAILABLE/ARTICLE_VERSION_CONFLICT/READING_PROGRESS_CONFLICT/IMPORT_CONFLICT；503 DICTIONARY_UNAVAILABLE。普通查词未命中返回200 found=false，不当成系统异常。

文章不存在、草稿或无权版本统一404；已知公开文章在新阅读请求期间下架可返回409 ARTICLE_UNAVAILABLE；收藏等私有资源非本人统一404。所有资源归属必须在服务端查询条件及事务内检查，不能依赖前端角色开关或传入userId。

## 7. 代码与页面范围

后端新增reading、dictionary、vocabulary业务包，按模块放Controller/DTO/Service/Mapper/实体；安全配置显式开放对应已登录/ADMIN路径，默认拒绝其他接口。复用MyBatis-Plus/JDBC事务能力，不增加基础框架。

前端新增ReadingListView、ReadingDetailView、NotebookView、AdminArticlesView及编辑/预览页面，复用LearnerLayout/AdminLayout、FeatureIcon、useFormFeedback、FormFeedback和notify。查词弹窗、文章卡片按实际复用需要拆分，不预建通用页面框架。

修改router/index.ts、navigation.ts、AdminLayout的文章入口以及HomeView的真实阅读/笔记本入口。新增路由按现有身份规则区分学习端/管理端，扩展安全的登录后返回路径，仍拒绝任意外链redirect；不能遗漏嵌套阅读地址和管理员重定向。现有认证接口不变。

学习页面样式独立维护，复用tokens；不改已确认登录注册布局。桌面查词浮层不超出视口，手机保持选词、滚动、关闭和收藏按钮可达。必要确认复用已有Element Plus MessageBox，不另装组件库。

## 8. 分步交付与验证门槛

下面是设计中的功能闭环顺序，不是已完成任务或逐文件实施计划：

1. 一篇文章闭环：管理员保存/预览/发布 → 学习端列表与详情 → 真实MySQL持久化与权限测试。
2. 阅读恢复闭环：开始阅读、保存/恢复、版本更新、下架后的新旧访问、并发进度冲突。
3. 查词闭环：本地词典/词形、桌面点击与手机选词、未找到/失败/取消请求。
4. 收藏闭环：明确收藏、并发去重、原句来源、生词本、取消和再次收藏、用户隔离。
5. 内容与管理闭环：导入校验/事务/幂等、至少10篇原创文章及配套词典、今日推荐和首页接入、完整联调。

每步交付可运行的前后端流程，不能先写完全部后端再补前端。逐任务实施计划已据确认设计编写，明确V5—V9顺序、文件、测试和交接点；计划审阅后再实施。

验收必须覆盖：

- 管理员发布后学习者可见；普通用户直接调用管理API403；未登录401；客户端伪造角色/用户ID无效。
- 草稿与他人旧版本/收藏/进度不能读取；全部查询、保存和取消以服务端验证的principal用户为准。
- 更新发布不覆盖旧版本；下架不能开始新阅读，已开始者可恢复旧快照；无硬删除历史。
- 进度刷新/重新登录恢复，失败不显示已保存；两个页面冲突不会静默覆盖。
- 查词不收藏；原词/基本词形/原句/来源准确，词典未命中和故障明确且不阻塞阅读。
- 并发重复收藏只一个词条，同一位置只一条来源；不同位置保留多来源；取消与恢复不产生重复任务。
- 导入格式错误逐条定位；混合有效/无效批次零部分写入；同请求重试不重复建文章；并发编辑409。
- 日期跨天推荐、无已发布文章、类别/搜索空结果、长标题/长句、网络断开、会话过期及CSRF保持正确反馈。
- 前后端单元、真实专用MySQL集成、类型检查、构建、桌面/320px手机浏览器分别记录命令与退出码，Mock与真实联调不混用。
- 用户确认阅读/查词视觉和手机选词体验；10篇正文/重点词/释义校对通过才算内容验收，不用自动测试代替质量确认。

尚未完成的工作：全部功能代码、迁移、内容与词典编写、阶段2自动测试和人工验收；现有阶段1测试通过不代表上述工作已完成。
