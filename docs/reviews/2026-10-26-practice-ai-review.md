# DeepSeek学习反馈独立审查与修补

范围：新增practice/review后端职责、V29、配置、共用前端反馈组件/store、单项与整卷入口和测试。旧大规模未提交工作未纳入审查。Reviewer只读，不执行数据库/付费调用。

发现3项P2，均已修复并复核：

1. Worker取得permit后claim抛异常会永久耗尽名额。新单元RED：期望第3次继续领取，实际只有2次；改用成功移交executor前finally归还名额，GREEN。
2. 明确未发送的QUEUE_EXPIRED仍占3次/每日尝试数。新真实库IT RED：第4次期望202、实际429；两处COUNT排除该类别，活动/已发送任务仍占额度，GREEN。
3. 同账号凭据generation变化后loading/requesting不复位。新前端单元RED：期望false实际true；分离owner/epoch清理guard与generation写入guard，GREEN。

Reviewer二次只读核实上述三处落实；新实际requestJson字节上界检查在预留前与发送前一致，未发现明显退化。主代理另自审发现原始字符串不计JSON转义会低估输入上界，新增控制字符用例先失败（本应拒绝，实际发送），再统一实际wire JSON≤90000字节，测试通过。

早期联调发现的约束名冲突、Spring代理直接字段访问、Vue首次state返回raw对象问题均已修复，最终验收用例保留。数据库失联时诊断维持安全边界，不回显配置/正文；只读开发快照经明确审批重试成功。

最终证据见[验收](../acceptance/2026-10-26-practice-ai-review.md)。真实模型质量/实际费用/账号数据政策尚待联调，自动化测试不能代替这些验收。
