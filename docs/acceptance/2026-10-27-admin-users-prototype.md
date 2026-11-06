# 用户管理方案与独立原型验收

2026-10-27按用户“可以”确认的范围完成方案和独立原型，未实现正式账号接口、未迁移、未修改真实账号。入口 http://127.0.0.1:4187/admin-users-preview.html ，方案 [设计](../superpowers/specs/2026-10-27-admin-users-design.md)。演示数据刷新重置，无API/邮件/模型外呼。

原型：账号/昵称/ID搜索、状态角色筛选、分页/空态/清空、详情与遮罩邮箱、管理员受保护、普通账号停用恢复及必填原因、重置指引限制与请求受理说明、全局/账号操作记录。保留其他正式管理入口链接；原始English Learner原型不变，HTML不进入Vue生产构建。

命令：加载scripts/use-workspace-tools.ps1后执行 `pnpm --dir frontend exec playwright test --config playwright.prototype.config.ts admin-users.spec.ts`，退出0，6项通过。桌面1440、平板582、手机390完整交互；另在每个项目检查320/390筛选文字宽度和当前导航可见，无页面横向溢出。0脚本错误、0/api请求。截图admin-users-desktop/mobile/tablet.png存忽略目录，已查看桌面与手机；发现手机筛选文字过窄后修复并重跑。只验证原型行为，未验证服务端ADMIN/会话撤销/真实投递，这些留待实施。

待用户审阅原型与书面方案；下一步定稿并编写正式实施计划，不自动接入真实操作，不暴露密码或哈希。

## 用户审阅后调整

用户询问重置指引价值及停用限制后同意继续。按已建议范围删除管理重置按钮与模拟发送逻辑，保留用户自助邮箱找回；原型明确停用对登录/写入的限制、历史数据保留及访客公开浏览。原因必填、停用恢复、审计/查询回归更新，6项最终原型检查退出0（admin-users-prototype-v2.log）。本轮未执行正式业务代码或迁移。

已写 [正式实施计划](../superpowers/plans/2026-10-27-admin-users.md)，四段完整流程，建议主代理顺序执行；待书面计划审阅后实施。实际Token身份验证读取enabled/security_version，版本递增失效复用点已核对，最终行为仍须正式真实库测试。
