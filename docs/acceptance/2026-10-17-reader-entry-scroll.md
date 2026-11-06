# 精读入口滚动修正

日期：2026-10-17。

根因：ReadingListView为已有阅读记录生成version/sentence参数；ReadingDetailView加载后自动恢复位置，即使记录为首句offset=0也调用scrollIntoView，使标题与工具栏离开首屏。无参数但已有保存版本的记录也存在相同路径。

修正：ReadingDetailView.scrollPosition统一将当前版本首句且start/end均为0视为文章起点，滚到页面顶部；其他段落、首句内部非零偏移、显式选词范围仍按既有逻辑定位。没有删除记录、改写数据库或取消中途续读能力。

验证（本轮命令均在项目根目录加载scripts/use-workspace-tools.ps1）：

- RED：4174旧构建运行reading-detail-upgrade.spec.ts的resume at测试，桌面和320px首句起点2项失败（实际已下滑），其他4项通过；日志.tool-cache/reader-entry-red.log。
- pnpm --dir frontend build：退出0，包含vue-tsc/tsc/Vite；日志.tool-cache/reader-entry-build.log。
- E2E_EXTERNAL_SERVER=1、E2E_PORT=4174，pnpm --dir frontend test:e2e reading-detail-upgrade.spec.ts reading-enhancement.spec.ts：32项桌面/手机Mock浏览器通过，退出0；日志.tool-cache/reader-entry-green.log。

新增5种位置场景各覆盖桌面/手机，共10项，验证起点标题可见且scrollY=0、真实后续段落和首句内部定位保留；既有查词/翻译/保存/收藏/游客登录与筛选回归保持。Mock认证与进度不代表本轮真实账号联调，本轮没有后端改动或数据库测试。最新dist由已运行4174 preview提供；用户可刷新原地址验证。
