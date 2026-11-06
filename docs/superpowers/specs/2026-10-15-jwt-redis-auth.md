# 阶段1补充：JWT + Redis认证迁移设计

日期：2026-10-15（Asia/Shanghai）。状态：**用户已确认并选择顺序实施，2026-10-16已完成迁移和本地切换；见 [实施计划](../plans/2026-10-15-jwt-redis-auth.md) 及 [验收](../../acceptance/2026-10-15-jwt-redis-auth.md)。** 阶段2阅读保留设计草案，先完成本次认证迁移及验收再开始业务实现。

## 1. 目标与现有系统

保留用户名密码登录、普通用户/管理员、邮箱注册验证、首次绑定、找回密码、改密、头像菜单与错误反馈。迁移不重做界面、不删除账号、不改变密码哈希和邮件验证码规则。

迁移前后端使用Spring Session JDBC、HttpSessionSecurityContextRepository、SessionPrincipal、Session CSRF以及security_version。前端通过同源Cookie访问、Pinia保存用户、没有Token持久化。MySQL已执行V1—V4。旧方案的Session选择作为历史决策保留，由用户本次选择更新后续方向。

方案选择：

| 方案 | 决定 |
| --- | --- |
| 保留Session，仅迁到Redis | 改动较少，但不是用户选择的JWT方案 |
| 短期JWT + 随机刷新凭证 + Redis登录状态 | 推荐并按用户选择细化；兼顾浏览器恢复与立即撤销 |
| 长期JWT存localStorage、仅验签 | 不采用；不能直接保留退出与改密失效语义 |

Vue3/TypeScript、Java21/Boot4.0.8、MyBatis-Plus、MySQL保持。新增BOM管理的JWT资源服务器/Jose与Spring Data Redis依赖，不增加完整OAuth授权服务器、SSO或第三方身份平台。Redis只承载认证临时状态；用户、邮件证明/限流和未来学习数据继续在MySQL。

## 2. 凭证与生命周期

- Access Token：RS256签名JWT，建议10分钟有效；JSON返回，仅放前端模块/Pinia内存，以Authorization: Bearer发送。刷新页面可丢失，随后用刷新Cookie恢复；不写localStorage/sessionStorage/URL/日志。
- Refresh Token：256位密码学随机秘密，与随机sid组合为不透明凭证，不使用长期JWT。仅通过HttpOnly、host-only、SameSite=Lax Cookie发送；生产Secure。Cookie Path=/api/v1/auth，登录/刷新/退出可接收，账户和学习API不会自动携带此认证Cookie。
- JWT字段：iss、aud、sub（用户ID）、sid（登录实例ID）、sv（security_version）、iat、nbf、exp、jti。严格指定算法/issuer/audience/时间/claim类型与边界，不接受none、任意kid/URL，也不在payload放密码、邮箱、姓名或刷新秘密。
- 角色及账号状态在服务端读取，不把客户端或过时角色claim作为授权依据。JWT成功验证后显式转换成业务使用的principal，不能直接将Spring Jwt对象强转为原SessionPrincipal。
- 签名私钥、当前kid、刷新摘要HMAC密钥均用受控本地文件/环境配置，稳定跨重启；不写Git或聊天，不每次启动自动换密钥。公钥仅用于验签；可配置上一把公钥作有限轮换窗口，窗口内仍受Redis与账号版本约束。

| 模式 | 空闲/绝对期限 | 刷新Cookie |
| --- | --- | --- |
| 普通登录 | 2小时无有效活动失效；自登录起最长24小时 | 会话Cookie，不设置持久Max-Age |
| 保持登录7天 | 闲置及绝对上限都不超过自登录起7天 | Max-Age始终为剩余绝对期限，不因轮换重置7天 |

JWT exp不得超过该登录实例的绝对期限。Redis保存服务端起止时间与lastSeen；认证请求成功后更新活动时间，并检查闲置期限，不能靠前端声明。只按需刷新，不用后台定时刷新无限延长空闲登录。浏览器可能恢复会话Cookie，不能承诺所有浏览器关闭时一定删除；服务端期限仍强制生效。

## 3. Redis模型与环境隔离

每个登录实例一个随机sid。拟用项目/环境前缀及{sid}哈希标签，确保同一次轮换的键位于同一Redis Cluster槽；首版仍为单实例，不引入集群部署。

