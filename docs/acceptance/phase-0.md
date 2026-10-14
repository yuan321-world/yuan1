# 阶段 0 验证记录

日期：2026-10-13（Asia/Shanghai）
当前结论：**阶段0已于2026-10-14完成验收**。下文保留执行历史，当前结果以最后的最终联调段落为准。

## 当前交付

Java 21 / Boot 4.0.8 / Maven Wrapper 3.9.9、Vue 官方工程及精确依赖锁、系统状态接口、
MySQL V1 迁移、最小访问边界、请求超时/取消/重试、history 路由和手机空布局已实现。
本地分支为 phase-0-foundation，未创建远程仓库、合并或发布。

[优化后的独立 HTML 原型](../prototypes/lingua-loop-preview.html) 直接基于原始文件，保留完整页面与演示交互，
先登录再首页；原始文件保持不变。它位于独立文档目录，不随 Vue 正式构建发布。
当前 Vue 空布局需在用户确认上述原型后调整，不是最终视觉验收结果。

## 实际验证

| 检查 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| Java / javac / Maven | use-java21.ps1 后查询实际版本 | 均使用 Java 21.0.12.1 |
| Maven Wrapper | backend 中 .\\mvnw.cmd -version | 退出 0，Maven 3.9.9 / Java 21 |
| 前端冻结安装 | pnpm --dir frontend install --frozen-lockfile | 退出 0 |
| 前端类型检查 | pnpm --dir frontend typecheck | 退出 0 |
| 前端单元测试 | pnpm --dir frontend test:unit | 退出 0，15/15 |
| 前端构建 | pnpm --dir frontend build | 退出 0 |
| Vite 浏览器验证 | pnpm --dir frontend test:e2e | 退出 0，12 通过，2 个真实数据库用例明确跳过 |
| preview 默认端口 | E2E_PREVIEW=1，test:e2e | 退出 1，4173 已被现有服务占用；未停止该服务 |
| 构建产物浏览器验证 | 临时测试配置以 pnpm preview --port 4174 启动同一产物 | 退出 0，12 通过、2 个真实用例跳过；临时配置已删除 |
| 后端单元 / MVC | backend 中 .\\mvnw.cmd test | 退出 0，8/8 |
| MySQL 集成测试 | .\\mvnw.cmd verify -Pmysql-it | 退出 1，缺 config/application-test.yml，连接前失败，未迁移数据库 |
| 独立 HTML | Chromium file URL，1440x1000 / 390x844 | 登录前首页隐藏，账号按钮只填入，提交后进入对应端；主要导航、退出、刷新、无溢出通过 |
| 原型键盘 / 样式 | 手机 Enter/Tab/Escape，review-note computed style | 通过，关闭菜单恢复焦点，学习提示 position=static |
| 原型表单无障碍 | 空提交后切换登录/注册，检查 aria-invalid / skip target | 首次失败，修正后通过 |
| 原型隔离 | 检查 public / dist 及演示标识 | public 仅 favicon；dist 不含独立原型或其演示脚本 |

浏览器常规状态验证使用网络拦截：正常 200、失败 503 后重试、拒绝 200 HTML 回退，不能替代真实 MySQL 联调。
导航测试允许后端未启动，因此代理 ECONNREFUSED 8080 日志属于已覆盖的不可用状态。
Vitest 有非阻断的配置扩展名警告，未来 Vite native loader 默认值变更前需调整；测试与构建均退出 0。
启动和 Wrapper 下载从含空格的实际目录执行，中文用户路径可运行；Wrapper 用户目录在终端有编码显示问题。

## 独立审查与修正

Reviewer 审查后端、迁移、Java 脚本、原型差异和请求层，未发现其他 Critical / Important 缺陷。
以下问题均先复现失败再修正通过：

- HTTP 200 但 DTO 为 DOWN 时 Badge 不再误报连接正常。
- 预先取消的 AbortSignal 不再产生未处理 Promise 拒绝。
- 手机悬停词汇弹层不再遮挡菜单。
- 切换登录/注册重置 aria-invalid，跳转链接指向当前可见表单。

