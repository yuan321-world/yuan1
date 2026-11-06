# 2026-10-21 整套听力限定独立审查

范围：对照已确认的听力设计与集中实施计划，审查本轮音频资产/授权/Range、发布冻结、进行中隐私、阅读兼容及前端媒体与保存队列。未修改产品代码，未启动子代理，未运行广泛测试或读取整个仓库差异；保留工作区已有修改。

## 两项修复限定复核

本次仅复核下列原有两项P2，不扩大审查范围，不重跑测试。两项核心代码修复均可关闭；本次没有发现这两条路径仍遗留的P0–P2。

- **保存队列：已关闭。** `practice-attempt.ts:18,21–24,28` 的定时保存、选项保存和题号导航调用 `flush(false)`，媒体单独变脏时安排后续5秒批次，不立即递归追赶；显式最终保存仍可排空本地变化。`PracticeListeningPlayer.vue:27` 暴露 pause，`PracticeAttemptView.vue:23–25` 在交卷、退出与路由离开前暂停并同步记录当前位置，阻止播放继续推进而阻塞最终保存。新增 `practice-attempt.spec.ts:75` 对每次响应期间继续更新媒体的情况断言只写一次且保留未保存状态，针对原缺陷有效。主代理报告该测试RED后GREEN、相关13项及全部106项单测通过；本复核未重跑这些命令。
- **公开原文复用：已关闭核心泄漏路径。** `PracticeAdminService.java:21,23,50–51` 分别在创建、保存与发布检查要求听力文章为 `practiceOnly()`；`PracticeImportService.java:13` 在导入校验同样拒绝非专项材料，阅读分支不受此条件影响。编辑器 `AdminPracticeEditorView.vue:44` 在新建切入听力时清除阅读文章引用并转为独立原文。复核后主代理已给文章选择框加 :disabled="isListening"；最终构建和4项听力浏览器复验通过。

初次反向测试证据说明（后续已补齐）：`PracticeListeningIT.java:75–77` 的导入用例已去除不属于ImportInput的字段；创建/保存用例复核时仍从MaterialInput复制body/requestId，可能因未知字段先返回400。已反馈主代理清理这两个字段并断言articleId错误，避免把反序列化失败当成专用原文校验通过。代码关闭判断来自上述明确服务端条件，不依赖这两个400断言。

已读取修复后 `listening-final-retest.log`：1项PracticeListeningIT、0失败/错误、BUILD SUCCESS；读取 `frontend-final-e2e.log`：18项通过；读取 `frontend-final-build.log`：构建成功。均为主代理运行记录，本复核没有重新执行测试或浏览器操作。

## 初审发现（保留历史）

1. **P2：持续播放可使保存队列一直无法结束。** 发现位置：`frontend/src/stores/practice-attempt.ts:22–24`，关联 `PracticeListeningPlayer.vue:15–16` 与 `PracticeAttemptView.vue` 的退出/交卷入口。播放器每次 `timeupdate` 都递增媒体修订号；当单次保存耗时超过事件间隔，响应回来时播放状态又变脏，`flush()` 随即递归保存。在慢连接上，约5秒合并保存退化为连续请求，等待 flush 的交卷/退出无法完成，直到用户手动暂停。独立窄复现直接在内存转译实际 store，使用真实 Vue/Pinia 与可控 API Promise：8次响应期间各推进一次播放位置，产生9次请求且 `flushSettled=false`；停止推进后才结束。建议媒体自动保存仅完成当前批次，后续媒体变动重新按5秒调度；退出/交卷前先暂停并记录位置，再等待最终保存。主代理已收到并正在修复。

2. **P2：听力可以绑定公开阅读文章，绕过配套原文的交卷前隐藏。** 发现位置：`backend/src/main/java/com/englishhero/practice/PracticeAdminService.java:21`（保存分支同类），关联 `frontend/src/views/AdminPracticeEditorView.vue:85` 与 `backend/src/main/java/com/englishhero/reading/ReadingService.java:21–24,34–37`。听力新建仅检查文章/版本存在，管理界面也仍提供已发布阅读文章复用。这样生成的听力原文仍属 READING，学员可经公开阅读接口获取，即使进行中 Attempt 本身不返回 article。方案明确要求听力配套原文属于专项专用材料。建议服务端在听力创建/保存/发布检查中要求 `articles.practiceOnly()`，导入验证同样拒绝非专项材料；听力界面仅使用专项原文录入，保留阅读复用入口。主代理已收到，修复状态待记录。

未发现其他有充分证据的 P0–P2。音频票据按本人轮次/冻结资产签发，媒体票据限定资产并有 Redis TTL；Range/HEAD、随机文件键与临时文件清理、不可变发布引用、进行中答案解析过滤及阅读题量限制在本轮检查中未发现新的实质缺陷。

## 验证证据与边界

- 本审查独立执行了上述保存队列窄复现，命令退出0；未重复运行整套后端或前端测试。
- 查阅已有 `backend-integration.log`：10项 Listening/Match/Cloze 集成，0失败/错误，BUILD SUCCESS。该记录早于本次审查反馈修复。
- 查阅已有 `real-browser-results.json`：1440/768/320px 各记录播放、拖动、暂停恢复、交卷成功及0页面错误；不等同于本审查重新操作浏览器或用户视觉确认。
- 初审时 `frontend-e2e.log` 为16通过/2失败，失败为新增管理员保存流程；后续结果见上方修复复核记录，最终定向浏览器18项通过。
- 本报告不确认真实听力内容/来源已齐，也不授权内容发布或下一阶段开发。

主代理最终补充：已去除创建/保存的body/requestId，并断言fieldErrors.articleId具体来源错误；导入断言对应字段信息。privacy-final.log显示79单测与1项听力集成通过，四宽度真实播放器通过；这是主代理执行证据，不冒充独立复核重跑。
