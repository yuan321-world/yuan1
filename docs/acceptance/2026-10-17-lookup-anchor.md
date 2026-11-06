# 查词浮窗位置与词库覆盖排查

日期：2026-10-17。用户反馈释义远离所点击单词，且annual等常见词查不到。

## 原因与修改

- reading-library.css将.lookup-backdrop对齐右下角。WordLookupPanel此前没有接受任何原词几何位置，无法靠近点击位置。
- ReadingDetailView新增lookupAnchor，按现有sentenceKey与UTF-16偏移生成原文Range边界；不修改原文节点或查询接口。
- WordLookupPanel桌面用固定视口坐标靠近原词，下方空间不足时上移，夹紧水平位置和最大高度；查询返回/错误、面板尺寸、滚动或窗口变化时重新计算；原词已离屏时居中。600px及以下保留现有底部面板。
- Escape关闭、Tab循环、焦点返回、合成朗读、查询重试及生词收藏保持，初次焦点移入不自动滚动正文；卸载取消动画帧/观察器/监听器。

## 词典排查结论

当前content/dictionary/entries.v1.json只有319词头。LocalDictionaryProvider/DictionaryMapper只查词头及显式dictionary_form，没有通用词形还原或外部兜底。真实开发API只读检查：annual与committee为found=false；contribution与contributions为found=true。当前是覆盖不足，未伪装为网络故障，也没有补几个样例词冒充完整词库。

本轮不修改词典数据或后端；后续按已讨论的第二小阶段处理中文词库选源、授权、通用词形还原和可许可的外部查询/缓存。第三方来源与成本仍待确定，不擅自开通付费API。

## 本轮验证

根目录加载scripts/use-workspace-tools.ps1；浏览器使用E2E_EXTERNAL_SERVER=1、E2E_PORT=4174。

- RED：新word lookup stays测试旧构建桌面距离超限失败，手机通过；.tool-cache/lookup-anchor-red.log。
- pnpm --dir frontend build：vue-tsc/tsc/Vite退出0；.tool-cache/lookup-anchor-build.log。
- pnpm --dir frontend test:e2e reading-detail-upgrade.spec.ts reading-enhancement.spec.ts：34项Mock桌面/320px通过，退出0；.tool-cache/lookup-anchor-green.log。
- pnpm --dir frontend test:e2e reading-lookup.spec.ts：2项原生选择/503重试/Escape通过，退出0；.tool-cache/lookup-anchor-lookup-regression.log。
- node .tool-cache/check-lookup-anchor.cjs：真实4174/8080公开文章10，1440px鼠标点击annual，未收录提示准确、浮窗距原词不超过12px、Escape关闭，游客零阅读写入/零pageerror，退出0；.tool-cache/lookup-anchor-live.log。实现者检查真实截图。

无数据库/接口/认证配置变更，无后台测试或真实账号写入；未宣称词库覆盖问题已经解决。项目保持运行。
