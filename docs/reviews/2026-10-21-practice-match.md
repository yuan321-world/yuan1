# 四级长篇匹配正式实现独立审查

审查依据：`docs/superpowers/specs/2026-10-21-practice-match-design.md` 与同日实施计划。按 `.tool-cache/practice-match-implementation/before/` 对照本次 practice 后端、V22、MATCH 新组件及 helper、专项 API/Pinia、管理编辑/导入/预览/校对、目录与学习页面；没有将整个 dirty tree 当作本次差异。审查者未修改产品代码、未提交、未启动 Maven 或访问数据库。

## 结论

初审三项P2均已修复，修复代码及定向单测已独立复核，当前审查范围未发现剩余可证实P0–P2。最终5个定向测试文件共32项通过、退出0；下方如实保留各次结果。

## 已修复发现

1. **P2：手机“定位所选段落”仍停留在题目标签，目标原文不可见。**
   - 初审位置：`frontend/src/components/practice/PracticeMatchPlayer.vue:182`、`:191`、`:533`。
   - 触发：视口不大于960px，已有匹配选择，切到“题目与匹配”并点“定位所选段落”。`locateSelected()` 只调用 `locateParagraph()`；该函数设置目标并 focus/scroll，却没有把 `mobileTab` 改为 `article`。此时原文被媒体查询 `display:none` 隐藏，用户仍看不到目标段落。
   - 结果定位另有显式切换标签，因此只覆盖结果定位的测试没有发现此入口问题。应在定位前打开原文，并保留当前题与答案。
   - **已修复并复核：** `locateParagraph()` 现在先切换原文标签并等待DOM更新，再取目标ref、定位和滚动；当前题与答案不变。

2. **P2：同一轮次保存响应会重置手机标签、筛选及撤销栈。**
   - 初审位置：`frontend/src/components/practice/PracticeMatchPlayer.vue:227`。
   - 触发：手机题目标签进行选择或标记，后台保存返回新的 attempt 对象。监听器返回由三个身份字段组成的新数组，依赖中的 attempt 对象被替换时会重新运行，回调无条件恢复原文标签、全部筛选并清空撤销。即使轮次和版本身份未变，正常保存也会打断答题状态。
   - **已修复并复核：** 主任务在768px真实浏览器独立复现；监听源现改为由三个字段构成的稳定字符串，同身份保存响应不触发重置，新轮次仍会重置。对应新单测在独立第二次测试中通过。

3. **P2：保存冲突后加载已交卷记录，未确认的本地标记仍显示为冻结结果。**
   - 初审位置：`frontend/src/stores/practice-attempt.ts:26`；消费路径 `frontend/src/components/practice/PracticeMatchPlayer.vue:66`。
   - 触发：A页本地把某题标记为true，B页先以false交卷；A页保存收到409后点“加载最新记录并保留选择”。`reloadKeepingChoices()` 跳过 pending 对应题目，然后仅因 COMPLETED 清除 pending。该题的本地 `flags` 没有从已完成响应恢复；页面变为 `dirty=false`，而组件优先读取 props.flags，因此仍显示并允许筛选出服务端实际未标记的题。
   - **已修复并复核：** 完成状态现在无条件用服务端冻结响应恢复全部 choices/flags，然后清空待保存状态；进行中的轮次仍保留本地编辑。新冲突完成回归在独立第二次测试中通过。

## 已核对路径

- 未交卷 DTO 不含正确答案、解析或成绩；MATCH 选择按冻结原文中的字母验证，允许同段多题，CLOZE 仍限制重复词。
- 保存先验证整批题号、字母和当前位置，再执行写入；乐观版本、用户归属、事务和旧客户端省略 flagged 的保留行为保持。
- MATCH 重练完整冻结十题；DETAILED 仍从错题子集建立轮次。发布版本和已开始练习继续引用旧原文与试题。
- 新 V22 扩展约束；没有改写已有迁移。管理校对、发布及 JSON 导入使用 MATCH 专属检查；没有自动发布或伪造真题路径。
- Choice 从 record 改为带委托 JsonCreator 的 class；当前实际入站使用 Map JSON，未发现产品代码中将该类序列化为外发载荷的路径。旧两参数构造和省略/null/布尔标记已有定向测试；未发现可证实的兼容性问题。

## 本审查实际验证

`pnpm --dir frontend test:unit tests/unit/practice-match-player.spec.ts tests/unit/practice-match-admin.spec.ts tests/unit/practice-attempt.spec.ts tests/unit/practice-cloze-admin.spec.ts tests/unit/practice-cloze-player.spec.ts`

- 第一次沙箱执行因 pnpm 配置权限失败，退出1；随后经明确工具审批重跑。
- 重跑退出1：5个文件，29项通过，1项失败；失败为执行者新加入的手机定位回归（期望 article，实际 questions），与第1项发现一致。执行时其余修复仍在并行进行，不能将此结果记为最终通过。
- 三处修复后的第二次独立运行退出1：31项通过，1项失败。手机定位的data-tab/target断言已通过，scroll mock断言尚未等定位函数跨过第二次nextTick；已通知执行者补齐异步等待后重跑。未以产品代码复核替代测试通过。
- 最终独立运行（2026-10-21 10:25:21）退出0：5个测试文件、32项全部通过。定位测试补充 `flushPromises()` 等待，验证切原文/目标段落/实际调用滚动/不改答案；同身份保存保留交互与完成冲突恢复服务端标记回归均通过。无新增依赖。
- 本审查没有执行真实 MySQL/Redis、浏览器、生产构建或用户视觉验收；这些验证由主任务分别记录，不能与上述单测混同。
