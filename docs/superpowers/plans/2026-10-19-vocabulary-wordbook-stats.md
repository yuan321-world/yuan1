# 阶段3：参考词书与词汇统计实施计划

执行状态：用户已于2026-10-19明确选择参考词表并集中执行，现已交付和更新本地服务。[实际验收结果](../../acceptance/2026-10-19-wordbook-stats.md)为准；以下任务保留原实施计划，不将每项计划描述冒充单独已执行用例。


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 推荐继续在当前会话集中实施，不按接口逐次要求用户确认。

**Goal:** 让用户选择一本有来源记录的四级参考词表，通过现有学习/复习流程学习，并在/stats查看真实词汇记录。

**Architecture:** 复用V16个人学习状态与命令，新增公共词书/条目及用户来源选择，按需要生成最多20词的轮次。统计读取已有反馈和学习状态，不新建另一套学习记录或阅读打卡。

**Tech Stack:** 现有Vue 3/TypeScript/Pinia、Java 21/Spring Boot/MySQL/Flyway；Python标准库离线校验，无新依赖。

**Spec:** [阶段3原方案第四、五步](../specs/2026-10-19-phase-3-vocabulary-design.md)，前一批验收见[个人学习与复习](../../acceptance/2026-10-19-vocabulary-personal-learning.md)。

## 当前状态与本批来源决策

2026-10-19用户要求继续下一批。已完成代码和固定数据核对；当前仅形成计划，未改业务代码/数据库或导入词书。用户此前允许ECDICT本地开发使用，并未指定一本已获完整授权的官方四级词书。

推荐：**大学英语四级·参考词表**，来源副标题ECDICT，限定本地开发，不称官方大纲。固定输入仍为bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b的ecdict.csv；SHA256为1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf。按原始tag中独立cet4筛选，不按词频排名冒充四级。

实测结果（`.tool-cache/audit-cet4.py`）：

- 原始cet4标签记录3849条；按当前单词格式/中文释义条件可练3846词，去重后仍3846。
- 三项B.C.、i.e.、P.M.暂不符合当前学习格式，单列为不支持，不静默丢掉后仍声称3849可练。
- 可练词中10项缺音标，保留缺项提示和现有合成朗读，不编造音标。
- 276个词不在现有10000词离线子集中；实际导入以数据库为准，仅补公共词典缺项，已有ID/中文/音标不覆盖。
- 原仓库tag字段说明：https://github.com/skywind3000/ECDICT#数据格式 。仓库有MIT声明但混合多种上游来源，保留[既有许可边界](../../../content/dictionary/ecdict-local/NOTICE.md)，不作逐词授权或商用发行结论。

备选：若用户需要与指定出版物/官方大纲逐项一致，则本批先完成统计，词书待提供并核对来源；不抓取出版社内容或擅自接付费服务。**待用户确认采用推荐参考词表路线及本计划后执行。**

## Global Constraints

- 保留全部已完成阅读、认证、个人收藏、学习和复习；原型不进入Vue构建。
- 用户/entry_id共享一份学习阶段，切书不删历史、不重置复习、不把全书标成已学。
- 继续fixed-v1-binary的1/3/7/14/30天及失败重置规则，练习交互不变。
- 新词来源可选个人或词书；复习始终涵盖本人全部已激活来源，页面明确说明。
- 不一次加载全书，不为选择词书预造数千个人学习记录；开始一批才分配最多20项。
- 词汇记录不满足阅读打卡，首页不恢复计划/打卡大卡，不增加虚假词量、时长或掌握率。
- 所有私人接口认证/CSRF/归属检查；写入锁序和幂等命令沿用V16，不接收前端统计数字。

## Review Focus

1. 收藏/词书同词：已有阶段去重，新词加入不重置到期；测试归任务2。
2. 字典补缺后的查询缓存：旧未命中缓存须因manifest变化失效；测试归任务1。
3. 下架或未知词书：不能开新词任务，已有轮次/历史/到期复习保留；测试归任务2。
4. 上海跨日/重复反馈：每日与累计计数来自唯一成功反馈，GET不补写；测试归任务3。
5. 统计部分失败/换号：词汇错误不隐藏阅读统计，旧账号迟到响应不显示；测试归任务3。

