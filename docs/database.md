# 数据库设计与迁移约定

## V20：专项原文用途与独立发布（2026-10-20）

reading_article新增content_usage VARCHAR(16) NOT NULL DEFAULT READING，CHECK限定READING/PRACTICE；不新增表，既有文本/题目/轮次外键不变。既有文章保持READING，专用材料创建为PRACTICE且不走阅读发布。practice_request结果CHECK增加MATERIAL操作（set_id非空、attempt_id空），复用同用户/request_id主键保障原文+题组原子创建的幂等性；历史START/SUBMIT/RETRY/IMPORT规则不变。已执行V1—V19不改写。

本地开发库对本任务专门录入的文章11/12单独调整用途；只改content_usage，保留原状态、正文、版本和所有历史。其他文章不会因关联专项自动迁移。修改专用原文依旧文章→quiz→set锁序，题组版本校验后同事务新建原文版本/保存题目/撤销校对；原文不变时复用旧版本。普通文章保存接口拒绝PRACTICE以避免绕过题组锁。

## 2026-10-19 专项练习：V19

`V19__practice.sql` 为增量，旧V1–V18不改、不清库。`reading_quiz.usage` 默认ARTICLE、支持PRACTICE，原同文章版本唯一键改为含用途；先保留外键需要的前缀索引。旧QuizMapper固定ARTICLE，专项mapper固定PRACTICE，共用题目版本/不可变题目而不共用逐题作答流程。

| 新表 | 内容与约束 |
| --- | --- |
| practice_set | 同article/exam/type唯一，草稿metadata及review hash JSON、文章版本/quiz关联、状态、revision/lock_version、发布版本引用 |
| practice_set_version | 每set/revision唯一，冻结材料/quiz版本/metadata；联合外键确保属于同set/article/quiz |
| practice_attempt | 本人FULL/MISTAKES、冻结set/quiz版本、来源轮次、当前题与版本、完成总分；生成active_set_id保证每用户/题组/kind至多一个未完成 |
| practice_answer | 每轮每题唯一及序号唯一，draft/submitted choice分离；联合外键限定题目属于本轮quiz版本 |
| practice_request | 每用户/requestId唯一，operation/hash、原结果轮次或导入set引用，CHECK约束结果类型 |
| practice_mistake | 每用户/冻结question唯一，source/latest轮次、PENDING/RESOLVED、wrong_count；同用户联合外键保护归属 |

发布按文章→quiz→set锁定，生成不可变版本；开始继续到user/attempt。本人草稿/提交从user写锁开始，不反向锁文章。READ_COMMITTED下整组判分、答案冻结、错题投影和幂等结果同事务完成。错题按提交事务顺序更新，重放不增加次数；旧来源版本不与新题合并。导入锁文章后检查唯一题组，再锁user写请求结果，仅插入新quiz/set，不等待已有子记录。

逐题校对hash包含题目内容，题组确认hash包含材料版本/元信息/题序与内容；排序保留未改题校对但撤销题组确认。仅review API根据已保存内容记录，导入和客户端布尔不能伪造。开发/测试库迁移及真实检查见[验收](acceptance/2026-10-19-practice.md)；不访问其他项目库。

## 2026-10-19 退出保存：V18

增量迁移V18__saved_learning_rounds.sql为vocabulary_learning_session增加source_key/source_title：NEW为PERSONAL或BOOK:id，REVIEW为GLOBAL。旧未完成轮次按原锁定来源回填，历史学习/反馈不删除。

生成列unfinished_scope在非COMPLETED时为kind:source_key，否则NULL；unique(user_id,unfinished_scope)防同scope重复保存。active_slot在ACTIVE时为1，否则NULL；unique(user_id,active_slot)防多个活动轮次。允许多PAUSED，最多一ACTIVE，沿用用户写锁和READ_COMMITTED。分配排除未完成轮次占用词；新增额度扣除未完成NEW的未通过项。

不改已执行V1—V17，不清库；以下旧单轮次说明为历史约束，由V18替代。


## 2026-10-19 V17：参考词书和来源选择

新增vocabulary_wordbook（code/version唯一、来源/notice/manifest_hash、原始数/可练数/音标缺项/排除JSON、PUBLISHED/UNLISTED），vocabulary_wordbook_entry（book/entry主键，book/sort_order唯一），vocabulary_learning_source（user主键，PERSONAL或BOOK与可空book_id的组合CHECK）。复用dictionary_entry和identity_user外键。

