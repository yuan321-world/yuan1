# JWT + Redis Authentication Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** 把现有浏览器认证迁移为短期JWT、不透明刷新Cookie和项目专用Redis登录状态，保留账号、邮箱、权限与既有页面。

**Architecture:** JWT只进入前端内存；刷新秘密只进入HttpOnly Cookie，Redis只存摘要、期限和撤销状态。请求认证仍核对MySQL账号版本与角色，维持立即撤销。当前运行的旧构建在最终前后端就绪前保持，不做长期双认证兼容。

**Tech Stack:** Vue3/TypeScript/Pinia、Java21/Boot4.0.8、Spring Security/Jose、Spring Data Redis、MyBatis-Plus、MySQL；新增依赖版本由Boot BOM管理。

**Spec:** [已确认设计](../specs/2026-10-15-jwt-redis-auth.md)。用户于2026-10-15确认设计；用户已选择当前会话顺序实施；实际完成日期2026-10-16，见 [验收记录](../../acceptance/2026-10-15-jwt-redis-auth.md)。

## Global Constraints

- Access JWT有效期10分钟，签发不超过family绝对期限；RS256、固定issuer/audience、受控kid、稳定本地密钥。
- 普通2小时空闲/24小时绝对；记住登录自登录起最多7天，轮换不延长绝对期限。
- Refresh为256位随机秘密，sid为128位随机值；Cookie HttpOnly/host-only/SameSite=Lax，Path=/api/v1/auth，生产Secure；不存浏览器持久存储。
- Redis键eh:dev:auth:v1:{sid}:family及used摘要标记，测试用独立实例和eh:test前缀；只有sid的大括号是Cluster标签。
- 项目开发/测试建议6380/6381；不操作已有6379、D:/redis配置、其他项目键或全局ACL，不用FLUSHDB/FLUSHALL。
- 当前Redis二进制5.0.14.1仅已查版本，未验证兼容；失败先诊断，不自动安装Docker/替代软件或降低安全要求。
- MySQL/V1—V4和用户数据不改；停用JDBC Session但不删表，不为本任务消耗V5。
- API前缀/api/v1；CSRF仍覆盖所有写入，Origin严格按配置允许源；业务字段/权限在服务端验证。
- Redis不可达503 AUTH_SERVICE_UNAVAILABLE，不旁路、不伪退出；改密/找回以MySQL security_version为撤销底线。
- 不改登录注册/首页视觉，不发真实邮件，不进入阶段2阅读实现。新凭证、密码、Cookie及真实配置不入Git/日志/聊天。
- 开始每任务先看git status，保留已有大量未提交工作；不切到缺少当前身份代码的旧HEAD工作树。只审查/暂存本任务差异，未获要求不自动提交或发布。

## Review Focus

1. Redis在JWT已正确验签后故障：受保护请求仍须503，不误放行；Task3测试。
2. 攻击者知道sid但刷新秘密随机错误：不得撤销合法family；Task2原子脚本测试。
3. 用户退出/切换账号时刷新响应迟到：不得恢复旧Token或旧用户；Task4单元/浏览器测试。
4. 轮换返回新Cookie时接近7天或24小时边界：不能重置期限，超期即拒绝；Task2/3时钟及Cookie测试。
5. 无Origin/Referer、跨源和带旧Bearer的公开找回请求：敏感Cookie操作按来源规则拒绝，公开邮件流程不被旧Bearer干扰；Task3/5集成测试。

## 文件边界和统一类型

- `backend/.../identity/token/`负责配置、JWT、刷新凭证、Redis原子状态、认证过滤器和Cookie；既有IdentityService/EmailService继续负责账号与邮箱业务。
- `frontend/src/auth/token-session.ts`仅保存内存Token/代次；`auth-transport.ts`负责同源请求的按需刷新与跨标签协调；`api/client.ts`保留底层超时、取消、结构化错误，避免Pinia循环导入。
- 初期复用`SessionPrincipal`作为不含秘密的业务principal，更新其历史命名注释；JWT过滤器从真实用户/角色构造它。业务Controller不直接接收Spring Jwt，不整体重命名认证模块。
- `LoginFamily(sid,userId,securityVersion,createdAt,lastSeen,absoluteExpiresAt,idleSeconds,remember,state)`，state=ACTIVE/REVOKED，时间统一Instant。
- `VerifiedAccess(userId,sid,securityVersion,expiresAt)`只在签名、类型及除exp是否已过期之外的全部约束验证后产生；过期判定与family检查由认证入口完成，过期对象不能直接授予权限。
- `AuthTokens(user:UserDto,accessToken,tokenType:"Bearer",expiresIn)`为唯一前后端成功契约；含秘密的Java对象必须覆盖toString为脱敏，不用默认record输出秘密。

