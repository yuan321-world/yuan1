# English Hero / Lingua Loop

阶段0已验收，阶段1账号与验证邮箱已实现。2026-10-16迁移JWT + Redis，阅读/词汇业务尚未实现；先按 [认证配置](docs/auth-jwt-redis-setup.md) 初始化项目专用Redis及稳定密钥。
实际验证与未完成项见 [验收记录](docs/acceptance/phase-0.md)。

开发前请阅读 [项目工作约定](AGENTS.md)、[文档入口](docs/README.md) 和 [当前进度](docs/progress.md)。
文档入口集中索引需求、设计、数据库/API、决策与阶段计划；原技术栈和阶段顺序保持不变。

## 页面原型与已确认的基础布局

[基于原始原型优化的独立 HTML](docs/prototypes/lingua-loop-preview.html) 可直接在浏览器打开，无需安装或启动工程。
它保留原始页面与演示交互，先显示登录页；点击“使用学习账号”或“使用管理账号”填入演示账号，再点击登录。
刷新或退出后回到登录页。演示密码 123456，数据为原型示例，不连接数据库。
本次优化包括登录页细节、缩小首页品牌区、手机菜单与键盘操作、留言便签/学习提示样式分离。

原始文件 D:\codex\English Learner\prototype\index.html 保持不变。
该 HTML 在独立 docs/prototypes 目录，正式 Vue 构建不包含它的演示登录、词典和 AI 脚本。
用户已确认最基础布局，正式Vue已按该结构落实。学习数据为空，真实登录与业务操作仍按后续阶段实现。

## 环境与依赖

- JDK 21：本机 C:\Java\jdk-21.0.12.1；Maven Wrapper 固定 3.9.9。
- Node.js 24 LTS，pnpm 11.25.0；前端由 Vue 官方 create-vue 初始化，精确依赖与锁文件已提交。
- MySQL 8：仅使用专用开发库和测试库，认证使用项目专用 Redis（开发6380、测试6381）；不要求Docker或Python。

首次 pnpm 安装、Maven Wrapper 和 Chromium 下载需要网络及缓存写权限；如被沙箱阻止，使用对应工具审批。
当前 Codex 自带 Node/pnpm 已验证；普通 PowerShell 终端需安装相同版本工具或自行添加其路径到当前进程 PATH。

本机当前会话的工具路径：

```powershell
$env:PATH = 'C:\Users\遇岸的神\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin;C:\Users\遇岸的神\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback;' + $env:PATH
node --version
pnpm --version
```

## 数据库准备（待本地配置）

1. 用本地 MySQL 管理工具连接管理员账号，先单独执行 infra/sql/create-dev-databases.sql 中的重名库检查。
2. 仅在两个库名均未被使用时创建 english_hero_dev 和 english_hero_test。已有同名库先确认用途，不清库或覆盖。
3. 通过工具账号管理界面分别创建 english_hero_dev@127.0.0.1 和 english_hero_test@127.0.0.1，密码只在本地输入。
4. 按 SQL 模板给账号授予对应项目库的最小迁移和数据访问权限，核对 SHOW GRANTS 不含其他库/全局权限。
5. 分别连接对应库执行 SELECT VERSION(), DATABASE(), @@session.time_zone。
6. 在 backend/config 复制 application-local.example.yml 为 application-local.yml，复制 application-test.example.yml 为 application-test.yml，填入各自密码。不要发送到聊天或提交 Git。

两个实际配置文件均被 Git 忽略。集成测试在连接前只接受 english_hero_test，且从不调用 clean。
所有项目 JDBC 连接设置 UTC、3 秒连接/socket 超时；开发启动迁移失败即退出，不伪造健康状态。

## 启动工程

