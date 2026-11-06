# 阶段 0 工程基础 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 交付能在本机启动的 Vue + Java 工程，真实连通 MySQL、数据库迁移和健康 API，并延续原型布局，为阶段 1 提供稳定基础。

**Architecture:** 一个 Vue 应用包含学习端与管理端空布局，一个 Spring MVC 应用提供系统基础 API。本地使用现有 MySQL 和 Vite 代理；业务数据、登录会话与容器部署属于后续阶段。

**Tech Stack:** Java 21、Spring Boot 4.0.8、Maven Wrapper 3.9.9、MyBatis-Plus 3.5.17、MySQL、Flyway、springdoc 3.1.1；Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、pnpm 11.25.0、Vitest、Playwright。

**Spec:** [技术栈与阶段方案](../specs/2026-10-13-architecture-roadmap-design.md)

## Global Constraints

- 正式开发工作目录为 `D:\codex\English Hero`；原型 `D:\codex\English Learner\prototype\index.html` 保持原文件，内容和注释只作为设计参考。
- 浏览器通过 `/api/v1` 调用 Java；前端路由采用 history 模式。
- 使用 Java 21，不得使用 SNAPSHOT 或预览版本作为产品依赖。
- Node.js 24 LTS、pnpm 11.25.0；精确依赖版本锁入 pnpm-lock.yaml；CI 使用 pnpm install --frozen-lockfile。
- 本阶段只创建系统元数据表和迁移历史，不提前创建用户、词库、题库等业务表。
- 阶段 0 管理端仅预览空布局，不提供管理数据或修改接口。
- MySQL 时间统一保存为 UTC；数据库密码由用户在本地配置，不在聊天中收集。
- Docker、Redis、Python、AI、JDBC 会话表不属于阶段 0 前置条件。
- 阶段 0 已开始实施，实际进度与验证记录见 ../../acceptance/phase-0-progress.md 和 ../../acceptance/phase-0.md；未满足完成条件的项目不标记完成。

## Review Focus

1. 本机默认 JAVA_HOME 指向 17：Task 1 验收 Maven 实际运行于 21，不能只看到已安装目录就放行。
2. 目录含空格、中文用户路径：Task 1 用 PowerShell LiteralPath 与正确引号，Task 5 从实际路径启动与构建。
3. 数据库服务可达但账号无权限：Task 2 用项目专用新库验证，启动失败给出明确配置问题，不读取其他库。
4. 后端未启动或请求在导航后完成：Task 4 验证超时、请求取消及卸载后不更新页面，状态提示可重试。
5. 手机导航与 history 路由刷新：Task 3/5 检查 390px 导航、键盘焦点、直接访问与刷新，未知路由进入 404。

## 实施前提与本轮检查结果

- 已验证 JDK 21.0.12.1 在 `C:\Java\jdk-21.0.12.1`，Maven 3.9.9、Node.js 24.19.0、Git 2.47.1 和 pnpm 11.25.0 可运行。
- Node 路径：`C:\Users\遇岸的神\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe`。
- pnpm 路径：`C:\Users\遇岸的神\.cache\codex-runtimes\codex-primary-runtime\dependencies\bin\fallback\pnpm.cmd`；下文 `pnpm` 指该已验证工具或同版本系统命令。
- MySQL80 服务运行，127.0.0.1:3306 TCP 可连接；客户端版本 8.0.41，服务端版本与账号权限待登录验证。
- 无需现在安装 Docker、npm 或 JDK。Codex 外独立运行前端需要另行准备系统级 Node/pnpm。
- 下载项目依赖、写入工具缓存或执行建库时按实际权限处理；本轮只批准了版本检查，不推定所有后续操作均已获批准。

## 文件职责

