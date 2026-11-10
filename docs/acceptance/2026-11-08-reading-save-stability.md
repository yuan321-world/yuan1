# 阅读自动保存提示稳定性

2026-11-08，用户反馈底部“进度已保存”频繁闪动。

根因：每次IntersectionObserver更新阅读位置，内部状态依次unsynced（排队）→saving→saved；原界面将排队误当失败展示“重试保存”，并在任何saving时禁用已读按钮，造成文字和按钮反复变化。未修改既有两秒防抖/串行保存/冲突/离页flush逻辑。

修复：ReadingDetailView在正常排队、发送、成功时固定显示“阅读位置自动保存”；只有unsynced且真实error时才显示失败/重试。冲突提示和加载最新保留。标记已读用独立UI busy，仅主动标记时禁用/展示“正在标记”；后台保存时按钮保持可用，由原串行flush将完成操作排在当前请求后执行。阅读按钮最小宽度108px避免文案改变挤动。

验证：新增回归先在旧构建失败（状态文字改变），新构建后桌面/320手机10项Mock通过，包含普通保存提示/按钮稳定、保存中标记已读、失败重试、冲突重载和阅读打卡。既有reading-progress单元3项通过；类型/生产构建通过，均退出0。日志 `.tool-cache/reading-save-stability-{red,build,e2e,unit}.log`。git diff --check通过。

修改限于ReadingDetailView.vue、reading-detail.css和相关测试/文档；无后端或数据库修改，未代用户写阅读位置/完成状态。4174新dist已更新，等待用户体验；未开启下一阶段或推送GitHub。
