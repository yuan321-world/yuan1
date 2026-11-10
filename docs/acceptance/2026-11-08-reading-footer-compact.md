# 阅读底部收紧与收藏态增强

用户依次反馈文章收藏状态不明显、阅读底部工具卡片松散。两项均为已有UI样式调整。

- reading-library.css：已收藏书签使用accent-dark，图形填充currentColor，未收藏保留空心；不改收藏存储或请求。
- reading-detail.css：全文译文上间距、写作积累外间距、阅读理解及打卡卡片间距统一约12px；摘要行内边距约11—13px、左右16px，工具标题16px，边角14px；打卡卡片内边距16px、状态徽标小号、提示段距6—8px，减少空白。内容、展开交互、原生正文DOM、阅读/练习/打卡规则全部保留。

验证：前端类型及生产构建退出0（日志`.tool-cache/article-favorite-dark-build.log`、`reading-footer-compact-build.log`）。外部4174的既有Mock用例：`reading-enhancement.spec.ts -g favorite`桌面/手机4项通过；`reading-checkin.spec.ts reading-detail-upgrade.spec.ts -g 'reading completion refreshes|wide reader keeps'`4项通过；`writing-materials.spec.ts -g 'published writing keeps'`2项通过。三个命令均退出0。

只在原收藏用例增加截图，未新增重复样式测试。截图`.tool-cache/article-favorite-dark-{desktop,mobile}.png`及`.tool-cache/reading-warm-checkin-{desktop,mobile}.png`（后者由紧凑版回归更新），手机截图已核对实心深绿收藏与紧凑打卡信息。日志`.tool-cache/article-favorite-dark-e2e.log`、`reading-footer-compact-e2e.log`、`reading-footer-writing-e2e.log`。

4174已加载新构建；未代用户刷新可能带未同步进度的当前页面，不写真实用户记录，无接口/后端/数据库修改或公开部署。