| 键 | 数据与TTL |
| --- | --- |
| eh:dev:auth:v1:{sid}:family | userId、sv、createdAt、absoluteExpiresAt、idleSeconds、lastSeen、remember、state、currentRefreshHash；TTL不超过剩余绝对期限；测试环境用eh:test前缀 |
| eh:dev:auth:v1:{sid}:used:digest | 已消费刷新凭证的摘要标记，digest替换为实际摘要，TTL到该family绝对期限；只有sid保留大括号作为Cluster哈希标签 |

Redis不存明文刷新秘密或访问JWT；刷新摘要用独立服务端HMAC密钥。key不包含邮箱、用户名或密码。随机sid不是撤销授权：仅知道sid或提交随机错误秘密不能撤销他人登录。

轮换用短小Lua脚本或等效原子CAS：核对family状态、期限、sv和currentRefreshHash → 记录旧摘要已使用 → 替换新摘要并设置TTL，一次完成。新JWT/随机凭证在状态更新前准备，签名失败不能先消耗刷新凭证。脚本不执行网络调用或复杂扫描。

本机只读检查发现D:/redis/redis-server.exe为5.0.14.1，尚未验证其服务、密码、Spring客户端兼容性或稳定性。拟复用该二进制做兼容预检，但另建工作区内项目专用开发/测试实例（建议6380/6381，冲突时明确报错），数据/配置放忽略目录、bind 127.0.0.1、保护模式和项目独立凭据；不操作6379、不改全局服务或D:/redis配置、不使用FLUSHDB/FLUSHALL。

连接和测试采用各自实例及前缀，仅清理本任务生成的键。部署使用维护中的Redis版本和受支持平台另行核验，不把旧Windows版本的本地联通当作生产验收。此设计阶段不安装Docker、WSL或第三方Redis替代软件。

新模式启动前验证Redis连接及密钥可用性，缺失即明确失败；运行期间Redis故障按503处理。原/system/status的数据库健康契约先不改变，不能只凭其UP就宣称认证依赖就绪；认证闭环验收必须包含Redis实测。

## 4. 后端请求与撤销

受保护API：验JWT签名/claim → 查询当前MySQL用户启用状态、security_version及角色 → 查询Redis family及期限/状态/归属 → 构建业务principal。任一不符拒绝。为了立即撤销，首版每次受保护请求仍查询Redis和必要账号状态，不宣称纯无状态或免查库。

JWT过期且其余可验证条件成立，返回结构化ACCESS_TOKEN_EXPIRED；伪造、错误issuer/audience/算法、family撤销、账号禁用或版本不符不走自动续期。公开注册/邮箱发送/找回/CSRF/健康接口不因无关旧Bearer而失效。

登录：保持原密码校验及MySQL持久限流，成功后创建新的family并返回JWT/设置刷新Cookie。Redis写失败返回503，不假装登录成功。每次登录生成新sid，不接受客户端指定角色或复用sid；如有可验证的旧刷新凭证，切换账号时撤销对应旧family，其他设备不受影响。

刷新：读取HttpOnly Cookie、CSRF及Origin校验 → 验证账号/version/family → 原子轮换 → 返回新JWT/替换Cookie。已消费的真实旧凭证再次使用，撤销整个family；随机错误秘密只拒绝，不撤销family，以免知道sid即可制造登出。未知/过期/撤销刷新统一401，不泄露身份细节。

退出：不能依赖Access Token仍有效；可用合法刷新Cookie，或仍有效且通过验证的Bearer定位并撤销本次family。无凭证/已失效可幂等204并清Cookie，但不得凭任意sid撤销。成功后清Cookie/前端内存，Redis状态让该family的旧JWT也失效，而不只是等10分钟。

改密及找回：继续在MySQL事务递增security_version，所有旧family/JWT在下一次请求或刷新时失效。Redis清理可在事务提交后尽力进行，但不得把跨库清理成功作为安全前提；即使清理失败也要依靠MySQL版本拒绝旧凭证。当前页面改密成功后清凭证并回登录，不自动登录。

绑定邮箱：保留当前密码与邮件证明验证；接口继续只接受本人Session等价principal，不引入从请求体传userId。发送状态、CSRF、OTP一次性消费及防枚举不因JWT而弱化。