vocabulary_learning_word增personal_enrolled BOOLEAN NOT NULL DEFAULT TRUE；历史V16行仍属个人来源，书中按需分配的新行FALSE。个人明确加入已有书词时标记TRUE，原学习阶段和到期时间保留。词条进度仍唯一user/entry，切书不删历史。新词候选按来源过滤，到期复习保持全局全部来源。

公共词书数据用固定hash校验后在dictionary_manifest_lock下事务补充，只INSERT IGNORE缺词/词形，不覆盖原释义、ID或私人记录。独立dictionary_wordbook_cet4_hash纳入查词manifest使缓存可失效，保留旧10000子集版本/hash。导入重复无新增，同版异manifest拒绝；下架状态不因重启自动重发。选择词书只写本人来源，开轮才分配最多20项。

词汇统计不另建事实表，读取既有反馈和轮次；不改阅读资格/打卡表。V1—V16保持原样，V17已在项目专用开发/测试库迁移，无清库。

## 2026-10-19 V16：个人生词学习与复习

新增六表：vocabulary_learning_setting（user主键及目标范围）、vocabulary_learning_word（unique user/entry，共享阶段、due_at、active、revision及规则版本）、vocabulary_learning_session（kind/mode/status/phase/version、有界queue_json）、vocabulary_learning_item（不可变词典/来源/选项snapshot、passed/failure_count/outcome）、vocabulary_learning_event（反馈/判定/排期快照）、vocabulary_learning_command（unique user/request、hash及响应）。

复用identity_user/dictionary_entry，不重建收藏；session/item/word采用复合归属外键。队列最多20项，由queue_json保存顺序，省去冗余position；失败事务移队尾，不复制词条。用户写锁保证唯一未结束轮次，暂停也占用该轮；反馈、排期和命令同事务。写事务READ_COMMITTED，避免身份查询先建立MySQL可重复读旧快照。

不改V1—V15，不清库。user_word取消收藏不删学习历史，暂停学习独立。时间TIMESTAMP(6)/Instant，日统计与间隔按Asia/Shanghai；旧事件保留规则版本不静默重算。题目及来源快照按开轮保存，公共字典变化不替换已有题目。

## 2026-10-18 写作阶段A：V11

新增reading_writing_material，开发切换前已在专用测试库验证。id、article_id/article_version_id联合外键指向不可变文章版本；content_json草稿、published_content_json发布快照，status(DRAFT/PUBLISHED/UNLISTED/ARCHIVED)、revision、lock_version、created_by/updated_by及时间。发布状态CHECK要求非空发布快照；按文章/版本/状态索引。

保存草稿不改变公开内容，发布事务原子复制快照；版本行锁防并发覆盖。归档保留记录，不级联删除。当前每文章版本最多50条活跃素材，创建锁所属文章行串行校验。只新增V11，不改V1—V10或旧文章/用户/词汇记录；B阶段再新增个人收藏/仿写表。

## 2026-10-17 参考译文补齐：仍无结构迁移

十篇60段13句编辑参考译文在content/reading/editorial-translations.v1.json随JAR交付，dataVersion=1.1；不更改不可变英文版本、发布状态、个人进度、收藏或生词来源。dev/test仍V10/18表，没有为了内容文件更新创建空Flyway或清库。其余草稿的译文仅备好，不自动发布；全文匹配规则继续阻止原文变化后错配。

## 2026-10-17 词典离线扩容：复用V7表，无结构迁移

ECDICT本地开发子集复用dictionary_entry、dictionary_form和system_metadata。启动导入开关默认false；显式开启后锁既有dictionary_manifest_lock，在事务中批量INSERT IGNORE词条/词形，不覆盖已有释义、音标或ID，不改user_word/source。相同版本及hash重复执行无数据变更；相同版本内容改变拒绝导入。独立dictionary_ecdict_version/hash元数据不改原词库版本/hash。关闭开关只停止自动导入，不删除已持久化条目或收藏引用。

补充10,000词头、23,112词形，和原319合并去重10,006词头。表结构仍V10/18表，无新Flyway；本轮不建外部缓存表。源版本、原始/派生hash和许可见content/dictionary/ecdict-local/NOTICE.md。只在用户确认的本地开发启动时显式启用，不自动配置生产环境。

## 2026-10-17 精读详情第一小阶段：无数据库迁移

开发/测试继续V10、18表，未改历史迁移或旧数据。本人本版本生词分页复用user_word/user_word_source的所有权、唯一约束与来源字段。相关文章/邻接查询复用已发布reading_article，工具数据不写阅读记录。

