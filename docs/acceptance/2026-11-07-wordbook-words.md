# 词书词表正式接入验收

日期：2026-11-07。用户确认经过简化的独立词表原型后，授权接入正式模块。此记录取代原型阶段“尚未正式实施”的状态，不抹除原型历史。

## 实现范围

- 学习书架各卡片等宽“查看词表 / 切换使用”；当前书右侧禁用、左侧仍可查看。查看通过居中词表呈现，关闭返回原书架，不切换当前学习来源。
- 全本词表分页30条，全部/未学习/复习中/已熟练与真实数量，中英文按字面搜索、收藏筛选、字母/词书排序、中文显隐、加载更多。当前查看其他书时明确提示仍在学习的来源。关闭后重开重置查询。
- 单词详情复用VocabularyDetailDialog、WordDetails、PronunciationControls，包括已有本地双语例句、短语、UK/US；星标及详情收藏接已有词典收藏/取消接口，不自动加入学习。
- 熟练接口以当前用户/词条保存全局状态，词书成员校验、用户写锁、CSRF沿用。未分配的新词标熟只惰性建学习词记录；不收藏、不加入个人来源、不写学习事件。恢复保留原阶段、到期和active。状态和收藏跨词书共享。
- 新词表仅三类学习状态，不增加难词/暂停入口。现有正式模块其他位置的暂停/难词业务保留；历史active=false词按原阶段归类，且不会误显示“今天到期”。个人来源列表只列明确加入个人来源的词。
- 不新增迁移，不改数据库结构、学习调度、保存轮次或原型文件。没有调用翻译/AI付费接口。

## 修改文件

- 后端：WordbookController新增分页浏览和词书成员熟练路由；WordbookWordsService集中查询、状态统计、字面搜索、权限和熟练更新；WordbookWordsIT覆盖真实库行为。
- 前端：WordbookWords.vue承载词表/详情、异步取消和账号隔离；WordbookSelector.vue接双入口；api/vocabulary-learning.ts增加类型及接口函数。
- 测试：新增unit/wordbook-words.spec.ts及e2e/wordbook-words.spec.ts；旧书架测试适配双按钮文案，旧“预留新词阻止开始”测试改为此前已确认的无限续学预期（核对HEAD业务早已允许，不修改学习逻辑）。
- 文档：api.md、decisions.md、README.md、progress.md与本验收。此前独立原型和验收保持。

## 验证证据

使用项目工具缓存脚本和Java21脚本；Maven日志确认release21和Java21运行。未安装依赖。

| 检查 | 命令与结果 |
| --- | --- |
| 前端单元 | `pnpm --dir frontend test:unit`，退出0，52文件205测试全部通过；日志`.tool-cache/wordbook-words-unit-final.log` |
| 类型/生产构建 | `pnpm --dir frontend build`，退出0；日志`.tool-cache/wordbook-words-build.log`，已有大包体积提示未作为错误 |
| 浏览器Mock回归 | 外部4174预览，`pnpm --dir frontend test:e2e wordbook-words.spec.ts vocabulary-wordbooks-stats.spec.ts vocabulary-dashboard.spec.ts vocabulary-v2.spec.ts`，退出0，桌面1440与手机320共58项通过；日志`.tool-cache/wordbook-words-regression.log` |
| 后端单元及真实专用库 | backend下`./mvnw.cmd -o verify -Pmysql-it -Dit.test=WordbookWordsIT,WordbookLearningIT,LearningV2IT`，退出0，128单元+23IT（4新词表、5词书、14学习v2）全部通过；日志`.tool-cache/wordbook-words-backend-final.log` |
| 差异 | `git diff --check`退出0；只保留本轮相关文件及已有原型文档修改，未提交/推送 |

新IT覆盖懒读取、不改source、不建学习词/事件；本账号收藏与阶段隔离、复习/熟练计数、分页/排序/搜索（含百分号和JSON字段名不被误匹配）；真实重叠词书共享熟练、恢复不重置stage/due、不自动个人enroll；匿名/未发布书/非成员词/非法参数拒绝。

新双端Mock覆盖查看不切书、切换使用、双按钮等宽、共享状态、搜索及空结果、个人空来源、分页、隐藏中文、词条双语例句及双口音控件、收藏/熟练与恢复、失败重试、关闭重开清空查询、无横向溢出。独立审查发现筛选详情反向操作后漏行，已改为筛选下每次变更刷新，并补双端回归；独立复核未发现剩余实质问题。Mock不替代真实后端验证。

## 本地运行验证

复制验证后的JAR至忽略目录`.tool-cache/vocabulary-full/english-hero-wordbook-words-20261107.jar`，核对原8080项目Java进程后重启，保留既有本地配置/开关，更新本地启动脚本的JAR和校验哈希。4174代理健康接口验证服务正常、数据库UP。未改公开部署。

实际IAB打开正式/vocabulary→切换来源→查看考研词表，读出4801总词/4725未学习/75复习中/1熟练，已覆盖76与当前书架一致；查看secular显示原有双语例句、UK/US及熟练恢复入口。仅只读验证，未更改当前用户收藏、熟练或学习来源，未启动/提交学习轮次。截图`.tool-cache/wordbook-words-live.jpg`；双端Mock截图`wordbook-words-{desktop,mobile}.png`、`wordbook-word-detail-{desktop,mobile}.png`。

下一步为用户体验正式页面；不自动进入AI或其他模块。尚未进行本功能生产部署和用户最终视觉验收。