Redis超时/不可达返回503 AUTH_SERVICE_UNAVAILABLE，前端展示连接可恢复提示，不清掉合法身份并伪称退出；绝不绕过family检查或回退成只验签。找回等明确依赖MySQL的公开流程按自身规则工作。Redis数据丢失意味着需重新登录，不能用本地内存自动重建family。恢复旧快照时必须启用新的本项目认证命名空间代次使旧状态不被复活；不复用旧撤销状态做静默恢复。

## 5. Cookie与CSRF

移除HttpSession作为认证和CSRF存储，不再启动Session JDBC自动配置；使用无状态SecurityContext策略。旧EH_SESSION不再被信任，切换时清除该Cookie。旧会话不转换成刷新凭证，迁移后所有用户重新登录一次，账号和绑定数据不变。

仍保留GET /auth/csrf，换用CookieCsrfTokenRepository等无Session仓库，保持X-CSRF-TOKEN和现有JSON返回方式；使用框架支持的masked token处理。CSRF Cookie也为host-only，生产Secure；若从JSON取令牌可保持HttpOnly，避免不必要的JS读Cookie。

所有原有写接口及新增refresh继续要求CSRF，尤其是依靠Cookie的登录、刷新、退出，不能因JWT而全局关闭。敏感Cookie写操作同时校验Origin/Referer：生产限定配置的同源；开发仅允许明确本地前端源，不能任意反射来源或开放带凭证通配CORS。原型file://不是正式调用源。

登录/退出后按框架约定重建CSRF令牌，避免继续使用旧mask；不让CSRF不匹配自动触发登录刷新循环。精确SameSite/Secure/Path及Cookie删除属性需有集成验证。

## 6. 前端恢复与并发

新增独立auth transport持有访问JWT和刷新中的Promise，避免请求层与Pinia互相循环依赖；Pinia继续管理用户/交互状态。登录、刷新响应的Token只进入内存，页面/日志不展示。

首次进入页面没有Access Token时，先获取CSRF、尝试一次refresh，再请求me。有效Cookie恢复成功；无凭证401进入登录；503保留连接失败状态及重试。公开页不反复刷新，刷新API本身不得被拦截器递归处理。

只有来自认证过滤器、在业务执行前返回的ACCESS_TOKEN_EXPIRED允许一次共享刷新后重试原请求。网络超时/503、业务401 BAD_CREDENTIALS、403和一般失败不自动重放POST，保持验证码/收藏等操作语义。所有重试至多一次，防无限循环。

同页single-flight共享一次刷新。多标签用Web Locks序列化同源刷新/退出，锁内发送请求时由浏览器读取最新HttpOnly Cookie，不缓存旧刷新值；BroadcastChannel只通知退出/身份变化，不传Token。无跨标签锁支持时仍有本页保护，若真实旧刷新凭证重放则安全退出；不以无限宽限期允许旧凭证重复使用。

严格轮换在请求已提交但响应完全丢失的少数情形可能需要重新登录，这优先于长期接受旧凭证。刷新/退出有超时，退出中不发起新刷新；auth generation防止退出、改密或切换账号后的迟到响应恢复旧身份。多个标签登录不同账号共享同源Cookie，需要通知其他标签重新核实，不能出现旧用户界面配新用户Token。

## 7. API变更草案

以下设计已落实；实际定义以docs/api.md顶部2026-10-16 JWT契约为准。

| 接口 | 新行为 |
| --- | --- |
| GET /api/v1/auth/csrf | 保持{token,headerName}，不创建HttpSession |
| POST /api/v1/auth/login | 保留username/password/rememberMe；200 {user,accessToken,tokenType:"Bearer",expiresIn}，设置刷新Cookie |
| POST /api/v1/auth/refresh | 无明文刷新凭证请求体；Cookie+CSRF；同上Token响应、轮换Cookie |
| GET /api/v1/auth/me | Bearer鉴权，保持UserDto结构 |
| POST /api/v1/auth/logout | CSRF，撤销本family，清Cookie，204；不可确认撤销的基础设施错误503 |
| 注册、验证码、找回 | 业务请求/响应保持；公开接口不要求Access Token，继续CSRF与限流 |
| account及admin接口 | 请求业务字段保持，改为Bearer+现有CSRF规则；角色/归属在服务端 |

登录/刷新返回Cache-Control:no-store，Token和Set-Cookie不进入日志/Trace截图。新增ACCESS_TOKEN_EXPIRED、AUTH_SERVICE_UNAVAILABLE等结构化错误；UNAUTHENTICATED及BAD_CREDENTIALS的现有区分保留。

