# 阶段1：身份与账户验收

日期：2026-10-15。用户“可以，继续”授权阶段1，未授权自动进入阶段2。

## 已实现

- 真实注册、登录、退出、刷新恢复、JDBC持久会话、USER/ADMIN服务端权限。
- 账户昵称、改密、版本条件写入及所有旧会话下次请求失效。
- 普通2h idle/24h absolute、保持登录7d、HttpOnly/Lax Cookie及prod Secure。
- 受控local一次性管理员bootstrap，无默认密码、无公开提权接口。
- Vue登录/注册/账户表单、登录前路由保护、权限入口、错误重试；沿用确认的黑白灰/玫红与基础首页布局。

## 验证命令与结果

所有终端先点加载scripts/use-workspace-tools.ps1，Java终端另点加载use-java21.ps1。Maven实际编译及日志为Java21.0.12.1，release21。

| 命令 | 退出码/结果 | 类型 |
| --- | --- | --- |
| backend：mvnw.cmd -o verify -Pmysql-it | 0；24单元/MVC + 8集成，0失败/跳过；可执行JAR重打包成功 | 真实专用MySQL + 单元 |
| frontend：pnpm test:unit | 0；18通过 | 单元 |
| frontend：pnpm typecheck | 0 | 类型检查 |
| frontend：pnpm build | 0 | 生产构建 |
| E2E_PREVIEW=1 E2E_PORT=4174 E2E_REAL_BACKEND=1 E2E_EXTERNAL_SERVER=1 pnpm test:e2e | 0；22通过，11.6s | 桌面1440/手机390，生产构建 |

22浏览器项中4项为真实后端/测试库（桌面/手机各有数据库联通和注册→登录→刷新→昵称→改密→重登→退出）；其余18项为明确Mock身份/状态的布局、导航、错误恢复与键盘操作，不把Mock权限当作服务端验收。

真实集成覆盖：CSRF缺失/轮换/旧token，Session ID更换与旧Cookie拒绝、退出删除Cookie；哈希不进入JDBC principal；重复/并发注册、角色和他人id注入；USER403/ADMIN200、禁用401；双会话改密失效、真实并发改密仅一次成功；完整关闭并重启Spring应用后同一客户端恢复、真实数据库idle过期拒绝；V1/V2及重复迁移无新增、系统真实UP。

期限精确边界、Cookie共享序列化器普通/保持登录/删除/生产Secure、bootstrap既有账号不覆盖及一次性关闭由单元测试验证。未以单元测试替代实际HTTPS部署。

## 失败与修复记录

- 首轮新增HTTP测试得到403，证明接口尚未实现；之后实现并通过。
- Jackson3缺省primitive boolean导致登录400；最小解析测试复现，rememberMe改为可空并明确false默认后通过。
- 新增登录守卫使旧键盘测试在页面未就绪时按Tab；改为等待首页可见，再检验真实焦点顺序，两端通过。
- 独立审查的跳转循环/迟到profile响应已先复现再修复，见[审查记录](../reviews/2026-10-15-phase-1-identity-review.md)。
- Windows临时服务器锁JAR曾造成repackage失败；停止服务后完整verify通过，后续运行使用独立忽略目录runtime副本。
- Playwright自动Vite子进程回收曾停滞；按日志中本轮PID停止预览，未停止其他服务。最终使用明确外部受控preview，取得22通过及命令退出0；默认严格端口行为未改变。

浏览器trace/截图/视频关闭，避免保存凭据。fixture只按本轮记录的随机用户名删除：前两轮8个、最终轮4个浏览器账号，未对其他用户或数据库清库。集成测试各自清理本用例命名记录。

## 交接与范围

功能及技术验收完成；新登录/注册/账户页面仍待用户亲自体验确认。独立HTML原型未被Vue构建包含，原始参考HTML未修改。阶段2文章/词典/生词业务未开始。

管理员示例见backend/config/application-admin.example.yml，真实配置不提交。没有替用户设置默认应用管理员密码；需按 [本地使用说明](../phase-1-setup.md) 填写并运行受控创建命令。

保留此前原型、阶段0方案与其他任务文档的工作区修改。最终开发服务及数据库核对结果见docs/progress.md。

2026-10-15只读复核：开发/测试均6表、专用账号权限隔离、V1/V2成功记录和元数据检查通过；开发API真实200/UP。开发后端8080及preview4174已保留供用户体验，原4173服务保留。Codex打开页面请求返回queued，未冒充用户已看到或确认。

## 用户补充：管理员账号与注册前缀

按2026-10-15用户明确要求，local受控bootstrap创建admin123并使用用户给定密码，执行退出0；没有默认密码进入代码或示例，真实配置被Git忽略。真实HTTP验证登录200、管理员接口200并包含ADMIN，验证后退出204。

公开注册禁止所有admin前缀，先去首尾空白并统一小写，root/system仍保留；不限制管理员受控创建及登录。新增单元回归首先失败（原先只禁止精确保留名），修复后backend `mvnw.cmd -o verify -Pmysql-it`退出0：25单元/MVC+9真实集成；frontend `pnpm build`含类型检查退出0。额外在更新后的开发服务验证小写和混合大小写/首尾空白前缀均400，未创建注册记录。注册表单提示同步更新。
