# 真题／原创入口与生成按钮占位

用户要求真题单独入口，原创可以收录本人或用户借助AI制作的题目；明确生成模块先不做，仅保留位置按钮。

- 正式专项首页提供真题训练、原创练习，默认真题。考试家族入口继续四/六级合并、英语一/二合并。
- 两入口进入题库分别限定PAST/ORIGINAL；清空、刷新、翻页、返回保持来源，原创不带考试年月套次。旧无collection链接及管理端原来源筛选继续可用。
- 生成题目为禁用规划按钮，没有AI请求/生成表单。用户导入入口同样明确规划；现有管理员录入、校对、发布和JSON导入保留。没有新增普通用户发布权限。
- 本次无后端、数据库、题目内容写入或发布，原HTML原型保留。

## 验证

终端加载 `scripts/use-workspace-tools.ps1`，Mock浏览器使用受控Vite5176，环境 `E2E_EXTERNAL_SERVER=1`、`E2E_PORT=5176`。

| 检查 | 结果 |
| --- | --- |
| `pnpm exec playwright test practice-entry-sections --project desktop` 修复前 | 1440/390px两项因缺少来源入口失败，符合预期，red.log。 |
| `pnpm exec playwright test practice-entry-sections practice-study-layout practice-history practice-attempt practice-admin practice-editor-recovery practice-import` | 34项通过，退出0，browser.log。包含来源隔离、清空来源不混合、返回/刷新、生成禁用与旧专项行为；桌面/手机项目包含重复视口。 |
| `pnpm build` | 构建与类型检查退出0，build.log。没有重跑后端或前端单测。 |
| `node .tool-cache/practice-entry-sections/check-live.cjs` | 真实4174匿名浏览器检查退出0，无Mock；真题/原创两入口请求来源正确、返回数据不混合，读取到PAST/ORIGINAL各1组。生成按钮禁用。未创建作答或发布记录。 |

日志与截图在忽略目录 `.tool-cache/practice-entry-sections/`；已查看1440px和390px入口截图。只记实际检查，不代替最终用户视觉确认。当前真题数量是现有公开接口读取结果，不计为本批发布或重新校对。

4174已使用最终构建，打开 `/practice` 检查。未安装依赖、提交或推送，保留其他任务修改。
