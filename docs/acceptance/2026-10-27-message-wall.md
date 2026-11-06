# 2026-10-27 留言墙正式验收

用户已确认方案和实施计划。正式Vue/后端已实现公开浏览、登录发布、共享避让坐标、点赞/评论/举报/本人删除、管理员公告/管理审计、作者私有通知；铃铛仅墙内底部加号旁。手机默认列表。AI/积分保留明确演示说明，本轮没有模型调用或积分入账。

## 实际验证

以下命令均退出0，完整日志保存在忽略目录 `.tool-cache/wall-implementation/`：

- Maven实际Java21.0.12.1；backend `./mvnw.cmd -o test`：105单元测试，0失败/错误/跳过（backend-all-unit.log）。
- backend `./mvnw.cmd -o '-Dtest=WallRulesTest,WallLayoutTest' '-Dit.test=Wall*IT,WallBrowserVerification' -Pmysql-it verify`：最终14项真实数据库集成，0失败/错误/跳过，BUILD SUCCESS（backend-final-expanded.log）。包含通知失败整体回滚、本人删除与管理编辑并发、布局并发、权限/CSRF/归属/幂等/限额/隐私边界。
- 前端全单元36文件142项通过（frontend-all-unit.log）；类型检查及生产构建通过（review-build-3.log）。
- wall.spec.ts/wall-admin.spec.ts/wall-notifications.spec.ts 双端Mock：9通过、1手机跳过（桌面画布专属），review-ui-green.log。
- navigation/navigation-stability/auth-modal/home-polish/admin-layout及匹配到的practice-navigation：46项通过（navigation-regression-final.log）。原先4项旧导航数量断言更新为新增墙入口后的7项/首页3模块，不删除原功能。
- WallBrowserVerification使用专用测试库、真实JWT/CSRF，通过API转发访问真实服务，无Mock业务响应：发布/刷新持久化、赞评举报、管理员公告加粗与HTML转义、隐藏恢复、通知本人可见/他人隔离/已读持久化、1440/320布局均通过；账号与临时凭据在finally清理。真实截图 real-desktop.png/real-mobile.png/real-notifications.png。

## 修复与审查

RED→GREEN覆盖缺失接口、仓储代理字段访问NPE、并发布局间隙锁死锁、429缺少Retry-After、不合法UTF16、KEEP内部原因误入本人列表。独立最终审查4项P2均已修复并有先失败后通过的测试：关闭详情清除私有DOM、未知结果评论保留同UUID重试、通知分页保留最初已读上界、异步初载及新便签自动定位。MySQL JSON键顺序的重放断言使用语义树比较。

## 部署与边界

部署核对另追加在下方。正式墙不灌演示便签，不创建假管理员。原始English Learner原型未改；4187独立演示保留。浏览器自动化与截图检查不能替代用户最终视觉体验。未执行全项目所有真实库历史IT，本轮覆盖墙真实IT和必要导航回归。无提交/推送、无AI外呼，后续通用AI阶段不自动启动。

## 本地交付结果（15:43，北京时间）

8080已替换为验证后的新JAR，PID11520；不可变产物 `.tool-cache/20261027-wall-live/english-hero-wall-v1.jar`，SHA256 `0A323BCF4DB9B0607E2322E43043CED4FC4BD3DBC02170CDB793022D77949A9C`。现有启动脚本已同步路径及哈希。停止旧PID的普通权限失败，第一次自动审批超时，允许的一次重试成功；未修改系统权限。

Flyway启动日志确认 english_hero_dev 从V29应用V30成功。4174首页/正式墙/后台墙路由HTTP200；代理system/status为english-hero且database=UP，公开留言接口200且0条。已有56张业务表CHECKSUM前后完全相同；活动AI任务0，本轮无模型调用。现有AI开关与人民币1元日限额配置未改。

正式入口 http://127.0.0.1:4174/wall ，后台 http://127.0.0.1:4174/admin/wall 。1440/320真实测试截图已人工检查；最终视觉体验待用户确认。

## 2026-10-27 画布/列表统一框架

按用户截图修正切换突兀：删除列表模式移除外框/背景、自动撑高、搜索挪位及工具栏sticky的独立规则；两模式共用固定尺寸点阵外框和标题/分类/搜索/发布/铃铛/AI位置，列表仅在预留内容区内滚动，手机仍默认列表。缩放控件在列表保持位置但禁用，分页按钮在列表内容末尾，避免遮住卡片。公开列表切到画布仅切展示，不重取或清空留言；由“我的”切回公开画布仍需查询公开数据。

新增回归先在旧版失败（列表移除缩放控件/框架变化），修改后1440和1011宽度逐项矩形、页面高度、scrollY完全一致；24卡片内滚动可见最后一张且工具位置不变，两次切换仅一次留言查询。截图 unified-list-desktop.png/unified-list-mobile.png 人工检查（后者为1011窄桌面，不冒充320）。墙UI最终11通过/1手机画布专属跳过，墙单元5文件9通过，vue-tsc及生产构建退出0。4174预览已使用最新dist，后端/数据结构未修改，无模型调用。

## 2026-10-27 点赞与登录弹窗修复

原因：原生dialog.showModal位于浏览器top layer，盖过Element Plus登录窗口，调高z-index无效。详情现在监听登录窗口状态，打开登录时暂时close原生详情/举报弹窗，取消登录后恢复详情并保留评论草稿；账号切换仍走既有清理逻辑。便签底部爱心成为语义按钮，支持直接点赞/取消、aria-pressed及提交中禁用；访客只打开真实登录、不发点赞请求。服务确认后才更新计数；失败保留已确认状态，可重试，跨账号迟到响应丢弃，不刷新整墙或移动便签。

六项双端新增回归在旧版全部失败，修复后通过。最终wall/admin/notifications/auth-modal共35通过、1手机画布专属跳过；墙单元11项通过（含同便签重复点击、账号切换迟到响应、失败重试），vue-tsc与构建退出0，4174dist已更新。截图like-login-1440.png/320.png关闭动画后检查，登录可输入/关闭，取消返回原草稿。旧详情用例及真实浏览器脚本选择器限定详情内点赞，避免与新增卡片按钮重名；真实脚本本轮仅node --check通过，未重跑真实库全流程。后端和数据库未改，无模型调用。

## 2026-10-27 用户要求的完整复测

最终重跑：backend `./mvnw.cmd -o -Dtest=WallRulesTest,WallLayoutTest -Dit.test=Wall*IT,WallBrowserVerification -Pmysql-it verify` 退出0，14项真实库IT通过（含真实JWT/CSRF浏览器1440/320、发布刷新、点赞评论举报、公告隐藏恢复、通知读状态及他人隔离）。frontend墙与认证四组E2E 35通过/1手机画布专属跳过；墙单元5文件11通过，类型检查与生产构建通过。日志recheck-backend/browser/unit/build.log位于忽略目录。测试使用专用测试库与临时账号，无开发留言修改、无模型调用；本轮未实现用户管理模块。密码存储已核对为password_hash与PasswordEncoder，账号管理建议展示资料/状态并支持密码重置，不返回密码或哈希。

## 2026-10-28移除未规划的墙AI入口

按用户明确要求，正式WallView移除“向 AI”演示按钮和说明文案，清除wall-ai-demo样式与无用aux插槽，调整既有几何回归；不改作文/翻译AI批改、积分和原型历史。前端类型/构建退出0，wall.spec双端13通过/1手机画布专属跳过，4174预览使用最新dist。无后端/数据库/模型外呼。日志remove-wall-ai-build/ui.log存忽略目录。
