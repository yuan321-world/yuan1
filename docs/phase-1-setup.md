# 阶段1本地使用与验收

2026-10-16：认证已迁移JWT + Redis。本文保留阶段1初始设置的历史上下文；当前启动须先完成 [项目专用Redis及密钥配置](auth-jwt-redis-setup.md)，原Session专用运行方式不再适用。


## 启动

项目已初始化，无需重新建工程。在项目根目录打开两个PowerShell终端：

```powershell
# 终端一：后端，使用已填写的专用开发库配置
. .\scripts\use-workspace-tools.ps1
. .\scripts\use-java21.ps1
Set-Location backend
.\mvnw.cmd -o package -DskipTests
java -jar target/english-hero-0.0.1.jar --server.address=127.0.0.1
```

```powershell
# 终端二：前端
. .\scripts\use-workspace-tools.ps1
pnpm --dir frontend dev
```

打开 http://127.0.0.1:5173/login 。新注册需填写邮箱并验证验证码，再使用用户名登录；新建普通账号仅有USER角色。已有账号不受影响，可先登录后在账户设置绑定邮箱。QQ邮件配置见 [邮件说明](email-setup.md)，邮件未启用时不能发送验证码，已有账号仍可登录。当前学习业务仍按后续阶段开放。

公开注册禁止以admin开头的所有用户名（去首尾空白、不区分大小写），并保留root/system。管理员使用受控创建流程，允许admin前缀；普通用户不能通过注册取得管理员名称或权限。

## 管理员

本机已按用户要求创建管理员admin123，使用用户指定密码；可在相同登录页面登录后进入 `/admin`。以下流程供未来创建新管理员使用，不会覆盖现有账号。

管理员登录后直接进入数据管理后台，仅显示数据管理、账户设置和退出；后台账户地址为 `/admin/account`。管理员不进入背单词、阅读或练习学习页面；这些功能仍提供给普通学习者。数据管理的实际文章/词书/题库操作随对应阶段开放。

复制 `backend/config/application-admin.example.yml` 为同目录`application-admin.yml`，只在文件中填写新的应用管理员用户名、昵称、密码。它与MySQL账号不同；无默认密码，真实文件忽略Git。

在backend目录、已设置Java21的终端执行：

```powershell
java -jar target/english-hero-0.0.1.jar --server.address=127.0.0.1 --server.port=8081 --spring.config.additional-location=file:./config/application-admin.yml
```

仅local profile且enabled=true会运行。成功后程序自动停止；已有同名账号报错，不重置密码或提升权限。正常启动不加载该文件。管理员登录后显示管理端入口；内容管理随学习业务阶段实现。

## 会话与账户

普通登录闲置2小时、最长24小时；保持登录最多7天。刷新/后端重启后仍由数据库会话恢复。昵称1—32个Unicode字符；密码8—64个字符且UTF-8最多72字节。改密成功需重新登录，其他设备下次请求失效；账号用户名不可修改。

## 自动验证

backend目录：`mvnw.cmd -o verify -Pmysql-it`。测试配置必须指向127.0.0.1的english_hero_test；不使用开发库跑集成测试。frontend目录：`pnpm test:unit`、`pnpm typecheck`、`pnpm build`。

浏览器Mock：`pnpm test:e2e`。真实邮箱流程需要独立测试后端和SMTP收件器，见下节。没有 `E2E_REAL_BACKEND=1` 和 `E2E_MAILBOX_URL` 时，真实注册用例明确跳过，不视为通过。测试关闭trace/截图/视频。

Windows进程回收停滞时，先手动运行 `pnpm preview --port 4174`，再设置 `E2E_EXTERNAL_SERVER=1`使用这个受控preview，默认严格端口检查保持。运行中的JAR在Windows可能被锁住，打包前先停止该后端；验证时可复制JAR到忽略目录单独运行。

验收结果和未覆盖的部署环境见 [阶段1验收](acceptance/phase-1.md)。用户页面确认完成并批准后才开始阶段2。

## 真实邮箱流程的自动浏览器测试

仅使用已配置的english_hero_test，保留8080/4174开发体验服务。在根目录先加载workspace工具和Java21。三个独立终端执行：

```powershell
# 根目录终端一：只绑定回环地址的测试收件器，无QQ投递，无凭据日志
node scripts/test-smtp-sink.cjs
```

```powershell
# 根目录终端二：随机测试密钥留在当前进程环境，不输出或放命令行
$env:ENGLISH_HERO_TEST_EMAIL_HMAC_SECRET = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
Set-Location backend
java -jar target/english-hero-0.0.1.jar --server.address=127.0.0.1 --server.port=8082 --spring.config.import=file:./config/application-test.yml,file:./config/application-mail-test.example.yml
```

```powershell
# 根目录终端三：构建后启动独立预览，保持此终端运行
pnpm --dir frontend build
$env:E2E_API_TARGET = 'http://127.0.0.1:8082'
pnpm --dir frontend preview --port 4175
```

另开已加载工具的根目录终端运行：

```powershell
$env:E2E_PREVIEW='1'; $env:E2E_EXTERNAL_SERVER='1'; $env:E2E_PORT='4175'
$env:E2E_REAL_BACKEND='1'; $env:E2E_MAILBOX_URL='http://127.0.0.1:2526'
pnpm --dir frontend exec playwright test --workers=1
```

收件器2526仅供测试Node进程读取验证码，应用生产代码没有验证码查询接口。测试账号用户名记录于忽略缓存，应按本轮具名记录清理，不清库。浏览器和API并行复跑时会共享测试库内IP限流预算，应串行运行或为测试注入独立时间窗口，不降低生产配额。验证后关闭本轮8082/4175/2525/2526进程。
