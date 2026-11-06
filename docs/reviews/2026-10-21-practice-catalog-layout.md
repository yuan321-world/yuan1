# 专项目录布局独立审查

## 范围与结论

按主任务提供的 `.tool-cache/practice-catalog-layout/before/` 快照，仅对照本次以下三个产品文件，以及新增 `practice-catalog-list.spec.ts` 和修改后的 `practice-section-filters.spec.ts`。没有将整个工作区的既有修改当作本次差异，也未修改被审查代码。

- `frontend/src/views/PracticeLibraryView.vue`
- `frontend/src/components/practice/PracticeFilters.vue`
- `frontend/src/styles/practice.css`

在上述差异中未发现可证实的 P0–P2 问题。

核对结果：考试家族入口与栏目导航保留；阅读题组仍分别列出，新增紧凑行仅作用于目录题组，不改变历史卡片；真题年份/月筛选直接显示；听力无题型或单项入口，旧 `listeningType` / `listeningMode` 参数被清理，显示规划与未接入整套试卷的空态。原创题目目录、管理端筛选默认行为、历史/错题及开始/继续练习的既有处理未被本次差异替换。

## 实际验证

使用 README 的项目工具初始化与现有 Playwright 命令，对当前源代码的 Vite 开发服务执行 Mock 浏览器检查。

| 检查 | 结果 |
| --- | --- |
| `pnpm --dir frontend test:e2e practice-catalog-list.spec.ts practice-section-filters.spec.ts --reporter=list --output=.tool-cache/reviewer-playwright`，外部服务模式 | 退出 0，桌面/手机共 6 项通过 |
| `pnpm --dir frontend test:e2e practice-entry-sections.spec.ts practice-history.spec.ts --reporter=list --output=.tool-cache/reviewer-regression`，外部服务模式 | 退出 0，共 6 项通过，覆盖真题/原创入口、来源锁定、清空筛选、刷新和历史状态查询 |

首次自动启动 webServer 的定向运行已输出 6 项成功，但 Windows 子进程清理未退出；随后复用该服务完整重跑取得退出 0，再停止本审查启动的命令。没有将首次未结束的命令记为退出 0。

本审查未执行真实数据库/后端联调、生产构建或用户视觉验收；未据此宣称听力业务已实现。可访问性和 CSS 检查结合本次差异与 [Web Interface Guidelines](https://raw.githubusercontent.com/vercel-labs/web-interface-guidelines/main/command.md)，未将既有未修改问题扩大为本次发现。

## 补充审查：聚焦阅读选择器修复

主任务扩大浏览器回归时发现聚焦阅读会隐藏整个练习页面，随后明确追加两个文件中四个 CSS 选择器的独立复核。按同一 `before/` 目录新增的组件快照对照：

- `frontend/src/components/practice/PracticeClozePlayer.vue:575`
- `frontend/src/components/practice/PracticeMatchPlayer.vue:530`

差异仅将 `:global(父级) 后代` 改为 `:global(父级 后代)`；各自两条规则，没有业务代码、模板、属性值或媒体阈值变更。本补充范围未发现剩余可证实的 P0–P2 问题。

独立使用项目已安装的 `vue/compiler-sfc` 对两个组件的快照和当前 scoped style 调用 `compileStyle()`，最终命令退出 0，四次编译均无错误。快照中的两个隐藏规则实际都编译为 `.practice-study-attempt.practice-reader-focus{display:none}`，并把工作区布局属性错误作用于根元素；当前输出保留完整后代选择器：

```css
.practice-study-attempt.practice-reader-focus .practice-cloze-workspace
.practice-study-attempt.practice-reader-focus .practice-cloze-bank
.practice-study-attempt.practice-reader-focus .practice-match-workspace
.practice-study-attempt.practice-reader-focus .practice-match-questions
```

因此隐藏规则现在仅匹配词库/题目侧栏，聚焦阅读根元素不再被这两处规则隐藏；工作区网格规则也落在正确容器。CLOZE 的 921px、MATCH 的 961px 最小宽度约束保持。此前两次编译命令分别因 PowerShell 原生参数转义、直接导入非顶层依赖失败（均退出 1）；改为 stdin 传入脚本并从 Vue 的公开 `compiler-sfc` 入口读取后，取得上述成功结果。没有为验证修改源码、安装依赖或调用数据库。

本补充只独立验证差异及编译产物；主任务后续完整浏览器回归结果由主任务验收记录单独记载。
