# 2016年6月第1套中英详解样板验收（2026-10-24）

## 本轮交付

55条客观题解释增加关键英文（节选）、中文释义、英文对应关系与干扰项英文提示；保留原中文定位、解析和排除依据。选词填空保留原空位，并标注补全示例。没有把整个段落或所有选项机械重复一次，也没有将分析者的改写冒充原文。

- 62处原文摘录均自动匹配题库缓存原文，选项短语匹配相应选项；匹配题的相近段落短语匹配原文，选词题干扰词匹配词库。大小写/空白规范化只用于校验，呈现文字保留选取内容。
- 55个正确答案与上一版保持，原文/选项/题键/词库/音频/元数据保持。最大解析735字符，仍使用现有文本字段及交卷后权限。
- 客观题组25—29发布v3；两主观题组30/31保持v2；整卷4发布v3。捕获的14份v1/v2冻结DTO逐项一致。新开始的练习读取新版，旧轮次不覆盖。
- 修正仔细阅读结果页面直接使用p.practice-explanation时未继承换行规则的问题。仅新增一条CSS，没有接口、结构、权限或功能变更。

## 验证

- 实际Maven Java21，`verify -Pmysql-it -Dit.test=PracticeContentBundleIT -Dtest=PracticePaperRulesTest -Dpractice.content.bundle=true`及本轮payload/audio/browser路径：退出0，1内容集成、2规则单元通过。提交后全部55解释与输入逐字符串一致，主观说明保持；交卷前不返回解释/答案，其他学员不能读取轮次。
- 同一隔离测试内，当前4174连接随机真实后端，1440/320写作、翻译和两篇仔细阅读共8条流程通过，包含保存、刷新恢复、交卷、对照说明与历史。并非55道题都做过逐题人工视觉验收；55条完整内容经API核对。
- `practice-bilingual-explanation.spec.ts`用真实DOM Range测量英文标题、英文引句、中文释义分属不同行：修改前桌面/手机两项均失败，修改后通过。与practice-attempt.spec.ts合计8项双端Mock通过，覆盖原保存/恢复/提交；没有用Mock结果替代真实库结果。
- pnpm --dir frontend build含类型检查退出0。发布后匿名整卷可用、7组成且55解析齐备，匿名单项/整卷DTO不含私有解释字段；源文、音频与14份冻结记录不变。
- 本轮不创建或提交开发学员练习，不修改真实学习记录；使用既有管理鉴权/CSRF/校对/发布流程，未直接改库，无迁移/依赖/后台重启/提交/推送。

## 证据

忽略缓存.tool-cache/cet4-explanations-2016061-bilingual：english.json、formatted.json、payloads.json、validation.json、before.json、published.json、result.json、integration.log、real-browser-results.json、build.log、layout-red/green.log、publish.log。旧样板Markdown备份在sample-v2.md。

完整可审阅文本：[中英对照样板](../content/2026-10-23-cet4-2016061-explanations.md)。这是现有首套样板的修订，全年份其余详解尚未补齐。
