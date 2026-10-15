# 阶段 1：真实身份与账户

状态：用户于 2026-10-14 批准实施（“可以，继续”）；沿用已确认布局。阶段 2 不在本次授权内。

## 范围与流程

用户名注册 → 登录 → 首页 → 账户设置 → 退出；管理员通过本地受控引导创建，服务端校验角色。注册成功回到登录页，不自动登录。不实现邮件、找回密码或学习业务。

Vue 3/TypeScript/Pinia、Java 21/Boot 4.0.8/MyBatis-Plus/MySQL 保持不变。新增 Boot BOM 管理的 Session JDBC；Session 4.0.5、Security 7.0.7。Flyway V2 新增 identity_user、identity_user_role、SPRING_SESSION、SPRING_SESSION_ATTRIBUTES，不改 V1。

## 输入与持久化

- username：去首尾空白、Locale.ROOT 小写，3—32 ASCII 字母/数字/下划线，唯一且不可修改。按用户2026-10-15补充要求，公开注册禁止整个admin前缀（不区分大小写，去首尾空白后检查），同时保留root/system；受控管理员创建和登录允许admin前缀。
- password：8—64 Unicode 字符、UTF-8 最多72字节；不 trim，拒绝控制字符及不配对代理字符。使用 DelegatingPasswordEncoder 的 BCrypt。错误密码与禁用账号均返回相同401。
- displayName：1—32 Unicode 字符、去首尾空白，无控制字符；注册未填默认用户名。
- USER/ADMIN 为服务端角色；注册仅 USER。DTO 不接受 role/userId/enabled，拒绝未知字段。用户DTO：id（字符串）、username、displayName、roles。
- identity_user 的 security_version 在改密时条件更新递增。认证后 session principal 只存 id/username/version/roles，不含密码哈希。

## 会话与边界

普通登录闲置2小时、绝对24小时；保持登录闲置及绝对上限均7天。登录时间从成功认证计，不使用匿名CSRF会话创建时间。EH_SESSION Cookie 为 HttpOnly、SameSite=Lax、host-only、Path=/，生产Secure；普通为session cookie，保持登录Max-Age最多604800秒。逐请求设置CookieValue，禁止修改单例cookie配置或默认remember超长期行为。

JSON登录显式 authenticate → ChangeSessionId + CsrfAuthenticationStrategy → 保存无密码principal及SecurityContextRepository。使用同一个HttpSessionCsrfTokenRepository/XOR handler；GET返回masked token，写请求携带返回的header。登录/退出后重新获取CSRF；旧token不能重用。

过滤器在SecurityContextHolderFilter之后、CsrfFilter之前核对enabled/version/absolute期限。检测到认证失效：注销并返回401（公开接口可匿名继续）。idle到期后匿名写请求可能先CSRF403；前端重新探测me，不把所有403视为退出。改密成功当前会话注销，其他会话下次请求失效；事务内再次检查version避免并发改密覆盖。

参考：[Security会话](https://docs.spring.io/spring-security/reference/7.0/servlet/authentication/session-management.html)、[CSRF](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)、[Session对应schema](https://raw.githubusercontent.com/spring-projects/spring-session/4.0.5/spring-session-jdbc/src/main/resources/org/springframework/session/jdbc/schema-mysql.sql)。

## 接口

所有接口no-store；成功直接DTO，无额外包裹。

| 方法/路径（/api/v1） | 请求 | 返回/角色 |
| --- | --- | --- |
| GET /auth/csrf | 无 | token/headerName，匿名 |
| POST /auth/register | username/password/displayName? | 201 UserDto，匿名+CSRF |
| POST /auth/login | username/password/rememberMe | 200 UserDto，匿名+CSRF |
| GET /auth/me | 无 | UserDto，登录 |
| POST /auth/logout | 无 | 204，登录+CSRF |
| PATCH /account/profile | displayName | UserDto，本人+CSRF |
| POST /account/password | currentPassword/newPassword | 204并注销，本人+CSRF |
| GET /admin/identity | 无 | UserDto，ADMIN |

错误结构沿用 code/message/traceId，可附fieldErrors（只字段名与提示，不含输入值）。400 VALIDATION_ERROR/BAD_REQUEST，409 USERNAME_TAKEN/ACCOUNT_CHANGED，401 BAD_CREDENTIALS/UNAUTHENTICATED，403 FORBIDDEN/CSRF_INVALID，500 INTERNAL_ERROR。不得记录请求体、Cookie、Token、密码或SQL异常全文。

## 界面与验收

保持黑白灰与 #c0324f、现有导航及首页布局；登录/注册/账户页复用现有视觉。Pinia仅存交互状态，无本地假登录或localStorage身份。刷新先me恢复；未知服务错误显示重试，不伪装成匿名。保护首页/模块/账户/管理员路由，保留安全内部redirect。管理员入口仅ADMIN可见且服务端仍核权。

2026-10-15用户补充：管理员界面仅用于数据管理，管理员登录默认进入/admin，账户设置为/admin/account且沿用后台布局；去除返回学习端及背单词等学习入口。管理员访问旧学习路径回/admin，旧/account回/admin/account。后台保留数据管理、账户设置和退出；未接入业务的数据管理模块显示真实空状态，不提前实现阶段2及后续CRUD。

验证真实专用测试库迁移及重复迁移、注册唯一约束、输入/角色注入、CSRF轮换、Cookie期限、两个客户端改密失效、USER/ADMIN边界、持久会话重启恢复。桌面/手机走真实注册登录退出；浏览器Mock与真实验证分别记载，视觉变化仍可由用户后续确认。
