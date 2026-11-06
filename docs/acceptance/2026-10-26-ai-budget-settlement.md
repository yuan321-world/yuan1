# AI费用结算修复验收

用户完成手动体验后授权继续改善费用预留。根因是客户端先验证内容再读取usage，失败时用量丢失，worker仅传错误码，失败统一保留整笔预留。修复仅限现有链路，无新增框架/表/迁移。

实现：先提取安全用量，仅已知Flash型号、可无损转换long的整数、输入0–90000/输出0–8000范围可信；负值、超大整数、缺项和未知型号不用于退额。INVALID_OUTPUT仍FAILED且result=null，但可信用量随安全Failure传给worker和预算事务，按峰值未缓存价保守结算，释放剩余预留。原文/上游body/密钥不进入费用提示或日志。

确认未发出的UNAVAILABLE/INPUT_TOO_LONG和官方400/401/402/422/429拒绝结算0；403/5xx、响应损坏/超时或不可信usage继续保留。失败事务锁有效RUNNING、lease token与lease_until，状态/用量/账本一次提交，重复或过期结果不能重复释放。不自动重发、不释放旧未知费用。依据[错误码](https://api-docs.deepseek.com/quick_start/error_codes/)和[峰值费率](https://api-docs.deepseek.com/quick_start/pricing/)。

本人记录派生budgetStatus及budgetAmountMicrocny，显示本地预留/用量结算/释放/待确认；微元金额向上取整，明确不是平台实扣。缓存/低谷费率可能更低。每日人民币1元、固定单笔50000微美元预留、次数/权限/重试限制保持。

验证记录（均在忽略目录.tool-cache/20261026-ai-live）：

- settlement-red.log：两项新断言在旧客户端失败（缺少用量及未生成标记）。
- 首轮真实库验证因专用测试Redis未启动失败，启动6381后重跑；第二轮两项新IT误用仅接受200的helper处理正常202受理，改为明确断言202，未放宽业务校验。
- settlement-verified.log：Maven实际Java21；`-o verify -Pmysql-it -Dtest=DeepSeekReviewClientTest,PracticeReviewWorkerTest,PracticeReviewRulesTest,PracticeReviewBudgetCurrencyTest -Dit.test=PracticeReviewIT,PracticeReviewPaperIT`，14单元、8真实english_hero_test集成，BUILD SUCCESS、退出0。覆盖异常用量/整数溢出、无效反馈结算、明确拒绝释放、重复/过期结果、并发/幂等/归属。
- settlement-frontend-final.log：pnpm --dir frontend build，含Vue/TS类型检查，退出0。
- settlement-browser-final.log：review/subjective/paper-sheet三组1440/320 Mock回归24 passed（23.8s），退出0。新增结算/释放/未知提示且无效结果不展示评分；不是新增真实模型质量测试。
- 无活动任务后核对PID11392身份，仅更新该后端为PID6192，AI开启。JAR SHA256：EF55C3BD71D63E4D7D095BFDF65E6483BCE89325A15409378F34A961D14C3379；旧JAR保留。4174代理API服务/数据库UP。
- settlement-dev-before/after.properties所有业务指纹一致；今日费用占用5986微美元、活动任务0保持。没有调整开发账本或发送真实模型请求。

未运行完整后端所有测试。固定预留仍较保守，未实现平台逐请求账单核对，未知调用仍可能占额度；不宣称未知费用已退款。
