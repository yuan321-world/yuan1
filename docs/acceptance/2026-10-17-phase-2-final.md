# 阶段2本地功能收尾与参考译文验收

日期：2026-10-17（Asia/Shanghai）。用户要求补齐剩余7篇参考译文并集中验收，继续保留Vue3/Spring Boot架构、原账号与库、认证/阅读进度/收藏/笔记本。没有进入主路线阶段3、开通收费服务或自动提交Git。

## 当前结论

阶段2现有本地功能闭环及自动回归收尾完成，正式项目保持8080/4174/6380。用户此前已认可当前页面基本无问题；本轮新增译文仍标注待人工校对，不能把AI独立复核当作人工语言/视觉验收。真实账号/SMTP浏览器夹具依赖的6项未在本轮执行，其他条件与限制见下表。

## 本轮改动

- editorial-translations.v1.json dataVersion从1.0升为1.1，新增7篇42段，全部10篇60段/13条准备句均匹配原稿。标题、英文正文和既有3篇译文保持；仅新增文中的“由于没有必要的数据”按审查润色为“由于缺少所需数据”。
- EditorialReadingCatalogTest增加全覆盖检查：10篇可用、60段中文非空、段落key与原文一致、每条准备句能匹配原文句键、标题或正文变化时不复用旧译文。原生产匹配逻辑不变。
- 7篇新译文由独立Reviewer逐段复核，无实质遗漏/误译或新增医疗、隐私、环境结论；13条开篇参考句与原文对应。角色是AI审查，未标为独立人工校译。
- 更新内容说明、API现状、数据库无迁移说明、进度、计划和运行入口。无新增接口、后端业务改动、数据库表变化、第三方密钥、收费请求或自动发布文章。

## 验证命令与证据

先加载scripts/use-workspace-tools.ps1；Maven加载use-java21.ps1，实际Java21.0.12.1。

| 验证 | 本轮结果 |
| --- | --- |
| 失败复现 | mvnw test -Dtest=EditorialReadingCatalogTest：原3篇目录下全10篇覆盖断言先失败；.tool-cache/reading-translation-red.log |
| 全后端 | backend目录 ./mvnw.cmd verify -Pmysql-it：54单元/MVC＋52真实专用MySQL/Redis集成通过，0跳过，退出0；reading-translations-full-backend.log |
| 最终措辞后内容检查与打包 | ./mvnw.cmd package -Dtest=EditorialReadingCatalogTest：2项内容绑定测试通过，退出0；reading-translations-final-content.log。此最终包包含润色后的中文；完整套件此前检查的是润色前、结构与绑定相同的数据 |
| 前端单元 | pnpm --dir frontend test:unit：60项/15文件通过，退出0；reading-translations-front-unit.log |
| 类型/构建 | pnpm --dir frontend build：vue-tsc/tsc/Vite通过，退出0；reading-translations-front-build.log |
| 完整浏览器 | E2E_EXTERNAL_SERVER=1 E2E_PORT=4174，pnpm --dir frontend test:e2e：178 Mock桌面/手机通过，6真实账号/邮件夹具条件测试跳过，退出0；reading-translations-full-browser.log |
| 临时新包检查 | 8082读取开发库两个已发布版本的tools，均6段中文且available=true；没有自动发布其他草稿 |
| 正式服务阅读 | node .tool-cache/check-reading-translations-live.cjs：1440/320px真实4174→8080检查原文/对照6段/全文6段及来源/准备选句、Aa、重点词、相关文章、私人words401、游客保存触发登录；零游客阅读写入、横向溢出、pageerror，退出0；reading-translations/live-reading.log |
| 正式服务词典 | node .tool-cache/check-dictionary-live.cjs：1440/320px实际新词中文/音标、紧凑卡、英美入口、原文例句/来源/Escape、7种错拼拒绝，零游客阅读写入/pageerror，退出0；reading-translations/live-dictionary.log |

Mock浏览器验证交互；私人数据隔离/权限/并发/版本由真实数据库集成覆盖。开发服务检查只读，不操作真实账号的学习数据。未用QQ真实收件箱做自动浏览器验收，未把合成语音参数测试等同扬声器/本机口音验证。未清空库或新建空库重演V1→V10，不把迁移幂等测试写成空库安装通过。

## 阶段2验收范围对照

| 功能 | 状态与边界 |
| --- | --- |
| 文章列表/精选/组合筛选/分页/空态/重试 | 本地已实现，集中回归通过；精选独立于筛选 |
| 游客公开阅读/个人接口认证 | 本地已实现；游客不写进度，个人数据服务端归属检查 |
| 文章收藏/登录后续操作/取消/持久化 | 本地已实现；真实库及Mock交互回归通过 |
| 本人历史/阅读进度/继续阅读/主动完成/冲突 | 本地已实现；原文版本绑定、用户隔离与并发回归通过 |
| 字典/词形/生词来源/取消与恢复/笔记本 | 本地离线10,006词头，既有稳定ID和来源保持；多候选单卡切换不混状态；未保证任意单词全覆盖 |
| 英文/Aa/关键词/段落对照/全文中文 | 本地已实现；10篇准备60段，实际开发仅2篇已发布，其他仍由管理员审核后发布 |
| 选句翻译 | 13条准备句和完整段落可复用；任意新句在线翻译未接入，不冒充完成 |
| 管理员草稿/导入/预览/发布/下架 | 既有实现与完整回归保持；不为验收自动发布开发稿 |
| 人工内容校对/上线词典授权 | 仍需人工确认；ECDICT仅按用户确认用于本地开发，许可与混合来源事实保留 |

## 运行交接

- 正式8080固定包：.tool-cache/reading-translations/english-hero-reading.jar。
- SHA256：6EC67636267A8AFDC72E28D2F3C555CA7B6F5AF0F3030C2CCFD03BD60B78B3CE。
- backend目录启动：java -jar ../.tool-cache/reading-translations/english-hero-reading.jar --english-hero.bootstrap.enabled=false --english-hero.dictionary.ecdict.enabled=true。
- 4174提供本轮构建；开发Redis6380保留，临时8082候选和测试Redis6381已核实后关闭。
- 开发数据仍V10/18表、原有2篇已发布；译文扩容不改正文/发布/私人记录，不替换本地认证、邮件或数据库配置。

下一步不自动实施：真实账号/邮件浏览器专项、人工文章校对、生产词典授权审查、按用户选择的在线补查/翻译服务；进入主路线阶段3需新的明确安排。
