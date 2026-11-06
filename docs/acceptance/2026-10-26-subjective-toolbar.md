# 作文与翻译底部工具栏（2026-10-26）

用户提供截图并要求功能键集中左下角，按该布局调整正式单项及整卷。输入框主体保留；底部横排参考范文/参考译文、解析、AI批改，右邻实时词数，窄屏换行。建议词数留在输入框标题旁。

参考与解析分别展开当前提交的参考表达及原有对照要点；AI复用现有同意发送、申请、轮询、失败重试、历史及双语反馈。未提交时入口禁用并显示提交后解锁，不提前暴露答案。保存、清空、继续修改、提交/交卷、题号卡功能保留。没有增加API、数据迁移、外部调用或费用。

修改范围：PracticeSubjectiveTools.vue、PracticeReviewPanel.vue、两类AttemptView、practice-review.css及既有相关E2E断言。其他已有脏工作区保持。

验证：

- 新工具栏断言先失败（旧版没有subjective-tools-row），实现后通过；草稿断言从参考入口不存在改为禁用，与设计一致。
- `pnpm --dir frontend build`：最终退出0，包含Vue/TS类型检查与Vite构建。
- `E2E_EXTERNAL_SERVER=1 E2E_PORT=4174 pnpm --dir frontend exec playwright test tests/e2e/practice-review.spec.ts tests/e2e/practice-subjective.spec.ts tests/e2e/practice-paper-sheet.spec.ts --reporter=line`：最终22 passed（23.4s），退出0。1440px和320px，Mock接口；覆盖申请同意/结果恢复/失败、参考、原文只读、草稿与修改、连续整卷/题号卡。不是新一轮真实DeepSeek验证。
- 桌面和手机截图位于忽略目录.tool-cache/20261026-ai-review/desktop-writing.png和mobile-writing.png，目视复核无横向溢出。发现全局按钮规则覆盖圆角后提高局部选择器优先级，最终构建及22项回归均重跑。
- 4174首页包含最终构建index-B3TVO4fM.js，服务已提供新产物。用户视觉验收仍待反馈。
