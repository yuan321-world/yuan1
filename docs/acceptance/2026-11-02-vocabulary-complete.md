# 词汇中心正式模块验收

日期：2026-11-02。用户已授权按确认原型完成整个背单词模块。本次正式实现、真实数据库联调和本地运行更新均已完成；用户对正式页面的最终视觉体验仍待实际使用确认。

## 已交付范围

- 统一「今日学习 / 生词本 / 词组本」布局及导航，旧`/notebook`跳转生词本，原有收藏、来源、学习词管理、词书、计划及统计入口保留。
- 新学看词与复习看词手动下一词；新学跟打连续整轮，过程中隐藏中文与提示，结束集中展示固定词条资料。复习默写保留正确前缀，提示/错误落库，辅助完成后回队；只有干净默写受自动下一词设置控制。
- 学习中收藏不重置前缀或轮次；手动熟练跨词书免复习，不伪造学习通过，可恢复原到期安排。难词自动参考最近5次尝试中的3次错误/辅助，并支持手动覆盖及恢复自动。
- 新单词按版本化1/3/7/15/30/60/90天安排，flow与item调度版本分别冻结，旧保存轮次和历史不重写。复习目标仅参考；保存轮次按真实id及冻结来源/范围恢复。
- 生词本真实搜索、状态筛选、分页、完整词典、固定双语例句、短语、近义词及多个来源；学习收藏有独立来源，不伪造文章来源。
- 词组真实私有收藏、规范去重、个人中文释义/备注、多个来源快照、取消/恢复及独立加入练习；阅读、练习选区和词条短语提供显式收藏入口。来源重新校验本人可访问版本和真实选区。
- 词组回想/默写有独立轮次、幂等命令、提示/错误、保存/恢复与7阶段调度，不占单词额度。只有无辅助且本轮未失败的默写计独立通过。
- 学习统计增加近30天、连续天数、难词及无辅助独立复习，按上海自然日及单词日去重。旧事件不补造独立回忆证据。

## 验证结果与实际命令

以下命令退出码均为0；后端真实测试只使用专用`english_hero_test`，未调用真实AI/翻译服务。Maven实际使用Java21.0.12.1，离线缓存Maven3.9.9。

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| Maven `-o -Pmysql-it verify`，限定LearningBoundaryIT、LearningCollectionIT、LearningDashboardIT、LearningEnrollmentIT、LearningSessionIT、LearningV2IT、VocabularyInsightsIT、VocabularyStatsIT、WordbookLearningIT、SchemaMigrationIT及PhraseCollectionIT/PhrasePracticeIT/PhraseScheduleIT | 126单元、42真实库IT通过，0跳过 | `.tool-cache/vocabulary-full/backend-verify.log` |
| 最终 Maven `-o -Dtest=VocabularyStreakTest -Dit.test=PhraseCollectionIT,VocabularyBrowserVerification -Pmysql-it verify` | 2单元、5真实检查通过；含新增空轮次JSON断言及真实浏览器 | `.tool-cache/vocabulary-full/real-browser-final.log`，19:55:32完成 |
| `pnpm test:unit` | 48文件、194测试通过 | `.tool-cache/vocabulary-full/frontend-final-unit.log` |
| 相关桌面/手机Playwright Mock回归 | 36通过，22.6秒 | `.tool-cache/vocabulary-full/mock-final.log` |
| `pnpm build`（含typecheck） | 类型与生产构建通过 | `.tool-cache/vocabulary-full/frontend-final-build.log` |

上述真实库IT去重后为43项，另有1项真实浏览器流程；不得把Mock检查计为数据库联调。`VocabularyBrowserVerification`创建隔离测试账号，使用真实JWT/CSRF登录，浏览器仅把API转发到真实测试Spring服务，不替换业务响应，结束清理临时账号/记录与忽略目录下的凭据文件。

真实浏览器覆盖：旧笔记本入口、收藏/取消/加入学习、学习中再次收藏、错误前缀跨标签和刷新恢复、跟打错误回队及整轮释义、手动熟练恢复、看词手动下一词、词组规范去重/加入/回想/保存/刷新恢复/到期/取消恢复、另一账号隔离，1440与320宽度无横向溢出。已人工查看桌面和手机截图，词组空状态无连接错误提示。

独立复核及实际联调发现并修复：保存轮次来源恢复错配、旧JSON新增基本类型字段兼容、MySQL重复命令事务快照、代理Mapper直接字段访问、词组忙碌时筛选状态丢失、无保存轮次返回空body导致连接失败误报。最后一次测试曾因恢复收藏尚未确认就刷新而失败，测试已等待保存与列表更新，随后完整流程通过；未用增加任意等待掩盖业务错误。

## 本地运行与数据保留

- 4174前端已更新为本次生产构建；8080已从旧V34运行JAR切换到经过验证的本次JAR，PID42040。保留词典/参考词书/已有AI配置，不变更额度或调用供应商。
- 本地`english_hero_dev`已增量执行V35—V39，Flyway确认5项迁移成功，当前v39。原70业务表变为76表；69个原表行数保持一致，仅`vocabulary_learning_setting`按迁移为4个旧账号补充原默认设置（0→4），没有删除账号、收藏、轮次或事件。计数比较不等同逐行指纹校验，调度历史保护另有真实库测试。
- `GET http://127.0.0.1:4174/api/v1/system/status`返回service=english-hero/database=UP；`/vocabulary`返回HTTP200。未来本地启动脚本也已更新JAR路径和SHA256，避免再次启动旧业务代码。
- 验证JAR：`.tool-cache/vocabulary-full/english-hero-vocabulary-20261102.jar`；SHA256 `15CFE1B2F384BF854F7C579CB029D69CA4A178A9BDB075BF8A858427695C113F`。
- 更新前后计数保存在忽略目录`dev-before-switch.properties`/`dev-after.properties`。运行日志与截图仅为本地验收材料，不提交凭据或真实配置。

## 明确边界与下一步

- 已提交的轮次、队列、辅助证据由服务端持久化；单词尚未提交的字母草稿使用当前浏览器标签页sessionStorage，新设备或存储受限时需重输。词组草稿在保存时提交服务端。
- 词典缺项不编造例句或词根；未扩展新词书，不开放没有真实来源的考试分类。词组中文释义为用户填写，不后台偷偷调用翻译或模型。
- 原始原型保持不变，独立演示不进入Vue构建；此次没有提交、推送或部署外部站点。
- 下一步为用户在正式`/vocabulary`体验；后续新AI模块或扩展词书另行确认范围，不自动启动。

关联：[设计](../superpowers/specs/2026-11-02-vocabulary-system-upgrade.md)、[融合设计](../superpowers/specs/2026-11-02-vocabulary-notebook-integration.md)、[实施计划](../superpowers/plans/2026-11-02-vocabulary-complete.md)、[使用说明](../vocabulary-guide.md)、[API](../api.md)。
