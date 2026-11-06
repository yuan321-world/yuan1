# 词内片段翻译（2026-10-29）

用户更正：选中 looking 中的 look 也应提供翻译；实际选文若有本地中文词义则用本地，未命中才百度。允许任意含英文的原文子串，包括单个字母。替代上一轮“半词隐藏入口”的决定。

练习页保留 partial 标记但不拒绝选区，不自动弹出“未收录”；点击“翻译”后 localSelectionWord 仅在词典有中文释义时命中，否则走原有参考/短语/百度任务。外刊选词复用该逻辑；完整词原有查词/收藏流程保留。词典传输错误提示重试，不伪装未收录。关闭/切换上下文的 generation/AbortSignal 防止旧结果覆盖。后端仅放开 TRANSLATION 为至少一个英文片段，GRAMMAR 至少两个词，原文、归属、CSRF、交卷限制、任务缓存和月字符配额不变。无数据迁移。

验证：新增后端片段测试先红400/预期200；新增本地命中/未命中双端4例先红入口缺失；单字母外刊双端先红，放开非空条件后通过。最终 Java21 Maven mysql-it verify：116单元及12真实专用库IT通过（SelectionTranslationIT11 + PracticeSelectionIT1），exit0；供应商由受控结果模拟，未调用真实百度或DeepSeek。162前端单元、类型/构建、60桌面1440/手机320 Mock界面通过，exit0；含完整词查词收藏、Escape、选区稳定、跨空位、听力/整卷锁、语法及单字母。日志在忽略目录 .tool-cache/partial-word-{backend,unit,build,ui}.log。独立只读审查发现单字母外刊入口遗漏，已补齐及回归。

运行JAR为 .tool-cache/20261029-practice-translation-live/english-hero-selection-fragment-v2.jar，SHA256 FE1003CC0825BF0F3FB6C5AC2EF6D50E78DB52D1580A9A171AF7A2C81C36D08E。运行状态验证另记下方。没有提交或推送。

本地更新已完成：仅停止已核对Java路径/JAR身份的本项目旧PID27124，启动哈希校验v2；启动脚本及独立GET均确认4174 HTTP200、service english-hero/database UP。前端dist已更新，刷新即可使用。
