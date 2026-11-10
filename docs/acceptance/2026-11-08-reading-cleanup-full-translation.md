# 阅读收尾、紧凑列表与全文翻译

日期：2026-11-08。范围为本次九项标注；保留其他任务已有修改，不推送 GitHub。

## 交互与布局

- 阅读详情头部移除来源长段说明；作者/原文链接与完整转载说明移至文末，许可信息保留。
- 下线阅读写作积累、个人菜单“我的写作素材”、管理端素材编辑入口与登录续接；旧地址安全重定向。历史数据和受鉴权接口保留，考试作文/批改不变。
- 返回文章列表改为暖色描边圆角按钮，保留键盘焦点；文章列表压缩标题、摘要与卡片间距。
- 生词本词头、音标、释义、状态与按钮收紧；学习词管理压缩行高，全部功能保留。
- 七日统计/指标字号收紧；近30天明细展开后为有焦点样式的限定高度滚动列表，全部记录保留。
- 新增全文翻译状态组件，展示已完成段数、加载、失败与显式重试；与英中对照共用完整段落缓存。机器译文独立于异步工具数据，修复快缓存/慢tools及焦点刷新时丢译文的竞态。

## 文件

- 前端：`ReadingDetailView.vue`、`ArticleFullTranslation.vue`、`reading-tools.ts`；`ReadingRail.vue`；`AppNavigation.vue`、`LearnerLayout.vue`、路由及两个后台文章视图；阅读两份 CSS；`WordNotebook.vue`、`LearningWordManager.vue`、`LearningTrend.vue`。
- 后端：`reading/translation/FullArticleTranslation*`、`ReadingToolsService`、`SecurityConfig`、V44迁移与测试。
- 文档：API、decisions、README、progress及本验收。

## 已执行验证

- 前端 `pnpm --dir frontend test:unit`：205项通过，退出0。
- `pnpm --dir frontend build`：类型检查及生产构建通过，退出0；保留既有大词典资源包体积提醒。
- 定向 Playwright 10个文件：104项桌面/手机 Mock 通过，退出0，日志 `.tool-cache/reading-cleanup-e2e.log`。包括718px和320px截图、卡片高度、收藏/取消、词条弹窗、完整30天记录的键盘滚动、阅读定位、朗读、查词、翻译/解析、轮次恢复、退休入口与旧链接。
- 新全文翻译测试先在旧构建失败（无准备/状态功能），实现后通过；异步竞态由独立审查复现后闭合。
- 更新了旧测试的过时断言：写作入口改为不出现，阅读统计日期限定阅读周历（避免与词汇周历匹配两项），首页专项入口按当前正式卡片断言。
- 独立前端/后端只读审查未发现剩余P1/P2；前端额外以tools延迟600ms验证机器译文先返回和focus刷新后英中对照均保留。

## 实际服务与内容验证

- Java21.0.12.1 / Maven3.9.9：`verify -Pmysql-it -Dit.test=FullArticleTranslationIT,ReadingToolsIT,ReadingWordbookKeywordsIT`，131单元/MVC及11真实专用MySQL IT通过，退出0，无跳过。日志 `.tool-cache/full-article-backend.log`；全部测试mock百度，无外部调用。
- 当前PowerShell中mvnw启动器报null-array，使用仓库同版本Maven3.9.9直连路径，先加载use-workspace-tools/use-java21，项目缓存和Java21要求保持。
- 经进程身份与JAR哈希校验后，8080替换为 `english-hero-reading-full-translation-20261108.jar`，开启全文worker和首次补齐开关。4174健康接口验证服务/database UP，V44已增量应用开发库，无清库；忽略的启动助手同步新JAR、哈希与开关。
- 已真实访问456/458，验证25/25段中文同时进入英中对照和全文翻译，无reading写请求/无横向溢出；截图 `.tool-cache/reading-cleanup-real-{header,translation}.png`。最终逐篇校验公开清单共6篇均 SUCCEEDED，available=true，译文段落数与原文键匹配：

|文章/版本|全文段落|译文|
|---|---:|---|
|456 / 458 Decoding Dyslexia|25/25|百度机器参考译文|
|457 / 459 Creating Healthy Habits|41/41|百度机器参考译文|
|458 / 460 Build Social Bonds to Protect Health|34/34|百度机器参考译文|
|8 / 8 A Library Card and an Empty Chair|6/6|已有编辑参考译文保留|
|9 / 9 The Note That Helped the Next Shift|6/6|已有编辑参考译文保留|
|10 / 10 A Meeting with Time to Think|6/6|已有编辑参考译文保留|

共补齐100段机器译文，保留18段编辑译文。清单 `.tool-cache/reading-full-live-status.json`。这里是可用性/段落覆盖验证，机器译文未宣称经过全文人工精校；页面明确标记参考译文。

没有代用户写收藏或学习进度，没有自动开启下一阶段。新布局仍待用户视觉确认。
