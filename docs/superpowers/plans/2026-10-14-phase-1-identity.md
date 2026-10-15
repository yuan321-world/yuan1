# 阶段 1 身份实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 交付注册、登录、会话、权限和账户设置完整流程。
**Architecture:** identity模块使用MyBatis持久用户与角色，Spring Security认证，Session JDBC持久会话；Vue/Pinia调用同域API。
**Tech Stack:** 沿用阶段0；新增Boot管理的Session JDBC。
**Spec:** ../specs/2026-10-14-phase-1-identity.md

## Global Constraints

- Java21、Boot4.0.8、/api/v1；只新增V2，禁止清库。
- 无密码/Token/Cookie日志；真实配置忽略Git；仅项目专用库。
- 普通2h idle/24h absolute；保持登录7d；不自动开始阶段2。
- 用户已批准范围，内联执行，保留既有无关修改。

## Review Focus

- Cookie并发串配置：两种登录分别检查Max-Age。
- XOR随机mask误判轮换：旧token写操作失败、新token成功。
- session序列化泄露hash：最小principal字段及真实JDBC恢复验证。
- 晚到me覆盖退出：auth store generation测试。
- 并发改密覆盖：版本条件更新、两个旧会话失效测试。

### Task 1：注册与登录闭环

Files：新增backend identity模块、V2迁移、IdentityFlowIT；修改SecurityConfig/pom/application；新增frontend auth API/store，修改LoginView/router/client。
Interfaces：UserDto{id:string,username,displayName,roles}；csrf{token,headerName}；spec中的/auth接口。

- [x] 先写IdentityFlowIT：csrf取token→注册201→错误登录401→正确登录200→me200→USER访问admin403→logout204→me401；无csrf注册403、重复409、角色注入400。
- [x] 运行 `mvnw -o verify -Pmysql-it`，确认未实现接口导致失败。
- [x] 实现V2及identity数据/验证/认证/会话；安装依赖必要时使用工具审批。
- [x] 写前端身份恢复/退出竞态测试，运行失败；实现auth API/store及真实登录注册和路由保护。
- [x] 运行后端集成、前端unit/type/build和真实浏览器登录流程。

### Task 2：账户、期限与管理员

Files：identity账户service/controller/session filter/bootstrap；新增AccountView；AdminLayout/AppNavigation；config admin example。
Interfaces：spec /account/profile/password、/admin/identity；受控bootstrap-only配置，已有账号不覆盖/提升。

- [x] 测试profile只改本人、password旧凭据失败、成功后双会话失效；absolute精确边界、cookie期限、bootstrap已有USER不提升。
- [x] 实现事务内version条件改密、session校验、受控管理员创建及前端表单。
- [x] 真实库验证、管理员403/200、普通/保持登录Cookie、后端重启恢复；测试只清理本次fixture，不清库。

### Task 3：交付验证与文档

Files：更新docs/api/database/progress；新增acceptance/phase-1.md；必要README追加操作说明；更新阶段0测试的阶段相关断言。

- [x] 完整 Maven verify -Pmysql-it、pnpm test/type-check/build、Playwright桌面/手机身份流程；记命令退出码与区分Mock/真实。
- [x] 新鲜上下文Reviewer只读最终差异；重要问题先复现再修复。
- [x] 文档记录真实结果/限制/后续视觉确认；只暂存本任务文件，Git写入需明确工具审批；不推送或合并。
