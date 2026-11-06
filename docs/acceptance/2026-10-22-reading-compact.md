# 正式阅读文章页紧凑化

2026-10-22，用户确认写作/翻译紧凑原型，并要求阅读精选里面的文章布局也紧凑。本轮调整正式 `/reading/:articleId` 文章页，不改文章列表筛选/每日精选逻辑、文章内容或学习记录。

## 修改

- 正文默认20px→18px、行距1.7→1.6；段间距19→14px，手机12px。移动端不再额外减1px，用户选择的字号在设备上保持一致。
- 标题桌面34→28px、手机27→24px；文章纸张内边距收紧，整体最大宽1280px；侧栏默认300→280px、卡片内边距24→18px，工具栏同步紧凑。
- 保留查词/选词选句、英中对照、全文翻译、收藏、生词、笔记/写作积累、阅读进度、阅读答题及相邻文章等既有能力。
- Aa设置仍能在16–26px调整字号及行距，恢复默认回18px/1.6；100%按新默认字号计算，放大一级显示106%，不改为不可调整的小字。手机工具按钮保留44px高度。

范围：reading-detail.css、reading/settings.ts、ReadingToolbar.vue和两个既有相关浏览器断言。正式主观题业务本轮没有开发。

## 实际验证

新增默认字号/标题尺寸断言在修改前失败（20px与18px不符），修改后构建/类型检查退出0。

`. ./scripts/use-workspace-tools.ps1` 后 `pnpm --dir frontend build` 退出0；受控4174执行 `pnpm --dir frontend test:e2e reading-enhancement.spec.ts reading-detail-upgrade.spec.ts reading-lookup.spec.ts -g 'guest detail reads|wide reader|native sentence selection|native selection offers|multiword selection'`：9项通过、1项在page.goto前遇到ERR_NO_BUFFER_SPACE浏览器临时加载错误。仅该项用mobile/--workers=1定向复验通过，未为环境错误改产品代码。

真实匿名访问现有文章8，1440/390px确认正文18px、Aa放大19px/恢复18px，无横向溢出、页面异常或阅读写请求；已查看两种截图。未登录真实用户、不新建阅读进度/成绩，不更改任何文章发布版本。

日志在.tool-cache/reading-compact/：red.log、build.log、browser.log、retest.log、live.log及live-results.json；截图article-1440.png、article-390.png。未运行无关套件或后端测试，未安装依赖、使用子代理、暂存或提交。4174最终dist生效，用户视觉检查待体验。
