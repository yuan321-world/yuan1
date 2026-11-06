# 整卷练习最终独立审查

主任务修复状态（2026-10-23）：下述Important已修复。新增远端仍IN_PROGRESS的另一页作文/本页译文测试先RED，随后改为采用服务器快照、独立保留本地恢复副本；后续显式恢复译文不会清空作文。combined frontend11单元与最终6整卷Mock退出0，日志conflict-red/green及ui-reviewed。Minor按范围延后，输入区仍展示真实建议范围。下文保留审查时的原始发现。

审查日期：2026-10-23。只读检查当前工作区整卷功能及明确列出的接入点，对照2026-10-22整卷设计和实施计划；未以HEAD差异扩大到历史未追踪功能，未运行测试、访问数据库或修改产品代码。

## Important

1. **冲突恢复会以新锁版本覆盖其他标签页答案** — `frontend/src/stores/practice-paper.ts:27`，`reloadKeepingLocal()`。
   - 复现路径：A、B同时打开同轮次；A保存作文；B仍持有空作文，编辑译文后保存得到409；B点击“重新读取并保留本地副本”，再编辑译文或保存。
   - `apply(a)`先接受服务器新锁版本和答案，但IN_PROGRESS分支随即回填完整旧`local.responses`、清除冲突。`flush()`随后携带新锁版本提交这七个旧槽位，导致A的作文被清空。该分支也未创建独立`recoverySnapshot`，与按钮及方案的冲突保护语义不符。
   - 修正建议：重载时采用服务器快照，将本地七槽另存可复制恢复副本；需要回填时必须显式解决冲突，不能隐式重基完整旧快照。补充两个标签页分别编辑不同主观槽位、远端仍IN_PROGRESS的回归。已有单元仅覆盖远端COMPLETED，无法发现此缺陷。

## Minor

- `frontend/src/views/PracticePaperAttemptView.vue:38`的交卷字数提示硬编码为少于120词，不读取冻结题目的`minWords/maxWords`，也不提示超过上限。输入区已展示实际建议范围，确认框宜使用同一范围。

## 范围与证据

- 检查了PracticePaper后端服务/Mapper/规则/DTO/Controller、V26、前端API/store/整卷管理和作答界面、结果组件，以及关联鉴权、音频与路由入口。重点检查本人归属、进行中参考隐藏、七槽验证、版本引用、下架锁定、新开始及事务提交/幂等。
- 在上述范围内未确认其他Critical/Important缺陷。已知目录分页问题由主任务修复，不重复列入。
- 阅读已有`regression.log`：16 tests，0 failures/errors/skipped，BUILD SUCCESS；阅读`real-results.json`：1440/320流程成功。它们是主任务既有运行证据，本审查未独立重跑，也不代替用户视觉确认。
- 开发库迁移/部署及本次发现的修复验证由主任务交付记录补充。此审查记录保留发现时状态，后续修复可在此追加证据。
