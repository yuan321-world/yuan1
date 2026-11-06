# Codex Windows 沙箱局部修复记录

日期：2026-10-14（Asia/Shanghai）。用户在方案检查期间明确要求修复执行环境。

## 原因与证据

普通终端与 Node 工具均在命令执行前失败，错误为 `helper_unknown_error: setup refresh had errors`。沙箱外只读诊断正常。

当日 sandbox 日志显示具体失败：`deny ACE failed on D:\codex\English Hero\.git: open deny ACL target for update`。目录所有者为 `DESKTOP-CI9NGKS\CodexSandboxOffline`；当前 Windows 用户能修改目录内容，但不能更新该目录 ACL。项目根目录属于当前用户。

## 执行的修复

在项目忽略提交的 `.tool-cache/sandbox-repair/` 准备局部脚本，经工具批准并完成 Windows 管理员授权后执行：

1. 保存 `.git` 根目录原所有者和 ACL 备份。
2. 仅把该目录所有者改为当前 Windows 用户。
3. 对比访问规则 SDDL，确认修复步骤没有改变原访问规则。

没有递归更改仓库权限或删除 Git 数据，没有关闭沙箱、改为 Full Access、切换 sandbox 模式或改写全局配置。之后 Codex 正常重新应用自身的保护规则。

结果文件记录 Success=true、AccessRulesUnchanged=true，管理员修复进程退出 0，完成时间 10:16:26。备份和脚本保存在项目缓存，仅用于诊断，不提交凭据或个人配置。

## 恢复验证

- 普通沙箱内 exec_command 成功，Get-Location 与 git status 能运行。
- Node 工具返回 `Node sandbox probe succeeded`。
- 新初始化日志显示 `processed 3 write roots ... errors=[]`。

以上仅证明本次执行环境恢复，不代表所有系统日志警告都已消失，也不替代产品测试。

官方排查依据：[OpenAI Windows 沙箱说明](https://learn.chatgpt.com/docs/windows/windows-sandbox)。具体根因和修复结果依据本机日志与执行验证。

## 2026-10-14 第二次排查：工具用户目录与工作区缓存

用户继续要求修复沙箱内的工具配置。此时初始化日志 setup refresh errors=[]，普通 shell 和 Node 已可运行；不是早先 .git 所有者故障复发。

证据：
- whoami 为 CodexSandboxOffline。pnpm 默认尝试读取用户 AppData/Local/pnpm/config/config.yaml，出现 EPERM。
- Windows PowerShell 5.1 查询用户 .m2 的 Target 为 null，Wrapper 原有 Target[0] 访问抛空数组错误；同一查询在可访问的工作区目录返回空数组而非 null。
- 项目 pnpm exec 的 PATH 未包含 node_modules/.bin，无法找到 playwright；pnpm 脚本入口则可运行。
- 没有再次修改 ACL、全局 Codex 配置、沙箱模式或审批策略。

修复：
- 新增 scripts/use-workspace-tools.ps1，点加载后仅在当前终端设置 XDG_CONFIG_HOME/CACHE_HOME/STATE_HOME、MAVEN_USER_HOME 和 Maven 本地仓库至忽略的 .tool-cache，补项目 .bin 到 PATH。
- Maven 工作区仓库参数放在 JVM 选项末尾，保留其他选项，覆盖既有用户仓库路径；该边界先失败后通过。
- 为本轮离线验证，只把已有 Maven 3.9.9 分发包和 artifact repository 复制到工作区缓存，未复制 settings.xml 或凭据。以后首次缺依赖时仍按网络权限处理。
- 保持 pnpm 11.25.0、Wrapper Maven 3.9.9，不替换工具链。

普通沙箱实测：pnpm --version=11.25.0，pnpm exec playwright --version=1.64.0，Wrapper -version=Maven3.9.9/Java21，后端离线测试、前端类型/单元/构建、浏览器测试均已能执行。阶段0真实数据库配置仍缺失；这是业务验收依赖，不是沙箱故障。

官方边界依据：[Windows sandbox](https://learn.chatgpt.com/docs/windows/windows-sandbox)：读取失败应核对有效权限及 Windows 文件权限，保留沙箱边界。本次修复将项目所需可写数据移到已授权工作区，不扩大访问权限。