| 文件或目录 | 职责 |
| --- | --- |
| `.gitignore`、`.editorconfig`、`README.md` | 排除凭据与构建产物，统一编码，记录启动方法 |
| `scripts/use-java21.ps1` | 仅设置当前 PowerShell 进程的 JDK 与 PATH，不修改机器配置 |
| `backend/pom.xml`、`backend/mvnw.cmd`、`backend/.mvn/wrapper/` | 后端依赖与固定构建工具 |
| `backend/src/main/java/com/englishhero/EnglishHeroApplication.java` | 应用入口 |
| `backend/src/main/java/com/englishhero/system/` | 元数据查询与系统状态 DTO、服务、控制器 |
| `backend/src/main/java/com/englishhero/config/SecurityConfig.java` | 阶段 0 最小访问边界，不实现真实认证 |
| `backend/src/main/resources/application.yml` | 端口、UTC、连接池、迁移和本地配置导入 |
| `backend/config/application-local.example.yml` | 可复制的配置示例，不包含真实密码 |
| `backend/src/main/resources/db/migration/V1__system_metadata.sql` | 系统标识与元数据表 |
| `infra/sql/create-dev-databases.sql` | 专用开发/测试库创建说明，不嵌入密码 |
| `frontend/package.json`、`frontend/pnpm-lock.yaml`、`frontend/vite.config.ts` | 依赖锁定、脚本和 API 代理 |
| `frontend/src/styles/tokens.css`、`frontend/src/styles/base.css` | 原型设计变量与基础样式 |
| `frontend/src/router/index.ts`、`frontend/src/router/navigation.ts` | history 路由、导航项与标题 |
| `frontend/src/layouts/`、`frontend/src/views/` | 学习端、管理端布局和明确标记的待开发页面 |
| `frontend/src/api/client.ts`、`frontend/src/api/system.ts` | Fetch 封装与状态 API |
| `frontend/src/components/SystemStatusBadge.vue` | 连接状态、加载与重试 |
| `frontend/tests/`、`backend/src/test/` | 单元、数据库集成和浏览器验证 |
| `docs/acceptance/phase-0.md` | 实际执行后的证据与限制，不预填成功 |

## Task 1：可重复运行的工程与环境配置

**Files:** 创建 `.gitignore`、`.editorconfig`、`README.md`、`scripts/use-java21.ps1`；初始化 `backend/pom.xml`、Maven Wrapper、应用入口；初始化 `frontend/package.json`、Vue 入口、Vite/TS 配置与锁文件。

**Interfaces:**
- `use-java21.ps1 -JavaHome <absolute-path>` 仅修改当前进程环境，检查 java 与 javac 为 21。
- 后端应用名 `english-hero`，端口 `8080`；前端端口 `5173`，设置 strictPort，冲突时报告错误。
- 前端脚本固定为 `dev`、`typecheck`、`build`、`preview`、`test:unit`、`test:e2e`；`preview` 使用 4173。

- [ ] 检查目录仍只有规划文档，并初始化 Git；不创建远程仓库，不覆盖原型。
- [ ] 建立 `.gitignore`，排除 `.env*` 的真实配置、`backend/config/application-local.yml`、`node_modules`、`target`、浏览器报告和工具缓存，保留 `.example` 文件。
- [ ] 创建项目级 JDK 脚本；用本机路径执行后运行 `java -version`、`javac -version`、`mvn -version`，均应显示 21；不修改全局 JAVA_HOME。
- [ ] 初始化 Boot 4.0.8，设置 `java.version=21`、UTF-8 和 Maven Wrapper 3.9.9。
- [ ] 前端采用 Vue 官方工程方式初始化，保留 TypeScript、Router、Pinia；安装所需稳定版并记录精确版本，packageManager 固定 `pnpm@11.25.0`。Element Plus 按组件显式引入，避免新增自动导入插件。
- [ ] 配置测试与构建脚本。运行 `pnpm --dir frontend typecheck`、`pnpm --dir frontend build`；在 backend 工作目录运行 `.\mvnw.cmd -version`，预期类型检查和构建成功、Wrapper 输出 Maven 3.9.9/Java 21。
- [ ] 将首次依赖安装及权限限制写入 README，提交任务文件，提交信息 `chore: initialize phase-zero workspace`。

依赖原则：版本由 package.json/锁文件与 Boot BOM 管理；安装工具依赖不意味着用户必须手动逐个安装 Vue、Spring Boot、Flyway 或 MyBatis。

## Task 2：MySQL 迁移与真实系统状态 API

