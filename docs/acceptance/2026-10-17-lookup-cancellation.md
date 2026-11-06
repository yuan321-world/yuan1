# 查词浮窗取消收藏验收

日期：2026-10-17。

## 后续确认修订：移除来源按钮

用户确认收藏自动记录当前句子、已收藏仅保留取消入口。已删除ReadingDetailView的“记录此处来源”按钮和collectedHere状态，并移除多按钮间距样式；原POST收藏/恢复已保存版本/句子/UTF-16偏移来源，后端无须修改。打开或查询已收藏词不会隐式发POST，笔记本继续展示既有来源；下文保留来源按钮是上一轮历史行为。

验证：旧构建新断言（重新打开已收藏词不显示来源按钮）桌面/手机两项先失败，.tool-cache/lookup-simple-red.log。pnpm --dir frontend build退出0（含类型检查），.tool-cache/lookup-simple-build.log。设置E2E_EXTERNAL_SERVER=1及E2E_PORT=4174后，pnpm --dir frontend test:e2e reading-detail-upgrade.spec.ts notebook.spec.ts reading-lookup.spec.ts reading-enhancement.spec.ts共40项Mock浏览器通过，退出0，.tool-cache/lookup-simple-green.log。笔记本测试断言收藏请求包含句子/位置且来源可查看，详情测试断言重新打开无冗余按钮、取消/恢复正常。仅前端与文档，4174提供最新构建，没有真实账号写入或新的后端验收。

根因：浮窗仅接POST收藏；成功后collectedHere使按钮禁用为“已收藏”，已收藏词原本只能记录来源，未暴露既有DELETE操作。

## 本轮行为

- ReadingDetailView复用cancelWord：已收藏时主按钮为“取消收藏”，复用笔记本ElMessageBox确认；取消后为“恢复收藏”，恢复复用原collectWord。
- 服务端成功后才更新候选词collection.active和刷新本篇词汇；取消失败保留原状态，在该词局部显示错误，可重试；请求中禁用重复提交。
- 保留“记录此处来源”作为独立按钮，同一次保存成功后隐藏避免重复记录；取消不删除阅读来源，恢复沿用既有词条ID。
- cancelWord增加可选current回调，与原collectWord一样在CSRF准备完成、真正发送前检查选区/文章上下文；原笔记本调用保持兼容，身份隔离继续由原认证传输及服务端校验承担。
- WordLookupPanel在确认弹窗处理键盘时不抢Escape/Tab；ReadingRail在查词层存在时不响应Escape关闭侧栏，确认取消与查词关闭保持独立。

文件：frontend/src/views/ReadingDetailView.vue、api/vocabulary.ts、components/reading/WordLookupPanel.vue及ReadingRail.vue、styles/reading-detail.css；对应详情和笔记本浏览器测试。后端、数据库结构、API路径未变。

## 验证

根目录加载scripts/use-workspace-tools.ps1；浏览器设置E2E_EXTERNAL_SERVER=1、E2E_PORT=4174。

- RED：新lookup can cancel测试旧构建桌面/手机均因缺少取消按钮失败；日志.tool-cache/lookup-cancel-red.log。
- pnpm --dir frontend build：vue-tsc/tsc/Vite退出0；.tool-cache/lookup-cancel-build.log。
- pnpm --dir frontend test:e2e reading-detail-upgrade.spec.ts notebook.spec.ts reading-lookup.spec.ts reading-enhancement.spec.ts：40项Mock桌面/320px通过，退出0；.tool-cache/lookup-cancel-green.log。
- 新测试覆盖刚收藏可取消、Escape放弃确认且零DELETE、503失败不撤销状态、重试成功计数归零、恢复计数回一、重新打开从接口读取已收藏状态。原笔记本来源记录与确认取消回归通过。新增重新打开断言发现Escape误关闭侧栏，已修复并回归。

本轮未执行后端测试或写入真实账号收藏，不能将Mock结果当作新的真实数据库验收。复用既有取消接口的用户归属检查与软取消语义。4174最新构建保持运行，无自动提交。