第一个 PowerShell 终端：

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
. .\scripts\use-workspace-tools.ps1
. .\scripts\use-java21.ps1
java -version
javac -version
Set-Location -LiteralPath '.\backend'
.\mvnw.cmd -version
# 完成本地数据库、JWT密钥配置并启动项目dev Redis后再启动
.\mvnw.cmd spring-boot:run
```

后端工作目录固定 backend，以加载 ./config/application-local.yml。
本地查词请保留 `english-hero.dictionary.ecdict.enabled: true`（本地配置示例已包含）；它同时启用已有离线词条的音标补全和短语数据。仅保留数据库词条而关闭此项，会导致原始词条音标和短语缺失。该本地选择不代表生产数据许可已确认。
脚本只修改当前进程的 JAVA_HOME/PATH，不修改全局环境。
后端地址 http://127.0.0.1:8080；端口占用时 Spring Boot 启动失败并报告 8080 in use。
默认 local profile 开启 OpenAPI 和 Swagger UI，prod profile 关闭文档。
系统状态 GET /api/v1/system/status 正常返回 200，数据库查询失败返回 503；
阶段1账号、验证邮箱和JWT认证接口见 docs/api.md；尚未实现的业务API仍拒绝。

第二个终端：

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
. .\scripts\use-workspace-tools.ps1
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend dev
```

前端 http://127.0.0.1:5173。API 经 Vite 代理访问 http://127.0.0.1:8080。
5173 / 4173 使用 strictPort，端口被占用时明确退出，不自动换端口。