---

### Task1：项目专用Redis与密钥配置可验证

**Files:** Create `scripts/initialize-auth-local.ps1`、`scripts/start-project-redis.ps1`、`scripts/stop-project-redis.ps1`、`backend/config/application-auth.example.yml`、`backend/config/application-auth-test.example.yml`、`backend/.../identity/token/AuthProperties.java`、`AuthConfiguration.java`、`backend/src/test/java/com/englishhero/identity/token/AuthConfigurationTest.java`、`RedisConnectionIT.java`；Modify `.gitignore`、`backend/pom.xml`。

**Interfaces:** AuthProperties绑定english-hero.auth，字段issuer、audience、currentKid、privateKeyPath、publicKeyPath、refreshHmacSecret、namespace、accessTtl（默认600秒）；Redis连接用Boot标准spring.data.redis配置，不维护第二套客户端连接属性。脚本`-Environment dev|test`分别管理6380/6381，PID/配置/数据限定本工作区忽略目录；配置中的真实凭据不通过命令行参数暴露。

- [x] 写`rejectsMissingKeysOrUnsafeRedisNamespace()`、`preservesKeysAcrossRepeatedInitialization()`测试；断言缺密钥启动失败、重复初始化不覆盖密钥/现有配置、测试前缀不能为dev。
- [x] 运行`backend/mvnw.cmd test -Dtest=AuthConfigurationTest`，确认新增测试先失败；检查BOM实际依赖坐标和Java21，不自行升级Boot/Security版本。
- [x] 实现初始化及启停脚本：规范化绝对路径必须留在工作区；生成RSA至少2048位及独立HMAC/Redis凭据，已存在则保留；Redis bind127.0.0.1/protected-mode，启动使用配置文件，后台窗口隐藏；停止前验证PID进程路径/命令行属于本项目实例。
- [x] 新增JWT/Jose和Redis依赖，配置严格校验；连接超时约2秒，支持现有Windows Redis的命令预检。不改当前运行实例的认证配置。
- [x] 先启动独立test实例，运行`mvnw.cmd verify -Pmysql-it -Dit.test=RedisConnectionIT`：验证PING、短TTL、Lua原子操作、实例/前缀隔离及断线错误。仅清理随机测试前缀；查验启动文件被Git忽略，输出仅版本/布尔结果。
- [x] 兼容失败记录实际错误及阻塞；未经处理不得把Redis视为就绪。成功则记录端口/进程和命令退出码，继续Task2。

### Task2：JWT和Redis登录实例内核

**Files:** Create `identity/token/JwtService.java`、`RefreshTokenCodec.java`、`RedisFamilyRepository.java`、`LoginFamily.java`、`VerifiedAccess.java`、`AuthTokens.java`、`backend/src/main/resources/redis/auth-{create,touch,rotate,revoke}.lua`；Test `JwtServiceTest.java`、`RefreshTokenCodecTest.java`、`RedisFamilyRepositoryIT.java`。

**Interfaces:**
- `JwtService.issue(SessionPrincipal, LoginFamily): IssuedAccess`、`verifySignatureAndClaims(String): VerifiedAccess`；后者不授予权限，exp在Task3最终认证前单独判定。
- `RefreshTokenCodec.create(): RefreshCredential`，凭证格式`32位小写hex sid + '.' + 43位base64url秘密`；`digest(secret): String`为独立HMAC-SHA256十六进制摘要，parse拒绝超长/畸形输入。
- `RedisFamilyRepository.create(LoginFamily,currentDigest)`、`validateAndTouch(sid,userId,sv,Instant): LoginFamily`、`rotate(sid,presentedDigest,nextDigest,userId,sv,Instant): RotationResult`、`revoke(sid,CredentialProof,Instant)`；RotationResult仅SUCCESS/REPLAY/INVALID/INACTIVE，不返回秘密。CredentialProof只能来自已验证Bearer或刷新摘要匹配，不能由原始sid直接构造。