首篇原创参考译文随content/reading/editorial-translations.v1.json持久交付到JAR，按标题与规范化正文完全匹配后绑定paragraph.key/sentence.key；不是第三方结果缓存，也不修改不可变版本。以后引入外部提供器或可维护译文表时，使用下一未占用Flyway迁移并明确授权/缓存规则；本阶段不预建空表。


## 2026-10-16 阅读增量：V10

新增 Flyway V10__article_favorites.sql，仅增加 user_article_favorite：id、user_id、article_id、active、created_at、updated_at。user_id/article_id 为外键，联合唯一约束防止重复收藏；(user_id, active, updated_at, id) 索引用于本人活动收藏列表。幂等设置与文章锁处理并发，不使用前端 userId。

收藏归属文章，阅读进度继续归属不可变文章版本，复用原有表，不复制用户或阅读体系。开发/测试专用库已迁移至V10、18表。未修改已执行迁移，未删除旧数据，未自动发布草稿。


## 阶段2 Task1：V5文章与版本

2026-10-16：V5__reading_articles.sql已在专用测试库验证（11表），开发库仍为V4，待阶段2统一切换。新增reading_category五类、reading_article及reading_article_version；草稿/发布版本指针以复合FK限定本文章，版本分类单独保存，保存草稿不提前改已发布分类。无硬删除接口，用户/版本历史外键不级联删除。真实测试证明并发保存一胜一409、跨文章版本引用失败、事务失败不留半篇文章。后续V6—V9尚未实现。

2026-10-16认证迁移：用户及邮箱继续在MySQL，V1—V4不改，本轮无新迁移。SPRING_SESSION、SPRING_SESSION_ATTRIBUTES保留但不再读写；登录family及刷新摘要转入项目专用Redis，使用到期TTL和独立环境namespace。security_version仍是改密/重置撤销的持久依据。阶段2业务仍从V5设计，不提前建表。

日期：2026-10-14。只记录当前已设计的结构与后续设计要求，不提前创建后续业务表。

## 2026-10-15：邮件发送状态V4

后续阶段2数据仅为[设计草案](superpowers/specs/2026-10-15-phase-2-reading.md)，拟从V5追加；当前没有这些业务表，不把设计写入等同于迁移已执行。

新增 `V4__email_delivery_state.sql`，不改V1—V3、不新增表。identity_email_challenge增加delivery_state VARCHAR(16)、ASCII二进制排序、NOT NULL、默认NOT_SENT，CHECK限定NOT_SENT/QUEUED/SMTP_ACCEPTED/FAILED。默认值兼容历史证明，不虚构旧邮件投递结果。

新请求内部记录QUEUED，SMTP调用返回后写SMTP_ACCEPTED；失败写FAILED并禁用证明。不匹配公开请求记录NOT_SENT，不触发投递，也不能禁用已有真实证明。公开响应不返回此字段；仅登录本人BIND请求可通过受保护接口查询，过期/禁用状态由时间及原字段共同决定。SMTP接受不代表收件成功，数据库写入异常仍按不可用证明处理。表数仍为8，迁移历史增至V4。

## 环境与迁移

- 开发库 `english_hero_dev`，集成测试库 `english_hero_test`，使用各自的项目账号。
- 本地现有 MySQL 服务先复用；部署目标保持 MySQL 8.4。2026-10-14以专用账号确认本地服务端8.0.41。
- 编码 utf8mb4；项目连接使用 UTC。数据结构由 Flyway 管理，clean 保持禁用。
- 项目账号、授权和配置方法以 [README](../README.md) 为准；实际密码不写进文档。

## 当前阶段 0 的表

迁移文件：`backend/src/main/resources/db/migration/V1__system_metadata.sql`。

| 字段 | 类型 | 约束与用途 |
| --- | --- | --- |
| metadata_key | VARCHAR(64) | 非空主键，系统标识键 |
| metadata_value | VARCHAR(255) | 非空，系统标识值 |

表名 `system_metadata`；InnoDB；utf8mb4_0900_ai_ci。初始记录为 `service_name = english-hero`。本表没有外键，也不需要额外索引；主键支持状态接口的单记录查询。

Flyway自行维护迁移历史。2026-10-14已在专用新库完成首次/二次迁移验收；两个库的V1成功记录各1条，只含系统元数据与迁移历史两表。

## 后续各阶段必须补齐的设计

