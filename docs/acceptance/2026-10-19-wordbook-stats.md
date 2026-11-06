# 2026-10-19 参考词书与词汇统计验收

## 已交付

/vocabulary新增“切换来源”：个人阅读生词或“大学英语四级·参考词表”。明确显示原始3849、可学3846、3项缩略词排除及10项源音标缺失，来源/许可可展开。切换保留学习进度，选择不生成全书个人记录，开始最多20词；同entry跨来源共享阶段，到期复习仍涵盖所有来源。

/stats保留原阅读统计，新增词汇累计学习过、成功复习、到期词、完成轮次和本周每日新学/复习。统计来自后台事实，失败重练与请求重放不重复计通过；词汇不满足阅读打卡。两块独立加载/错误/重试，后台刷新保留内容，身份变化丢弃旧响应。

原认词/选义、逐字拼写、释义手动继续、暂停/恢复、Notebook、阅读、认证保留。存在未结束轮次（含暂停）时，必须先完成再切换来源，界面明确说明，不静默抛弃轮次。

## 数据与运行版本

固定ECDICT版本bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b。原CSV SHA256为1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf；参考entries.json SHA256为3cc6d01e6268806c6a9a34203a022242129c745a7229dde982d28a8ed729a0d7。实际cet4标签而非排名，非官方大纲，仅本地开发；混合上游授权边界见content/vocabulary/ecdict-cet4-local/NOTICE.md。

原10000词生成检查仍为5c9720461a258635b24762975ae49dca1a1b06de1de22729e08c12e6c6efc4e8。参考和原词库--check都退出0，独立审查亦复现。276词不在原10000子集，导入按库中实际缺项补充，不覆盖已有词头/音标/释义/ID。

V17新增公共词书/条目及本人来源，给学习词新增personal_enrolled，保留全部旧记录。8080运行新JAR，显式启用本地参考导入；专用english_hero_dev迁移17。只读JDBC核验实际发布词书3846条，与manifest可练数一致。服务/数据库UP，4174/vocabulary为200，游客词书私人目录401。

后端PID95968，独立运行JAR位于.tool-cache/wordbook-runtime/english-hero-wordbooks.jar，工作目录backend；日志server.log。4174预览使用最新dist。运行包与target分离，避免后续打包覆盖正在使用JAR。

## 验证

| 检查 | 实际结果 |
| --- | --- |
| 生成与复现 | prepare-ecdict.py --check及prepare-cet4-reference.py --check均0；旧数据hash不变 |
| 后端单元 | Maven实际Java21；65用例，零失败/错误/跳过 |
| 完整真实集成 | `mvnw.cmd -o test failsafe:integration-test failsafe:verify -Pmysql-it`退出0，83用例，零失败/错误/跳过；专用MySQL及6381测试Redis；日志wordbook-full-backend.log |
| 前端单元 | test:unit退出0，19文件67用例；日志wordbook-unit-final.log |
| 类型/生产构建 | build（含类型检查）退出0；最终文案明确暂停不结束轮次后再次build通过；日志wordbook-build-final.log |
| 浏览器回归 | 受控5174，词书/统计/学习/Notebook/导航/查词/打卡/登录弹窗54用例退出0，含桌面/手机与768px场景；日志wordbook-regression-final.log |
| 运行验证 | health UP、开发库实际3846发布条目、页面200、游客私人目录401 |

新增真实用例涵盖选书无个人行、20词懒生成、个人/书同词阶段共享、未完成轮次不能切换、未知/下架书拒绝新任务、幂等导入；统计A/B隔离、GET零写入、错误重练/反馈重放不重复、上海跨日、词汇不产生阅读打卡。旧学习到期/并发/权限测试仍通过。

浏览器检查使用Mock响应，真实数据库/HTTP另作集成；未声称已在浏览器真实账号跑完全书。已查看手机选书弹窗及统计截图，图片wordbook-selector-mobile.png/wordbook-stats-mobile.png；弹窗内部可滚动，手机/平板无横向溢出。用户最终视觉确认待体验。

## 失败与审查

数据/统计测试先因无实现失败；后补实现通过。导入器类型名及Vue SFC纯tsc测试导入问题已定位修复。独立审查两项P2均补失败回归再修复：见[审查记录](../reviews/2026-10-19-wordbook-stats.md)。未关闭沙箱、未改全局权限或其他项目库。后端全回归工具审批仅为已有认证故障用例恢复测试Redis。

## 边界

这是本地参考词表，不是官方/商业词书授权结论；没有付费API或AI依赖。后台未新增完整词书编辑/上传系统，当前为固定版本受控导入，重启不自动重发下架书。学习过不是永久掌握，合成朗读并非真人音频。所有现有无关修改保留，未提交/推送/部署外网。