- [x] 写密码学与Redis回归：固定Clock验证`expiresIn==600`及exp不超过family期限；坏算法/签名/issuer/audience/kid/claim类型/未来nbf拒绝；原始Token不出现在对象toString。
- [x] 写`concurrentRotationHasOneWinner()`、`usedRefreshRevokesFamily()`、`randomSecretDoesNotRevokeKnownSid()`、`rotationNeverExtendsAbsoluteDeadline()`、`idleBoundaryRejectsAtTwoHours()`和独立前缀隔离测试，先运行并观察失败。
- [x] 实现JWT签名/验签及严格解析；只采用配置中的密钥，不从JWT中的URL/jku动态下载。允许当前/受控上一kid，验签仍需后续family和账号核验。
- [x] 实现短Lua脚本：create/touch/rotate/revoke以同sid标签键为原子单位；识别真实used摘要才触发重放撤销，随机错误摘要不改变family。过期、撤销、uid/sv不符不触碰lastSeen；TTL不得延长至绝对期限之外。
- [x] 运行`mvnw.cmd test -Dtest=JwtServiceTest,RefreshTokenCodecTest`及`mvnw.cmd verify -Pmysql-it -Dit.test=RedisFamilyRepositoryIT`，并发和TTL断言必须通过；记录真实Redis边界，不能只用Mock证明原子性。

### Task3：后端登录—刷新—me—退出与CSRF闭环

**Files:** Create `identity/token/TokenAuthenticationService.java`、`JwtIdentityFilter.java`、`RefreshCookieService.java`、`AuthOriginFilter.java`、`AuthReadinessCheck.java`、`backend/src/test/java/com/englishhero/identity/token/TokenTestSupport.java`；Modify `config/SecurityConfig.java`、`IdentityController.java`、`SessionPrincipal.java`、`application.yml`及local/prod配置；Replace不再装配的`IdentitySessionFilter`及其专用测试；Test `TokenFlowIT.java`、`TokenCookieTest.java`、`JwtIdentityFilterTest.java`。Task3同时更新`IdentityFlowIT`中基础登录响应/凭证构造，不能留无意义Session期望。

**Interfaces:** TokenAuthenticationService提供`login(username,password,remember,oldCredential,ip): TokenIssue`、`refresh(refreshCredential): TokenIssue`、`authenticate(compactJwt): SessionPrincipal`、`logout(optionalRefresh,optionalBearer): void`。TokenIssue包含AuthTokens及刷新Cookie值，toString脱敏；只有Cookie服务可写刷新Cookie。沿用GET csrf JSON和其余业务DTO。

- [x] 写HTTP回归：login返回AuthTokens/HttpOnly Cookie，Bearer me200；refresh换Cookie/旧刷新重放使family失效；logout后旧JWT401；无Cookie/已过期退出幂等；错误sid不撤销他人。
- [x] 写CSRF/来源回归：Cookie依赖的login/refresh/logout缺CSRF、跨源、Origin/Referer均缺失被拒绝；明确允许同源/配置本地源；伪造转发来源头不能放行。测试HTTP客户端主动提供配置允许的Origin。
- [x] 写`redisFailureAfterValidSignatureReturns503()`、`revokedFamilyWithExpiredJwtDoesNotRefresh()`及普通/7天Cookie剩余Max-Age测试。运行目标测试，观察旧Session实现不符合新契约而失败。
- [x] 接入Task2：认证先验签/字段，再核对真实账号enabled/sv/角色和family，最后判定exp；仅其余条件合法的过期JWT返回ACCESS_TOKEN_EXPIRED，失败绝不先构造已认证SecurityContext。
- [x] 配置STATELESS与无Session安全上下文；CSRF改Cookie仓库，保留masked token及X-CSRF-TOKEN。区分公开端点和受保护端点，公开找回/验证码不被旧Bearer拒绝；logout允许刷新凭证在Access过期后撤销。删除Session JDBC依赖和自动配置，旧表不删。
- [x] application.yml显式导入忽略的application-auth.yml；TokenTestSupport.register(DynamicPropertyRegistry)供各集成上下文注册独立auth-test配置/允许Origin，并拒绝开发Redis前缀或非english_hero_test库。MVC切片Mock认证边界，不依赖真实dev Redis；不通过跳过全量测试掩盖启动配置问题。
- [x] 启动检查验证Redis/密钥，运行期故障503 AUTH_SERVICE_UNAVAILABLE；成功才发Token/Cookie。新登录使用新sid，验证旧Cookie后仅撤销其family；数据库/Redis错误响应不泄露底层消息。
- [x] 运行目标单元及`mvnw.cmd verify -Pmysql-it -Dit.test=TokenFlowIT`，覆盖Secure/Path/SameSite/删除属性、匿名csrf不创建HttpSession、旧EH_SESSION不能认证。当前8080仍不切换，准备Task4前端契约。

