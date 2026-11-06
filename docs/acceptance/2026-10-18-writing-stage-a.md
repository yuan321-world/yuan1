# 写作积累阶段A验收与交接

用户确认最终原型并要求执行，本轮交付A：管理端录入/修改/发布/下架/归档→指定文章版本公开摘要与完整解析。B收藏/仿写、C/D练习、E打卡未实施，不将原型模拟用于生产。

## 实现与数据

新增V11 reading_writing_material，article_id/article_version_id联合外键、作者外键；草稿JSON与发布快照JSON分离，status/revision/lock_version。保存不改变读者看到的内容；发布原子替换，归档不物理删除。

reading/writing模块复用ReadingService.version、ADMIN和现有事务/结构化错误。公开DTO无草稿/管理锁；所有管理写操作保留CSRF、服务端管理员身份和行锁/版本比较。版本不匹配404、并发409、输入字段有界；每版本最多50活跃素材，创建锁文章行串行计数。

管理入口：文章编辑→管理写作素材，可选择当前发布版/草稿版。前端新增API、AdminArticleWritingView及ArticleWritingPanel；单份组件桌面置于本篇词汇下，手机Teleport至全文翻译后折叠，完整解析为原生dialog。原文DOM、工具栏、查词、翻译、收藏、进度与相邻文章均保留；没有新增已删除的“阅读理解↓”工具栏链接。

本轮没有创建或自动发布开发库素材/文章，没有伪造学习记录。字段错误、局部重试、未保存保护和状态变更确认沿用项目规范。

## 验证

Maven实际Java21.0.12.1，先加载项目工具/Java脚本。新增WritingMaterialIT在旧接口POST404/预期201时失败；初次PowerShell点号参数解析失败已改为带引号`'-Dit.test=WritingMaterialIT'`取得有效RED。浏览器旧版缺素材模块失败。日志`.tool-cache/writing-a-red.log`及`writing-a-ui-red.log`。

- backend `./mvnw.cmd verify -Pmysql-it`退出0：58单元/MVC、56真实专用MySQL/Redis集成。覆盖管理员CRUD/发布快照、USER403、草稿不可见、版本错配/旧版本人历史、并发409、归档与字段边界；SchemaMigrationIT断言新增表，19表/V1—V11。日志`writing-a-full-backend.log`。
- `pnpm --dir frontend build`和`test:unit`退出0：类型/构建与60单元。最终完整`test:e2e`194通过，6真实账号/邮件夹具条件跳过；10项本轮桌面/手机测试包含管理录入/状态切换、公共详情/局部失败、工具栏保留、冲突恢复和身份切换。日志`writing-a-build.log`、`writing-a-front-unit.log`、`writing-a-browser-final.log`、`writing-a-final-full-browser.log`。浏览器用例为Mock，不冒充真实管理员浏览器发信登录。
- Reviewer发现P2两项：冲突缺少重载入口、身份切换历史素材残留。已加结构化冲突重载/未保存确认；身份事件立即abort/清空/关闭详情并随身份重验。Reviewer复核已解决。随后为选择/重载确认增加代次/身份检查，最终完整回归通过。
- 开发候选8082由Flyway新增V11，旧数据保留；匿名真实列表200空数组。核验原8080进程路径后切换新包。
- `node .tool-cache/check-writing-a-live.cjs`退出0，真实1440/320px素材空状态、单份响应式模块、Aa/重点词/生词保留、管理401，零游客阅读/词汇写入、溢出和pageerror。实际查看1440全页截图确认布局。没有在开发库用真实管理员写入测试素材。

## 运行与下一步

8080固定包`.tool-cache/writing-a/english-hero-reading.jar`，SHA256 `84E6A4F5E8115EF75D8304031BDB3D1EEFB68824E9141434D24A09650F6F856D`，启动PID81708，bootstrap关闭、ECDICT显式本地开启。4174提供最终构建，开发6380保留；临时8082/测试6381按所属进程核验后关闭。开发/测试均V11/19表。

管理员可为文章9或10的对应版本录入、校对、发布素材，学习端即可看到；无内容时空状态正常。人工语言/视觉验收待反馈。下一小阶段B为本人收藏/取消、个人素材库与仿写，之后C—E；没有宣称整套词汇复习、专项训练或AI阶段完成。无Git提交或无关工作清理。
