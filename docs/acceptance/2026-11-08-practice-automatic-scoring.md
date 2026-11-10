# 交卷自动评分

日期：2026-11-08。用户确认客观题百分制、作文/翻译自动批改及整卷参考分方案后实施。

## 功能与计算口径

- 听力、仔细阅读、选词填空、长篇匹配和错题专项：使用服务端交卷返回的冻结 `correctCount/total` 显示百分制参考分；未答已由原判题逻辑按错题处理。不重判、不修改原记录。total缺失或0不伪造分数。
- 作文、翻译：已提交单项或已完成整卷非空作答进入现有 DeepSeek 学习反馈流程，15分制；旧成功反馈复用，不覆盖原作。打开尚无反馈的已完成轮次也补入队列。交卷确认说明发送题目/本次作答与额度使用。
- 整卷练习100分：听力35、选词5、匹配10、仔细阅读两篇各10、作文15、翻译15。客观部分各按正确比例计分；非官方赋分，不换算710或官方标准分。结果先显示客观70分小计，主观未完成不显示数值总分；空白主观题计0。数据不完整也不生成总分。
- 后端/数据库/API格式不变，原本人鉴权、冻结题目、幂等、配额、服务端判题及后台worker继续使用。

## 队列与失败边界

- 前端同一账号串行申请各部分，复用服务端历史中的成功/活动任务；服务端仍限制本人最多一个活动任务。ACTIVE仅等待、不换请求UUID；显式失败、超时未知、鉴权/预算/配置问题停止自动重发，结果页提供重试。
- 持久化仅本人来源ID/槽位/请求UUID/队列状态和公开错误信息，不存答案或凭据；正常刷新/返回可恢复。已进入服务端的任务由现有worker处理；关闭整个网站时尚未申请的下一项，需下次打开网站接续。未实现服务端离线批量编排。
- 账号/凭据变化隔离旧响应，账号切换不申请前一账号任务；同账号凭据更新会释放执行锁。恢复未知结果时沿用UUID，优先读取服务器历史；不会自动重试终态失败。
- 批改使用现有服务开关/预算/次数，不调整额度；不可用时保留客观分及错误原因。整卷失败显示“评分未完成”，不假装已经出总分。

## 文件

- `frontend/src/practice/scoring.ts`：统一练习分数与整卷参考权重。
- `PracticeObjectiveScore.vue`、`PracticeResult.vue`、`PracticePaperResult.vue`及三类练习视图：出分、分项/总分、自动申请、状态和重试。
- `stores/practice-auto-review.ts`、`practice-review.ts`、`LearnerLayout.vue`：恢复队列、串行申请、幂等和状态共享。
- `PracticeSubjectiveTools.vue`、`PracticeReviewPanel.vue`、`PracticeEntryView.vue`：交卷自动评分说明与参考分文案。
- `practice-compact.css`：回归发现整卷/专项切换计数行相差10px，统一基线；原筛选样式保留。
- unit/e2e：评分、队列与交卷流程回归；Mock身份夹具增加批改兜底拦截，测试不会落到真实付费服务。

## 实际验证

- 新模块缺失时RED退出1；独立审查发现凭据变化卡住busy、ACTIVE清理retry两个P2，分别新增复现测试RED后修复GREEN。
- `pnpm --dir frontend test:unit`：54文件214项，退出0。
- `pnpm --dir frontend build`：类型检查+生产构建退出0，保留既有大包提示。
- E2E external4174，8文件（auto-score/review/listening/paper/paper-sheet/subjective/cloze/match）50项桌面及手机Mock通过；随后加强进行中刷新恢复与客观分数可见断言，auto-score6项、cloze/match/listening10项再通过。
- 独立审查复核两项修复闭合；相关12项单元通过。
- 视觉检查`.tool-cache/practice-auto-score-{desktop,mobile}.png`，分项与总分、320手机按钮无溢出。截图使用合成92分，不代表真实用户成绩。
- 最后文案修正与入口回归记录在`.tool-cache/practice-auto-score-final.log`；构建日志build.log，主回归e2e.log、unit.log、resume.log、practice-objective-score-e2e.log。

4174静态dist已更新。不提交/推送；未改后端/迁移，未运行本轮真实数据库或真实DeepSeek付费评测。
