# 写作/翻译 AI 批改配置

服务端接入DeepSeek，单项和整卷共用学习反馈；只有本人已交卷/已提交的作文与翻译可申请。原成绩、参考内容与继续修改保持独立。当前自动化验证使用本地HTTP Stub，不代表真实模型质量通过。

## 密钥与开关

1. 在DeepSeek开放平台创建项目专用密钥；不要发到聊天或提交到Git。
2. 将 `backend/config/application-ai.example.yml` 复制为同目录 `application-ai.yml`。该文件已加入Git忽略，启动从backend目录自动读取；已有同名文件先检查并编辑，不直接覆盖。
3. 用本地编辑器填 `api-key`，或在启动服务的进程环境提供 `DEEPSEEK_API_KEY`。本地编辑器不回显到聊天、终端日志或浏览器。不要把密钥放进Vite环境变量。
4. `enabled` 仍保持false，直到确定真实测试预算。需要联调时改true并重启；模型当前仅允许 `deepseek-flash`，不自动切换更贵的模型。没有有效配置时申请返回503，作答/参考流程仍可用。

```yaml
english-hero:
  practice:
    ai:
      enabled: false
      api-key: ${DEEPSEEK_API_KEY:}
      model: deepseek-flash
      user-daily-limit: 10
      daily-budget-microcny: 1000000
      worker-enabled: true
```

日预算单位为微元人民币：1000000=人民币1元，适用于整个项目每日（北京时间）。为兼容已有账本，内部仍按保守微美元结算：官方Flash人民币/美元两币种费率比例为20/3，人民币上限向下换算为150000微美元；不是实时汇率。原有已使用/预留金额继续计入。每请求仍预留50000微美元（按官方费率对应人民币不超过1/3元），成功且usage可信后结算，无usage/未知结果保留预留。上游服务价格变化或同账号其他程序消费不受该预算控制。真实开启前按[官方价格](https://api-docs.deepseek.com/quick_start/pricing/)重新核对；当前费率实现只面向此Flash配置。

## 学员体验

新提交使用subjective-review-v4翻译规则：语义/语法区分、中文来源定位、避免重复遗漏，发现不可靠句数总评时生成基于反馈条目的中英摘要。历史成功记录原样复用，不自动重新调用；需要新版反馈时先“继续修改”提交新轮次，再主动申请。

费用状态：可信用量用于保守结算，即使反馈内容无效也释放多余预留；明确未生成的拒绝释放本地预留，超时或无可信用量仍待确认。反馈区区分预留/结算/释放/未知，不代表平台实扣。固定单笔预留、每日人民币1元保持；不自动重算旧未知费用，详见[验收](acceptance/2026-10-26-ai-budget-settlement.md)。

作文/翻译输入框下方左侧集中显示“参考范文/参考译文”“解析”“AI批改”，旁边为实时词数；单项和整卷一致。提交前按钮禁用并提示提交后解锁，提交后参考与对照要点分别展开，AI同意发送及反馈显示在工具栏下方。

提交作答后点击“申请批改”，阅读发送说明并勾选确认。服务端读取冻结题目和当次作答，不发送账号资料或其他记录。整卷须先统一交卷，随后在结果页点击“查看作答与参考”，作文/翻译下方各有独立反馈区。

排队/处理中可以离开，返回会读取任务记录。完成后显示15分学习参考、中文总评与英文对应、逐句批注、信息遗漏及参考修改稿；不自动覆盖原作。继续修改保留旧反馈。参考分不换算官方考试分。

失败不自动再次调用。结果未知意味着上游可能已计费，再申请可能再次收费；确定未发出的排队过期任务退还本地预留。每用户最多1个活动任务，全局2个；每天10次、同份冻结作答最多3次外呼尝试、重试冷却10秒。重复点击与幂等重放复用任务；已成功反馈复用。

## 验证与恢复

- 自动测试仅用english_hero_test和127.0.0.1 HTTP Stub；不需要真实密钥。
- 原创人工预期样本在 `content/ai-review/original-calibration.json`，10作文+10译文。HTTP Stub证明请求/解析/流程，并不能评判真实模型能否正确识别这些错误。
- 真正联调时先确定允许消费额，再用这些原创样本逐份人工比较：不能捏造原文、把正确变体标错、服从文中注入指令或漏掉核心误译。保存结果时不得含密钥。
- 服务中断后RUNNING租约过期标为结果未知，不自动补发；未发送任务10分钟过期。关闭enabled阻止新领取，排队到期仍会清理；`worker-enabled=false`只用于受控测试/运维，会暂停恢复任务，平时保持true。
- 如原文涉及个人信息，实际开放前核对适用账号的数据处理/留存安排并完善用户说明；未宣称DeepSeek零留存或不训练。来源：[开放平台协议](https://cdn.deepseek.com/policies/zh-CN/deepseek-open-platform-terms-of-service.html)。

## 本地检查命令

从项目根目录点加载 `scripts/use-workspace-tools.ps1`、`scripts/use-java21.ps1`，确认Maven实际Java21。

后端从backend目录运行：

```powershell
./mvnw.cmd -o verify -Pmysql-it '-Dit.test=PracticeReviewIT,PracticeReviewPaperIT,PracticeSubjectiveIT,PracticePaperIT#paperLifecycleIsAtomicPrivateAndFrozen'
```

前端从frontend目录运行：

```powershell
pnpm test:unit
pnpm build
$env:E2E_EXTERNAL_SERVER='1'
$env:E2E_PORT='4174'
pnpm exec playwright test tests/e2e/practice-review.spec.ts tests/e2e/practice-subjective.spec.ts tests/e2e/practice-paper-sheet.spec.ts
```

须预先按README启动4174 preview；E2E仅Mock服务，不能当作真实数据库联调。真实数据库、Stub、构建、视觉与真实AI结果分别记录在验收文档中。

2026-10-26用户更正：每日上限为人民币1元，不是1美元。新版配置使用 daily-budget-microcny；旧 daily-budget-microusd 仅作为更低的兼容上限。当前仅支持Flash，价格变更时须同时复核两币种费率，不能把该比例用于其他模型。来源：https://api-docs.deepseek.com/zh-cn/quick_start/pricing/ 。