| 阶段 | 设计对象 | 必须明确的约束 |
| --- | --- | --- |
| 1 | 用户、角色、数据库会话 | 用户名唯一、密码编码、会话过期、管理员来源、用户归属 |
| 2 | 文章/版本、词典、生词与来源 | 发布状态、版本关系、收藏去重、来源语境保留 |
| 3 | 学习状态、复习事件 | 一次反馈的唯一性、规则版本、到期查询索引 |
| 4 | 题组/题目、练习与作答 | 共用材料、题目版本、一次交卷唯一结果、重做产生新记录 |
| 5 | 试卷、分区、考试记录、音频引用 | 服务端时限、作答版本冲突、音频关系、主观题评价状态 |
| 6 | 智能辅助和可选扩展 | 按独立功能需求设计，不为假设能力预建表 |

每次详细设计写出字段类型、主键、关系图、外键或由业务服务维护的关联理由、唯一约束、查询与索引、状态变化、历史保留和事务边界。图与迁移脚本采用同一字段命名。

迁移在专用测试库验证后再用于开发/部署。已执行迁移只通过新增版本演进；历史记录不因内容下架或修改而改变。高风险变更说明备份和兼容旧应用的方法，不自动删表或重建数据。

## 阶段1：身份与持久会话

迁移：`V2__identity_and_sessions.sql`。保留V1，Session自动建表设置never，Flyway唯一维护结构。角色从数据库读取，密码编码使用`{bcrypt}`；哈希不进入Session属性。

| 表 | 字段/约束与索引 |
| --- | --- |
| identity_user | id BIGINT自增主键；username VARCHAR(32) ascii_bin唯一；display_name VARCHAR(64)（应用限制32 Unicode字符）；password_hash VARCHAR(255)；enabled BOOLEAN默认true；security_version INT默认0；created_at/updated_at DATETIME(6) UTC |
| identity_user_role | (user_id,role)复合主键；role VARCHAR(16)，CHECK USER/ADMIN；外键user_id→identity_user.id，级联删除角色链接 |
| SPRING_SESSION | Spring Session4.0.5官方MySQL结构；PRIMARY_ID主键，SESSION_ID唯一，EXPIRY_TIME/PRINCIPAL_NAME索引；creation/lastAccess/idle等毫秒/秒字段沿用官方语义 |
| SPRING_SESSION_ATTRIBUTES | (SESSION_PRIMARY_ID,ATTRIBUTE_NAME)主键；ATTRIBUTE_BYTES BLOB；外键→SPRING_SESSION.PRIMARY_ID级联删除 |

Spring Session表使用官方大写名称；Windows MySQL可能返回小写，验收按大小写规范化比较。用户与会话通过principal用户名形成可查询索引，不加会话→用户外键：匿名CSRF会话不存在用户，失效及删除由Session生命周期管理。

注册事务包含用户及USER角色；bootstrap同事务增加ADMIN，已有用户名绝不覆盖。昵称更新、改密按当前principal id与security_version写入；改密递增版本，防止并发覆盖并使旧会话失效。无删除账户或业务记录清理接口。

只在专用测试库跑真实集成，fixture逐个随机用户名记录及删除，不清库。迁移后每库预期6表、成功历史V1/V2各1条；部署目标MySQL8.4仍在后续部署阶段验证。

## 阶段1认证补全：V3（2026-10-15）

V3__verified_email.sql追加迁移，不改V1/V2，保留既有账号与角色。迁移后为8张表（含Flyway历史），成功历史V1/V2/V3各一条。

| 结构 | 新增约束与用途 |
| --- | --- |
| identity_user.email | VARCHAR(254)，ASCII二进制排序，规范化小写，允许NULL，唯一；既有账号保留NULL |
| identity_user.email_verified_at | DATETIME(6) UTC，只有验证证明成功时写入，不能由客户端指定 |
| identity_email_challenge | UUID主键，REGISTER/RESET/BIND用途CHECK；邮箱SHA-256摘要、可空user_id及security_version、验证码HMAC、到期/冷却/消费/禁用时间及错误次数；无明文邮箱/验证码；user_id外键级联删除；用途邮箱与到期索引 |
| identity_auth_rate | scope+subject_hash+window_seconds组合主键，滚动窗口毫秒事件及到期索引；仅记录限流所需摘要和时间，不存密码/验证码；事务行锁串行检查与记账 |

