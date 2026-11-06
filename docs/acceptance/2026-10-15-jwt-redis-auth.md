# 阶段1补充：JWT + Redis认证迁移验收

方案/计划日期2026-10-15，实际执行与最终验收2026-10-16（Asia/Shanghai）。用户确认设计并选择当前会话顺序实施。阶段2阅读未实施。

## 交付范围

- 后端新增identity/token配置、RS256签发/严格验签、刷新凭证摘要、Redis登录family/Lua原子创建/活动/轮换/撤销、刷新Cookie、来源过滤与就绪检查。
- SecurityConfig停用JDBC Session，改无状态上下文及Cookie CSRF；IdentityController登录返回AuthTokens、新增refresh、退出与改密清理。SessionPrincipal仅作经验证的业务principal。
- 前端auth transport仅在内存保存Access，按需single-flight刷新，只有ACCESS_TOKEN_EXPIRED允许一次重试；跨标签Web Locks协调登录/刷新/退出/改密，BroadcastChannel不传Token。账户切换时重置旧页面，字段/页面反馈及现有布局保留。
- 项目专用Redis开发6380、测试6381；初始化/启停脚本、稳定密钥及独立凭据均在忽略目录。不操作原6379或D:/redis配置。
- MySQL V1—V4、用户/密码/验证邮箱保留；Session两表停用但不删除，无V5。邮箱证明/限流、防枚举和真实账号关联规则不变。

## 验证证据

Maven实际Java21.0.12.1、Wrapper3.9.9。下列最终命令退出码均为0；执行日志保留在忽略的 `.superpowers/sdd/2026-10-15-jwt-redis-auth/`。

| 验证 | 命令及结果 |
| --- | --- |
| 后端完整回归 | backend目录 `./mvnw.cmd verify -Pmysql-it`：40单元/MVC、30真实MySQL/Redis集成，0失败/跳过；full-backend3.log |
| 前端单元 | `pnpm --dir frontend test:unit`：38通过；final-front-unit.log |
| 类型与构建 | `pnpm --dir frontend build`（包含vue-tsc和node tsc）：通过；final-build.log |
| 完整浏览器 | 受控测试前后端4175/8081，`E2E_EXTERNAL_SERVER=1 E2E_PORT=4175 E2E_REAL_BACKEND=1 E2E_MAILBOX_URL=http://127.0.0.1:2526 pnpm --dir frontend exec playwright test --workers=1`：104通过，0跳过；final-browser.log |
| 初始化安全 | 重复初始化前后密钥/配置hash相同；实际配置/私钥Git忽略检查通过 |
| 本地切换 | 8080直接、4174代理system/status及/login均200；合法格式但未知的合成refresh401，旧EH_SESSION探针401；CSRF Cookie存在 |

浏览器104项分为100 Mock交互与4真实测试库/项目test Redis/本地SMTP收集器流程；不是104项全部真实后端测试。真实流程覆盖注册收码、登录、刷新页面恢复、账户资料、改密重登与退出，未发QQ真实邮件。真实角色/启用状态隔离还由TokenFlowIT、IdentityFlowIT验证。

安全边界包括：错误算法/issuer/audience/kid/claim类型/未来nbf；JWT寿命不超family期限；并发刷新一胜、旧凭证重放撤销、随机错误秘密不撤销；2h闲置及7天绝对边界；轮换TTL不延长、环境前缀隔离；CSRF/Origin、无Session、过期Access退出、撤销family不能误报可续期；安全版本/角色/启用状态即时检查；邮箱全部既有证明、限流及并发回归。

真实故障演练仅关闭6381：正确签名的受保护请求503 AUTH_SERVICE_UNAVAILABLE；Redis不可达期间通过账号服务提交改密，MySQL版本已递增；恢复Redis后旧Access/refresh仍401。首次恢复受沙箱CIM权限限制失败，审批后恢复并在允许环境完整重跑通过，未更改全局权限或保护。

## 独立审查及修复

[独立报告与修复证据](../reviews/2026-10-16-jwt-redis-review.md)：发现2项Important，无Critical。旧表单等待CSRF期间切换账号可能跨身份写入；改密未纳入凭证锁可能覆盖新登录Cookie。均先写失败复现，修复代次检查/凭证锁/迟到store回调，再跑38单元及104浏览器完整相关套件。无待处理minor，不把作者修复验证称作第二次独立评审。

## 运行与交接

体验入口 http://127.0.0.1:4174/login 。切换时后端PID81092/8080、项目dev Redis PID69100/6380、现有4174预览读取最终dist。后端在经工具审批的可联网环境启动，沿用QQ配置；启动不等于重新验收QQ投递。所有旧Session失效，用户需重新登录一次。

独立测试后端8081、预览4175、SMTP2525/2526已按进程归属清理，test Redis正常持久化关闭，dev Redis继续运行。只删除本轮记录的12个e2e测试账号及对应证明/邮箱限流；未处理历史夹具或开发用户。后续测试先启动test Redis，见 [配置](../auth-jwt-redis-setup.md)。

切换清单/新后端副本保存在忽略的 `.tool-cache/auth-cutover/2026-10-16/`。原Session进程来自旧classpath，没有可确认的不可变旧构建可自动回滚；不把jar.original冒充历史可用版本。若回退Session，必须使用新Cookie及隔离仓库；Redis恢复旧快照须提升namespace代次，不能复活旧登录。

## 实施取舍与限制

- 保留当前脏工作区、不自动提交：前序已批准身份代码不在旧HEAD中；代价是审查需区分既有修改。因没有提交历史，保留本计划的忽略执行账本。
- 使用PowerShell账本替代bash任务脚本：匹配本机环境；代价是逐任务证据需手动维护。
- Lua内SET+EXPIREAT替代Redis5不支持的SET EXAT：原子语义相同；代价是仅证明现有Windows版本本地兼容。
- 两个明确的Bearer/摘要撤销入口替代CredentialProof DTO：保留服务端证明检查；代价是方法名偏离原计划。
- 评审未重复运行集成测试、未查看真实秘密/ACL、未重新视觉验收历史UI；分别依赖作者实测、受控配置与已有回归，不能据此认证生产密钥分发、全部浏览器竞态或整体视觉。
- 生产Redis版本/平台、TLS/反向代理、密钥轮换及真实备份恢复需部署验收；QQ投递未重复验证。已鉴权的在途操作可以完成，撤销约束下一次鉴权，不承诺追溯取消。

未提交/暂存整个工作区，未合并/推送，未开始阶段2。下一步按阅读设计草案确认后编写或确认其实施计划。
