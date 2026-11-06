# 精读写作积累、理解练习与阅读打卡：设计记录

2026-10-18最新状态：A—E已按用户确认顺序完成，当前交付见docs/progress.md及阶段E验收。E规则已明确确认：同版读完+完整正式练习自动计上海当日、不设正确率、每天一次/同篇一次，统计放/stats不恢复首页大卡。以下最初草案的未确认/待实现状态保留历史，不覆盖当前交付。


2026-10-18状态更新：用户已确认原型并授权实施，A/B已完成，C见阶段C实施/验收文档。D/E待顺序实现，打卡具体规则在E前复核；下文最初盘点的未确认或未实现为历史，不覆盖当前进度。

状态：仅代码检查与设计草案；用户未确认本方案，不实施业务代码、数据库迁移或测试数据写入。阶段A—E是本次增量的小阶段，不替换主路线阶段0—6。

## 1. 已核实的基础

核对AGENTS、文档入口/进度、架构路线、API、数据库迁移V1—V10、最新验收，以及ReadingDetailView、ReadingRail、ReadingToolbar、ReadingService、ReadingProgressService、ReadingMapper、RecommendationService、AdminArticleService、管理端编辑器和路由。

- 已有reading_article及不可变reading_article_version；发布、下架、并发lock_version及旧版访问规则可复用。
- reading_progress按user_id/article_version_id唯一；完成状态由用户明确标记，completed_at首次设置后保留。它证明已标记阅读完成，不证明真实认真阅读，更不是打卡记录。
- 词典/短语、个人生词与来源、文章收藏、本人历史、游客公开阅读、登录弹窗、JWT/Redis、CSRF和服务端身份/权限均已存在。
- ReadingRail包含相关文章、本篇词汇；<=1020px词汇改为底部面板。不能直接把全部长表单塞进此面板。
- 写作素材/收藏/仿写、题目/判分/练习记录、每日打卡均未实现。/practice和/stats仍为PendingFeatureView；首页已移除计划/打卡，仅有推荐逻辑，不存在可直接同步的真实打卡组件。
- 原路线阶段3负责词汇复习与统计，阶段4负责题组/答题/判分。新请求将部分阅读练习提前，应经用户确认记录为阅读扩展；未来专项训练复用此处题组/版本/判分基础，不能再复制一套题库。
- 本轮表结构核对基于实际迁移代码和数据库文档，未连接实时数据库做新验收。实施前再次核对Flyway实际版本及下一可用编号。

## 2. 方案比较与推荐

1. 只做侧栏展示：最省事，但没有个人保存、判分或打卡闭环，不满足完整需求。
2. **推荐：写作侧栏摘要+独立展开详情，正文后做逐题练习，分A—E实现。** 保留阅读主线，每阶段独立验收。
3. 直接开发完整写作中心/通用考试平台/统计面板：范围过大，会延迟当前可用版本，本次不采用。

## 3. 页面和组件

桌面右侧依次：更多文章、本篇词汇、写作积累独立卡片。写作卡默认显示最多2—3条核心表达、简短中文和收藏状态；点击查看解析打开较宽统一详情面板，展示重点词组、结构、完整例句/翻译、适用场景及仿写。分页或展开其余素材，不重复在正文放第二份完整素材列表。不为填空强制固定高度或制造素材。

移动端正文和全文翻译之后以折叠模块显示写作积累、阅读理解；词汇面板继续只做词汇。写作详情内部可滚动，保留关闭/焦点管理和未保存提示。无素材/无题目时真实空状态；模块数据独立加载，失败不阻断正文与查词。

正文之后顺序：全文翻译、移动端写作入口、阅读理解、阅读完成/本次练习/打卡状态、上一篇下一篇。桌面可在同一位置保留轻量“查看写作积累”定位入口，不复制完整内容。保留原文DOM文本、UTF-16偏移和正常选区，工具栏至多增加一个练习定位入口。

建议组件：ArticleWritingPanel、WritingMaterialCard、WritingMaterialDetail、ImitationForm、ArticleQuizPanel、QuizQuestionCard、ReadingCompletionCard。ReadingDetailView只编排，不继续堆业务脚本。复用既有FieldError、FormFeedback、Toast、确认弹窗和认证弹窗。

## 4. 写作业务