验证码消费与注册、首次邮箱绑定、密码重置条件写入保持同事务，失败回滚；失败计次和限流独立提交以防绕过。重置匹配当前已验证邮箱、enabled及security_version，成功递增版本；绑定以email IS NULL及安全版本条件保护，不能通过并发请求覆盖另一邮箱。唯一键处理邮箱归属竞争。

HMAC密钥只在受控本地/环境配置，重启保持稳定。过期验证码及限流记录由应用定时按到期索引清理，绝不清理用户或学习业务记录。集成测试仅删除具名fixture及其摘要对应记录。

## 阶段2增量迁移（开发/测试均已至V9）

V5建立reading_category（5个固定主题）、reading_article及reading_article_version。版本含正文JSON、内容摘要、完整来源和分类快照；article的draft/published指针以(article_id,version_id)复合FK约束，保存不覆盖已发布快照，发布/下架同文章行锁。
V6 reading_progress以(user_id,article_version_id)唯一，锁版本、句键/UTF-16位置、显式完成时间和本人历史索引；用户/文章版本均有外键，无级联删业务历史。
V7 dictionary_entry的lemma唯一；dictionary_form以(form,entry_id)唯一，支持多候选。打包本地数据按manifest版本/摘要事务载入，system_metadata独立锁key串行化，同版本改内容拒绝，升级保留词条ID与旧映射。
V8 user_word以(user_id,entry_id)唯一并软取消；user_word_source以(word_id,article_version_id,sentence_key,start_offset,end_offset)唯一，保留服务端原句/词形快照，指向原文章版本，无级联删除。V8中间验收时测试库16表；最终开发/测试均V9共17表，切换证据见阶段2验收。
V9 reading_import_batch以client_import_id唯一，保存创建管理员、规范化payload SHA-256和文章ID数组；批次与草稿同一事务，重复ID只能匹配原管理员和摘要。测试库现17表且V1—V9已做重复迁移校验；开发库已完成V4→V9增量升级，原数据保留。
# 2026-10-17 近义词与短语增量

音标补缺增量仅在查词响应中将原空值补为已有ECDICT音标；不会更新dictionary_entry、source_note、ID、收藏或来源。新增短语精确查询同样只读JAR资源，不增加表或迁移。

数据仅随JAR资源读取，现有candidate响应附加，不新增表、不改变V10/18表或用户收藏来源。公共词典增强不包含用户状态；collection仍按当前认证身份实时组装。ECDICT短语复用既有显式本地开关。离线数据、许可、hash及生成规则见`content/dictionary/lexical-extras/NOTICE.md`。


## 2026-10-18 写作阶段B：V12

新增 `user_writing_favorite` 与 `user_writing_imitation`，共21张表（V11为19张）。两表均引用现有 identity_user / reading_writing_material，唯一(user_id,material_id)，按用户和更新时间索引；无级联删除历史数据。

收藏采用active软取消及内容快照；仿写包含text_content(2000)、material_revision、content_snapshot_json、lock_version。首次保存固定当时素材快照，修改只改文本/锁版本，不替换旧依据。数据库唯一键及用户/素材行锁防重复首存，expectedLockVersion防并发覆盖。写操作使用锁定的最新文章/进度权限，避免快照读绕过刚发生的下架。迁移文件为 `backend/src/main/resources/db/migration/V12__personal_writing.sql`；未改写V1—V11，未删已有文章/用户/进度。
## 2026-10-18 阅读题组C：V13

新增reading_quiz / reading_quiz_version / reading_question，共24表。reading_quiz以(article_id,article_version_id)唯一并联合外键绑定现有文章版本，draft_json有界校验、status、revision与lock_version；published_version_id通过(id,published_version_id)联合外键保证属于同题组。作者引用现有用户。

每次发布插入新的reading_quiz_version，(quiz_id,version_no)唯一；题目行引用冻结版本，(quiz_version_id,question_key)唯一，四选项JSON、类型、题干、正确选项、解析及sort_order。题目类型/正确选项有CHECK。保存草稿不改公开版；发布/下架行锁与版本检查，首次创建先锁文章，防并发重复题组。没有物理删除公开历史的接口；修改移除题只影响下次发布。

游客状态不建个人学习表，仅复用Redis项目专用namespace下的reading-guest前缀/600秒TTL；Lua原子限制顺序和重复提交。阶段D可基于不可变quiz_version/question ID保存个人轮次，E再新增打卡表；本次没有预建未使用的成绩或打卡表。迁移仅新增V13，不改V1—V12、不清库。
## 2026-10-18 阅读练习D：V14

