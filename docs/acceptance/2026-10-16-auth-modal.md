# 认证弹窗与游客首页验收

## 后续修订：旧认证入口统一到首页弹窗

用户指出改密后仍出现旧左右分栏页面，最新指令取代此前“保留独立认证页面”的展示选择。/login、/register、/forgot-password现仅作为兼容入口，路由恢复身份后回首页并打开对应弹窗；原品牌宣传面板不再挂载，进入生产包的旧页面引用已移除。changed/expired提示与redirect目标传入弹窗，沿用原安全返回白名单。网络恢复也使用同一入口，管理员连接故障时避免首页与管理端循环。

最终验证：类型检查/构建退出0，54单元通过；生产4174完整浏览器138项Mock通过、6真实库/SMTP条件跳过、退出0（.tool-cache/auth-entry-final-confirmed.log）。旧URL实际返回最新index-BYja_2xg.js。旧页面专属测试改为兼容弹窗/背景位置/内部滚动断言；管理员退出断言更新为统一首页地址。

背景同时使用inert与aria-hidden。修复“弹窗已关闭时身份变化”的缓存清理：立即销毁旧缓存，不把清理延迟到下一次新弹窗取消。新增单元失败复现后通过。本次类型检查/构建与54单元退出0；最新浏览器结果见进度顶部。以下为前次交付的历史记录，独立左右分栏页面不再对外提供。

日期：2026-10-16（Asia/Shanghai）。用户确认HTML样例与导航修订后实施。

## 交付与范围

- 首页公开访问，保留黑色Logo、胶囊及六项完整导航；游客显示登录按钮，已登录显示头像菜单，退出后回公开首页。
- AuthForm/PasswordResetForm同时供ElDialog弹窗与/login、/register、/forgot-password使用。独立页面保留原品牌宣传区，弹窗仅简洁标识。
- 复用现有密码显隐、验证码、错误映射、用户名密码登录与保持登录7天。后端、数据库、认证API、JWT/Redis与CSRF均未修改。
- 受保护目标保存fullPath，经既有safeRedirect白名单与角色规则继续导航。未登录不渲染保护页面；会话失效时先回公开首页再弹窗。管理员仍进入管理端。
- 游客不请求个人阅读推荐；登录后恢复。首页移除今日计划/本周打卡，未来归入学习统计，本轮不新增统计业务。
- 遮罩不关闭；关闭/Escape可取消，发送/提交/重连期间禁止关闭。切换或取消保留当前页面组件中的草稿和真实冷却，不写localStorage/sessionStorage；认证成功销毁缓存，刷新清除。
- 背景inert、滚动锁定、首字段聚焦及关闭后焦点返回；补充仅认证弹窗首尾Tab约束，处理鼠标聚焦末尾后的首次Tab越界。480px宽、动态视口最大高度、长注册表单内部滚动。

修改入口：frontend/src/components/auth/{AuthForm,PasswordResetForm,AuthDialog}.vue、stores/auth-dialog.ts、App.vue、components/AppNavigation.vue、router/index.ts、views/{LoginView,PasswordResetView,HomeView}.vue、styles/auth.css及相关测试。

## 实际验证

命令先点加载scripts/use-workspace-tools.ps1；Mock浏览器使用独立Vite4176，日常4174读取frontend/dist。

| 检查 | 命令/证据 | 结果 |
| --- | --- | --- |
| 失败复现 | 修改前auth-modal.spec.ts的guest home | 退出1，原首页无每日一句，要求认证 |
| 类型/构建 | pnpm --dir frontend build（含typecheck） | 退出0 |
| 单元 | pnpm --dir frontend test:unit | 退出0，13文件53项 |
| 弹窗专项 | playwright test auth-modal.spec.ts | 修复后16项通过，1440px及320px |
| 完整浏览器 | E2E_PORT=4176、E2E_EXTERNAL_SERVER=1，pnpm --dir frontend exec playwright test --reporter=list | 退出0，136项Mock通过，6项真实库/SMTP条件跳过 |
| 日常生产构建专项 | E2E_PORT=4174、E2E_EXTERNAL_SERVER=1，playwright test auth-modal.spec.ts | 退出0，16项Mock通过；草稿/关闭/切换流程无pageerror |
| 布局 | 专项截图及实现者检查，边界、横向溢出、提交按钮滚动可达 | 桌面/320px通过；非用户视觉验收 |
| 差异空白 | git diff --check，仅本任务相关已跟踪前端文件 | 退出0；非整个既有工作区 |
| 实际服务 | GET 4174/、4174/api/v1/system/status | HTTP200，UP/database UP |

日志：忽略目录.tool-cache/auth-modal-final-e2e.log。Mock截图：.tool-cache/auth-modal-desktop.png、auth-modal-mobile.png。

日常构建专项日志.tool-cache/auth-modal-production.log；实际4174入口确认加载本次index-B1By3c-5.js。专用测试Vite4176已停止，保留日常4174及原后端/Redis。

前轮完整回归6项失败分别为旧首页卡片断言2项、测试未等初始身份恢复2项、首次Tab越界2项；更新对应断言/测试等待并修复局部焦点边界后，最终136通过。

## 限制与交接

本轮未重跑真实MySQL/Redis集成与QQ收件；后端无修改，公开状态联通不等同于真实注册/重置验收。独立只读审查已发起，但代理因工具额度限制未完成，不计通过；实现者完成范围内人工检查。

Vitest仍提示未来Vite native config loader兼容事项，当前命令退出0，未为此改工具链。保留原型及其他任务修改，未提交、不自动进入阶段3。新版4174首页供用户体验，原独立认证路由保留。
