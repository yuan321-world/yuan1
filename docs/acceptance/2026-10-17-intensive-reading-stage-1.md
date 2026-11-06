# 精读详情第一小阶段验收

日期：2026-10-17。用户确认宽版HTML并要求先启动项目后开始正式接入。仅交付本小阶段，不将外部词典/任意实时翻译算作完成。

## 交付范围

- 详情页最大1440px宽版白色正文卡、右侧已发布文章与本篇词汇，保留原收藏/进度/主动完成/旧版阅读规则。移动端单栏、相关文章置后、词汇按需打开。
- Aa收纳字号/行距/字体/对齐/恢复默认，默认英文20px/1.7（窄屏19px）。hover与重点词使用CSS Custom Highlight，不改变正文文本节点、字重或UTF-16偏移。旧浏览器不支持该API时不着色，查词仍可用。
- 重点词来自现有10篇原创编辑manifest，每篇3词；没有虚构四级词书或词频/生词率。已存词与重点词分开，只有服务端成功保存后刷新本篇状态。
- 新增公开版本tools上下文：相关已发布文章最多3篇、ID顺序相邻文章、原文匹配的重点词和参考译文。草稿/非本人旧版拒绝；游客读取不创建进度。
- 新增本人本版本words分页，按principal及已有reading_progress归属检查，复用user_word/source去重和原文位置校验。
- 原生单词/句子选择保留；释义浮窗提供明确标注的浏览器合成朗读，音标缺失不伪造。游客保存打开现有登录弹窗。
- 原创参考译文覆盖首篇及当前已发布的第9/10篇，合计18段/6句；随JAR交付、标题与规范化原文精确绑定，不改不可变文章版本，不自动发布草稿。中英对照/全文展开/已准备选句共用数据，来源明确待人工校对。

## 文件范围

后端：ReadingToolsController/Service、EditorialReadingCatalog，ReadingMapper的相关/邻接查询、DictionaryMapper.forms、VocabularyMapper/Service版本分页、SecurityConfig GET边界、pom内容资源配置。新增ReadingToolsIT、EditorialReadingCatalogTest。

前端：api/reading-tools、reading/useReadingTools/useReadingHighlights/settings、ReadingToolbar/ReadingRail、ReadingDetailView、ArticleBody、WordLookupPanel、详情专属CSS及布局容器；新浏览器/单元回归，原阅读测试调整到Aa入口。认证接口、数据库表和本地配置不变。

内容：editorial-translations.v1.json和内容说明；原有词典及文章正文不修改。

## 验证结果

加载scripts/use-workspace-tools.ps1；Maven加载use-java21.ps1并用mvnw -version验证实际Java21.0.12.1。以下成功项退出0，日志均在忽略目录.tool-cache。

| 检查 | 命令/结果 |
| --- | --- |
| RED复现 | ReadingToolsIT在无接口时2项失败；新浏览器在旧版缺Aa/侧栏时失败；合成朗读入口缺失先失败；Escape关闭翻译误关桌面侧栏先失败 |
| 完整后端 | backend下 ./mvnw.cmd verify -Pmysql-it；49单元/MVC＋50真实项目专用MySQL/Redis集成通过，零跳过，intensive-full-backend.log |
| 补充参考译文后 | verify -Pmysql-it '-Dit.test=ReadingToolsIT'；49单元＋2集成通过，包含3篇源文绑定/段落数及已准备句子断言，intensive-editorial-final.log |
| 前端单元 | pnpm --dir frontend test:unit；60项/15文件通过，含旧文章/旧身份迟到响应、失败保留已确认分页；intensive-unit.log |
| 类型/构建 | pnpm --dir frontend build；vue-tsc、tsc、Vite成功，intensive-build.log |
| 浏览器完整回归 | pnpm --dir frontend test:e2e；160 Mock通过，6项依赖真实账号/SMTP夹具的浏览器测试跳过；最终intensive-full-browser.log |
| 实际开发服务 | node .tool-cache/check-intensive-live.cjs；1440x1000、320x844，真实4174→8080→开发库：tools/重点词/相关文章、英中6段切换、原生选句参考翻译、Aa、词典/登录拦截、私人words401、游客零阅读写入、零pageerror/横向溢出；intensive-live.log |
| 视觉 | 实现者查看真实服务桌面完整页与手机首屏截图，用户视觉确认另行进行 |

Mock浏览器证明前端交互；私人收藏/版本隔离由真实专用测试库验证；开发库冒烟只读、未创建账号/写私人记录。未发送真实QQ邮件、未验证真实扬声器可听：合成朗读浏览器测试检查调用与词头，实际声音依赖本机语音能力，失败有局部提示。

## 审查与修复

独立Reviewer只读审查发现合成朗读函数没有入口（P2），已接入按钮并以失败→通过测试验证；随后窄范围复核关闭。真实页面检查另发现原生翻译dialog的Escape传播误收起侧栏，增加dialog[open]守卫与回归；独立复核未发现新问题。详见reviews/2026-10-17-intensive-reader-review.md。

最初实际冒烟脚本错误使用无baseURL的相对API地址、并将手机隐藏面板按可访问角色计数；已修正脚本并重跑。不是接口/数据缺陷。未把初次失败记为通过。

## 部署、清理与后续

先启动6380/8080/4174并确认200；最终8080运行固定包.tool-cache/intensive-reading/english-hero-reading-v2.jar，SHA256 8D5180745CCF7C6A9AB8F2E0BD89618CE9B683467D73AE91105276627C4466EC，启动工作目录backend。4174读取最新dist。开发Redis6380保留，测试Redis6381正常持久化停止，无临时8082。数据库V10、18表，无新增迁移/删数据/改账号/自动发布/自动Git提交。

下一小阶段仍需选定并核查中文词库及外部提供器许可，扩充词形还原和查词覆盖；后续再扩充剩余7篇参考译文，以及授权后的任意选句在线翻译与缓存。本阶段不接付费API、不泄露密钥、不宣称完整中文词典或所有文章已有译文。