**Files:** 创建 `infra/sql/create-dev-databases.sql`、本地配置示例、V1 迁移、`SystemMetadata.java`、`SystemMetadataMapper.java`、`SystemStatusResponse.java`、`SystemStatusService.java`、`SystemStatusController.java`、`SecurityConfig.java`；测试 `SystemStatusControllerTest.java`、`SystemStatusServiceTest.java`、`SchemaMigrationIT.java`。

**Interfaces:**
- `SystemMetadataMapper extends BaseMapper<SystemMetadata>`，表 `system_metadata`，字段 `metadata_key varchar(64)` 主键与 `metadata_value varchar(255)` 非空。
- V1 写入 `('service_name', 'english-hero')`；禁止在正式库启用 Flyway clean。
- `SystemStatusService.getStatus(): SystemStatusResponse`；DTO 字段为 `status`、`service`、`database`，值限定 `UP`/`DOWN`，service 固定 `english-hero`。
- `GET /api/v1/system/status`：数据库查询正常且服务标识正确返回 200/UP；运行期间查询失败返回 503/DOWN，不暴露异常、连接串或账号。
- 本地启用 `/v3/api-docs` 和 Swagger UI；生产配置禁用文档。本阶段允许 GET 状态接口、本地文档与错误分派，其他 API 拒绝访问，不关闭 CSRF。

- [ ] 先准备新库 SQL：`english_hero_dev`、`english_hero_test`，utf8mb4；使用仅能访问项目库的账号。创建账号的密码由本地工具输入，SQL 模板不写入真实值。建库前核实重名库，发现已有库则暂停并确认用途，不清库。
- [ ] 用户在本地复制配置示例到忽略提交的 `backend/config/application-local.yml`；示例键为 `spring.datasource.url/username/password`，连接开发库，测试使用独立测试配置。启动配置通过 `spring.config.import=optional:file:./config/application-local.yml` 加载，命令工作目录固定 backend。
- [ ] 通过配置的账号执行 `SELECT VERSION(), DATABASE()` 并记录服务端版本；不得在输出中打印密码。没有账号配置时本任务标记待完成，先推进不依赖数据库的任务。
- [ ] 引入 `spring-boot-starter-webmvc`、`spring-boot-starter-security`、`spring-boot-starter-flyway`、`org.flywaydb:flyway-mysql`、`com.mysql:mysql-connector-j`、`mybatis-plus-spring-boot4-starter:3.5.17`、`springdoc-openapi-starter-webmvc-ui:3.1.1`。测试使用 Boot 4 的 WebMVC 测试 Starter；其余版本跟随 BOM，不重复引入 MyBatis Starter。
- [ ] 写控制器/服务测试后先运行，确认缺少实现导致失败，再实现最小能力。断言：正常查询得到 200 且三个 JSON 字段符合契约；数据库异常得到 503 且响应不含异常消息；元数据缺失得到 DOWN；未知 API 不提供业务能力。示例断言：`jsonPath("$.service").value("english-hero")`、`jsonPath("$.database").value("DOWN")`。
- [ ] 实现 Mapper、迁移与 API；连接池连接超时设置 3000ms，MySQL connect/socket 超时设置为有界值；应用启动无法迁移时失败并提示检查本地配置，不能返回虚假的 UP。
- [ ] 实现 `SchemaMigrationIT`：只接受数据库名 `english_hero_test`，新测试库首次迁移后存在元数据和成功的 Flyway 历史；再次 migrate 没有待执行迁移。测试不调用 clean，不创建业务表。`failsafe` 的 `mysql-it` profile 才运行 `*IT`。
- [ ] backend 工作目录运行 `.\mvnw.cmd test`，再在独立测试库配置完成后运行 `.\mvnw.cmd verify -Pmysql-it`。预期单元测试与数据库集成测试都成功。
- [ ] 启动后访问状态 API 和 `/v3/api-docs`，后者必须包含 `/api/v1/system/status`；Swagger UI 加载无资源或 schema 错误。提交 `feat: add database-backed system readiness`。

真实账号、JDBC 会话和用户角色不在这里实现；阶段 1 接管安全配置。本阶段不添加 JPA/H2 来绕过 MySQL 验证。

