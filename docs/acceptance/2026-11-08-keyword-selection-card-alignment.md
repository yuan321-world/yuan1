# 精选重点词与文章卡片对齐

2026-11-08。用户先确认筛词方案，随后明确取消全部匹配词折叠入口，仅展示10—15个、长文最多20个；同时要求文章卡片等宽等高。本记录以后一条指令为准。

## 修改

- `ReadingKeywordRanker`：当前词书/个人学习候选仍按正文完整词与已知词形匹配、同词条去重；先排除常用功能词和本人全局标熟练词，再按未学/当前待复习优先，结合英文标题命中和重复频次排序，同分保持原文顺序。≤500词最多10个，501—1000最多15个，>1000最多20个，候选不足不凑数。
- `ReadingWordbookKeywords`：只读按user_id及候选entry_id批量查询本人stage/active/due_at/proficient；不创建或更新学习记录，不修改调度。`ReadingToolsService`游客文章编辑候选同样限量。
- `ReadingRail`：展示“精选重点词”；正文只高亮精选列表，保留本篇收藏生词。没有全部匹配列表或折叠区，也没有新增allKeywords接口字段。前一轮已修复的刷新保留内容/滚动逻辑保持。
- `reading-library.css`：网格stretch、自动行等高，卡片height100%，footer自动贴底；不以标题文字长度决定单张外框高度，长标题仍按原两行上限展示。书签、进度、阅读入口保留。

## 验证

- 排序测试先RED（缺少Ranker），实现后3单元通过；文章卡片测试先RED（长标题卡片高度不一致），修复后通过。
- Java21.0.12.1 + Maven3.9.9：完整134单元/MVC和12项专用MySQL集成通过，无失败/跳过，退出0。覆盖共享熟练状态、跨账号隔离、读取前后stage/due/revision不变；同时复核tools及全文翻译。日志 `.tool-cache/keyword-selection-backend.log`。沿用项目缓存，当前mvnw启动器故障使用同版本直接Maven。
- 前端205单元通过，退出0；类型检查/生产构建通过，退出0（既有大词典包体积提醒保留）。日志 `.tool-cache/keyword-selection-{frontend-unit,build}.log`。
- 34项桌面/手机Mock回归通过，退出0：精选高亮/无全部展开、刷新取消/失败重试稳定、长短标题等高、卡片功能/收藏、字体/词书刷新和全文翻译竞态。日志 `.tool-cache/keyword-selection-e2e.log`。
- 独立只读审查未发现本轮P1/P2，未替代测试或用户视觉确认。
- 8080经旧进程身份/JAR哈希核对更新为 `english-hero-keyword-selection-20261108.jar`，启动助手已同步；4174服务/database UP，无新数据库迁移。
- 实际匿名浏览器读取当前6篇文章，1440px下6卡宽350.656px/高259.547px；320px下宽288px/高249.859px。两种视口所有卡片宽/高差<1px、无横向溢出。截图 `.tool-cache/article-cards-equal-live-{1440,320}.png`。
- 实际GET当前文章8/8 tools确认精选数≤10、无allKeywords字段、原全文译文仍available。没有代用户登录/改变当前词书、收藏、熟练状态或阅读进度；未调用新的翻译/AI服务。

当前仍为阅读收尾，不自动进入下一阶段，也未推送GitHub。筛词为可解释的基础排序，未宣称已做语义级专业标注。
