# English Hero / Lingua Loop

阶段 0 工程基础正在实施。真实登录、学习数据与业务操作属于后续阶段。
实际验证与未完成项见 [验收记录](docs/acceptance/phase-0.md)。

## 页面原型：先由用户确认

[基于原始原型优化的独立 HTML](docs/prototypes/lingua-loop-preview.html) 可直接在浏览器打开，无需安装或启动工程。
它保留原始页面与演示交互，先显示登录页；点击“使用学习账号”或“使用管理账号”填入演示账号，再点击登录。
刷新或退出后回到登录页。演示密码 123456，数据为原型示例，不连接数据库。
本次优化包括登录页细节、缩小首页品牌区、手机菜单与键盘操作、留言便签/学习提示样式分离。

原始文件 D:\codex\English Learner\prototype\index.html 保持不变。
该 HTML 在独立 docs/prototypes 目录，正式 Vue 构建不包含它的演示登录、词典和 AI 脚本。
正式 Vue 界面目前为工程空布局，需在用户确认上述原型后据此调整，不能作为最终设计。

## 环境与依赖

- JDK 21：本机 C:\Java\jdk-21.0.12.1；Maven Wrapper 固定 3.9.9。
- Node.js 24 LTS，pnpm 11.25.0；前端由 Vue 官方 create-vue 初始化，精确依赖与锁文件已提交。
- MySQL 8：仅使用专用开发库和测试库，本阶段不要求 Docker / Redis / Python。

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
. .\scripts\use-java21.ps1
java -version
javac -version
Set-Location -LiteralPath '.\backend'
.\mvnw.cmd -version
# 完成本地数据库配置后再启动
.\mvnw.cmd spring-boot:run
```

后端工作目录固定 backend，以加载 ./config/application-local.yml。
脚本只修改当前进程的 JAVA_HOME/PATH，不修改全局环境。
后端地址 http://127.0.0.1:8080；端口占用时 Spring Boot 启动失败并报告 8080 in use。
默认 local profile 开启 OpenAPI 和 Swagger UI，prod profile 关闭文档。
系统状态 GET /api/v1/system/status 正常返回 200，数据库查询失败返回 503；
阶段 0 拒绝其他 API，不实现真实账号和会话。

第二个终端：

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend dev
```

前端 http://127.0.0.1:5173。API 经 Vite 代理访问 http://127.0.0.1:8080。
5173 / 4173 使用 strictPort，端口被占用时明确退出，不自动换端口。

## 验证

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
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