## Task 3：原型风格的路由、布局和手机导航

**Files:** 在 frontend 创建 `src/styles/tokens.css`、`src/styles/base.css`、`src/router/index.ts`、`src/router/navigation.ts`、`src/layouts/LearnerLayout.vue`、`src/layouts/AdminLayout.vue`、`src/components/AppNavigation.vue`、`src/views/HomeView.vue`、`src/views/PendingFeatureView.vue`、`src/views/NotFoundView.vue`、`playwright.config.ts`；测试 `tests/e2e/navigation.spec.ts`。

**Interfaces:**
- 导航项类型 `NavigationItem { label: string; to: string }`；使用 RouterLink，不依赖全局 DOM 切换。
- 路由 `/`、`/login`、`/vocabulary`、`/reading`、`/practice`、`/stats`、`/admin` 与未知地址 404。
- Home 显示品牌、入口和开发状态，不显示虚构的学习量、打卡或登录状态；业务路由显示“待开发”及对应阶段。
- 管理布局为“管理端布局预览”，空页面无后台数据、无写操作；真实鉴权由阶段 1 加入。
- 手机宽度 390px 下采用可发现的菜单或展开按钮，所有主要入口可通过点击和键盘访问。

- [ ] 拆出原型的颜色、字体、圆角和间距变量，学习端保留 CSS；后台仅引入实际用到的 Element Plus 组件。
- [ ] 不复制整段单文件脚本、演示登录、假词典和 AI 回复。预留独立类名 `wall-note` 与 `study-hint`，避免原型 `.note` 污染；本阶段无留言墙，不为未来组件写占位 CSS。
- [ ] 创建独立布局和上述路由，缩小首页品牌区；未实现的功能不放可点击的假提交按钮，不声称后台或用户权限已经实现。
- [ ] 建立 Playwright 基础配置，webServer 启动 Vite；使用 Chromium，配置桌面与 390px viewport，按 Task 5 的命令安装浏览器测试运行时。
- [ ] 编写 Playwright 导航测试，先验证缺少路由/布局时失败：桌面与 390px 都能进入各入口；未知地址显示 404；Tab 可聚焦主要入口，菜单按 Escape 可关闭。
- [ ] 实现导航和反馈，再运行 `pnpm --dir frontend typecheck`、`pnpm --dir frontend test:e2e navigation.spec.ts`。预期断言通过。
- [ ] 桌面与手机截图检查品牌区、空状态和管理端布局，确认无横向溢出：`document.documentElement.scrollWidth <= window.innerWidth`。提交 `feat: add learner and admin preview layouts`。

## Task 4：前后端请求与连接状态反馈

**Files:** 创建 `api/client.ts`、`api/system.ts`、`components/SystemStatusBadge.vue`；修改 `HomeView.vue`、`vite.config.ts`；测试 `tests/unit/api-client.spec.ts`、`tests/unit/system-status.spec.ts`。

**Interfaces:**
- `requestJson<T>(path: string, options?: RequestInit): Promise<T>`，仅接受以 `/api/v1/` 开头的同域路径，credentials 固定 same-origin。
- `ApiError extends Error { status: number }`，非 2xx 返回明确的状态错误；默认超时 5000ms，支持调用者传入 AbortSignal。
- `getSystemStatus(signal?: AbortSignal): Promise<SystemStatusResponse>`，DTO 与 Task 2 三个字符串字段一致。
- Badge 状态为 loading/ready/unavailable；unavailable 显示“服务暂不可用，点击重试”，异步变化通过 aria-live 通知，不循环自动重试。
- Vite 将 `/api` 代理到 `http://127.0.0.1:8080`。API 缺失不能回退成 HTML 页面并被误判为成功。

- [ ] 先写单元测试：模拟 200 正确解析；503 抛 ApiError 且 status=503；5000ms 超时取消 fetch；外部 AbortSignal 取消请求。运行 `pnpm --dir frontend test:unit api-client.spec.ts`，确认缺实现失败。
- [ ] 实现请求封装与系统接口，响应格式非法时显示失败，不能把任意 200 HTML 当成 UP。
- [ ] 写 Badge 测试：请求中显示 loading，成功显示连接正常，失败可重试；组件卸载取消请求，旧请求完成不覆盖新状态。测试使用 fake timers，避免真实等待。
- [ ] 实现 Badge，并放在 Home；保持开发连接状态与未来学习数据分离。
- [ ] 运行 `pnpm --dir frontend test:unit` 与 `typecheck`，再启动前后端检查浏览器请求确实通过 Vite 代理。提交 `feat: connect frontend to system status API`。