新增user_reading_attempt / user_reading_answer / user_reading_attempt_request，开发/测试V14共27表。复用现有用户、文章版本和冻结题组/题目；没有另一套文章或认证体系。

轮次含user/quiz/quiz_version/article/article_version、attempt_no、首个请求ID、status、correct_count/total_count、lock_version、started_at/completed_at。生成列active_quiz_id只在进行中取quiz_id，唯一(user_id,active_quiz_id)限制一组一个活动轮次；唯一(user_id,quiz_id,attempt_no)及首个请求唯一约束。联合外键确保题组/文章/版本归属一致，状态/成绩范围/完成时间CHECK。

答案以(attempt_id,question_id)主键，含quiz_version_id，分别联合FK到轮次版本与题目版本，保存draft_choice、submitted_choice、is_correct、submitted_at。提交前后三字段一致性和A—D有CHECK，提交后不修改。最后一题与轮次完成同事务。

请求映射表以(user_id,client_request_id)主键，联合FK到本人轮次；除首次创建，恢复请求也映射到既有轮次，保证跨完成状态重试仍幂等。记录不清除或级联删除历史。新迁移仅V14，为既有quiz/question增加归属唯一索引；不改V1—V13。

开始按文章→题组→用户→轮次顺序锁定，当前读取决定请求映射/活动轮次/轮次序号；提交锁轮次及答案，按冻结quiz_version判分。事务旧快照与新发布可能交叠，变更路径读取冻结题目使用FOR SHARE、答案使用当前锁读；普通只读GET维持一致快照。专用库确定性RR测试与并发HTTP测试覆盖这些边界。

## V15：阅读自动打卡（2026-10-18）

在V14基础上增量至29表，原数据保留。迁移`V15__reading_checkin.sql`：

- `reading_article_qualification`：id/user_id/article_id/article_version_id/progress_id/attempt_id/qualified_date/qualified_at。UNIQUE(user_id,article_id)，保留首次来源、版本、日期和UTC时间，防同篇重做/重发刷天数。联合外键引用同一用户、文章及版本的reading_progress与user_reading_attempt。两父表补对应联合唯一索引。
- `reading_daily_checkin`：PRIMARY KEY(user_id,checkin_date)，qualification_id唯一；联合归属外键引用本人资格。保留当日首篇来源；同日后续达成文章仍存第一表，未来不能重复消费。

日期为服务端上海日历DATE，时间戳UTC TIMESTAMP(6)。不提供前端指定日期写接口。两个完成事务先锁identity_user本人行，再取进度/轮次，所有资格读取当前数据；两表原子upsert保留原记录，避免RR空范围锁后插入引起不同用户间隙锁升级死锁。只在真实完成状态转换检查，GET不写。唯一约束、外键和服务校验共同防重/隔离，不以浏览器状态代替。
# 2026-10-21 四级选词填空（V21）

新增迁移 `V21__practice_cloze.sql`，不改V13/V19/V20。

- `practice_set` 增加允许 CET4/CLOZE；同文章/考试最多一个专项题组，防止不同题型共改同一个试题草稿。
- `practice_set_version` 新增 `reading_type` 和 `word_bank_json`，已有版本补为DETAILED；发布原文/题目快照机制沿用。
- `reading_question` 允许 CLOZE，正确答案扩展至A–O；旧阅读业务仍由既有规则限制题型与A–D。
- `practice_answer` 草稿/提交选择允许A–O；服务按冻结题型收窄可选值并校验共享词唯一使用。仍由事务、用户/轮次锁和版本号保护批量保存与提交。
- 草稿词库保存在现有 `draft_metadata_json` 的 `wordBank`，发布后单独冻结到版本；不复制15份选项，不增加独立题库表。

实际迁移与回归结果见选词填空验收文档；结构描述不代表已在日常开发库执行。



# 2026-10-21 四级长篇匹配（V22）

新增 `V22__practice_match.sql`，不改已执行迁移。`practice_set` / `practice_set_version` 允许CET4/MATCH；reading_question允许MATCH及A–Z答案，practice_answer允许A–Z选择。题型范围由原业务规则继续分别限制，阅读模块不因此接受MATCH。

`practice_answer.flagged BOOLEAN NOT NULL DEFAULT FALSE` 保存每轮每题待检查标记。旧数据默认false，省略字段请求保留旧值；更新和答案/轮次乐观锁在同一事务。该标记不计分、不影响统计或校对状态。

