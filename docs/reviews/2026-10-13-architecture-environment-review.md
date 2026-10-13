# 架构评审与本机依赖检查

日期：2026-10-13。仅检查与规划，未安装软件、下载产品依赖、修改全局环境变量或访问已有业务库。

## 评审结论

整体架构和阶段顺序可继续使用。本轮修正的是可执行性：复用已安装的工具，明确 Boot 4 依赖，收紧阶段 0，减少前置安装。

| 优先级 | 原问题 | 已写入方案的修正 |
| --- | --- | --- |
| P1 | JDK 版本写了 21，但机器默认 Java/Maven 运行在 17 | 阶段 0 使用项目级 JDK 21 配置，验收 Maven 实际运行版本，保留原全局配置 |
| P1 | 只列 Flyway，容易沿用 Boot 3 依赖后遗漏自动配置或 MySQL 模块 | 明确 Boot 4 Flyway Starter、flyway-mysql 和 MySQL Connector/J，验证从空库迁移 |
| P1 | npm 不可用，原方案以 npm 作为默认命令 | 改用实际验证可用的 pnpm 11.25.0，统一锁文件和冻结安装命令 |
| P2 | 本机 MySQL 8.0，而方案直接要求 8.4 | 本地先复用专用新库；部署目标保持 8.4，部署前实测，不升级现有服务 |
| P2 | Docker 被误当作工程启动前提 | 本地原生运行，容器化和 Nginx 运行验收在部署阶段完成 |
| P2 | 工程阶段可能扩大成整站重写或假业务实现 | 只做路由布局、基础 API、迁移和状态反馈；真登录和业务表留到后续 |
| P2 | Cookie 会话提到但阶段边界不清楚 | 本轮不建会话表；阶段 1 集中实现 Spring Security、JDBC 会话与 CSRF |

没有发现必须更换 Vue、Spring Boot 或模块化单体路线的问题。版本兼容属于文档核查结果，尚未执行项目构建，不宣称组合已联调通过。

## 已验证环境

| 项目 | 实测结果 | 决策 |
| --- | --- | --- |
| 默认 java / javac | 17.0.8 | 不用于本项目 Java 21 构建 |
| JDK 21 | `C:\Java\jdk-21.0.12.1`，java 与 javac 均 21.0.12.1 | 已安装，无需重装 |
| Maven | 3.9.9，`D:\Maven\apache-maven-3.9.9\bin\mvn.cmd` | 临时指定 JAVA_HOME 后实际使用 JDK 21，输出成功 |
| Node.js | 24.19.0，Codex 自带运行时 | 当前会话可用，无需为本轮重装 |
| pnpm | 11.25.0，Codex 自带 fallback pnpm.cmd | 可用；初次沙箱检查因用户配置目录 EPERM 失败，经批准在沙箱外运行 --version 成功 |
| npm | PATH 与已检查的运行时目录均未发现 npm 命令 | 使用 pnpm，非阻塞项 |
| Git | 2.47.1.windows.1，`D:\Git\Git\cmd\git.exe` | 可用；本目录目前未初始化 Git |
| MySQL 客户端 | 8.0.41，`D:\MySQL\bin\mysql.exe` | 可用；客户端版本不等同于已核实的服务端版本 |
| MySQL 服务 | MySQL80 Running，127.0.0.1:3306 TCP 连接成功 | 服务可达；还需账号登录验证、查询服务端版本及创建项目新库 |
| Python | 3.12.1 | 已有，阶段 0 不使用 |
| Docker | PATH、常见安装目录和已检查的卸载记录中未发现 | 目前不作为依赖；不认定系统所有位置都没有安装 |
| WSL | wsl.exe 可发现，状态查询返回 E_ACCESSDENIED | 不能判断是否缺少；阶段 0 不依赖它 |

检查命令包括版本查询、服务状态、限定目录与软件安装记录、对本地 3306 的 TCP 连接。未登录 MySQL，未读取保存的数据库密码，未修改数据库。

## 谁需要做什么

目前没有必须立即由用户安装的软件。实施阶段可由执行者完成：项目级 JDK 21 配置、Maven Wrapper、前端依赖、Java 依赖、迁移脚本、布局与测试。下载依赖和工具操作如遇沙箱限制，使用明确的工具审批；本轮版本检查批准不等于后续安装批准。

数据库连接需要本地配置：用户可在自己的数据库工具中创建专用数据库账号，或在后续具体 SQL 准备完成后授权执行。数据库密码写入忽略提交的 `backend/config/application-local.yml`，不要发送到聊天。建库、用户名、权限范围和验证 SQL 会在实施计划中给出，不使用其他项目的库，不重置现有 MySQL。

如果用户希望以后脱离 Codex，在普通终端独立运行前端，再安装系统级 Node.js 24 LTS 和匹配 pnpm；当前规划与开发可复用自带工具。Docker Desktop、WSL、Redis、Nginx、对象存储与 AI SDK 都不需要现在安装。

## 官方核查依据

- [Spring Boot 4.0.8 系统要求](https://docs.spring.io/spring-boot/4.0/system-requirements.html)：Java 21 在支持范围，Maven 3.9.9 高于最低要求。
- [Boot 4 迁移说明](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)：Flyway 等能力拆分 Starter。
- [Flyway MySQL 支持](https://documentation.red-gate.com/flyway/reference/database-driver-reference/mysql)：Java 接入需要独立 MySQL 支持模块。
- [MyBatis-Plus 安装](https://baomidou.com/getting-started/install/)：选择 Boot 4 Starter，避免再次引入冲突的 MyBatis Starter。
- [springdoc 官方版本说明](https://springdoc.org/)：3.x 支持 Boot 4，3.1.1 为文档列出的稳定版本。
- [Vite 环境要求](https://vite.dev/guide/)：前端运行时需符合当前 Vite 的 Node 版本要求。