未判断事项：真实 MySQL/Swagger 运行结果（缺凭据）、用户视觉批准、继承的演示业务行为、未批准的 Vue 美学。
这些是明确的验收边界，不推定成功。原型只用于界面确认，不是服务端认证实现。
Boot 4 Security 测试模块依据：[官方迁移说明](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)。

## 未完成项

1. 在本地创建专用新库及账号、填写被忽略提交的开发和测试配置。
2. 核对 MySQL 服务端版本，验证首次/二次迁移、后端首次/二次启动。
3. 验证真实状态 API、OpenAPI 和 Swagger UI，运行 E2E_REAL_BACKEND=1。
4. 用户亲自确认独立 HTML，再按确认结果调整正式 Vue 页面。
5. 4173 的服务冲突解除后，按 README 在普通终端复验默认 preview 命令。临时 4174 已验证构建产物的路由刷新。

真实认证及数据库会话属于阶段 1。原型门槛和演示角色不代表真实账号/权限已实现。

## 2026-10-14 收尾更新

用户已明确确认当前独立原型的最基础布局，授权完成阶段0剩余工作。正式 Vue 已按该基础结构实现品牌区、Georgia 每日名言、六个静态模块、今日计划与打卡空状态、登录外观；未复制原型演示账号、假统计或整段演示脚本。真实登录与首页访问鉴权仍属阶段1；阶段0可直接预览路由。原型完整名言交互继续保留于独立 HTML，Vue 当前只移植名言显示及按日稳定选句，不推定已实现真实收藏/翻译业务。

后端已实现 RequestTraceFilter、ApiErrorResponse、ApiExceptionHandler：服务端生成 X-Request-Id，日志与响应对应；内部 checked/runtime/显式5xx异常统一脱敏；非5xx框架状态及Security/CSRF拒绝保持；系统状态503 JSON不变。日志不采集query/body/cookie/token/password或异常消息。MDC在成功/异常后恢复或清理。

本轮命令在普通沙箱执行，先点加载 scripts/use-workspace-tools.ps1，Maven实际使用Java21。工具缓存修复见沙箱记录。
| 检查 | 结果 |
| --- | --- |
| mvnw -o test | 退出0，14/14；两种异常边界先红灯再通过 |
| 前端 typecheck / build | 退出0；最终样式加载顺序修正后重建通过 |
| 前端 test:unit | 退出0，15/15 |
| 前端 test:e2e（5173） | 退出0，16通过，2真实数据库用例跳过 |
| E2E_PREVIEW=1 默认4173 | 测试命令退出1，已有Python服务占用；没有停止该服务 |
| E2E_PREVIEW=1 E2E_PORT=4174 test:e2e | 退出0，16通过，2真实数据库用例跳过；新增显式端口选项供复现 |
| 独立 HTML 原型 Playwright | 退出0，39/39，1440/582/390px |
| mvnw -o verify -Pmysql-it | 退出1，缺 application-test.yml，真实库迁移未执行 |
| 差异审查 | 独立审查完成，异常边界与缓存覆盖问题已修正，未发现剩余重大问题 |

桌面及390px Vue排版已在浏览器检查；系统UP状态截图使用Mock，不代表真实数据库联通。测试服务退出等待期间仅清理本轮已知启动的Node进程，保留4173现有Python进程。

当前阶段仍未完成。剩余：用户填写开发/测试本地配置后，验证服务端版本、首次与二次迁移、两次启动、真实状态API、OpenAPI/Swagger与真实浏览器联调；完成后再更新阶段0结论。不进入阶段1。

## 2026-10-14 最终数据库联调与结案

**阶段0完成。** 用户已确认最基础布局；此前专用数据库与真实运行验收阻塞已解除。阶段1没有启动。

### 配置与建库

