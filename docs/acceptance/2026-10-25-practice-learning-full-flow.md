# 四级整卷与错题复习流程验收

## 范围和隔离

按用户“继续下一步”授权，对当前构建执行整卷学习流程验收。把已发布的2016年6月第3套最终内容复制到项目专用 `english_hero_test`，使用临时管理员发布七题组和一份整卷，再由临时普通学员操作当前4174前端。此套听力按原来源共用6月第1套真实录音；录音SHA-256为 `1b29fcff40871e601a9cb86945cc87015864a0c7a6b9f4b8608f82abb75ff61b`，没有生成替代音频或修改切片边界。

浏览器全部 `/api/v1` 请求代理到Spring随机测试端口；测试使用项目测试MySQL/Redis，夹具按既有用户归属清理。开发8080的题库、冻结版本、用户账号与学习记录未写入。没有直接发布开发库内容、迁移、依赖安装、服务重启、提交、推送或外网部署。

这是一套真实来源材料的代表性功能验收，不是把70套试卷全部重新逐题审读，也不是43份录音的完整听感验收。

## 最终真实浏览器结果

在1440×1000及320×1000实际视口各跑一遍，脚本先设置并断言视口宽度，最后查看双端截图。

| 检查 | 结果 |
|---|---|
| 从目录开始新整卷 | 双端通过 |
| 写作、听力、选词、匹配、两篇仔细阅读、翻译七部分填写 | 双端通过 |
| 完整音频实际播放，片段进度从0开始、实际播放并在片段末尾停止 | 双端通过 |
| 片段播放暂停完整音频，切换部分后完整音频DOM实例仍保留 | 双端通过 |
| 55个客观选择及作文、译文保存，刷新后恢复 | 双端通过 |
| 未交卷响应不含答案、解释或参考文本字段 | 双端通过 |
| 交卷确认可返回检查，正式提交后只读 | 双端通过 |
| 故意错一道听力，结果54/55，且该题标错 | 双端通过 |
| 交卷后55条解释逐字符串等于最终来源输入 | 双端通过 |
| 听力中英栏目、选词代入示例、主观参考及历史入口 | 双端通过 |
| 独立仔细阅读故意错一道，4/5 → 错题本待订正 → 重做1/1 → 已订正 | 双端通过 |
| 错题列表最终页面无横向溢出、全流程无pageerror | 双端通过 |

整卷与单项轮次保持原有独立设计。整卷错题**当前不会自动进入单项错题本**；整卷可逐部分回看结果或重练整卷。本轮错题复习验证的是已有单项错题闭环，没有将它表述为整卷错题归集功能。

## 发现和修复

视觉复核发现错题列表及整卷仔细阅读的解释文本没有保留换行，答案、定位、原文、释义和辨析挤成一段。分别增加解释专用class，复用 `white-space:pre-line` 的展示方式；不改字体、解析文本、判分或业务接口。最终真实浏览器复验两个位置的换行样式，查看1440/320错题截图，确认各栏目分行显示。

新增可选真实来源整卷测试 `PracticePaperIT#publishedSourcePaperCompletesInRealBrowser` 和 `frontend/tests/real/practice-paper-source.cjs`，复用既有专用库、鉴权与清理辅助，不新增生产框架。原合成整卷权限/并发测试保持并重新执行。

首次测试辅助有异步取值写法和控件精确定位两处问题，均改在测试脚本。截图复核还发现 `BrowserContext.newPage` 的视口参数未生效；已改为显式 `setViewportSize` 并断言宽度，再完整重跑真实1440/320流程。此前未生效的结果不计手机验收。以上不是产品故障，也没有通过改产品行为迎合脚本。

## 命令和结果

实际Maven Java为21.0.12.1。最终真实来源运行退出0：

```powershell
./mvnw.cmd -o verify -Pmysql-it '-Dit.test=PracticePaperIT#publishedSourcePaperCompletesInRealBrowser' '-Dtest=PracticePaperRulesTest' '-Dpractice.paper.source=true' '-Dpractice.paper.source.bundle=../.tool-cache/20261025-explanation-patch/2016-06-3/payloads.json' '-Dpractice.paper.source.audio=../.tool-cache/cet4-complete/audio/2016-06-1/recording.mp3' '-Dpractice.paper.source.output=../.tool-cache/20261025-paper-learning-acceptance'
```

1项实际MySQL/Redis集成中包含两条完整整卷及两条单项错题闭环；相同2项规则单元通过。最终输入和音频指纹保存在 `input-fingerprints.json`，源录音前后指纹一致。

另运行原合成权限与并发测试，退出0：

```powershell
./mvnw.cmd -o verify -Pmysql-it '-Dit.test=PracticePaperIT#paperLifecycleIsAtomicPrivateAndFrozen' '-Dtest=PracticePaperRulesTest'
```

1项真实库集成通过，覆盖未登录/他人资源拒绝、音频权限、锁冲突、重复/并发提交幂等、冻结版本、下架与失败事务回滚。重复执行的2项规则单元不当作新的不同测试。

`pnpm --dir frontend test:unit tests/unit/practice-paper.spec.ts tests/unit/practice-listening-clips.spec.ts tests/unit/practice-attempt.spec.ts`：23项通过。最终 `pnpm --dir frontend build`（含vue-tsc/tsc）退出0，4174已使用最终构建；`node --check frontend/tests/real/practice-paper-source.cjs` 退出0。本轮没有用Mock通过替代真实库验收。

证据保存在忽略缓存 `.tool-cache/20261025-paper-learning-acceptance`：最终 `integration.log`、`security-integration.log`、`unit.log`、`build.log`、`real-browser.log`、`results.json`、指纹及双端截图。初始测试辅助失败记录与修复前视口结果单独保存，未覆盖此前验收记录。

## 下一步

当前流程未发现未通过的执行检查。本轮修复已完成，不自动扩展六级或AI批改。最明确的后续功能是整卷错题归集与复习：需先确定它与现有单项错题本的版本、题号和重练规则，再实施，不能靠创建七份单项轮次替代整卷模型。
