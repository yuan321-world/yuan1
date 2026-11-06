# 2026-10-25 主观题批改方案/原型与遗留接口补修

用户选择无既定服务商，先完成方案和原型。正式AI业务未实施，无外部模型调用，无新依赖/迁移，无提交推送。既有工作区修改与原型保留。

## 已完成

- [方案](../superpowers/specs/2026-10-25-practice-ai-review-design.md)：主动申请、每轮冻结、学习参考分、双语逐句批注、历史与失败恢复；接入仍须选服务/费用/数据政策、确认正式契约和实施计划。
- [独立原型](../prototypes/practice-ai-review-preview.html)：单项/整卷、写作/翻译使用一致的紧凑反馈区。演示提交与批改、逐句/修改稿/当次原文、失败重试、继续修改及历史隔离。自定义文本不套用预置分数，恢复示例需确认。右侧题号卡仅演示整卷布局，客观题标为示意。
- 修复此前错题接口遗留：Map.of().get(null) 在领域验证前抛NPE，现显式空值保护，缺失/null slot由500变400。其他未知/空白/主观槽位继续统一验证；不改变正常UI、归属/判分/来源冻结逻辑。

## 验证证据

证据在忽略目录 `.tool-cache/20261025-paper-slot-fix` 和 `.tool-cache/20261025-ai-review`。以下均实际执行。

| 检查 | 命令/结果 |
| --- | --- |
| Java/Maven实际版本 | 点加载项目工具与Java脚本后 `backend/mvnw.cmd -v`：Maven3.9.9、Java21.0.12.1，退出0 |
| 领域RED | 新增missingSlot测试，3项中1项失败：应为BusinessException，实际NPE；退出1，unit-red.log |
| HTTP RED | 缺失slot POST应400实际500；退出1，http-red.log |
| 后端GREEN | `./mvnw.cmd -o verify -Pmysql-it '-Dit.test=PracticePaperMistakeIT,PracticePaperIT#paperLifecycleIsAtomicPrivateAndFrozen'`：86单元/MVC、12真实english_hero_test集成，0失败/0跳过，BUILD SUCCESS退出0，green.log |
| 数据/幂等保护 | IT覆盖缺失、null、空串、空白、未知、写作/翻译；失败无重练/请求记录、源结果与错题计数不变；同UUID后续合法听力请求成功 |
| 原型交互 | frontend下 `pnpm exec playwright test --config ../.tool-cache/20261025-ai-review/playwright.config.mjs`：1440/320共8项，全部通过，退出0 |
| 原型范围 | 检查作答冻结、旧反馈原文隔离、定制文本拦截、错误重试、两个入口/题型切换、键盘标签；演示操作零HTTP服务请求；4组合×2视口无横向溢出 |
| 视觉 | 已查看 desktop-paper-writing.png、mobile-single-translation.png，正文/反馈/右侧示意卡无重叠，配色与紧凑样式沿用现有设计；用户视觉验收待确认 |

开发8080已安全更新为新JAR：核对原进程111140的项目路径和Java21身份后仅停止该进程，保留原JAR；新进程102416，状态UP。打包产物与运行JAR的SHA-256相同，`98311662109D2AC29D3CBC9BE50E5C2A6CD6D8B28284BB26F4EC4FB3B9FF8964`。更新前后只读业务快照所有计数/哈希相同，55待订正台账及原题库/原轮次保持。没有开发学员测试作答写入；4174前端无需重建。

## 下一步

先体验原型并审阅方案，确认反馈结构和评分口径。之后选择模型服务、明确预算与数据政策，再评审正式接口/数据库及实施计划。本轮通过不能作为真实AI准确性、费用或生产性能的验收。
