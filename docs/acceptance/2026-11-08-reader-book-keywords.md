# 阅读紧凑布局、词书重点词及字体

用户明确要求正文紧凑、重点词跟随当前背诵词书、字体更多、删除本篇生词按钮并默认打开。沿用已有Vue/Java模块和工具接口，不增加框架或数据库结构。

## 交付

1. 正文默认18→17px，行距1.6→1.5，段距14→10px，正文与头部留白收紧；仍可用Aa调整字号、行距和对齐。作者/原文链接仍可见，完整转载说明折叠保存，正文、句子键及定位不变；查词说明收为一句。
2. 已登录tools.keywords改按当前learning_source匹配：BOOK使用wordbook_entry，PERSONAL使用本人personal_enrolled；仅匹配正文完整单词与已有dictionary_form，大小写/合法连字符归一，不把smallness误作small。返回按正文首次出现排序，forms仅本篇实际形式；不创建学习词、轮次、收藏或阅读进度。游客使用原编辑精选词。
3. keywordSource/keywordSourceKind增量字段明确来源；原tools路由与文章版本权限、身份检查/no-store保留。开启重点词、页面重新可见/获焦点会重读来源；旧请求不能覆盖新文章/账号/请求。高亮使用原CSS Highlight，不包裹或重建正文。
4. Georgia、Times New Roman、Palatino、Cambria、Arial、Verdana、系统字体七种本机字体栈，缺失时fallback，不联网下载；保留旧serif/sans键。字体选择有明确可访问名称。
5. 删除“本篇生词”开关与关闭浮层；本篇词汇在桌面侧栏、手机正文下方默认可见。重点词列表内部最高240px，保留所有词和已有生词分页/点击查词/登录/错误重试入口，不遮正文。

## 文件

后端新增ReadingWordbookKeywords，ReadingToolsService复用文章权限后按身份选择来源；新增ReadingWordbookKeywordsIT。前端修改ReadingDetailView、ReadingToolbar、ReadingRail、reading/settings、api/reading-tools和reading-detail.css。新增reader-book-ui端到端用例，既有读取/打卡/写作测试更新删除开关及17px默认预期；未修改进度保存业务。

## 验证

| 检查 | 结果 |
| --- | --- |
| `pnpm --dir frontend test:unit` | 退出0，205项；`.tool-cache/reader-book-ui-unit.log` |
| `pnpm --dir frontend build` | 退出0，类型及生产构建；`.tool-cache/reader-book-ui-build.log`；既有包体积提示保留 |
| 外部4174：`test:e2e reader-book-ui.spec.ts reading-detail-upgrade.spec.ts reading-progress.spec.ts reading-enhancement.spec.ts` | 退出0，桌面/320手机66项；`.tool-cache/reader-book-ui-regression.log` |
| `test:e2e reading-checkin.spec.ts reading-closeout.spec.ts writing-materials.spec.ts -g 'reading completion refreshes\|article checkin conditions\|published writing keeps'` | 退出0，双端6项；`.tool-cache/reader-book-ui-tools-e2e.log` |
| Java21：backend `./mvnw.cmd -o verify -Pmysql-it -Dit.test=ReadingWordbookKeywordsIT,ReadingToolsIT` | 退出0，128单元+4真实专用MySQL IT；`.tool-cache/reader-book-keywords-backend.log` |

新增用例先在旧实现失败（tools无来源/个人词不匹配、开关仍存在/刷新来源不更新），再验证新实现。数据库IT覆盖词书切换、完整词/词形/大小写、个人来源隔离、游客无个人来源、no-store、无学习分配及原版本权限。前端验证默认常驻非fixed词汇区、七字体及切换/恢复、来源说明完整保留、重点词开启重读当前来源且仅高亮对应完整词、原正文Text节点与进度恢复不变、无横向溢出。

独立只读审查未发现实质问题；另外用60段Mock文章和两次快速focus验证来源更新Old→New，scrollY仍2068、正文同一个Text节点不变，刷新期间无进度写请求。该检查与真实数据库IT分开，不冒充当前用户数据验证。

双端截图`.tool-cache/reader-compact-{desktop,mobile}.png`已人工查看；内容为明确Mock的短文，正式原文未改。

## 本地运行与边界

通过进程/JAR路径和构建哈希校验后，把8080更新为`.tool-cache/vocabulary-full/english-hero-reader-book-keywords-20261108.jar`，保留已有本地配置和功能开关；更新本地启动脚本的JAR/哈希。4174代理系统健康验证数据库UP。未公开部署或推送GitHub，未调用AI，不修改真实学习/收藏记录，不代用户刷新当前可能有未同步位置的文章。刷新页面后加载新前端。
