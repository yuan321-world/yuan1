# 2026-10-26 DeepSeek写作/翻译学习反馈

用户批准[实施计划](../superpowers/plans/2026-10-26-practice-ai-review-deepseek.md)，代码接入和离线验证已完成。真实密钥未填写、未发起付费调用；AI默认关闭。源码、框架、数据库原业务保留，无Git提交/推送。

## 实现范围

- 服务端本人已提交单项与已完成整卷W/T冻结来源；创建/读取/历史API、UUID幂等、同来源成功/活动任务复用、用户及总额限额。
- V29三表：任务/租约/冻结原文/结构化结果/使用量、幂等映射、按上海日期的微美元预算预留。仅新迁移，既有V1–V28不改。
- 官方DeepSeek Flash服务端HTTP适配，非思考/非流式JSON、8000输出token、实际wire JSON90000字节上界、128KiB响应、5秒连接/120秒整次超时。HTTP错误/空JSON/引用不匹配不给评分，不自动重复调用；无账号资料、密钥、思考链或任意工具发送。
- MySQL领取和费用预留短事务，外呼在事务之外；全局2个运行任务。180秒租约失效不补发，未发送10分钟排队过期退预算及尝试额度。重试可能再次计费，界面明确说明。
- 单项/整卷共用Vue/Pinia反馈区：发送说明确认、任务恢复、学习分/中英理由/逐句引用、信息遗漏、修改稿/当次原文。原卷成绩与参考入口不受影响，账号变化清除私人状态。
- 本地配置 `backend/config/application-ai.yml` 已由无密钥示例创建并忽略Git；正式配置/联调步骤见[配置说明](../ai-review-setup.md)。原始原型均保留。

## 实际验证

证据目录为忽略的 `.tool-cache/20261026-ai-review`。所有下述结果均有执行日志，未运行项单列。

| 验证 | 结果及边界 |
| --- | --- |
| 后端全量单元/MVC | backend-final.log：94项，0失败/0跳过，实际Java21.0.12.1 |
| 真实专用MySQL | 同次 `./mvnw.cmd -o verify -Pmysql-it '-Dit.test=PracticeReviewIT,PracticeReviewPaperIT,PracticeSubjectiveIT,PracticePaperIT#paperLifecycleIsAtomicPrivateAndFrozen'`：9项，0失败/0跳过，BUILD SUCCESS/退出0，仅english_hero_test |
| HTTP适配与最终打包 | 随后优化120秒future取消（get超时后cancel，避免orTimeout先置完成导致cancel无效）；backend-package-verified.log：8项相关单元，BUILD SUCCESS/退出0；本地HTTP Stub，没有DeepSeek请求 |
| 核心行为 | 本人/他人/匿名、未提交拒绝、20并发同来源仅一任务、命令重放/冲突、单项与整卷W/T来源隔离、领取/过期/结果冻结、费用结算、原轮次DTO不变、未知结果不重发、明确未发送不耗尝试 |
| 前端全部单元 | frontend-unit-final.log：31文件/133项，退出0；含同UUID重试、源槽位隔离、账号变化迟到数据、凭据变化状态复位 |
| 类型/生产构建 | frontend-build-verified.log：类型检查和构建退出0 |
| 双端浏览器Mock | frontend-e2e-verified.log：1440/320共22项，全部通过/退出0；含单项保存/提交/修改、整卷连续布局与新批改、发送确认、队列恢复、成功/失败、双语/原文、HTML文本安全、题号卡与无横向溢出 |
| 视觉 | 已检查desktop-writing.png和mobile-writing.png，反馈在原作下方，无重叠或横向溢出；用户视觉仍以真实体验为准 |
| 校准样本 | 10份原创作文+10份原创译文及人工预期；20份通过本地HTTP Stub请求/解析，未把Stub固定返回当作真实语义评测。未执行20份真实模型评测 |
| 审查 | 3项P2各有RED→GREEN证据并经Reviewer二次只读核实；另修复JSON转义上界，见[审查记录](../reviews/2026-10-26-practice-ai-review.md) |

早期测试并非全绿：接口未实现时400预期实际404；第一次V29创建因与V27同名FK失败；测试中代理字段访问NPE、raw state导致加载不更新均随后修复。新V29未成功时仅在专用test验证无新表后清除失败元数据重试，不改成功迁移或清库。部分测试命令的参数转义/夹具导航问题已修正，最终结果取上表，不覆盖失败日志。

## 部署与下一步

开发更新使用独立JAR并核对旧进程身份，显式 `--english-hero.practice.ai.enabled=false`，保留旧JAR；实际切换/业务指纹结果在下方补记。前端4174提供最终dist。

下一步在本地配置密钥、明确允许的真实联调预算后，才能开启AI并进行原创样本人工校准。当前没有验证真实输出质量、真实价格/延迟、账号适用的数据留存条款或生产多实例负载。不要将离线接入完成说成真实AI已可用。现在的开发启动带强制关闭参数；开启时须按配置说明重新启动并去掉该CLI覆盖。

部署补记：已核对原项目Java21进程102416及JAR路径，仅替换该服务；新PID113328，状态UP，匿名批改接口401。V29成功应用开发库。最终产物/运行JAR SHA-256均为AA5210693CFCF0ACB16CD2833C88FECD9E88C9B423C35D0F8353BA8786AFDD05。dev-before/after.properties所有记录的业务计数/哈希一致，55历史错题台账保持；没有开发验收作答写入。启动参数强制关闭AI，旧JAR保留。

补充传输用例：provider-errors-verified.log再次运行7项HTTP适配单元，401/402/500与截断finish_reason均为安全失败且无隐式重试，BUILD SUCCESS/退出0。该次只新增测试，未改变已部署生产源码/JAR。