素材关联现有article_id与明确article_version_id。管理员录入expression/meaningZh/keyPhrases/structure/exampleEn/exampleZh/scenarios、展示顺序、草稿/发布/下架；全部为纯文本，不接收任意HTML，不实时AI生成。文章换版时旧素材不自动套到新正文，可由管理员复制后复核发布。

普通公开接口只返回可访问文章版本的已发布素材，旧版/下架访问沿用ReadingService.version本人历史规则。收藏接口不接收userId，唯一(user_id,material_id)，幂等设置/取消。个人素材库使用独立/writing入口置于账户菜单或笔记本附近，不占据主导航核心位置；列表后端分页。

仿写首版每用户每素材只保留一条可修改记录，不开发历史版本列表或AI批改。记录所参照material_revision及素材快照，管理员后续改例句不改用户当时的依据。保存显式按钮，不逐字自动请求；失败保留草稿，换文章/关闭编辑前提示未保存。中英文输入法不阻断，提交验证非空/长度/至少含英文字母，不用简单英文正则误禁标点、人名和缩写。

收藏仍显示当前可访问发布素材；下架素材的已有收藏显示“已下架”，允许取消、保留本人仿写快照，不能继续新增。已有引用时删除以归档为主，不级联删除个人记录。恢复发布沿用同一素材ID；material_revision和lock_version处理内容变更/并发编辑。

游客点收藏/保存时打开现有登录弹窗，内存保留本次仿写及目标素材，登录后复验素材可用再继续；退出或切换账号清理私人内容。密码及用户输入不进入公共缓存。

## 5. 练习与答案安全

每篇文章版本首版一个可发布练习题组，建议3题、允许1—10题。四选一、单正确答案；题型代码MAIN_IDEA/DETAIL/INFERENCE/VOCABULARY/ATTITUDE只是分类，不实现5套判分系统。

题组具有稳定ID、draft_version_id、published_version_id和lock_version。发布后题组版本及其题目冻结；修改新建草稿版本，旧练习继续引用原版。题目含稳定question_key、题型、题干、options_json(A—D)、correct_option、explanation、sort_order。题组必须同时绑定article_id/article_version_id，不能给旧正文套新题。

公开读取响应显式使用无答案DTO，只包含题组版本/题ID、题型、题干和四个选项。正确答案和解析只在管理员接口和允许的已提交反馈接口返回，不能藏在前端JSON/CSS/HTML或公开tools中。管理员草稿预览单独权限接口。

登录用户开始/恢复IN_PROGRESS attempt，冻结题组版本与题目集合。选择本身可修改，离题或切题时保存draft_choice并在刷新恢复；提交某题后服务端锁定该题答案并返回正确与解析，不允许同一轮反复改到全对。重复提交同答案幂等，不同答案409冲突；每轮完成所有题后保存最终正确数/总数/完成时间。最后一题提交与轮次完成同事务。结果查询只允许本人。重做新建一轮，不覆盖首次成绩；页面明确区分首次与本次。

同账号多标签页开始按用户+题组锁定，复用一个进行中轮次；重试/交卷幂等。题组下架后不能开始新轮次，既有学习者可读原轮次及解析；不删除历史证据。已发布版本修改不会重新判历史成绩。

游客使用同一后端判分逻辑，以服务端签名、短期有效的体验轮次凭证绑定题组版本；先提交当前题才反馈该题答案。凭证不产生个人记录、不能用于打卡，需限流/限次数，过期重新体验。刷新首版不承诺保存游客体验；登录后重新开始可持久化轮次，不自动把游客体验变成账号成绩。公开判分注定允许用户提交任意选项后知道答案，能防提前泄露，不能承诺防作弊考试安全。

推荐以每题提交即时反馈符合截图场景；未来完整考试需交卷后揭晓，可复用题组版本和判分服务，策略另行确认，不提前做通用规则引擎。

## 6. 打卡候选规则（须用户确认）

推荐阅读与练习达成后自动打卡，两个独立完成条件：本人reading_progress为COMPLETED；同一文章版本的已发布配套题组有完整提交的正式attempt。正确率不设门槛，答错仍是有效学习；收藏和仿写不作为必需条件。

在最后一个条件首次满足的服务端事务/统一资格服务中计算Asia/Shanghai日期并幂等记录；同用户同日最多1条，不能用客户端日期/完成标志/得分。跨午夜以最终满足条件时的日期计算，不补昨天；时间戳存UTC，周历按上海周一至周日转换。两个动作任意先后都可检查资格，单纯打开/滚动/点击“已读”均不足以打卡。

