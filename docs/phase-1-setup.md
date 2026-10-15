# 阶段1本地使用与验收

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

打开 http://127.0.0.1:5173/login 。先注册，再登录进入首页；新建普通账号仅有USER角色。当前用户看到真实空学习记录，学习功能仍按后续阶段开放。

公开注册禁止以admin开头的所有用户名（去首尾空白、不区分大小写），并保留root/system。管理员使用受控创建流程，允许admin前缀；普通用户不能通过注册取得管理员名称或权限。

## 管理员

本机已按用户要求创建管理员admin123，使用用户指定密码；可在相同登录页面登录后进入 `/admin`。以下流程供未来创建新管理员使用，不会覆盖现有账号。

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

浏览器Mock：`pnpm test:e2e`。真实流程需用 `java -jar target/english-hero-0.0.1.jar --spring.config.import=file:./config/application-test.yml` 启动测试后端，再设置 `E2E_REAL_BACKEND=1`、`E2E_PREVIEW=1`、`E2E_PORT=4174`运行浏览器测试。测试关闭trace/截图/视频。

Windows进程回收停滞时，先手动运行 `pnpm preview --port 4174`，再设置 `E2E_EXTERNAL_SERVER=1`使用这个受控preview，默认严格端口检查保持。运行中的JAR在Windows可能被锁住，打包前先停止该后端；验证时可复制JAR到忽略目录单独运行。

验收结果和未覆盖的部署环境见 [阶段1验收](acceptance/phase-1.md)。用户页面确认完成并批准后才开始阶段2。