## 文件与数据边界

新增：scripts/prepare-cet4-reference.py、content/vocabulary/ecdict-cet4-local/{manifest.json,entries.json,NOTICE.md,LICENSE}，沿用固定原CSV，不重新下载不明来源。prepare脚本只生成文件或--check，不接数据库。

新增：backend/src/main/resources/db/migration/V17__vocabulary_wordbooks.sql（执行前核对最大版本）；vocabulary/learning内WordbookDtos、WordbookMapper、WordbookService、WordbookDataLoader、VocabularyStatsService。增量修改LearningDtos/Service/Controller与DictionaryMapper.manifest。pom.xml增加content/vocabulary资源，保持已有dictionary/reading资源。

新增：frontend/src/components/vocabulary/WordbookSelector.vue、VocabularyStats.vue；修改vocabulary-learning API/store、VocabularyView、LearningStatsView，复用统一反馈和useReadingRefresh，不重排阅读统计主体。

V17新增vocabulary_wordbook（code/version/title/source/notice/hash/原始数/可练数/排除报告/status）、vocabulary_wordbook_entry（book_id/entry_id/sort_order，唯一book/entry和book/order）、vocabulary_learning_source（user主键，PERSONAL/BOOK与book_id）；给vocabulary_learning_word增加personal_enrolled BOOLEAN DEFAULT TRUE，保证现有记录仍属于个人来源。无清库或改写已执行迁移。

选择词书只保存来源，未学候选按词书条目LEFT JOIN本人学习记录；开始时才INSERT IGNORE所抽词，book新行personal_enrolled=false。个人主动加入同一词时将personal_enrolled设true，原active/stage/dueAt不变。取消收藏继续不影响学习。

## 任务1：可复现词表与安全公共导入

**Interfaces:** prepare-cet4-reference.py INPUT OUTPUT [--check]；WordbookDataLoader.load(byte[] manifest,byte[] entries)只处理固定来源、通过校验的数据；默认开关english-hero.vocabulary.cet4-reference.enabled=false。

- [ ] 写失败测试CET4ReferenceDataTest：源哈希、3849原始/3846可练/3排除、10缺音标、词头唯一、稳定排序、释义格式、生成两次一致；原10000子集--check继续通过。
- [ ] 写WordbookImportIT：重复导入无重复、同版本异hash拒绝、缺词只补不覆盖旧词、非法输入整批回滚、私人学习表无新增；缓存未命中后导入补词，后续lookup必须可查。
- [ ] 实现脚本、数据与V17。导入先验完整manifest/hash，再在事务内锁现有dictionary_manifest_lock；公共字典补缺INSERT IGNORE，不调用旧ECDICT.load覆盖其全局版本/hash。使用独立dictionary_wordbook_cet4_hash纳入DictionaryMapper.manifest，发布快照有独立version/hash。启动导入只在明确本地开关启用时进行。
- [ ] 测试通过后生成覆盖报告；保留3项排除详情与10项音标缺项，不把来源数据等同已获商用授权。开发库仅在用户同意本路线后启用导入。

## 任务2：选择词书并复用原学习流程

**Interfaces:** 新增认证GET `/api/v1/vocabulary/learning/wordbooks`，返回已发布目录/总数/可练数/来源/本人已学习过数量；GET `/source`返回选择及可用性；PUT `/source`接收`{kind:PERSONAL|BOOK,bookId?}`。学习overview增量返回source摘要、sourceAvailable及原因，不删旧字段。start仍使用现有kind/mode/requestId和服务端已保存来源，不接任意用户ID。