## 8. 文件、迁移和切换

- 后端：pom、SecurityConfig、身份Controller/Filter/认证服务适配；新增JWT配置/编码验证、Redis family仓库/原子脚本、Cookie及刷新服务。原Session策略停止使用，不把刷新逻辑塞入邮件服务。
- 前端：api/client、api/identity、stores/auth、轻量auth transport、路由恢复和测试fixture；登录注册页面布局不变。
- 配置：新增忽略本地JWT/Redis配置及不含真实密钥的example、项目专用Redis启动/测试说明。Redis实例安装/启动及兼容性在实施阶段验证，本轮未执行。
- 数据：当前不需要更改MySQL表，保留SPRING_SESSION及其属性表但不再读取，不直接删除旧迁移/表；未来确需清理另加Flyway迁移。验证码与限流保持MySQL，新阅读仍从V5开始，不能拿认证切换清库。
- 切换：先在测试环境完成闭环与回归，更新前后端同一契约后再切本地体验服务；旧Session统一失效一次，不做双认证长期并行。切换前记录配置及构建版本，禁止自动回退到可重新接受旧会话的模式；失败则明确维护/恢复步骤并要求重新登录。
- 回滚：不删用户数据和旧表；若回退Session版本，必须用全新的会话命名空间/Cookie和仓库隔离，不能让历史Session复活。Redis仅清理本项目/本轮已知键，不运行全局清库命令。

## 9. 验证门槛与下一步

书面设计确认后编写逐文件实施计划，按下列闭环实现，不先做完整后端再补前端：

1. 项目专用Redis可运行、认证配置和稳定签名密钥验证；模拟故障、实际版本/客户端兼容，确保不操作其他服务。
2. 登录 → Bearer me → 刷新页面恢复 → 头像退出；桌面/手机真实前后端连通。
3. 原子刷新/重放检测、并发标签、普通/7天期限、Redis故障和迟到响应。
4. 注册、邮箱发送/绑定/找回、改密与权限回归；切换体验服务、文档与验收。

必要测试：

- JWT伪造/错误算法、issuer/audience/kid、过期、未来时间、错误sub/sid/sv均拒绝；仅合法过期触发刷新。
- Redis只存摘要且有TTL；并发同刷新仅一个成功，已消费真实旧凭证重放撤销family，随机错误秘密不能撤销他人family。
- 退出后复制的旧JWT和刷新Cookie均不能继续使用；其他设备不被单次退出误伤。
- 普通2h空闲/24h绝对、记住登录最多7天，Cookie轮换不延长绝对期限，过期不被访问JWT绕过。
- 改密/找回后所有旧登录失效，即使Redis清理故障；账号禁用/角色变化在服务端生效。
- Cookie Path/HttpOnly/SameSite/Secure、Origin/CSRF、旧EH_SESSION拒绝与无HttpSession回归。
- Redis中断503不旁路、不伪登出；恢复/数据丢失状态明确；测试库与实例隔离，不清其他数据。
- 前端同页/跨页刷新、退出竞态、网络失败不重放写入、401业务错误不误刷新、无localStorage Token、界面和原认证流程回归。

单元测试、真实MySQL+项目专用Redis集成、Mock浏览器及真实前后端分别验收，未安装/连接/运行的项目不得写通过。迁移完成后，再修订阶段2方案中的身份依赖并进入阅读开发。

## 10. 依据

依赖保持现有Boot管理版本；文档用于支持设计，不代表安装新版本。

- [Spring Security 7.0 JWT支持](https://docs.spring.io/spring-security/reference/7.0/servlet/oauth2/resource-server/jwt.html)：验签、时间/issuer/audience校验及自定义decoder。
- [RFC9700 第4.14节](https://www.rfc-editor.org/rfc/rfc9700.html#section-4.14)：刷新凭证轮换、重放检测及安全事件撤销；本项目不是完整OAuth授权服务器，只采纳适用的安全原则。
- [Redis事务与脚本](https://redis.io/docs/latest/develop/using-commands/transactions/)：原子状态变更；本方案另需业务TTL和错误处理，不把原子性当作跨MySQL事务。
- [Redis Windows运行说明](https://redis.io/docs/latest/operate/oss_and_stack/install/install-stack/windows/)：部署平台需单独选择验证，现有Windows二进制并非新部署版本已验收。