段落字母存于不可变reading_article_version正文，各段起始 `[A]` 等；MATCH不新增独立题库/段落表。题组版本冻结原文/试题，旧轮次仍引用原版本。MATCH共享词库固定空数组，原CLOZE词库哈希/唯一选词约束保持。专用材料仍为PRACTICE，不出现在阅读目录。


# 2026-10-21 整套听力（V23、V24）

新增V23__practice_listening.sql与V24__practice_listening_answer_order.sql，不改已执行迁移。V23新增practice_audio_asset，保存管理员归属、随机不可变存储键、原文件名、大小和SHA256；文件位于项目专用忽略目录。备份应同时保留数据库和音频文件，不提供永久删除资产功能。

practice_set新增section默认READING，允许CET4/LISTENING；发布版增加listening_json和audio_asset_id外键，听力必须绑定资产。草稿沿用draft_metadata_json，发布时冻结分组、音频、原文与题目版本。轮次增加playback_json，答案/标记/播放状态同一乐观锁事务；仅听力上限50题，旧阅读规则不扩大。V24将practice_answer.ordinal约束扩展到0–49，修复12题集成时发现的旧0–9约束；判分和归属约束仍由服务执行。

测试使用english_hero_test、Redis6381和backend/target/listening-it-audio；开发使用既有项目库、Redis6380及默认.tool-cache/practice-audio/dev。媒体票据只存Redis且有TTL，不入业务表。


# 2026-10-22 写作/翻译（V25）

新增V25__practice_subjective.sql，旧迁移不改。共用practice_set/版本增加subjective_json和WRITING/TRANSLATION，主观才允许quiz为空，客观仍强制quiz；类型条件CHECK、独立article/version复合FK保留归属。主观题材料仅PRACTICE，参考只在主观JSON，不发布到阅读精选。

practice_subjective_attempt保存本人/题组/冻结版本、parent_attempt_id、answer_text、word_count、IN_PROGRESS/COMPLETED、乐观锁和时间；每人每组最多一个进行中稿，父稿复合FK保证本人归属，完成态词数>0且有提交时间，无分数字段。practice_subjective_request按(user_id,request_id)唯一，存START/SUBMIT/REVISE+请求摘要及本人attempt复合FK。命令使用本人行锁串行，草稿和提交持有轮次行锁；相同命令返回原结果，不同命令复用ID为409。

测试库english_hero_test/Redis6381与开发库english_hero_dev/Redis6380均本次迁移到V25，无清库；开发原5题组与听力冻结版本完整响应保持。新增原创草稿6/7未发布，无学员作答造数。

## V27–V28 整卷错题（2026-10-25）

新增 `practice_paper_mistake`，主键 `(user_id,set_version_id,question_id)`；同版题跨卷去重，不同版本独立。保留首次错误的本人原卷、槽位和原题号；最近完成来源为 paper/review 二选一，状态 PENDING/RESOLVED、错误次数和最近选择用于复习投影。复合外键约束本人来源、题组发布版及对应冻结题目。

`practice_attempt` 增加可空 `source_paper_attempt_id/source_paper_slot`，与既有单项 `source_attempt_id` 互斥，整卷来源仅允许 MISTAKES 客观槽。V27已经在测试库执行后发现 MySQL CHECK 的 NULL/UNKNOWN 边界，因此保留其checksum，新增V28显式要求来源槽非空。原活动唯一键继续生效，冲突由业务服务提示。

首次整卷提交与台账归集同事务，用户写锁串行化完成顺序；重练评分按数据库可信来源分流，原单项台账保持。V27使用冻结 `result_json` 和组件题序回填已完成历史，以完成时间/ID/组件顺序确定最新状态，忽略未完成及主观题；重放只补缺失身份，不覆盖后续订正。不会改写原响应/结果JSON或冻结版本。台账是派生数据，不是另一份整卷成绩。迁移与数据保持验证见本日整卷错题验收。

## V26 整卷（2026-10-23）

新增practice_paper（草稿及发布指针/校对/锁）、practice_paper_version（冻结元数据）、practice_paper_component（发布版7槽及题组版本）、practice_paper_attempt（本人整卷状态）、practice_paper_response（七槽草稿与冻结结果）、practice_paper_request（开始/提交幂等）。现有单项表及数据不改。

