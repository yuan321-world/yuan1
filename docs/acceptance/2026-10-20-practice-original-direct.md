# 原创入口直接展示题目

用户纠正原创练习仍要求选择四六级。已改为原创直接题目/题组列表，考试分类仅用于真题。

## 结果

- `/practice?source=ORIGINAL` 直接列已发布原创题组，移除考试家族、级别、考试时间和考试题型导航；搜索、主题、排序、分页保留。
- 原创请求仅限定ORIGINAL，不携带exam/readingType；题目仍按一篇原文对应一组问题组织。当前只有阅读作答能力，不宣称其他生成题型已可用。
- 我的练习、错题本、开始/继续、保存、整组交卷保留。退出保存支持回到原创首页及其筛选。真题入口仍按大学英语/考研等选择，旧题库链接继续可用。
- 管理员原创录入隐藏考试级别字段；未改后端结构或既有内容。生成、用户导入占位按钮继续禁用，用户投稿/发布未在本次接入。

## 实际验证

先加载 `scripts/use-workspace-tools.ps1`。Mock浏览器使用Vite5176、`E2E_EXTERNAL_SERVER=1`、`E2E_PORT=5176`。

| 检查 | 结果 |
| --- | --- |
| `pnpm exec playwright test practice-entry-sections --project desktop` 修复前 | 两项失败，复现原创仍有大学英语卡片。red.log。 |
| `pnpm exec playwright test practice-entry-sections practice-attempt practice-study-layout practice-history practice-admin practice-editor-recovery practice-import` | 36项通过，退出0，browser.log；新增原创入口的保存失败/重试/退出/续答/整组交卷场景，兼容旧题库入口。 |
| `pnpm exec playwright test practice-entry-sections --project desktop` 最终 | 1440/390px两项通过，final-entry.log；同时检查原创来源计数不显示跨来源真题统计。 |
| `pnpm build` | 最终构建及类型检查退出0，build-final.log。 |
| `node .tool-cache/practice-original-direct/check-live.cjs` | 无Mock，真实4174匿名浏览器1440/390px均通过；原创列表直接展示现有1组、无考试卡及考试筛选、API无exam、生成禁用；切回真题可见考试家族，无页面错误或横向溢出。 |

日志/截图在忽略目录 `.tool-cache/practice-original-direct/`；已查看真实桌面和手机截图。本次没有登录提交真实作答，也没有创建或发布题目；保存/续答/交卷由上述Mock浏览器回归验证，不记作本批真实数据库写流程。没有重跑后端或前端单测。

4174使用最终构建，已请求打开原创入口。保留其他已有修改，未安装依赖、提交或推送。用户最终视觉确认待体验。
