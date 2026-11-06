# 背单词首页与退出保存独立审查

以批准的dashboard/exit-save-2为准，最终Reviewer只读检查本批后端迁移/轮次/来源服务、Vue页面/组件/store与测试，未改文件、未自行运行测试。

1. P2：多来源暂停NEW可绕过新增额度。已修正新分配同时扣除today通过和所有未完成NEW未通过预留项。savedRoundsReserveDailyNewBudgetAcrossSources先观察200而应409，修复后通过。保存任务恢复不截断，跨日/降低目标不误称绝对通过硬上限。
2. P2：退出后切走，下架的原词书无法再选回恢复。目录与选择现在仅对本人保存轮次保留该下架来源，仍禁止新分配。savedUnlistedBookRemainsSelectableOnlyForItsOwner先观察目录空，修复后通过，验证其他账号不可见/不可选；浏览器桌面/手机补测选回入口。
3. 概览占用数量建议：与预留分配修复一起对齐，unlearned排除其他scope占用，due排除所有未完成轮次占用，减少假可开始入口；历史stats.due保留事实口径。以API最新说明为准。

修复后作者运行完整65单元/92真实集成、67前端单测/构建、60浏览器广域回归及2下架选回用例，全部退出0。没有二次派发相同审查。见[验收](../acceptance/2026-10-19-vocabulary-dashboard.md)。