建议同一文章只贡献一次阅读打卡，题目重做与版本重发不增加新天数；唯一(user_id,article_id)防重复刷同篇。一天多篇只加一天，其他文章的真实阅读/练习记录照常保存。只对当日完成动作触发资格评估，不通过每天读取旧记录补打卡。满足两条件但当日已有打卡，返回“今日已打卡，本篇练习已保存”。服务器只保证证据一致，不宣称能证明用户认真阅读。

无已发布配套题目的文章可标记读完，但不完成本轮“阅读+练习”打卡，并在开始前标明。当前内容少，需要先为公开文章配题；若采用同篇仅计一次，长期使用还需持续发布新文章。不要用虚假完成或随机文章数填充周历。

展示推荐：文章结尾显示本篇条件；/stats先实现真实阅读打卡概览/本周记录，词汇统计继续待开发。尊重此前首页移除计划/打卡的决定，首页默认只保留学习统计入口，不恢复两张大卡；若本次用户明确改变决定，可仅登录后展示简短“今日已打卡/待完成”及入口，游客首页不出现个人记录。

## 7. 数据库草案

下一可用Flyway起按A—E新增，不改V1—V10、不删数据；当前预计V11起，落地前再核对。复用identity_user、reading_article、reading_article_version；时间/外键/ID序列与现有规范保持。

| 表（暂定） | 主要字段和约束 |
| --- | --- |
| reading_writing_material | id/article_id/article_version_id、expression/meaning_zh/key_phrases_json/structure/example_en/example_zh/scenarios_json、status/sort_order/material_revision/lock_version、作者/时间/归档标志；文章版本联合归属外键 |
| user_writing_favorite | user_id/material_id/active、时间；UNIQUE(user_id,material_id) |
| user_writing_imitation | user_id/material_id/text/material_revision/material_snapshot_json/lock_version、时间；UNIQUE(user_id,material_id) |
| reading_quiz | article_id/article_version_id、draft_version_id/published_version_id/status/lock_version、作者/时间；首版每文章版本一个题组 |
| reading_quiz_version | quiz_id/version_no/article_version_id、创建人/时间；UNIQUE(quiz_id,version_no)，发布后不可变 |
| reading_question | quiz_version_id/question_key/type/stem/options_json/correct_option/explanation/sort_order；UNIQUE(quiz_version_id,question_key)，发布版禁止修改 |
| user_reading_attempt | user_id/quiz_id/quiz_version_id/article_version_id/status/correct_count/total_count/lock_version、开始/完成时间、客户端重试标识；幂等唯一约束，开始按用户+题组串行复用活动轮次 |
| user_reading_answer | attempt_id/question_id/draft_choice/submitted_choice/is_correct/submitted_at/lock_version；UNIQUE(attempt_id,question_id)，归属与题目版本服务端校验 |
| user_reading_checkin | user_id/checkin_date/article_id/article_version_id/attempt_id/reading_completed_at/rule_version/created_at；UNIQUE(user_id,checkin_date)，建议UNIQUE(user_id,article_id)，保存可审计证据 |

比需求示例多题组/版本和轮次，是为了防止改题影响历史、支持恢复/重做；不是再建文章体系。素材keyPhrases/scenarios、四选项适合有界JSON，不为每个词组建关系表。公开题目/后台答案DTO分离。

## 8. API草案

所有路径/api/v1，沿用Bearer/CSRF、结构化fieldErrors/no-store、字符串ID、分页和当前身份校验。写接口从认证身份取user_id，不信任请求用户ID或日期。