## 验证

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
. .\scripts\use-workspace-tools.ps1
pnpm --dir frontend typecheck
pnpm --dir frontend test:unit
pnpm --dir frontend build
pnpm --dir frontend exec playwright install chromium
pnpm --dir frontend test:e2e
. .\scripts\use-java21.ps1
Set-Location -LiteralPath '.\backend'
.\mvnw.cmd test
# 需要独立测试库本地配置；缺失时有明确错误，不跳过成成功
.\mvnw.cmd verify -Pmysql-it
```

常规浏览器错误态验证通过网络拦截，不停止 MySQL。真实联调另行运行：

```powershell
# 先确保后端与专用开发库已运行，再在项目根目录执行
$env:E2E_REAL_BACKEND = '1'
pnpm --dir frontend test:e2e foundation.spec.ts
Remove-Item Env:E2E_REAL_BACKEND
```

生产构建的 history 刷新验证：

```powershell
pnpm --dir frontend build
$env:E2E_PREVIEW = '1'
pnpm --dir frontend test:e2e
Remove-Item Env:E2E_PREVIEW
```

preview 默认 4173。部署时需服务器回退到 index.html；Nginx 实际部署验证属于部署阶段。
CI 使用 pnpm install --frozen-lockfile。未配置真实数据库或未确认页面时，阶段 0 保持未完成。


## 工作区内的工具配置

在 Codex 沙箱或需要项目缓存隔离的 PowerShell 终端，先执行 `. .\scripts\use-workspace-tools.ps1`。只修改当前终端环境，把 pnpm 配置、缓存和 Maven 分发/依赖缓存放到忽略提交的 `.tool-cache`，不修改全局 ACL 或沙箱模式。脚本不会导入用户私有 registry 凭据；首次缺依赖仍需允许依赖下载。本轮离线验证使用已缓存的 Maven 分发与 artifact 文件，没有复制 settings.xml。

若 4173 被其他服务占用，保留该服务并显式验证另一端口：
```powershell
. .\scripts\use-workspace-tools.ps1
$env:E2E_PREVIEW = '1'
$env:E2E_PORT = '4174'
pnpm --dir frontend test:e2e
Remove-Item Env:E2E_PREVIEW
Remove-Item Env:E2E_PORT
```
不设置 E2E_PORT 时仍使用原默认端口，严格端口冲突行为保留。真实数据库配置缺失时，阶段0仍不标为完成。

## 阶段1：真实账号使用

启动命令沿用上文。访问前端后先注册、再登录才能进入首页；注册用户名3—32位英文字母/数字/下划线，统一小写。普通登录闲置2小时、最长24小时；勾选保持登录最多7天。改密会退出当前登录，其他设备在下次请求失效。账号密码与MySQL数据库账号不同。

管理员没有默认密码或公开提权入口。如需创建，复制 `backend/config/application-admin.example.yml` 为同目录 `application-admin.yml`，只在该文件填写应用管理员新密码（真实文件已忽略Git）。从backend目录执行：

```powershell
. ..\scripts\use-workspace-tools.ps1
. ..\scripts\use-java21.ps1
.\mvnw.cmd -o package -DskipTests
java -jar target/english-hero-0.0.1.jar --server.port=8081 --spring.config.additional-location=file:./config/application-admin.yml
```

仅local profile可运行此一次性流程，成功后应用自动停止；已有同名账号报错，不重设密码或提升权限。正常启动不加载application-admin.yml。生产使用HTTPS及prod profile的Secure Cookie，部署验证仍在后续阶段进行。

身份浏览器测试关闭trace、截图和视频，避免记录凭据。常规测试使用明确标注的Mock身份；真实流程需先使用专用测试库启动后端：

```powershell
# backend目录；勿同时运行另一个8080服务
java -jar target/english-hero-0.0.1.jar --spring.config.import=file:./config/application-test.yml
# 另一个终端，frontend目录
$env:E2E_REAL_BACKEND = '1'
$env:E2E_PREVIEW = '1'
$env:E2E_PORT = '4174'
pnpm test:e2e
```

Windows若测试结束后Vite子进程回收停滞，可自行启动同一preview，再设置`E2E_EXTERNAL_SERVER=1`使用该受控服务运行测试；默认仍由Playwright启动且严格检查端口。fixture用户名记录在忽略目录，测试不提供公开删除账户接口。

## 阶段2：阅读与收藏

使用现有账号登录。管理员在“文章管理”创建/编辑/预览、发布及下架；学习者可阅读、保存本人位置、查词和明确收藏，笔记本保留原句及文章版本。阅读历史与收藏均由服务端隔离，新版发布或下架不覆盖本人原快照。首页推荐按上海日期稳定选择，尚未开放的复习、考试、AI保持原状态。

[启动与使用](docs/phase-2-setup.md) · [阶段2验收](docs/acceptance/phase-2.md) · [原创内容说明](content/README.md)。首批JSON导入只建草稿，不随数据库迁移自动发布；词典为319个原创编辑基本词条及显式形式，未收录时明确提示，不接外部翻译。数据库追加V5—V9，保留原表与数据，不清库。

## 留言墙（2026-10-27）

正式页面 `/wall`，管理页面 `/admin/wall`（真实ADMIN账号）；访客可浏览，登录后发布和互动。数据库迁移V30，沿用现有本地配置。原型4187仅为演示，不写正式库。

独立真实浏览器验证需先用现有工具脚本加载Java21/Node、启动专用测试Redis6381，并让4174运行最新dist；backend执行 `./mvnw.cmd -o '-Dtest=WallRulesTest,WallLayoutTest' '-Dit.test=Wall*IT,WallBrowserVerification' -Pmysql-it verify`。WallBrowserVerification显式运行 `frontend/tests/real/wall-browser.cjs`，真实JWT/CSRF和专用测试库，临时账号最后清理，无模型调用。常规后端IT默认不依赖浏览器。详见 [正式验收](docs/acceptance/2026-10-27-message-wall.md)。

## 用户管理

正式管理路由 `/admin/users`（本地交付状态见[验收](docs/acceptance/2026-10-27-admin-users.md)）。ADMIN查询脱敏资料、停用恢复普通账号、查看审计；无密码/哈希查看或代重置入口。独立原型4187仅演示。

显式真实浏览器验证：最新dist预览4174、测试Redis6381就绪后，backend执行 `./mvnw.cmd -o '-Dit.test=AdminUser*IT,AdminUserBrowserVerification' -Pmysql-it verify`。runner只用english_hero_test临时账号，凭据忽略且finally删除，UI转发真实业务响应。
