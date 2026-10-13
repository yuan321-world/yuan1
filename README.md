# English Hero / Lingua Loop

阶段 0：Vue 学习端与管理端布局、Java 系统状态接口、MySQL 迁移。
真实账号及学习业务在后续阶段实现。实施及验收记录在 docs/acceptance/。

## 环境

- JDK 21：本机 C:\Java\jdk-21.0.12.1。Maven Wrapper 固定 3.9.9。
- Node.js 24 LTS，pnpm 11.25.0。前端通过 Vue 官方 create-vue 初始化，精确版本见 package.json 与 pnpm-lock.yaml。
- MySQL 8：仅访问项目专用开发库与测试库，不要求 Docker。

首次依赖安装、Maven Wrapper 下载及 Chromium 下载需要网络和工具缓存写权限。
如 Codex 的沙箱阻止命令，使用相应工具审批；批准版本检查并不自动批准所有后续命令。
本机 Codex 自带 Node/pnpm 可用于当前会话，在普通终端运行前需安装相同主版本工具或配置相应 PATH。

## 启动

在 PowerShell 中使用带引号的真实路径：

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
. .\scripts\use-java21.ps1
java -version
javac -version
Set-Location -LiteralPath '.\backend'
.\mvnw.cmd -version
# 完成下面的数据库配置后才能启动后端
.\mvnw.cmd spring-boot:run
```

在第二个终端：

```powershell
Set-Location -LiteralPath 'D:\codex\English Hero'
pnpm --dir frontend install --frozen-lockfile
pnpm --dir frontend dev
```

前端 http://127.0.0.1:5173，后端 http://127.0.0.1:8080。
5173 / 4173 设置 strictPort，端口被占用时退出并报错，不自动换端口。
JDK 脚本只修改当前 PowerShell 进程的 JAVA_HOME/PATH，不修改全局配置。

## 数据库配置

建库和最小权限说明将放在 infra/sql/。本地账号与密码只填入
backend/config/application-local.yml，此文件已被 Git 忽略。不要把密码发送到聊天或提交到 Git。
后端启动命令的工作目录必须为 backend，以便加载 ./config/。

## 验证

```powershell
pnpm --dir frontend typecheck
pnpm --dir frontend test:unit
pnpm --dir frontend build
pnpm --dir frontend exec playwright install chromium
pnpm --dir frontend test:e2e
. .\scripts\use-java21.ps1
Set-Location -LiteralPath '.\backend'
.\mvnw.cmd test
```

数据库集成验证与真实浏览器联调需独立测试库及本地配置，后续在此补全。
CI 安装使用 pnpm install --frozen-lockfile。部署 history 路由需服务器回退到 index.html。

原型 D:\codex\English Learner\prototype\index.html 仅作视觉参考，保持原文件不变。

