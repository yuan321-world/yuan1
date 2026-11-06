# 整卷错题归集与复习验收（2026-10-25）

## 范围与用户授权

用户已确认方案及Native实施，连续完成归集、历史回填、来源重练、原卷定位和五类复习。只新增独立整卷错题台账，不改原卷成绩和既有单项错题；不进入六级、AI或其他阶段。

## 当前证据

- Java21真实专用库：`mvnw.cmd -o verify -Pmysql-it -Dit.test=PracticePaperMistakeIT,SchemaMigrationIT,PracticePaperIT#paperLifecycleIsAtomicPrivateAndFrozen,PracticeListeningIT,PracticeClozeIT,PracticeMatchIT -Dtest=PracticePaperMistakeRulesTest`，exit0，22项IT＋2规则单元；包含10项新错题IT。V27/V28重复启动迁移0次。
- 归集覆盖错/空、并发重放不重复计数、中途失败回滚、历史错→对、同完成时间按ID、未完成排除、SQL回填重放、原响应结果保持。来源重练覆盖25/10/10/1/1、原51题号、新错题再重练、同版本恢复、跨来源活动冲突、下架后冻结复习、他人来源/音频404、未登录401、新版独立、单项台账不变。
- 前端41单元（6文件）exit0：保存恢复、整卷、听力导航/片段、选词/匹配播放器；最终48项双端Mock回归exit0：新错题、整卷、年月、日期数量、历史及批量相关。最终类型/构建exit0；额外全量单元：前端30文件130项、后端85项均exit0。
- 真实源专用库：`PracticePaperIT#publishedSourcePaperCompletesInRealBrowser`，指定 `practice.paper.source=true`、源bundle、真实MP3、`practice.paper.source.browser=../frontend/tests/real/practice-paper-mistakes.cjs`，exit0。普通测试用户实际1440/320px均完成七部分、53题选择（两空漏答）、50/55正确、55解析逐字符串核对；完整音频和片段播放/边界停止、保存刷新、只读原卷定位及五类错题重练[25,10,10,1,1]通过。五道全部订正，同版第二次错误累计2次，原卷DTO逐字段相同，单项错题0。
- 源为已验收2016年6月第3套最终bundle；听力沿用该场共用第1套实际录音，未伪造独立第3套音频。临时管理/学习账号及成绩只写 `english_hero_test`，清理按测试拥有者执行；开发库不制造验收成绩。
- 截图已复核实际320px，无横向溢出和浏览器异常。浏览器只覆盖代表性真实题组，不代表70套逐卷人工视觉验收。

## 实施中的失败与修正

规则缺类、V26无台账、重练/列表接口缺失均先观察RED，实施后GREEN。MySQL CHECK允许UNKNOWN，来源ID非空但slot为NULL未被V27拒绝：保留已执行V27，新增V28收紧非空条件，边界测试通过。MySQL3819在当前Spring映射为DataAccessException子类，测试核对约束名，未修改产品异常译码迎合测试。

新Mock误传匿名身份造成登录弹窗，修正测试身份后32项初始目标回归通过；旧“整卷无错题栏目”断言更新为已确认的新入口。真实脚本首次回看后未等待路由完成导致找不到卡片，补明确URL等待，第二轮双端真实流程通过。以上未作为产品通过证据使用；最终通过记录分别保留。

## 本地迁移与独立审查

开发迁移前只读快照保存12张业务表计数/指纹，不导出响应正文或凭据；现有4整卷轮次、28槽响应、70卷、442全部题组，按迁移同一算法预计55道PENDING历史错题。只新增派生台账，迁移后55行台账与预计派生数据的15列排序哈希完全一致。10张原表指纹一致，包含58道单项错题、4原卷轮次、28响应、70卷与442全部题组；公开真题目录43听力/256阅读/各70写作翻译/70整卷保持。

V27/V28于14:23由项目JDK21新JAR正常启动应用；8080健康、401匿名保护和登录后本人查询通过，4174返回最终dist。原JAR保留于忽略证据目录，无清库、旧迁移改写或开发验收成绩。当前开发整卷来源重练轮次0。

数据保持边界：早期预检后、迁移前14:16:43存在已鉴权POST `/practice/sets/439/attempts`、14:17:18存在PUT `/practice/attempts/10/draft`，原服务日志可核对。因此单项轮次/答案两表分别10→11、130→135且有草稿变化，不能声称这两表与早期预检不变，也不能未经授权回退活动记录。迁移后第二次只读快照与第一次的12表均一致，派生台账与预检完全一致；不据此把并发活动冒称迁移写入或测试成绩。

一次[独立审查](../reviews/2026-10-25-practice-paper-mistakes.md)未发现核心归集/历史/权限问题，结果页冲突继续入口和从单项历史返回错题本的链接按实际影响重评级并修复：新双端测试4次RED后最终48项GREEN，修后41相关单元及全量130/85单元通过。低影响遗留：手工重练API省略/null slot返回500而非400，页面一直传合法槽位，不影响数据或正常用户流程。此项未记为通过。

本任务差异检查：未提交、未暂存、未回退无关dirty。全仓 `git diff --check` 发现既有 `2026-10-14-phase-1-identity.md:56` 空白行，未作为本轮失败修改；本轮文件未新增该问题。

## 可复现证据位置

全部原始日志位于忽略目录 `.tool-cache/20261025-paper-mistakes`：`rules-red/green.log`、`collection-red/green.log`、`collection-boundary-red/green.log`、`review-red/green.log`、`list-red/green.log`、`final-it.log`、`unit-final.log`、`ui-green-2.log`、`ui-final.log`、`real-run-2.log`、`real/results.json`及双端截图。开发快照 `dev-before/after/stable.properties` 只保存哈希及数量；`dev-verification.json`保存服务与数据核验，`review-ui-red/green.log`保存导航修正。登录凭据、Cookie与Token没有写入本报告。

## 下一步

本轮完成后先由用户体验整卷错题本及来源回看；题库内容维护继续遵守真实来源和冻结版本，未经用户另行确认不进入下一功能阶段。