### Task4：前端按需恢复、刷新和退出

**Files:** Create `frontend/src/auth/token-session.ts`、`auth-transport.ts`；Modify `api/client.ts`、`api/identity.ts`、`stores/auth.ts`、`router/index.ts`及`feedback/useFormFeedback.ts`必要状态映射；Test `tests/unit/token-session.spec.ts`、`auth-transport.spec.ts`、`auth-store.spec.ts`、`auth-extension.spec.ts`。

**Interfaces:**
- token-session：`getAccessToken(): string|null`、`acceptAccess(token,expiresIn,expectedGeneration): boolean`、`clearAccess(): void`、`getGeneration(): number`；内存模块，无storage插件。
- auth-transport：`restoreAccess(): Promise<void>`、`authenticatedJson<T>(path,options): Promise<T>`、`logoutCurrent(): Promise<void>`。底层`requestJson`仅做原始同源请求，不引用store；Token API用raw请求，避免刷新递归。
- api/identity解析`AuthTokens`，对外login/refresh更新内存后交UserDto给store；account/admin使用authenticatedJson，公开注册/邮件/重置/CSRF继续raw+原有CSRF。

- [x] 写`parallelExpiredRequestsShareOneRefresh()`、`expiredCredentialRetriesOnceBeforeBusiness()`、`business401AndNetworkFailureNeverReplayPost()`、`lateRefreshCannotRestoreAfterLogout()`、`refresh503PreservesRecoverableIdentity()`，先确认失败；断言零localStorage/sessionStorage写入。
- [x] 保留请求超时/AbortSignal及字段错误解析；只有结构化ACCESS_TOKEN_EXPIRED可触发一次刷新并重试。排除refresh/login/logout等端点，不能把BAD_CREDENTIALS或CSRF403当成续期信号。
- [x] 实现首次refresh→me恢复、single-flight、账号generation；退出中暂停新刷新并序列化凭证操作，失败显示原可恢复错误，不伪造已退出。修改Pinia响应解析，保持页面UserDto和头像菜单交互。
- [x] 跨标签使用Web Locks串行化refresh/logout，BroadcastChannel仅发送身份变化/退出通知；锁内读取最新浏览器Cookie，不广播Token。无锁能力保留本页single-flight，真实重放安全退出；超时/标签关闭不能永久占锁。
- [x] 写并运行跨标签协调Mock单元及Playwright草测：两个页面同时恢复、一个退出时另一页有迟到refresh、切换账号后旧用户界面不配新Token。键盘/表单布局不改。
- [x] 运行`pnpm --dir frontend test:unit`、`pnpm --dir frontend build`，通过后才将Task3后端与本任务前端作为同一契约进行真联调，不单独部署一边。

### Task5：邮箱、安全版本和全部既有流程回归

**Files:** Modify `IdentityController.java`改密/退出衔接，必要时`IdentityEmailService.java`提交后清理适配；业务验证码算法与限流不改。Update `IdentityFlowIT.java`、`IdentityRestartIT.java`、`VerifiedEmailFlowIT.java`、`IdentityCookieTest.java`及前端身份/认证fixtures；Create `TokenRevocationIT.java`、`frontend/tests/e2e/token-auth.spec.ts`。

**Interfaces:** 继续使用Task3的SessionPrincipal适配。security_version仍为MySQL撤销依据，不要求MySQL事务与Redis清理跨库原子提交；Redis清理失败也要拒绝旧登录。刷新/真实测试工具不能提供生产可访问的Token/验证码导出接口。

- [x] 写`passwordResetInvalidatesAccessAndRefreshOnEveryDevice()`、`passwordChangeStillRevokesWhenRedisCleanupFails()`、`roleAndEnabledChangesTakeEffect()`、`oldBearerCannotBlockPublicRecovery()`和正常/记住登录重启恢复测试，先跑红灯。
- [x] 改密成功撤销当前family、清Cookie/内存并返回原需重登流程；找回递增sv后旧Token在下一次请求被拒。不要因Redis清理异常回滚已提交新密码或伪称旧Token仍有效。
- [x] 改写测试认证助手传Bearer与新Cookie，不删除原有注册、角色、当前密码、邮箱占用、防枚举、发送状态归属、验证码到期/重放/并发消费/限流断言。真实集成只用test库/项目test Redis及模拟投递器。
- [x] Playwright fixture显式Mock login/refresh/AuthTokens/me；Mock Token不得进入生产逻辑。更新真实浏览器助手使用真实refresh Cookie，不将测试令牌打印或录制进trace。
- [x] 运行完整`mvnw.cmd verify -Pmysql-it`、前端单元/typecheck/build及全浏览器回归；分别记录真实MySQL/Redis与Mock结果。现有conditional真实邮件浏览器用例未启用就明确跳过，不算通过。

