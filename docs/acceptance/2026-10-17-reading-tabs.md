# 阅读标签切换与历史筛选修复验收

日期：2026-10-17。仅修复当前阅读列表交互与历史查询，不改认证、数据库或文章内容。

## 根因与修复

1. load在切换时清空文章，长列表变为空态后文档高度缩短，浏览器回夹滚动位置。浏览器测试复现精选位移82px。面板预留一个视口最小高度、禁用内部滚动锚定，确保上方精选稳定；没有用连续scrollTo掩盖问题。
2. 历史控件此前被主动disabled，后端历史接口只支持分页。已接入keyword/categoryId/difficulty/sort；按本人实际阅读版本查询及计数后分页，保留旧版/下架记录。默认recent，可选newest/short/long。非法参数400，保留401及所有权边界。筛选无结果与真正无历史分别说明。
3. 界面、原API路径及默认历史语义兼容。无需迁移，未修改用户数据。游客控件可操作，但查看记录仍显示登录入口。

修改范围：ReadingListView.vue、api/reading.ts、reading-library.css；ReadingController/ReadingProgressService/ReadingMapper；ReadingEnhancementIT和reading-enhancement.spec.ts；相关文档。

## 验证

先加载use-workspace-tools.ps1，Maven另加载use-java21.ps1。

| 检查 | 命令/结果 |
| --- | --- |
| 缺陷复现 | 新增浏览器测试先失败：精选82px位移、历史控件disabled；后端真实库测试先失败：无匹配关键词仍返回total=1 |
| 后端相关回归 | backend下 ./mvnw.cmd verify -Pmysql-it '-Dit.test=Reading*IT,DictionaryLookupIT'，退出0；48单元/MVC＋11真实专用MySQL/Redis集成通过 |
| 最终补充 | ./mvnw.cmd verify -Pmysql-it '-Dit.test=ReadingEnhancementIT'，退出0；48单元＋3集成通过，涵盖组合筛选、总数/分页、长短排序、非法值、转义%、账号隔离及本人下架版 |
| 前端 | pnpm --dir frontend build、test:unit，退出0；类型/构建与57单元通过 |
| 阅读浏览器回归 | reading-enhancement/reading-flow/reading-progress/reading-lookup，桌面手机共20通过；最终增强测试14通过（含874px/320px），零跳过 |
| 实际开发服务 | node .tool-cache/check-reading-tabs-live.cjs，退出0；874px/320px标签切换精选位移<2px、控件启用、无横向溢出/pageerror，私人历史401 |
| 差异检查 | 本任务涉及路径git diff --check退出0；无关既有修改保留 |

日志位于.tool-cache/reading-tab-jitter-red.log、reading-history-red.log、reading-tabs-backend.log、reading-history-final.log、reading-tabs-unit.log、reading-tab-build.log、reading-tabs-browser.log、reading-tabs-final-browser.log、reading-tabs-live.log。

初次Maven未加载工作区工具导致wrapper无法定位缓存，重新按README加载工具后成功；一次未加-Pmysql-it只执行了48单元，不计为集成通过。上述最终集成结果均明确启用mysql-it。没有关闭安全机制，也没有真实QQ投递。

## 运行交接

8080新版固定包 .tool-cache/reading-tabs/english-hero-reading.jar；SHA256 93510E1715AA23CD17B07EE6625CD8A8DF1362DEC31506D03801089A70C889CD。4174新构建继续运行，开发Redis6380保留，测试Redis6381关闭。本轮真实开发服务检查只读，不创建用户/阅读记录；真实私人筛选写入场景由专用测试库覆盖。用户正式视觉确认待进行。
