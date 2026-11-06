# 整卷完整流程复验（2026-10-23）

使用项目专用 english_hero_test 与测试Redis，临时管理员发布合成七部分试卷，临时普通USER通过当前4174界面，所有API代理至Spring随机测试端口。开发8080与用户学习记录未写入；测试夹具由既有AfterEach清理。没有生产代码、依赖或部署修改。

## 实际后端与浏览器

实际Java21 Maven执行 `./mvnw.cmd -o verify -Pmysql-it -Dit.test=PracticePaperIT -Dtest=PracticePaperRulesTest -Dpractice.paper.browser=true`，最终退出0：2规则单元、1真实MySQL/Redis集成，其中包含1440与320px两条真实浏览器全流程。

| 检查 | 结果 |
|---|---|
| 目录开始新整卷 | 双端通过 |
| 写作、听力、选词、匹配、阅读两篇、翻译共七部分填写 | 双端通过 |
| 55道客观题全部选答，作文和译文保存 | 双端通过 |
| 切换部分保留同一完整音频元素 | 双端通过 |
| 手动保存后刷新，55个选择和两份文本逐项核对 | 双端通过 |
| 未交卷响应不含答案/范文/解析 | 双端通过 |
| 确认交卷先返回检查，再正式提交 | 双端通过 |
| 合成预设正确答案判定55/55 | 双端通过 |
| 完成后作文/译文只读、客观项禁用、范文译文及解析可见 | 双端通过 |
| 返回目录，在我的练习查看历史 | 双端通过 |
| 页面异常与横向溢出 | 双端均无 |
| 未登录/他人轮次、音频票据隔离 | 真实后端通过 |
| 冲突锁、重复开始/交卷、旧版冻结、下架及失败回滚 | 真实后端通过 |

## 其他回归

- `pnpm --dir frontend test:unit tests/unit/practice-paper.spec.ts`：5项通过，含保存期间继续输入、冲突副本、提交不确定重试、切账号清理、多标签页避免覆盖。
- `pnpm --dir frontend test:e2e practice-paper.spec.ts practice-paper-layout.spec.ts practice-listening.spec.ts --workers=1`，E2E_EXTERNAL_SERVER=1、E2E_PORT=4174：18项双端Mock通过。Mock结果与真实库浏览器结果分开记录。
- `node --check frontend/tests/real/practice-paper.cjs`退出0；本轮仅扩展真实浏览器测试脚本，无Vue生产源码改动，不重复构建前端。

初次扩展脚本有两处测试定位问题：译文参考沿用已展开details而脚本再次点击使其关闭；手机“题目与匹配”有两处入口造成严格定位歧义。已改为先检查展开状态、限定移动导航作用域；最终完整重跑通过，未为测试修改产品行为。

## 边界与证据

这是合成测试卷的功能验收，不等于逐份人工核对70套真题的题文/详解。听力使用合成测试MP3，检查资产授权及播放器切换保留；本轮未逐份验证43份真实录音的听感与片段校准。独立中文详解未齐的既有状态不变。

- 最终真实库日志：忽略缓存 `.tool-cache/cet4-complete/paper-full-acceptance-complete.log`，BUILD SUCCESS，1集成无失败/错误。
- 单元/Mock日志：同目录 `paper-full-unit.log`、`paper-full-mock.log`。
- 双端结果JSON及截图：`.tool-cache/practice-paper-implementation/real-results.json`、`real-paper-review-1440.png`、`real-paper-review-320.png`；已查看两张截图。
- 本轮无尚未通过的执行流程检查；用户视觉确认仍由用户完成。未提交、未推送。