纸卷发布指针复合外键必须属于本卷；component按发布版+槽唯一，题组版本不能重复；attempt按本人+进行中paper唯一，复合外键约束整卷版本；response引用本轮整卷版本及组成槽，request按本人+操作+UUID唯一并引用本人轮次。状态/完成时间及槽枚举有CHECK，具体题型/题数/答案约束由事务服务校验。当前专用test/dev均成功V26，测试重复迁移0次，表总数53；最初失败重试说明见整卷验收，不改写先前成功迁移。

## 2026-10-26 主观题AI反馈（V29，实施中）

新增practice_subjective_review（本人冻结主观来源、租约状态、结果及使用量）、practice_subjective_review_request（用户UUID幂等映射）、practice_ai_daily_budget（按上海日期的微美元费用预留/结算；1970-01-01行仅作短领取锁，余额0）。单项/整卷两个可空来源列CHECK恰好一个；联合用户/来源外键以及整卷slot响应外键限制归属。既有V1–V28不改。

只对english_hero_test完成迁移与集成后才切换开发。V29初次因与V27同名FK创建失败，语句未建任何新表；已验证专用库无部分结构后仅移除失败V29元数据，使用ai_review_前缀重新执行成功。未清空业务表、未修改成功迁移。最终部署/验证状态见本轮验收记录。
2026-10-26 AI费用补充：复用V29已有input_tokens、output_tokens、settled_microusd及日预算表，无新增列或迁移。失败且有可信用量也持久tokens和结算额；明确未生成的拒绝结算0，未知仍保留预留。budgetStatus/budgetAmountMicrocny为读取时派生字段，不重算旧未知行。

## 2026-10-27 留言墙 V30

新增 `V30__message_wall.sql`：wall_message（共享坐标/类型/状态/version）、wall_comment、wall_like（用户与帖子唯一）、wall_report（同用户同目标待处理唯一）、wall_moderation_log（前后内容/作者原因/内部备注）、wall_notification（接收者/审计唯一/已读）、wall_request（用户+UUID幂等结果）、wall_user_quota（北京日配额）、wall_layout_state（单行布局锁）。使用外键、状态约束和软删除，不改旧迁移、不清库。

真实专用测试库已执行V30并验证事务回滚/并发；开发库应用状态以当日留言墙验收记录为准。既有56张业务表部署前后CHECKSUM核对，wall新表与Flyway记录不纳入旧业务指纹。

2026-10-27 15:43：开发库V30已由启动Flyway成功应用，56张旧业务表前后指纹一致。

## 2026-10-27 用户管理 V31

新增 `V31__admin_users.sql`：admin_user_command（actor+request_id唯一、目标、载荷SHA256、无凭据结果、时间）；admin_user_audit（command唯一FK、actor/target FK、前后enabled与安全版本、管理原因、时间，约束状态改变且版本+1）。既有identity_user.enabled/security_version参与同事务；不修改旧迁移/旧凭据字段，不创建默认账号。当前已在专用测试库应用V31，开发库应用状态见正式验收追加结果。


2026-10-27用户管理交付：开发库V31已应用；原65张业务表部署前后CHECKSUM保持一致，未修改真实用户状态。

## 2026-10-28 阅读选区翻译 V32

reading_selection_translation保存本人、文章版本、标准化选文scope_hash、状态/原文/纯文本译文、模型/可信用量/预留结算、lease与时间；reading_selection_translation_request按user+UUID唯一并外键约束本人任务，源文章/版本FK保留。数据库缓存按用户/不可变版本/选文复用成功任务。预算继续使用practice_ai_daily_budget，不另起一元额度；1970-01-02零值哨兵行串行全局翻译claim（与原批改1970-01-01分开）。V32已在测试库及本地开发库应用，无修改旧迁移。

## 阅读选区语法 V33

新增V33__reading_selection_grammar.sql，原任务表增加mode VARCHAR(16)默认TRANSLATION、GRAMMAR枚举检查，以及可空analysis_json JSON。翻译译文仍translated_text；语法结果写analysis_json，translation为空。旧翻译scope_hash与请求hash算法保持，语法以GRAMMAR前缀隔离。共用原任务/回执及费用表，无清库或改写旧迁移。测试与开发应用状态以本轮验收为准。

## 百度选区翻译 V34

V34__baidu_selection_translation.sql新增reading_baidu_monthly_quota（月首日期主键、非负used_characters），原任务表新增source_characters（可空1–1000）和quota_month（可空）。供应商使用既有model字段baidu-general标识，不改变旧行供应商。百度任务不使用AI金额表，独立预占/释放月字符；旧DeepSeek任务数据保持。应用状态与验收见当日百度切换记录。
