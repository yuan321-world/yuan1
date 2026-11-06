# 背单词正式模块实施计划

> **For agentic workers:** 使用subagent-driven-development按以下任务执行与复核。用户2026-11-02明确授权“确认，直接完成整个背单词模块”，无需再次确认是否开始或逐块继续。

**Goal:** 将已确认词汇中心原型落地为真实账号隔离的今日学习/生词本/词组本闭环，保留历史数据与既有学习入口。

**Architecture:** 保持Vue3/TypeScript/Pinia、Java21/Spring Boot/MyBatis-Plus/JdbcTemplate既有模块化单体。复用收藏与单词学习；词组独立实体、来源、学习会话/幂等命令/事件，不污染单词调度。前端真实API，不导入演示HTML/数据。

**Spec:** `docs/superpowers/specs/2026-11-02-vocabulary-system-upgrade.md`最新修订、`2026-11-02-vocabulary-notebook-integration.md`；视觉基线`docs/prototypes/vocabulary-upgrade-preview.html`。

## 全局约束

- 保留用户已有未提交修改；当前551项工作区变更包含正式功能与依赖，直接增量完成，不新建遗漏未提交代码的干净工作树，不清库/回退其他文件。
- 接口`/api/v1`，沿用账号鉴权/CSRF/归属与项目专用dev/test库，密钥/Token不输出；不接新AI服务。
- 新迁移V35单词v2、V36词组、V37学习收藏来源、V38词组调度、V39难词标记；不得改写已执行V1—V34。
- 旧flow/rule/due历史不重写；新旧客户端读取v2阶段不得误传旧0—5算法。
- 收藏、加入学习、手动免复习、暂停与到期独立；手动熟练不伪造独立通过或当日新学。
- 看词始终手动下一词；跟打连续整轮、结束集中释义；默写保留提示/辅助回队/可选自动next。

## 接口/责任边界

- learning_backend_v2：`backend/.../vocabulary/learning/*`、V35及对应测试。Command新增flowVersion/attemptId，Current新增entryId/attemptId/assisted/proficient；Settings新增autoNext/reviewScope；HINT/ERROR/PROFICIENT沿用幂等feedback协议。
- phrase_module_fullstack：独立`vocabulary.phrase`、V36、`vocabulary-phrases.ts`、`PhraseBook.vue`及独立测试；不改主视图/单词状态。
- vocabulary_frontend_full：主VocabularyView/学习组件/Pinia/API/样式、路由导航兼容及相关测试；不改WordNotebook或PhraseBook。
- root：收藏来源/V37、`VocabularyService/Mapper/Dtos/Controller`、`api/vocabulary.ts`、`WordNotebook.vue`、跨模块收藏入口、整合验证/文档/运行交付。

## 任务与验收

### A. 单词学习v2（后端与前端共同闭环）

- [x] 新迁移保留旧轮次/旧调度及独立flow/item rule快照；stage1—7/版本化固定间隔。
- [x] HINT/ERROR尝试证据、幂等重放、版本与attempt保护、辅助后回队；首次学习与独立复习区分。
- [x] 手动熟练跨书排除，可恢复原计划；当日去重、全局目标/保存额度不绕过。
- [x] 设置预设20/100仅新用户、autoNext与ALL/CURRENT持久化，冻结复习范围。
- [x] 真输入框稳定虚影/前缀、composition/粘贴/长按、重试与离页取消；旧轮次兼容。
- [x] 测试权限/并发/重复请求/错误刷新/旧stage6—7兼容/范围/配额。

### B. 生词本融合（root+前端）

- [x] 学习收藏POST `/vocabulary/words/from-learning`，由本人session/item验证entry，不改轮次/前缀；来源记录不伪造articleId。
- [x] 原有分页/多来源/定位/取消恢复保留，统一页面搜索筛选与词典详情抽屉。
- [x] 收藏与加入学习解耦，来源保留、熟练恢复；旧`/notebook`兼容入口。
- [x] 覆盖跨账号不能读取/收藏他人学习项、重复收藏不重复来源、取消不删学习。

### C. 词组本完整闭环（独立全栈任务）

- [x] 真实私有收藏、规范去重、个人释义/备注、多来源与取消恢复；来源校验可访问版本与真实选区。
- [x] 收藏/加入学习独立，保存/恢复看词组与默写练习、提示/错误/重试事件及独立调度。
- [x] 不挤占单词额度、记录/统计不混入，真实翻译/精读/练习入口可显式收藏词组。
- [x] 权限、命令幂等/并发、辅助失败不冒充独立成功、取消保留进度测试。

### D. 合并验证与交付

- [x] 检查Maven实际Java21、前端类型/构建/相关单元与双端Mock检查。
- [x] 串行运行相关真实专用库IT/迁移检查，记录退出码和跳过，避免并发Maven改写target/共享测试DB。
- [x] 独立复核，修实质问题；真实本地服务重建/更新并验收，不将构建/Mock等同数据库联调或用户视觉确认。
- [x] 同步API、decisions、progress、README和验收/交接，保留明确未完成事项；不提交/推送。

执行日志在同日期验收文档记录。若接口契约需调整，root统一协调并更新，不能用演示开关替代服务端校验。
