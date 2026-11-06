# 用户管理正式接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 管理员可查询真实账号、查看脱敏资料、停用/恢复普通用户并追溯操作，停用后的旧登录凭据失效。

**Architecture:** 扩展identity管理边界，复用JWT/CSRF及现有enabled/security_version；Vue/Pinia提供分页列表、详情和明确确认。新审计/命令表与账号状态同事务，不新增用户密码能力、不扫描Redis全库。

**Tech Stack:** Vue3/TypeScript/Pinia、Java21/Spring Boot/MyBatis-Plus/MySQL/Flyway、JUnit/Playwright；不加框架、不换包管理器。

**Spec:** `docs/superpowers/specs/2026-10-27-admin-users-design.md`。

**状态:** 用户已确认本计划并选择当前会话顺序执行，Task1–4正式实现/验证/本地部署完成。细项证据及边界见 [验收](../../acceptance/2026-10-27-admin-users.md)。保留原检查清单作为设计测试目标，不把每个组合边界视为单独执行。无提交/推送。

## Global Constraints

- 接口统一 `/api/v1`；复用真实ADMIN、CSRF、来源检查，业务服务再次检查身份与角色。
- 不返回/记录密码、password_hash、验证码、JWT、Cookie；默认只展示遮罩邮箱。
- 首版所有ADMIN和当前操作者只读，无角色修改、账号删除、代设密码或管理端重置指引。原有自助密码找回保持。
- 原学习/留言/AI记录保留；停用不自动隐藏历史发言，不中止已受理AI任务或退款，不声称撤销在途已鉴权请求。
- 停用/恢复均递增security_version。旧Token不可恢复，用户需重新登录；匿名公开阅读保持原规则。
- 所有数据结构新增Flyway迁移；当前最高V30，执行前重查，未冲突才用V31，不改旧迁移、不清库。
- 原型独立4187且仅演示，正式Vue4174；原English Learner原型不改。保留其他后台入口及已有工作区修改。

## Review Focus

1. 伪造目标ID/账号角色、本人或任意管理员停用请求必须服务端拒绝，前端disabled不是权限证据（Task2）。
2. 停用后旧access/refresh不可用，恢复后旧Token仍不可用；新登录与匿名公开阅读区别验证（Task2）。
3. 同UUID重试、过期版本、两个并发管理员操作不能重复递增版本或生成重复审计（Task2/3）。
4. 空白/超长/表情原因、SQL通配符关键词、空页、快速切换与账号改变的迟到响应不泄漏资料（Task1/2）。
5. 审计写入失败必须整体回滚；浏览器未知结果保留原命令并查询确认，不能生成新命令重放（Task2/3）。

## Task 1：真实用户查询与详情全栈

**Files:** 新建 `backend/src/main/java/com/englishhero/identity/admin/AdminUserDtos.java`、`AdminUserMapper.java`、`AdminUserService.java`、`AdminUserController.java`；`frontend/src/api/admin-users.ts`、`frontend/src/stores/admin-users.ts`、`frontend/src/views/AdminUsersView.vue`、`frontend/src/components/admin-users/AdminUserDetail.vue`、`frontend/src/styles/admin-users.css`。修改 `frontend/src/router/index.ts`、`safe-redirect.ts`、`layouts/AdminLayout.vue`。

**Tests:** 新建 `backend/src/test/java/com/englishhero/identity/admin/AdminUserHttpITSupport.java`、`AdminUserQueryIT.java`、`frontend/tests/unit/admin-users.spec.ts`、`frontend/tests/e2e/admin-users.spec.ts`。Support参照WallHttpITSupport的专用test库/Redis6381/JWT/CSRF流程，保持模块独立，创建UUID临时账号、只清理自有数据。