## Task 5：集成验收与启动交接

**Files:** 修改 `frontend/playwright.config.ts`；创建 `frontend/tests/e2e/foundation.spec.ts`、`docs/acceptance/phase-0.md`；完善 README；更新导航测试配置。

**Interfaces:**
- Playwright 默认 Chromium；配置两个 viewport：1440x1000、390x844。
- 浏览器安装是项目测试依赖：`pnpm --dir frontend exec playwright install chromium`，由执行者处理下载审批，无需用户先手动安装 Chrome。
- 常规浏览器测试使用 Vite webServer，端到端联调明确要求后端与项目新库运行；测试报告不得混淆 mocked API 与真实数据库联调。
- `docs/acceptance/phase-0.md` 记录版本、实际命令、通过/失败、截图和未完成项；没有数据库凭据就明确写未完成数据库验收。

- [ ] 本阶段 Tasks 2—4 涉及的测试文件随各自任务创建；基础 Playwright 配置先为 Task 3 添加，最终在本任务补齐真实联调用例，不把测试配置延迟到首次运行之后。
- [ ] 浏览器验证：从 `/reading` 直接进入并刷新保持页面；未知路由进入 404；路由跳转无控制台异常；真实后端 UP 时 Badge 为正常。
- [ ] 通过 Playwright 网络拦截模拟后端失败，验证失败提示与重试，不停止用户的 MySQL 服务；该用例标记为浏览器错误态测试。
- [ ] 检查 `pnpm --dir frontend build` 产物，启动 preview（4173），再次检查 history 路由刷新；部署时 Nginx 必须配置 history 回退，运行验证留给部署阶段。
- [ ] 从新终端按 README 运行项目级 JDK 脚本、后端与前端命令，验证有空格的真实目录可运行，版本检查仍为 Java 21；检查端口占用时提示明确。
- [ ] 最终运行 `pnpm --dir frontend typecheck`、`test:unit`、`build`、`test:e2e`、backend `.\mvnw.cmd test` 和 `.\mvnw.cmd verify -Pmysql-it`。独立记录每条退出码与结果，不因构建成功推定运行正常。
- [ ] 验证新库迁移、二次启动、状态 API、Swagger、手机导航与刷新均完成后，填写验收记录并提交 `docs: record phase-zero acceptance`。

## 阶段完成条件与后续

只有以下内容全部有实际证据才标记阶段 0 完成：依赖版本固定；Java 21 构建通过；专用新库迁移成功；系统接口读到真实数据库；Swagger 可用；前端类型检查/构建/必要测试通过；手机导航可用；刷新与直接访问路由正确；README 可复现启动。

本计划建议由主会话按顺序执行。后端契约、前端 API 和最终联调依赖较紧，现阶段逐任务实施更容易控制范围。用户评审此计划并确定执行方式后再开始业务骨架实现；数据库账号未配置时可推进布局和 Mock 测试，但不能宣称阶段 0 已完成。

## 2026-10-14 流程完善补充：保留五个任务与原顺序

本节对应用户参考流程中遗漏的项目规范、详细设计入口、异常与日志、持续进度及交接。原始复选框仍保留计划语义，当前实施状态见 [进度](../../progress.md) 与 [验收记录](../../acceptance/phase-0.md)。已有实现不重新初始化或覆盖，收尾只处理实际未完成项。

### 文档补充：本轮已落地

- 项目根 AGENTS.md、docs/README.md、docs/project-workflow.md、docs/progress.md、docs/decisions.md。
- docs/design/design-system.md、docs/design/page-interactions.md、docs/database.md、docs/api.md。
- 每阶段开工前核对需求/UI/数据/API，任务提交前审查差异，验收后更新进度与决策。文档采用主定义加索引，不另建重复的技术栈和阶段清单。
- 本轮没有重跑业务测试；历史测试数字来自原验收记录，未完成的数据库与视觉确认仍未完成。

