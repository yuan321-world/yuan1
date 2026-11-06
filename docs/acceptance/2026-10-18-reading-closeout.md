# 2026-10-18 阅读收尾验收

用户确认阅读盘点建议后实施。范围仅前端日期/恢复可见刷新、管理直达入口、落后文案，以及三条写作素材审核稿；不改后端/API/表结构/认证，不发布文章或素材，不启动阶段3业务。

## 已交付

- LearningStatsView、ReadingCheckinCard复用useReadingRefresh：每30秒只检查上海日期，日期变化且页面可见时GET；focus/visibility在可见且有账号时100ms合并。隐藏或禁用不请求；卸载清理计时器/事件。保留原组件序列、AbortController、身份代次保护，不通过GET生成打卡。
- 后台刷新期间旧周历/条件保留并标记aria-busy，避免折叠为一行造成跳动；首次无数据才显示加载，失败显示明确局部错误和重试。
- 管理文章列表增加管理题目/写作素材直达，指向已发布版本或无发布时草稿版本；原预览/编辑入口保留。平板侧栏与四按钮造成长标题溢出，已通过允许文章行/按钮换行、限制宽度修复。
- 首页和专项占位说明文章练习已经开放、独立专项仍待开发，不冒充完整专项训练；不恢复首页计划/打卡大卡。
- 为文章8/9/10各准备1条写作积累，原句、段落、正文hash及实时公开版本核对，字段长度符合WritingRules。第10篇例句标注编辑改写，第9篇They指代在审阅稿注明。待用户校对，未录入/发布。
- 更新AGENTS当前进度行、规范/决策，历史验收保留。主阶段3下一步先方案/原型，未自动实施。

## 验证

构建/TS：`pnpm --dir frontend build`退出0，reading-closeout-build-final.log；最终dist文件名以构建日志为准，不使用历史包名判断运行。前端preview4174继续读取最终dist，后端8080无需重启。

单元：`pnpm --dir frontend test:unit`退出0，16文件61项通过，reading-closeout-unit-final.log；新增覆盖hidden/disabled、事件合并、已排队卸载与定时器清理。

完整浏览器：E2E_EXTERNAL_SERVER=1/E2E_PORT=4174 `pnpm --dir frontend test:e2e`退出0，250通过、6真实账号/邮件条件不足跳过，reading-closeout-all-final.log。新增12桌面/手机场景包括跨午夜自动更新/焦点读取/零业务写入、可见恢复失败重试、后台响应挂起不折叠内容、文章资格刷新、管理直达与720px长标题、文案一致。初始10失败reading-closeout-red.log；后台布局2失败reading-closeout-layout-red.log；平板长标题1失败reading-closeout-tablet-red.log，均先红后修复。短标题最初未复现平板问题，改为现有Classroom文章长标题后复现，未因静态估算直接增加样式。

独立只读Reviewer复核刷新/素材/入口，提出平板溢出P2，修复后复核无剩余重大问题。没有重新执行Maven，因为本轮无Java/数据库修改；此前E后端结果只作历史证据。

`node .tool-cache/reading-closeout-live.cjs`退出0：真实公开首页文案正确；独立Mock管理员列表1440/874/720/320px，新入口与原入口正常、无横向溢出/pageerror，未写内容或个人学习记录。已查看720/320px截图。日期/身份浏览器测试为Mock；不冒充开发真实个人统计/管理员浏览器全链路。

## 待用户处理与下一步

写作稿：docs/content/2026-10-18-writing-materials-review.md及content/reading/writing-materials.v1.draft.json。待用户校对后再录入并发布；其余7篇文章和题组保持草稿，本轮没有上架。当前公开3篇不变。词汇学习/到期复习后续按原路线阶段3设计，不接付费翻译/AI。

未进行Git提交，保留工作区既有无关修改。正式8080、4174与开发6380保持；本轮未新开后台或测试Redis。
