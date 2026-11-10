# 阅读详情配色遗漏修复

用户截图指出“查看全文中文翻译”和“阅读打卡”仍是偏白色背景。根因为reading-detail.css遗留硬编码#fcfbfc/#fcfafb，绕过站点共享暖色变量。

改动：上述面板改surface-2/surface；同步清理附近阅读设置、侧栏标识、写作输入、练习选中/反馈、阅读统计中的白/粉灰背景，改为已有共享暖米白/柔绿变量；已完成徽标、日期状态、关键词悬停及译文边线也复用主题色。不改变布局尺寸、阅读/练习/打卡规则、文案或数据。

验证：`pnpm --dir frontend build`退出0（包含类型检查）；外部4174下`pnpm --dir frontend test:e2e reading-checkin.spec.ts reading-detail-upgrade.spec.ts -g 'reading completion refreshes|wide reader keeps'`退出0，桌面和320手机共4项通过，保留真实Mock流程验证阅读完成→练习提交→打卡状态更新与阅读设置/译文切换。只在既有用例增加截图，没有新增镜像样式测试。

日志`.tool-cache/reading-warm-{build,e2e}.log`，双端卡片截图`.tool-cache/reading-warm-checkin-{desktop,mobile}.png`，手机截图已人工核对暖底及浅绿状态。4174构建已更新；未代用户刷新可能在编辑的页面，无真实账号写入、无后端/API/数据库变更。
