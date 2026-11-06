# 专项原文与阅读发布分离

用户明确要求四级题目及原文在专项管理完成，不必再作为阅读文章发布；阅读模块用于用户AI生成文章或有使用依据的免费外刊。

## 已实现

- 专项编辑页默认直接录入原文与问题，同页保存、预览、校对和发布；新原文只属于专项，不进入外刊阅读列表。
- 保留关联已有阅读文章的可选复用、版本切换、旧题组、JSON导入、发布历史和下架功能。关联不自动发布阅读文章；阅读下架不连带下架专项。
- 专项发布检查、公开目录/详情和开始作答不再要求来源文章PUBLISHED。原文版本与题组发布快照固定，原有作答/判分/错题保持原逻辑。
- V20给既有文本存储增加READING/PRACTICE用途；专用原文经专项接口编辑，文章保存/发布接口拒绝它，避免绕过题组锁和校对。
- 原文与题组原子创建，新建requestId幂等；保存检查题组锁，旧版本409。原文修改撤销校对，未变的原文不新建版本、不无故撤销校对。预览使用当前未保存原文，失败保留输入。

## 运行检查

终端加载 `scripts/use-workspace-tools.ps1`，Java检查另加载 `scripts/use-java21.ps1`。Maven实际使用Java21.0.12.1；测试连接项目专用MySQL `english_hero_test` 和Redis6381。日志均在忽略目录 `.tool-cache/practice-material/`，不输出口令或令牌。

| 命令/检查 | 真实结果 |
| --- | --- |
| `./mvnw.cmd verify -Pmysql-it -Dit.test=PracticeMaterialIT`（首轮仅运行该IT，跳过单测） | 修复前3项失败：旧发布要求先发文章，新材料接口404，符合预期。red.log。 |
| `./mvnw.cmd verify -Pmysql-it -Dit.test=PracticeMaterialIT,PracticeAdminIT,PracticeAttemptIT,PracticeMistakeIT,PracticeImportIT,ReadingAccessIT,ReadingProgressIT,AdminArticleIT,ReadingEnhancementIT,SchemaMigrationIT,PracticeBrowserCheck` | 65项后端单测及27项集成检查通过，退出0，backend-verified.log。含迁移幂等、旧阅读访问与编辑、专项发布/保存/历史/导入。 |
| `./mvnw.cmd verify -Pmysql-it -Dit.test=PracticeMaterialIT,AdminArticleIT,PracticeBrowserCheck -Dtest=nonexistent -Dsurefire.failIfNoSpecifiedTests=false` | 后续输入/预览/编辑入口修复后9项定向集成检查通过，退出0，backend-final-fixes.log。 |
| `./mvnw.cmd verify -Pmysql-it -Dit.test=PracticeMaterialIT -Dtest=nonexistent -Dsurefire.failIfNoSpecifiedTests=false` | 最后5项通过，退出0，backend-material-verified.log；包含空来源URL与NULL等价、校对不误失效、空正文400、缺标题可存待完善草稿。前次密集复跑触发测试库LOGIN_IP限流429，已仅清理测试库loopback/900秒单个计数夹具后复验；生产限流不变。 |
| `PracticeBrowserCheck` | 上述驱动内4个真实浏览器场景通过：管理端直接录入原文、保存/逐题校对/发布，学员1440/768/320px保存、恢复、交卷、错题重做。使用测试专属原创夹具并清理，没有发布开发库真题。脱敏日志 `.tool-cache/practice/real-browser.log`。 |
| `pnpm exec playwright test practice-material practice-admin practice-editor-recovery practice-import practice-attempt practice-history practice-study-layout` | 32项Mock浏览器检查通过，退出0，ui.log。 |
| `pnpm exec playwright test practice-material practice-editor-recovery practice-admin` | 最后12项通过，退出0，ui-final.log；含当前未保存原文预览、失败重试同requestId、输入保留及阅读文章复用。 |
| `pnpm build` | 最终构建/TypeScript检查退出0，build-final.log。本批未重跑前端单测，不以构建代替交互。 |

Mock浏览器测试使用受控Vite5176。真实浏览器通过测试后端和Vite5186执行，不是Mock接口成功的替代说法。已查看真实管理端桌面截图；移动交互由上述实际浏览器检查覆盖，最终视觉仍由用户体验确认。

独立审查指出两个问题：通用文章接口可绕过专项原文锁，以及预览显示已保存旧原文。已分别限制通用保存/发布和改用当前原文预览，加入接口和浏览器回归。开发中也发现MySQL JSON格式差异导致原文误判修改，改为解析后比较文档；最终5项检查确认校对保留。

## 本地交付与内容归属

通过进程身份和项目健康检查后重启本项目8080；新JAR `.tool-cache/practice-material/english-hero-material.jar`，PID97172，旧JAR保留。系统权限限制曾阻止进程核对，使用明确工具审批执行重启，没有关闭沙箱或改全局权限。开发库增量迁移V20，4174使用最终前端构建。

仅对已核实的题组1→原文11、题组2→原文12做本地用途归类；改前身份、题目用途和记录计数快照在 `existing-materials-before.json`。只将content_usage改为PRACTICE，不删除或改写原文、版本、题组状态、校对与用户记录；其他文章默认READING，不因关联专项自动移走。最初客户端编码和标题完整匹配检查中断于读取/保护检查，未执行写入；修正后脚本退出0。

`python .tool-cache/practice-material/check-live.py` 最终退出0，真实4174/8080核对：

- 题组1仍PUBLISHED v1、5题、5/5校对、发布检查为空；公共题库仍1组原创。
- 真题题组2仍DRAFT、来源未确认、未发布；公共PAST为0。
- 两组均materialOnly，原文11不再出现在阅读管理/公共阅读列表，直接公共阅读详情404；专项公开详情仍正常。
- 专项入口及管理新建/原题组页面HTTP200；临时核对会话已退出。

未安装依赖、提交或推送。未接AI生成/校对、未抓取外刊或新增真题发布；现有原型和其他模块工作保留。
