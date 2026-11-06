# 专项学习界面正式布局验收记录

用户已确认按照考试家族版独立HTML修改正式页面。本记录为实现和自动化检查，不代替用户最终视觉确认。

## 修改范围

- 外层大学英语合并四/六级、考研英语合并英语一/二；高考禁用规划卡。新增 `/practice/cet`、`/practice/postgraduate`，保留 `/practice/cet4`。
- 类内级别、题型、来源等筛选、分页、历史和错题入口保留；未实现分类明确规划。只有四级仔细阅读可实际作答，未新增听力/整卷业务。
- 同一标签页按考试组记忆级别和目录筛选；历史/错题查看解析携带返回位置。仅记目录条件，不缓存答案或账户信息。
- 原文主区域＋右侧当前题目；原文不再受内部高度限制。专注原文、字号18/20/22、行距1.7/1.9/2.1；平板/手机切换原文与答题。真实保存/交卷/错题/查词逻辑继续复用。
- CSS限制在专项学习页，原阅读与背单词功能不修改。后端接口、数据库、正式题目内容不修改；真题草稿不发布。

## 实际验证

命令先在对应终端加载 `scripts/use-workspace-tools.ps1`；Java检查另加载 `scripts/use-java21.ps1`。浏览器Mock测试使用受控Vite5176：`E2E_EXTERNAL_SERVER=1`、`E2E_PORT=5176`。日志在忽略目录 `.tool-cache/practice-study-layout/`。

| 检查 | 结果与边界 |
| --- | --- |
| `pnpm test:unit` | 72项、20文件通过；退出0，见formal-unit.log。 |
| `pnpm build` | 最终退出0，含TypeScript检查，见formal-final-build.log。 |
| `pnpm exec playwright test practice-study-layout --project desktop` | 6项通过，1440/768/320px；包括家族往返、级别、规划分类、原文比例、设置、保存选择及无横向溢出。 |
| `pnpm exec playwright test practice-study-layout practice-history practice-attempt` | 最终18项通过；桌面/手机项目包含重复视口。增加PAST来源、technology主题往返恢复检查。见formal-targeted-final.log。 |
| 原有13组文件88项Mock浏览器回归 | 87项通过，1项桌面背单词等待拼写输入框超时。单独复跑 `pnpm exec playwright test vocabulary-dashboard --project desktop` 4项全通过，退出0；没有修改词汇模块或该用例。首次回归不能记成88项一次通过。 |
| `./mvnw.cmd verify -Pmysql-it -Dit.test=PracticeBrowserCheck -Dtest=nonexistent -Dsurefire.failIfNoSpecifiedTests=false` | 退出0，Java21.0.12.1；Java驱动1项、真实浏览器4场景通过。使用项目专用测试MySQL/Redis与隔离身份，真实登录、测试题发布、三视口保存/恢复/整组交卷/错题重做；夹具清理。未重跑全量后端测试。 |
| `python .tool-cache/practice-study-layout/check-public-runtime.py` | 最终退出0；4174四个学习入口HTTP200，真实公开CET4共1组、PAST为0。早期附加匿名访问受保护actuator路径返回401，移除无关健康探针后重跑。 |

本批新检查先复现旧界面缺少合并入口、原文比例不足；初次调整后发现移动阅读设置弹层遮挡答题按钮，已改为展开占位并通过三视口回归。独立代码审查发现返回考试类别丢失筛选，补充按家族记忆并以实际往返检查验证。

已查看正式Vue的桌面入口/阅读、手机原文和平板目录截图；Mock截图内容为测试原创数据。真实接口截图另存 `.tool-cache/practice/real-*.png`。当前浏览器控制不可用，交互检查通过Playwright执行，不声称已在用户当前浏览器逐项操作。

## 交接

已构建刷新本地4174预览，入口为 `/practice`。未安装依赖、提交或推送；保留其他已有修改。独立HTML保留用于参考。

待用户检查正式页面视觉；之后仍按原路线推进新题型方案与真实四六级内容。六级作答、选词填空/长篇匹配、听力、整卷、AI制作/校对均未因本批界面改动变成可用。
