# 查词近义词与短语验收

2026-10-17，用户确认将主要“词典来源”栏目换成近义词/短语。当前主阶段仍为阶段2增量，不进入新模块。

## 改动范围

- `scripts/prepare-lexical-extras.py`：标准库离线转换，固定原始归档hash、有限字段/数量、完整词匹配、可复现--check。
- `content/dictionary/lexical-extras/`：9,146词头、21,976词义组、25,861词头到短语引用；原始WordNet许可与来源说明随包保留。数据SHA256 `fa257051346687c0abdef52329a7b6f7e5c2bf3b90398700e9d84df4c96a4e74`。
- `DictionaryLexicalCatalog`/`DictionaryDtos`/`DictionaryService`：按实际词头和词性附加只读结果；不新增接口或改身份/收藏行为。
- `frontend/src/api/dictionary.ts`、`WordLookupPanel.vue`、`reading-detail.css`：可选兼容字段、近义词与短语折叠栏、暂无状态、来源缩至底部。原朗读/例句/收藏保持，多候选不重复整卡。
- 测试与API/数据库/进度/决策/文档入口同步。

## 验证证据

以下命令均退出0；工具路径由scripts/use-workspace-tools.ps1及use-java21.ps1设置，Maven实际Java21.0.12.1。

1. 先写后端/浏览器测试，旧实现因缺少类/栏目失败，日志`.tool-cache/lookup-lexical-red.log`与`lookup-lexical-ui-red.log`。
2. `python scripts/prepare-lexical-extras.py <固定两份官方归档> <固定ECDICT CSV> content/dictionary/lexical-extras/entries.json --check`，完整源路径/hash见来源NOTICE；派生数据和原LICENSE一致。独立Reviewer再次执行同命令退出0，无实质问题。
3. backend目录`./mvnw.cmd verify -Pmysql-it`：56单元/MVC、53专用MySQL/Redis集成，均0失败/错误。新增词性边界、未知词/关闭开关、beginning/begin隔离、公有数据不携私人collection和无进度写入检查。日志`.tool-cache/lookup-lexical-backend.log`。
4. `pnpm --dir frontend build`、`pnpm --dir frontend test:unit`、外部4174服务完整Playwright：类型/构建和60单元、180浏览器通过，6真实账号/邮件夹具条件用例跳过。日志`.tool-cache/lookup-lexical-ui-build.log`、`lookup-lexical-front-unit.log`、`lookup-lexical-front-browser.log`。Mock回归不替代真实账号联调。
5. 最终backend `./mvnw.cmd package -Dtest=DictionaryLexicalTest`：2项单元和含NOTICE/LICENSE资源最终打包通过。日志`.tool-cache/lookup-lexical-package.log`。
6. 临时8082真实接口beginning/begin分别有3/4词义组、各5短语，游客collection为null。切换8080后，`.tool-cache/check-lexical-live.cjs`在1440x1000及320x844真实文章10检查近义词、中文短语、候选切换、来源说明、英美语音按钮、Escape；零游客阅读/词汇写入、零横向溢出和pageerror。未使用模拟API或真实账号写入。
7. 实际查看320px截图，单卡/按钮/来源层级可见；用户视觉与语言校对仍待反馈，不以自动测试视为人工验收。

## 运行交接与边界

最终8080包`.tool-cache/lookup-lexical/english-hero-reading.jar`，SHA256 `4F60D2139AFC03C77CB5114742CABCD110373303D11FDC2CA386AFDA84037662`；4174前端及开发Redis6380继续运行。启动参数仍显式启用本地ECDICT且禁用bootstrap。临时8082与专用测试Redis6381按所属进程核验后关闭。

不改数据库V10/18表、既有账号/配置或文章发布状态。没有新增按次费用/外部查询服务，不保证每个词都有资料；近义词不同义项不能任意互换，ECDICT混合上游授权仍只限本轮已确认的本地开发使用。没有把推荐词或短语自动写入个人生词本。