**Interfaces:** ADMIN `GET /admin/users?keyword&status&role&page&pageSize`，status ALL/ENABLED/DISABLED，role ALL/USER/ADMIN（USER表示无ADMIN学习者），page从1、pageSize默认20最多50。keyword最多100码点，按ID精确或username/display_name字面包含查找，转义LIKE通配符。返回 `{items,page,pageSize,total}`；`GET /admin/users/{id}` 返回 `AdminUserDetail`。

列表DTO `{id,username,displayName,roles,enabled,version,createdAt,protectedAccount}`；version取security_version。详情增加 `{maskedEmail,emailVerified}`。查询列白名单不SELECT password_hash；不直接复用IdentityUser作为响应。只在浏览器登录身份的generation内接受响应。

- [ ] RED：QueryIT断言匿名401/普通用户403、只含白名单、脱敏邮箱、分页/关键词/role/status正确、缺失404、非法参数400；单元覆盖账号变化/筛选变化晚响应丢弃、重试保留筛选。记录失败命令。
- [ ] GREEN：实现只读列表/详情、Pinia分页与筛选、加载/错误/空态、后台入口和详情。使用相同后台布局，手机卡片，无密码入口；真实ADMIN业务复核，no-store。
- [ ] 验证：根目录 `pnpm --dir frontend test:unit admin-users`、`pnpm --dir frontend build`；backend `./mvnw.cmd -o -Pmysql-it '-Dit.test=AdminUserQueryIT' verify`，记录退出码和测试数。Mock用户列表/详情在1440/320可操作；不冒充真实库UI。

## Task 2：停用/恢复与登录失效全栈

**Files:** 新建 `AdminUserCommandService.java`、`AdminUserCommandIT.java`、`frontend/src/components/admin-users/AdminUserStatusDialog.vue`；扩展Task1 Service/Mapper/DTO/API/store。新增 `backend/src/main/resources/db/migration/V31__admin_users.sql`（执行前核对版本）。

**Interfaces:** `PUT /admin/users/{id}/status {requestId,expectedVersion,enabled,reason}` → `{requestId,targetId,enabled,version,completed:true}`；`GET /admin/users/requests/{requestId}` → 本管理员此前命令结果，未知404。UUID严格格式；expectedVersion非负，enabled必为boolean，reason去首尾后1–200Unicode码点，拒绝NUL及非法UTF16。

`admin_user_command` 保存actor_id/request_id唯一、target_id、载荷hash、无凭据的结果快照与时间；`admin_user_audit` 保存actor/target、before_enabled/after_enabled、before_version/after_version、reason、命令ID、时间，命令唯一FK。审计不可前端修改。

事务过程：验证ADMIN→同actor/UUID已完成同载荷重放、不同载荷409→锁定目标行→拒绝本人或含ADMIN目标→检查version与目标状态实际改变（无变化409）→CAS写enabled/version+1→写审计及命令结果→提交。并发相同UUID由唯一约束及短事务恢复读取，不吞掉其他错误。采用已验证READ_COMMITTED避免缺失命令间隙锁循环，不共享wall配额/命令表。

- [ ] RED：伪造管理员/本人目标403；普通目标停用成功、确认原因边界；同UUID只有一次版本递增/审计，异载荷409，旧版本409、并发两管理员只有一个成功。注入审计失败后enabled/version/命令全部回滚。
- [ ] RED：真实登录保存旧access及refresh，停用后旧access访问受保护接口与refresh均401，新登录失败；匿名读取公开墙仍200。恢复后旧access/refresh仍401，新登录200、历史学习/留言仍在；在途已鉴权请求不纳入原子撤回承诺。
- [ ] GREEN：实现状态命令与明确原因确认框，展示具体限制；提交期间禁重复和关闭，冲突保留原因允许重新加载；未知网络结果保留同UUID，先查命令结果再重试，400/403/409等确定失败按原因恢复。
- [ ] 验证Task1/2真实库IT、单元及双端Mock；持久化版本变化与审计同事务。复用现有TokenAuthenticationService的enabled/securityVersion检查，不扫描Redis、不改全局Token时效。任何发现的认证缺口先写失败用例再最小修复。

