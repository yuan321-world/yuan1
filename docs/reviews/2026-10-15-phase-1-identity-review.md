# 阶段1身份审查

范围：BASE e29fbea之后身份模块、安全配置、V2、前端身份状态/路由/表单及相关测试。独立Reviewer只读，不读取真实配置或运行写库测试。

## 发现与处理

1. 保护路由me503后跳login，已有user又跳首页，可能循环。主代理以桌面/手机SPA导航回归复现失败；现在connectionError时允许停留login显示重新连接，保留真实user直到服务器状态可确认。回归两端通过。
2. 昵称响应晚于退出时直接写Pinia，可能恢复过时显示身份。新增store.saveProfile的generation检查，页面通过store保存；延迟响应+退出单元回归先失败后通过。

后端审查未发现阶段1范围内阻断：认证后最小principal、session/CSRF轮换、显式context保存、期限/版本检查、逐请求Cookie、条件改密、注册固定USER、服务端ADMIN、bootstrap仅local显式开启且不覆盖账号、V2/Flyway结构均已核对。

限制：Reviewer没有独立运行测试或验证部署环境，前端修复结果由主代理的回归证据确认。未进行公开部署、MySQL8.4或实际HTTPS验收，这些属于后续部署阶段。