用户最初填写的是本地管理员连接。只读预检得到 MySQL8.0.41，两个项目库和两专用账号均不存在，具备建库/账号授权能力。按已授权阶段0范围创建 english_hero_dev / english_hero_test（utf8mb4_0900_ai_ci）及各自127.0.0.1专用账号，仅授予各自库的 SELECT/INSERT/UPDATE/DELETE/CREATE/ALTER/INDEX/REFERENCES。没有授予全局权限、DROP或GRANT OPTION，也未修改其他库或既有账号。

分别生成强随机密码并只写入Git忽略的两个本地配置，配置已切换为项目账号；密码没有打印到终端/聊天。随后用专用账号分别连接，SHOW GRANTS仅匹配USAGE及本库，首次连接两库均零表，后续均只有系统元数据和Flyway历史两表。

### 实際命令与结果

| 检查 | 命令/方法 | 结果 |
| --- | --- | --- |
| 配置保护 | git check-ignore 两个本地配置 | 退出0，两个文件均忽略 |
| 服务端与权限 | 专用账号JDBC只读预检 | 退出0，MySQL8.0.41，两个库身份正确、权限各自隔离 |
| 全后端验证 | use-workspace-tools + use-java21，backend mvnw -o verify -Pmysql-it | 退出0，14单元/MVC+2真实MySQL集成，零失败/错误 |
| 首次测试库迁移与重复迁移 | SchemaMigrationIT | V1成功；第二次migrate零新增；历史version1成功记录只有1条 |
| 首次开发库启动 | backend java -jar target/english-hero-0.0.1.jar | 启动成功，空开发库应用1个V1迁移 |
| 二次开发库启动 | 同一启动命令 | 启动成功，schema up to date，No migration necessary |
| 真实状态与trace | GET /api/v1/system/status | 200，UP/english-hero/UP，X-Request-Id存在 |
| 真实OpenAPI/Swagger | HTTP文档 + Chromium真实Swagger页面 | 退出0，包含状态GET，UI/schema/resources正常，无页面错误/失败请求 |
| 真实前端联通 | E2E_REAL_BACKEND=1 pnpm --dir frontend test:e2e foundation.spec.ts --grep 'real backend' | 退出0，桌面和390px共2通过；不拦截真实API |
| 最终打包应用安全边界 | 真实GET未知API、POST状态API | 均403，trace头存在；默认测试用户不再生成 |
| 持久化只读核验 | 两个库的元数据与Flyway历史 | 退出0，服务标识正确，成功V1历史各1条，两表 |

前端类型/构建、15单元、16常规浏览器及独立原型39项结果沿用上文2026-10-14收尾记录；本轮前端代码未变，仅补齐此前跳过的两个真实联通用例。默认4173现有Python服务保留，显式4174构建产物验收已通过。

### 运行验收修正

真实日志发现Boot自动创建未使用的默认身份/临时密码，与阶段0没有身份的边界不一致。主应用仅排除 UserDetailsServiceAutoConfiguration，仍保留Spring Security链和CSRF。新增 PhaseZeroStartupIT 在启动上下文前限制连接测试库；真实完整上下文断言没有UserDetailsService并读取UP。先复现有inMemoryUserDetailsManager的RED，再修正GREEN；MVC测试切片与实际配置一致。最终verify与打包应用日志均不再生成默认身份密码，既有拒绝边界保持。独立新增范围审查未发现剩余重要问题。

本轮临时后端/Vite服务在验证后关闭，不停止用户MySQL或4173现有服务。已执行V1不修改、不清库。真实登录与持久会话仍在阶段1，Vue登录外观不表示认证功能已完成。

## 2026-10-14 结案复核

独立审查发现URI日志可能包含URL重写的会话参数，新增失败回归后在日志值中剥离原始及编码matrix参数；请求处理不变。mvnw -o verify -Pmysql-it退出0，15单元/MVC+2真实MySQL集成通过。前端类型检查与15单元退出0；未发现剩余阻断问题。方案过时状态已同步，阶段0完成，阶段1等待批准。详见 ../reviews/2026-10-14-phase-0-final-audit.md。