### 原 Task 2 收尾：基础异常、请求追踪与日志（待实施）

**Files:** 后续创建 `backend/src/main/java/com/englishhero/web/RequestTraceFilter.java`、`ApiErrorResponse.java`、`ApiExceptionHandler.java`；测试 `backend/src/test/java/com/englishhero/web/ApiExceptionHandlerTest.java`、`RequestTraceFilterTest.java`。按需要修改现有服务日志，不改已有系统状态 DTO。

**Interfaces:** 请求标识由服务端生成，响应头 `X-Request-Id` 与日志 traceId 对应。非系统状态接口的内部异常返回 500、`code=INTERNAL_ERROR`、可恢复 message 和 traceId；系统状态的 503/DOWN 响应保留原契约。认证、授权及 CSRF 拒绝不误映射为 500，后续业务错误在对应阶段添加。

- [ ] 先补失败测试：未知内部异常的响应没有 SQL/凭据/堆栈；traceId 与响应头一致；系统状态 503 保持原 JSON；安全拒绝状态保持原 HTTP 状态。
- [ ] 实现最小异常映射与请求标识，日志仅记录方法/路径、状态、耗时和必要错误类别；不记录请求/响应全文、Cookie、Token 或密码。不新增日志平台。
- [ ] 在 backend 运行 `.\mvnw.cmd test`；预期新增用例及已有 8 个用例均通过，具体总数写入本次验收，不把旧计数当成新结果。

### 原 Task 4 收尾：接口文档和错误态一致（待实施）

- [ ] 核对 `docs/api.md`、前端 API 类型和实际响应；状态查询维持 UP/DOWN 契约，公共错误提示不显示内部工具信息。
- [ ] 只在实际实现了新的异常能力后更新接口规范，验证失败/重试及取消行为仍正确，运行相关前端单元验证。

### 原 Task 5 收尾：设计审查、代码审查与交接（待完成）

- [ ] 按 docs/project-workflow.md 核对当前阶段 UI、数据库和 API，不补未来业务表；视觉确认、真实数据库和 Swagger 不因文档完善而跳过。
- [ ] 对本阶段新增异常/日志能力及未完成项进行差异审查，记录范围、发现问题及处理结果；保留现有独立原型和其他任务的修改。
- [ ] 用实际命令与结果更新 docs/acceptance/phase-0.md，并同步 docs/progress.md、必要的 decisions 条目和会话交接信息。只有原验收要求和本节新增收尾项都有证据，才标记阶段 0 完成。

后续上线检查补充配置管理、健康检查、测试环境、迁移、备份恢复和应用回滚，仍放在原对外部署节点，不成为阶段 0 的额外安装要求。


## 2026-10-14 收尾实际状态

用户已确认基础布局并授权收尾。基础异常/trace/日志、接口规范一致性、正式Vue基础结构和独立审查已完成；验收见 phase-0.md 最新段落。异常checked/显式500与既有Maven缓存配置边界均先失败后修正。后端14、前端15单元、16浏览器（2真实数据库跳过）、独立原型39通过，最终类型/构建退出0。4173现有服务保留，E2E_PORT=4174明确验证同构建。

尚未完成：专用数据库本地配置、真实迁移/二次启动、真实API/OpenAPI/Swagger与真实浏览器联调。用户表示会本地填写后告知，配置缺失时不能把阶段0标为完成。已确认基础布局不要求再次批准同一布局；真实认证仍属于阶段1。


## 2026-10-14 最终完成

阶段0验收完成：专用开发/测试库及独立账号已创建，MySQL服务端8.0.41；空库V1迁移、重复迁移、开发两次启动、真实UP接口、OpenAPI/Swagger、桌面手机真实联通均通过。最后mvnw -o verify -Pmysql-it退出0，14单元/MVC+2真实MySQL集成，无默认自动身份。基础布局已获用户确认，Vue已落实结构。默认4173现有服务保留，显式4174同构建验证通过。详细命令见验收记录最终段落；阶段1未启动。