- [ ] 写WordbookLearningIT：选择不生成学习行、20词懒分配、同词多来源只推进一次、个人加入书中旧词不重置、切书/切回个人历史保留、并发选择与start串行、活动/暂停轮次存在时禁止切来源、未知/下架书不可开新词、书下架后已开始轮次仍能继续及全部到期复习可用。
- [ ] WordbookService.select(actor,input)沿用身份验证与用户写锁。PERSONAL候选只取personal_enrolled=true；BOOK候选只取书中未通过且未被主动暂停的词；两者NEW都受当日剩余额度限制。REVIEW查询保持全局全部来源，避免切书漏复习。轮次内容始终快照；不把字典替换为另一套词库。
- [ ] 实现WordbookSelector：来源/词量/本地参考说明/缺项展示与选择，当前轮次存在时明确先完成本轮；不静默切换。词书进度标“已学习过”，不用“永久掌握”。仍保留个人候选和已加入列表，允许看到书来源已分配的词并单独暂停。
- [ ] 前端测试选择失败保持原来源、首次空书态、下架提示、手机弹层键盘关闭/焦点恢复、来源切换后认词和逐字拼写仍可用；后端真实IT验证以上接口，不以Mock替代。

## 任务3：真实词汇统计与阅读统计共存

**Interfaces:** GET `/api/v1/vocabulary/learning/stats`，认证/no-store；`{today,weekStart,days:[{date,newLearned,reviewed}],totalLearned,totalReviewPasses,due,paused,completedRounds,nextDueAt}`。newLearned为当日NEW唯一成功项，reviewed为当日REVIEW唯一成功项；totalLearned为本人stage>0去重词条（暂停仍保留历史），totalReviewPasses为历史成功复习事件，completedRounds为COMPLETED轮次。

- [ ] 写VocabularyStatsIT：新/复习不混计、失败重练不重复加已学、同命令重试无重复、跨来源同词去重、上海23:59:59至00:00切日、固定Clock本周七日、暂停保留累计、A/B隔离、游客401、GET零写入、词汇成功不改reading_daily_checkin。
- [ ] VocabularyStatsService.stats(actor)只查询现有学习/事件/轮次，Clock+Asia/Shanghai确定边界。日期窗口转UTC参数查询，不依赖MySQL已安装时区表；日期和分组由服务器确定，不接受客户端任意统计数。七天有界聚合，不加载所有事件到前端。
- [ ] VocabularyStats.vue独立请求/加载/错误/重试，放入LearningStatsView现有阅读统计之后；一侧失败不隐藏另一侧，已有成功数据后台刷新不折叠。复用现有聚焦/上海跨日刷新；身份代次与AbortController阻止迟到数据。
- [ ] 前端单元/浏览器验证真实零态、部分失败、刷新、迟到响应/换号、320/768/1440布局，既有阅读周历/打卡入口及按钮保留。

## 任务4：集中验收、文档与运行服务

- [ ] README工具命令确认Maven实际Java21，运行后端单元及专用MySQL/Redis IT。已有Redis停机恢复测试需要进程权限时工具审批，不关闭沙箱保护。
- [ ] 运行前端test:unit、build和词书/学习/统计/阅读/Notebook/认证相关Playwright，区分Mock响应与真实数据库/HTTP联调；未跑项明确列出。
- [ ] 做一次集中独立审查，修复重要问题后跑相关回归。更新docs/api.md、database.md、decisions.md、progress.md、README和本批acceptance，记录hash/总量/缺项/费用边界与实际退出码。
- [ ] 保留现有未提交工作，不自动推送/合并；启动经测试的新本地服务并验health、游客私人接口拒绝、真实空态。用户视觉确认单列，不把构建成功当视觉验收。

## 自审与执行交接

本计划不重新开发词汇学习、复习或认证，不安装AI依赖。来源筛选是真实cet4标签而非排名；缺项/音标不伪造。来源选择只影响新词，到期复习继续跨全部来源，个人/词书共享entry进度；统计只读已有事实。默认建议当前会话集中实施；用户确认本计划与本地参考词表后执行，不再按内部任务逐次暂停。
