# JWT 与项目专用 Redis 配置

2026-10-28最新界面：按用户要求移除“保持登录7天”勾选项及公共设备提示。正式登录表单固定提交rememberMe=true，自动七天；旧普通会话不延长，重新登录生效。下文普通模式仅为保留的API兼容机制，正式界面不再提供选择。

2026-10-28：登录界面默认勾选“保持登录7天”，公共设备可取消。下文普通登录指主动取消勾选；已存在的短期会话不延长，重新登录才启用新选择。[验证记录](acceptance/2026-10-28-seven-day-login.md)。

认证迁移：阶段1补充，2026-10-16。Vue、Java21、MySQL和邮箱规则不变。令牌只放前端内存；刷新凭证只在 HttpOnly Cookie，Redis仅存摘要和登录期限。

## 本地初始化

使用 PowerShell 7；不改已有6379服务或D:/redis配置。本机 Redis 5.0.14.1仅供本地兼容验证，部署时另选受支持的平台和维护版本。

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
. ./scripts/use-workspace-tools.ps1
. ./scripts/use-java21.ps1
./scripts/initialize-auth.ps1 -Environment dev
./scripts/initialize-auth.ps1 -Environment test
./scripts/project-redis.ps1 -Environment dev -Action start
./scripts/project-redis.ps1 -Environment test -Action start
```

初始化创建忽略的 `backend/config/application-auth.yml`、`application-auth-test.yml`，各自使用独立6380/6381、密码、RSA密钥和HMAC密钥。重复运行保留已有文件，缺失一半的配置/密钥则报错，不静默换钥。数据、日志及密钥在项目 `.tool-cache/auth/dev|test`；不要复制开发配置覆盖测试配置，不发送配置内容到聊天。

使用 `-Action status` 查本项目进程，`-Action stop` 校验PID归属后通过认证连接正常关闭并持久化。禁止手动强杀后用旧快照悄悄恢复登录状态。端口占用时启动失败，不终止其他服务。启动脚本后台窗口隐藏，密码不出现在命令行。

后端固定以 `backend` 为工作目录，会导入开发认证配置。其余数据库/QQ邮件配置继续使用现有忽略文件。缺密钥或启动时Redis不可达会失败；`/system/status`只反映MySQL，不能替代认证就绪检查。

```powershell
Set-Location backend
./mvnw.cmd -version
./mvnw.cmd spring-boot:run
```

开发前端使用127.0.0.1:5173或4174，与allowed-origins一致；其他主机名、端口必须显式加入。正式请求不要从file://原型发起。

## 生命周期与故障

- Access JWT：RS256、10分钟，严格校验issuer/audience/kid/类型/时间；只进入内存，刷新页面通过刷新Cookie恢复。
- 普通登录：2小时有效活动空闲、登录起最多24小时；刷新Cookie不设置持久Max-Age。
- 保持登录：从原登录起最多7天，Cookie Max-Age只取剩余时间，轮换不重新计7天。
- 每次受保护请求核对MySQL账号启用/版本/角色和Redis登录实例。退出撤销本实例，改密/重置递增security_version使所有旧设备失效。
- 已消费的真实刷新凭证重放会撤销该实例；随机错误秘密只拒绝。响应完全丢失可能需要重新登录，不提供旧凭证无限宽限。
- Redis运行中故障返回503 AUTH_SERVICE_UNAVAILABLE；不回退签名鉴权、不自动清身份。邮箱公开恢复流程继续按自己的数据库规则处理。
- 多标签使用Web Locks串行刷新/退出，BroadcastChannel仅发身份变化通知，不传凭证；缺Web Locks时保留本页串行与安全重放检测。

## 部署与恢复

使用无真实秘密的 `backend/config/application-auth.example.yml` 配置受控文件/环境变量。RSA至少2048位，HMAC独立至少32字节；同一运行代次稳定保存，不能启动时重生成。当前仅接受配置的kid；换钥会使旧Access失效，需要安排重新登录，不提供自动信任JWT所指远程密钥。

生产profile要求Secure Cookie和eh:prod:auth:vN命名空间，允许源只列本站HTTPS；必须配置可信代理的HTTPS转发与同源部署，不能开放通配凭证CORS。Refresh Cookie为host-only、HttpOnly、SameSite=Lax、Path=/api/v1/auth；CSRF Cookie为HttpOnly、Path=/api/v1，页面通过masked JSON令牌写X-CSRF-TOKEN。

备份密钥及项目配置需要受控访问。Redis恢复旧快照必须提升本项目namespace代次（例如v1→v2），使历史撤销状态无法复活；不要FLUSHDB/FLUSHALL。正常停机使用脚本的持久化关闭。Redis数据丢失只能重新登录，不从JWT或内存重建family。

旧EH_SESSION及MySQL SPRING_SESSION两表不再参与鉴权，但保留结构和既有迁移。切换后需重新登录一次。若必须回退Session构建，必须同时更换Cookie名称和会话仓库命名空间，不能复用历史Session让旧登录复活；不删账号、不清库。前后端必须一起切换相同API契约。

## 验证

Maven实际Java21，测试连接只接受english_hero_test、6381和eh:test。测试邮件用测试投递器或2525/2526本地收集器，不调用QQ邮箱。

```powershell
# backend目录
./mvnw.cmd verify -Pmysql-it
# 项目根目录
pnpm --dir frontend test:unit
pnpm --dir frontend build
$env:E2E_EXTERNAL_SERVER='1'
$env:E2E_PORT='4174'
pnpm --dir frontend exec playwright test --workers=1
```

真实浏览器邮件用例需要独立测试后端和本地SMTP收集器；未设置E2E_REAL_BACKEND/E2E_MAILBOX_URL时条件跳过。Mock浏览器、真实数据库/Redis与真实SMTP收件验收分别记录，不混算。

改密也参与同一凭证锁，避免旧改密响应清除新账号Cookie；所有受保护写入在CSRF请求之前固定身份代次，跨账号后拒绝发送旧表单。