## Task 3：操作审计与管理闭环

**Files:** 新建 `frontend/src/components/admin-users/AdminUserAudit.vue`、`AdminUserAuditIT.java`；扩展AdminUserController/Mapper/Service/API/store与用户管理视图。

**Interfaces:** ADMIN `GET /admin/users/audit?userId&cursor&pageSize` → `{items,nextCursor,hasMore}`；userId可选，cursor为严格正整数字符串，pageSize1–50。审计DTO `{id,actor:{id,username},target:{id,username},beforeEnabled,afterEnabled,reason,createdAt}`，不返回载荷hash或其他凭据。详情展示该用户记录，页头可看全局记录。

- [ ] RED：匿名/普通用户不能读记录、管理员筛选与倒序分页稳定；新状态成功只一记录；未知命令查询不能读取其他管理员的命令。前端HTML/脚本原因按纯文本、取消操作不留记录、快速切换用户不串记录。
- [ ] GREEN：实现只读审计分页、详情记录和全局弹窗；操作成功刷新当前筛选页，目标不再匹配则移出，页越界回到有效页；错误不清空已有已加载记录。管理员保护字段来自服务端，客户端不自行授权。
- [ ] 验证真实库Query/Command/Audit联合IT、Pinia单元、双端界面操作，保留原留言管理/账户设置入口。

## Task 4：真实浏览器验收、本地交付与文档

**Files:** 新建 `backend/src/test/java/com/englishhero/identity/admin/AdminUserBrowserVerification.java`、`frontend/tests/real/admin-users-browser.cjs`、`docs/acceptance/2026-10-27-admin-users.md`；同步 `docs/api.md`、`database.md`、`decisions.md`、`README.md`、`progress.md`、`docs/README.md`。

- [ ] 确认Maven实际Java21、测试Redis6381/english_hero_test；真实UI runner显式指定，不使常规IT依赖浏览器。临时凭据只在忽略fixture中，finally删除，日志不输出。真实浏览器以A普通用户/B管理员登录，不伪造角色：查询A→停用A→A写操作和刷新失效→恢复A→旧会话仍无效→重新登录成功；1440/320流程、密码字段始终不存在。
- [ ] 根目录运行 `pnpm --dir frontend test:unit admin-users`、`pnpm --dir frontend build`，backend `./mvnw.cmd -o test`、`./mvnw.cmd -o -Pmysql-it '-Dit.test=AdminUser*IT,AdminUserBrowserVerification' verify`；4174可控预览运行admin-users/wall/auth-modal/navigation相关E2E。按日志记录真正通过数，跳过项和未跑项分开。
- [ ] 对开发库旧业务表只读指纹并保存基线，不对真实用户做停用试验；确认待迁移不冲突。打包不可变JAR并记SHA256，核对当前8080 PID/运行产物后替换项目后端；更新同一启动脚本，保持既有AI开关/限额/账本及Redis设置，0模型外呼。
- [ ] 验证迁移、服务/database UP、用户管理路由刷新HTTP200及ADMIN权限；旧业务表指纹一致，开正式 `/admin/users` 给用户。审查本任务差异、保留所有无关修改，不自动全仓暂存/提交或推送。
- [ ] 文档区分原型/Mock/真实库/真实浏览器/构建和用户视觉确认。交接写明残留事项；完成本模块后不自动扩展到角色修改或人工密码恢复。

## 自查与执行交接

查询→状态命令→审计→交付四段均为全栈可验证流程，五类Review Focus均有归属；移除的管理端重置指引不留后端接口/按钮，不影响自助邮箱重置。已有API/安全版本复用点核对，不引入Redis扫描或默认账号。已由主代理顺序执行；独立最终只读审查无实质问题。测试库验证与开发库V31迁移完成，未修改真实账号状态。
