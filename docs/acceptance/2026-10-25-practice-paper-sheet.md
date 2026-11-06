# 整卷连续试卷布局验收（2026-10-25）

## 已确认布局

用户提供三张参考图，并确认短设计：左侧连续展开题目，右侧完整题号/进度导航固定跟随；顺序为作文→听力→选词填空→长篇匹配→仔细阅读两篇→翻译。选词上方横排15词，原文内选择空位。手机通过“查看全部题号”打开完整面板。改正式前端，不新增后端接口、依赖或数据迁移。

## 最终实现

- 整卷单一阅读面板按七槽顺序展开；新轮次仍从作文开始，已有轮次保留原保存位置。每部分保留上一/下一部分及提交检查入口。
- 桌面右侧直接展示各部分计数和55个客观题号、写作W与翻译T，不再默认折叠。填写任意主观题计1项，摘要总计57项；该完成数不冒充客观正确数或考试标准分。题号可定位具体题/空位，已答高亮，滚动同步部分高亮。题号卡自身可滚动，标题保留可见。
- 听力25题按全部片段连续展示，取消整卷内重复的小题号侧栏；完整音频、±5秒、倍速、A–B和已核验8段播放器保留。播放不同片段互相暂停，与完整播放器互斥，切换部分不重建完整音频。
- 整卷选词采用横排词库＋下方原文填空，两端均内联。保留只看未用、清空、撤销、已用词移动确认；单项选词仍保留原桌面侧库/手机弹层。
- 匹配原文后展示全部10条陈述和各自段落选择，保留段落定位、重复匹配、待检查、筛选及撤销。答题卡定位会显式恢复被筛选隐藏的目标题。
- 交卷后原答案只读、解析/参考仍受服务器完成状态控制；来源错题定位、原成绩和所有后端接口保持。照片上传、AI等外站截图能力未被伪造接入。

## 实现与问题复现记录

前端限定范围：`PracticePaperAttemptView.vue`、新 `PracticePaperAnswerCard.vue`、三个播放器的可选paperLayout模式、`practice-paper.css`。原默认单项模式保留；保存/交卷继续调用现有Pinia与服务端。

先运行新连续试卷E2E，两端因不存在七连续部分失败（red.log）。第一轮验证发现新增空choice键没有触发依赖更新：`Object.hasOwn`条件短路导致计算属性未读取缺失键，连续选词可能跳过移动确认。改为先读取响应式choice键，再决定已有键/冻结回退，取消移动和保存刷新用例通过。

一次独立只读审查报告两项Important：筛选后题号定位无效、非当前题作答的撤销栈被currentId监听清除。新增两用例×双端4次RED（review-red.log），修复为完整卡定位先reveal目标/恢复all，整卷匹配保留跨行撤销。另截图复核发现手机匹配固定栏因全卷挂载而遮挡作文底部按钮，先追加缺席断言RED（mobile-bar-red.log），再限制该栏仅单项模式。修复后统一回归GREEN，无未处理的本轮严重项。

原整卷测试针对新手机面板和同时存在两主观参考调整定位方式；旧“听力另有小题号栏”断言改为完整答题卡默认展开，不把被明确替换的布局当功能回归。历史验收截图不覆盖，新证据全部使用本日独立目录。

## 最终验证（均exit0）

1. `pnpm --dir frontend build`：类型检查及生产构建通过，build-final.log。
2. `pnpm --dir frontend test:unit`：30文件130项通过，unit-after-review.log。
3. `E2E_PREVIEW=1 E2E_EXTERNAL_SERVER=1 E2E_PORT=4174 pnpm --dir frontend test:e2e tests/e2e/practice-paper-layout.spec.ts tests/e2e/practice-paper-sheet.spec.ts tests/e2e/practice-paper.spec.ts tests/e2e/practice-paper-mistakes.spec.ts tests/e2e/practice-cloze.spec.ts tests/e2e/practice-listening.spec.ts tests/e2e/practice-match.spec.ts`：36项1440/320及1011布局相关双端Mock通过，review-green.log。包含全55号导航、25听力全DOM、词库在原文上方、首次作文、移动取消、已答计数、保存刷新、跨部分答案归属、原51题、只读回看、匹配筛选/撤销和原单项回归。
4. 真实来源全栈：Java21实际Maven、专用 `english_hero_test`，`mvnw.cmd -o verify -Pmysql-it -Dit.test=PracticePaperIT#publishedSourcePaperCompletesInRealBrowser -Dtest=PracticePaperRulesTest`，加已验证bundle/真实MP3/`practice.paper.source.browser=../frontend/tests/real/practice-paper-sheet.cjs`等参数。最终real-final.log通过，1真实源IT及2规则单元。
5. 实际1440/320视口各七部分55题保存/刷新、54/55判分、55解释逐字符串匹配、8小播放器/25题同时存在、片段互斥、结束停止、完整音频DOM保持、只读参考、历史及旧单项错题订正均通过。来源为2016年6月第3套最终bundle及该场共用第1套录音，保留披露。只写测试库临时账号，不制造开发学员成绩。
6. 双端截图已复核，未横向溢出，无pageerror。真实来源代表性验收不等于70卷逐卷人工视觉确认。用户尚未对本次正式结果作视觉确认。

## 服务与交接

4174预览服务读取新dist，不需要重启未修改的后端；8080继续V28。本轮无数据库结构/API变更，不改冻结题文、原答案或学习记录，不提交/推送。公开目录与最终dist读取检查另见忽略目录service-check.json。

证据目录 `.tool-cache/20261025-paper-sheet`：RED/GREEN、构建、全单元、独立审查复现及 `real-final/results.json`/双端截图。此前错题API缺少slot时500的低影响遗留属于上轮，未借本轮布局宣称修复。
