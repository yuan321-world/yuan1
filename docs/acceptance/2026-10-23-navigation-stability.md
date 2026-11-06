# 顶部导航切换稳定性验收

## 2026-10-23 顶部栏目切换位移修复

根因与证据：html没有预留传统滚动条空间。关闭Playwright默认hide-scrollbars，在1011px实际页面模拟滚动条出现/消失，旧auto模式nav-shell左坐标144→152（8px横移），stable模式始终144。全局根元素采用scrollbar-gutter:stable；Element Plus模态锁滚动原先另减body宽度，实测又造成7.5px位移，现支持stable的浏览器取消这次重复宽度补偿，保留overflow锁、焦点与鉴权。

验证：新增传统滚动条模式回归先红后绿；类型/生产构建退出0；导航稳定、首页/账户与旧资源导航恢复共18项双端Mock最终通过。覆盖六个顶部入口循环切换的导航/链接坐标、滚动条切换、登录弹窗开关、菜单/退出、窄屏聚焦及缺失资源恢复。未改路由权限、滚动恢复、用户数据或后端。4174最终dist生效。验收见docs/acceptance/2026-10-23-navigation-stability.md。

日志与传统滚动条测量JSON位于忽略缓存 `.tool-cache/cet4-complete/nav-*`，复现实验脚本为 `verify-classic-scrollbar.cjs`。前端修改范围为 `base.css`，回归文件为 `navigation-stability.spec.ts`。正常进入另一栏目仍遵循原路由滚动到顶部及浏览器返回恢复规则。未提交或推送。