- GET /reading/articles/{articleId}/versions/{versionId}/writing-materials：可访问版本的公开已发布素材；GET /writing-materials/{id}复验文章访问。
- PUT/DELETE /writing-materials/{id}/favorite：本人幂等收藏/取消；GET /account/writing-materials?page&pageSize：本人收藏。
- GET/PUT /writing-materials/{id}/imitation：本人仿写读取/显式保存，带expectedLockVersion。
- GET/POST /admin/articles/{id}/versions/{versionId}/writing-materials；PUT/DELETE /admin/writing-materials/{id}；POST /{id}/publish、/unpublish：ADMIN录入/归档/状态切换。
- GET /reading/articles/{id}/versions/{versionId}/quiz：无答案公开DTO；POST /reading/quizzes/{id}/attempts：登录开始/恢复；GET /reading/attempts/{id}：本人恢复/结果；PUT /reading/attempts/{id}/answers/{questionId}：保存选项草稿；POST 同路径/submit：后端判分，带幂等/并发信息。
- POST /reading/quizzes/{id}/guest-attempts；POST /reading/guest-attempts/answers（凭证放请求正文，不放URL或日志）：无账号保存的体验判分。短期签名凭证及每题已提交状态只保留必要的Redis体验状态，使用独立前缀与过期，不复用账号会话或写个人学习表。限制单轮答题数及每IP请求频率；重复提交当前题返回同一反馈，不同答案拒绝，不提前返回其他题答案。
- GET/PUT /admin/articles/{id}/versions/{versionId}/quiz：题组管理；POST /admin/reading/quizzes/{id}/publish、/unpublish；后台预览单独端点。写入统一题目校验，不通过普通接口修改正确答案。
- GET /account/reading-checkins?weekStart=YYYY-MM-DD；GET /account/reading-task-status：本人只读状态。查询日期只限制读取合法周，不参与写入。打卡无“任意日期签到”写接口，由资格服务触发。

接口名在实施计划细化时与现有约定统一；创建201、幂等重试200/204、字段400、未认证401、无权资源404、权限403、并发409、限流429、服务故障503。失败在对应素材/题目局部提示，保留选项/仿写，不统一改Toast。

## 9. 文件与实施顺序

现有前端：ReadingDetailView.vue、ReadingRail.vue、reading-detail.css、router/index.ts、AdminArticleEditorView.vue（增加对应文章版本管理入口）、AdminLayout.vue（必要入口）、HomeView.vue（仅最终确认首页变化才改）、PendingFeatureView路由（统计局部落地）。

新增前端：writing/quiz/checkin API模块、上述阅读组件、WritingLibraryView、AdminArticleWritingView、AdminArticleQuizView、ReadingStatsView及相应测试。认证模块只调用现有弹窗，不重新实现。

后端在现有reading下新增writing/practice/checkin子包或相邻业务模块，包含Controller/Service/Mapper/DTO/Rules。复用ReadingService.version访问、AdminArticleService.requireAdmin和统一异常，不扩写DictionaryService或IdentityService。新增迁移、真实库集成/权限/并发/判分测试、Clock可控跨日测试。

| 小阶段 | 完整交付 | 核心验收 |
| --- | --- | --- |
| A | 管理录入/编辑/发布/下架写作素材→公开侧栏/手机展示 | 草稿不泄露、文章版本匹配、并发编辑、实际排版 |
| B | 收藏/取消→个人素材库；本人仿写保存/修改 | 唯一性、刷新持久化、跨用户隔离、素材下架快照、登录续操作 |
| C | 管理四选一题组与发布版本→公开无答案题干；游客体验判分 | 发布校验、游客响应不含答案、提交才揭晓、体验限流、旧版稳定 |
| D | 正式轮次/草稿恢复/逐题提交/解析/正确率/重做 | 服务端判分、伪造题目ID拒绝、并发/重试幂等、历史不变 |
| E | 阅读+正式练习资格→自动打卡→真实周历/入口 | 同日并发只计一次、同篇策略、跨午夜、无题目/游客不可打卡 |

每阶段完成前后端一个闭环，不一次写完9张表后再做前端。新迁移仅在相应阶段添加；A可先选现有公开文章准备1—2条原创编辑素材，必须人工复核，不伪造用户记录。题目优先为实际公开文章准备3题，内容量由编辑工作决定。

## 10. 风险和待确认

1. 侧栏只放摘要，完整解析/仿写放展开详情，避免狭栏长文本、移动端面板套面板。
2. 本次C—E提前原主路线部分阶段4能力；确认后记录范围调整，不宣称主阶段3/4全部完成。
3. 打卡推荐不设正确率门槛，同篇仅一次，按最后条件完成日自动计入；用户需确认是否允许日后重读同篇再次打卡。
4. 首页推荐沿用之前决定不恢复计划/周历大卡，统计页查看；若要恢复仅登录轻量展示需明确确认。
5. 不把网页计时/滚动作为“真实读懂”证明；不引入高成本反作弊。游客提交后知道答案是产品体验取舍。
6. 题组版本/文章版本冻结和资料下架规则须先做，不能等用户有成绩后再补。
7. 本轮只读检查和设计，未运行新功能测试，不声称新功能可用。未来AI只记录roadmap，不安装依赖。