### Task6：切换本地体验服务和交接

**Files:** Update `README.md`、`docs/api.md`、`database.md`、`email-setup.md`、`decisions.md`、`progress.md`、架构方案与阶段2身份依赖；Create `docs/auth-jwt-redis-setup.md`、`docs/acceptance/2026-10-15-jwt-redis-auth.md`。测试运行日期如跨日，验收文内写实际日期，不伪造本计划日期验证。

**Interfaces:** 使用Task1的项目dev Redis、本地稳定密钥和Task3/4匹配构建；不自动复制test配置为dev，不改SMTP授权信息。开发前端4174、后端8080沿用，端口冲突明确失败。

- [x] 开始切换前自审任务差异和spec覆盖；执行方式所要求的独立评审只读检查令牌/脚本/CSRF/竞态及错误边界，修复实际发现后重跑受影响检查。
- [x] 保存当前构建/配置路径及恢复步骤（不输出秘密）。启动dev项目Redis，确认独立端口、保护模式和TTL。生产化平台/版本验收不混作本地验证。
- [x] 在前后端都已验证后停止本任务掌握的旧后端进程，启动JWT后端，前端读取新构建；需要SMTP外网的后端按明确工具审批在允许联网环境启动，保留沙箱及全局网络设置。
- [x] 验证旧Cookie不再认证；手动/受控真实流程证明登录、刷新、头像退出、管理员隔离及改密重登；不擅自用用户真实账号改密或给真实邮箱发测试邮件。
- [x] 故障演练只停本项目test Redis，证明受保护API503且无签名旁路；恢复不接受已撤销family。Redis恢复旧快照时换本项目namespace代次；Session回滚使用新仓库命名空间，不能让旧会话复活。
- [x] 更新文档：真实有效的AuthTokens/refresh契约、配置步骤、命令/退出码/测试数、未执行项目与回滚限制。旧Session表标记未再使用但保留；阶段2草案身份描述更新为已验收的新模式，仍需其自身实施授权。
- [x] 完成后保持项目运行，告知用户需重新登录一次；未完成故障/权限/期限验证不能写迁移完成。只交付本任务文件清单，不自动提交整个脏工作区。

## 验证命令约定

所有Maven命令在backend目录，先在根目录加载`. ./scripts/use-workspace-tools.ps1`与`. ./scripts/use-java21.ps1`，并运行`backend/mvnw.cmd -version`确认实际Java21。命令先用项目缓存；缺依赖导致网络失败时走明确审批，不绕过沙箱。

Task1启动测试Redis并确认本地test配置后，目标单元用`./mvnw.cmd test -Dtest=类名列表`；目标集成用`./mvnw.cmd verify -Pmysql-it -Dit.test=类名列表`；全量用`./mvnw.cmd verify -Pmysql-it`。测试必须限定english_hero_test与项目test Redis，未连接时明确失败。

前端根目录运行`pnpm --dir frontend test:unit`、`pnpm --dir frontend typecheck`、`pnpm --dir frontend build`。受控4174 preview配合`E2E_EXTERNAL_SERVER=1 E2E_PORT=4174 pnpm --dir frontend exec playwright test --workers=1`；前端Mock和真实Token闭环分别记录。禁止并行构建/测试覆盖同一dist、数据库fixture或Playwright报告。

## 自审与执行交接

设计各节映射：环境/密钥→Task1；JWT/Redis/期限/原子性→Task2；后端API/CSRF/退出/故障→Task3；前端并发/恢复→Task4；邮箱/权限/版本回归→Task5；切换/回滚/文档→Task6。上方5个Review Focus均有对应测试。

计划没有新增阅读功能、数据库迁移或外部账号动作。六项任务已执行；命令与期望是原始执行指引，实际命令、测试数和实现偏差以验收及审查记录为准。测试夹具共用TokenHttpITSupport，Cookie测试复用IdentityCookieTest；撤销接口拆为校验Bearer与摘要证明两个明确方法，等价边界已回归。

推荐在当前会话顺序实施（Native）：认证接口和Token状态强耦合，当前工作区已有大量前序改动，由同一实现者按依赖推进可减少交接误差，末尾独立评审。也可选择分任务子代理执行，但须先明确文件所有权和前置接口，不并行写共享安全配置。
