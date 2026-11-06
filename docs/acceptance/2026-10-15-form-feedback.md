# 认证与账户表单反馈验收

日期：2026-10-15（Asia/Shanghai）。用户确认统一反馈方案后实施；本轮仅必要前端和文档，不进入阶段2。

## 交付范围

- 登录、注册、找回密码、账户资料、修改密码及邮箱绑定使用独立表单反馈状态。结构化ApiError.code/fieldErrors与操作上下文决定位置，不按中文文本分类。
- 字段错误显示在对应输入框下方并关联aria-invalid/aria-describedby；原生required/email等约束接入内联提示。已触发的本地错误持续校验，合法后清除；服务端错误在相关输入修改后清除。提交失败按DOM顺序聚焦首错并滚动到可见位置。
- 非字段业务错误在当前form内；网络、会话过期、权限错误按页面处理。连接恢复只做安全GET，不自动重发验证码或重放提交。真实UNAUTHENTICATED与当前密码BAD_CREDENTIALS区分。
- 昵称/邮箱保存成功复用现有Element Plus消息服务，使用中性样式、可关闭、role=status礼貌播报；注册成功/改密后重登及验证码接受说明保留在相关步骤。
- 保留黑白灰/柔和粉色、左侧品牌布局、密码显隐、密码清理、加载禁用、验证码challenge/冷却及认证跳转。没有新增依赖、后端改动、接口变更、数据库迁移或无用确认弹窗。

主要文件：`frontend/src/feedback/{useFormFeedback.ts,notify.ts}`，`components/{FieldError,FormFeedback,PasswordInput,EmailCodeField}.vue`，`views/{LoginView,PasswordResetView,AccountView}.vue`，`styles/auth.css` 及相关测试。规则入口见 [ui-guidelines.md](../ui-guidelines.md)。

## 验证证据

使用工作区工具脚本；命令由项目根目录执行。日志位于忽略提交的.tool-cache。

| 检查 | 结果与边界 |
| --- | --- |
| `pnpm --dir frontend test:unit` | 退出0，5个文件、29项通过；包含结构化分类、字段定向清理、跨表单隔离及局部错误保留 |
| `pnpm --dir frontend build` | 退出0；包含vue-tsc与tsc类型检查及Vite生产构建 |
| `E2E_EXTERNAL_SERVER=1 E2E_PORT=4174 pnpm --dir frontend exec playwright test --workers=1` | 退出0，80通过、4按条件跳过；桌面1440×1000、手机390×844，另有320px短屏专项；使用Mock身份与API，未发送邮件 |
| 新增验证码错误保留回归 | 旧构建桌面/手机均失败；修复requestBindingCode的整体reset后，两端通过并纳入全套80项 |
| `git diff --check` | 当前任务文件无空白问题；整个工作区仍报告既有身份方案文件末尾空行，未改写无关文件 |

浏览器覆盖错误位置/边框/aria、首错聚焦、输入纠错、表单隔离、服务器字段错误保留、401/403分类、429冷却、网络安全恢复、成功Toast、验证码证明失效、防重复提交、角色路由、品牌位置稳定、密码图标及小屏滚动。

初次整套浏览器运行遇到4174连接拒绝，确认原前后端进程均已停止后终止该轮；重新启动服务并完整重跑得到上述80通过结果，连接失败不计作通过。

## 审查及启动

独立审查发现：网络重试绕开EmailCodeField导致challenge/冷却未保存；输入或程序清空密码时误清服务端错误；已有错误阻止重新收集首错；验证码发送清空未解决的局部错误。以上已修复并有针对性回归。审查最终源码消息确认未见新增实质问题；审查助手在保存报告前因额度停止，本文记录其已返回的审查范围与结论，不宣称另有完整审查文件。

按用户要求重新启动项目：Maven实际Java21.0.12.1，`mvnw.cmd -o spring-boot:run` 启动后端PID8896、8080；开发库三条Flyway迁移校验正常，版本3，无新增迁移。`pnpm --dir frontend preview --host 127.0.0.1 --port 4174` 提供当前生产构建。直接8080和经4174代理的`/api/v1/system/status`均返回UP，`/login` HTTP200。

访问 http://127.0.0.1:4174/login 。本轮没有重跑无修改的Java测试、真实注册/密码重置或SMTP投递；先前用户确认QQ收件与本轮Mock测试分开记录。新增反馈的最终视觉体验仍由用户确认，不以自动测试替代。
