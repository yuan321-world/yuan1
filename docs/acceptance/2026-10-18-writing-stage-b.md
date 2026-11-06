# 2026-10-18 写作积累阶段B验收

用户确认A—E方案并要求继续，本次只完成B：本人素材收藏/取消、个人素材库分页、英文仿写保存与修改。C/D阅读题/解析、E打卡未实施，不接AI。保留原文选择、查词、翻译、Aa、重点词、本篇生词、文章收藏、阅读历史/进度及A管理发布。

## 入口与规则

- 文章详情本篇词汇下方「写作积累」卡片可收藏、查看解析、写仿写；手机仍在正文/全文翻译后单份折叠。
- 账户菜单「我的写作素材」或卡片底部链接进入 `/writing`；分「我的收藏」「我的仿写」，服务端分页12条，不混入生词本。
- 游客仅在内存编辑；登录取消返回草稿。登录后重新核验资料和本人记录，续本次收藏/首存；已有仿写不自动覆盖。意图绑定路由/当前登录人，一次取出，5分钟后只保留草稿不自动提交；退出/身份变化/离开清理。
- 保存失败/并发冲突保留草稿，加载最新用于对照而非覆盖输入；首次加载期间锁定输入，避免迟到数据覆盖新输入。未保存关闭/Escape/站内返回确认，取消保留草稿；确认离开关闭弹窗。
- 首次仿写保存当时素材修订与内容快照；后续编辑保留原依据。资料更新显示提示，下架后本人已有仿写只读，仍可取消收藏；其他人不可读。

## 数据、鉴权与文件

V12新增user_writing_favorite / user_writing_imitation，复用现有用户/素材，不改原迁移。联合唯一、行锁、预期素材/仿写版本防重复/并发覆盖。所有个人API取服务端身份并no-store；写操作沿用CSRF及认证，公共素材响应不带私有记录。已完成开发/测试V12、21表，原数据保留。

后端新增PersonalWritingController/Service/Mapper、DTO、V12、WritingPersonalIT，调整SecurityConfig与专用测试清理/表数量断言。前端新增WritingMaterialDetail/ContentBody/LoginContinuation、WritingLibraryView、writing-intent和个人API；调整ArticleWritingPanel、LearnerLayout、账户菜单/路由安全白名单及阅读样式。未引入依赖或修改认证接口。

## 验证证据

- 后端README命令加载工具/Java脚本，Maven实际Java21.0.12.1。`./mvnw.cmd verify -Pmysql-it`退出0，58单元/MVC+58真实MySQL/Redis集成，0失败/错误/跳过。覆盖本人隔离、匿名401、重复收藏、未知userId拒绝、并发首存200/409唯一一条、素材修订快照保留、下架拒新/本人只读与取消。日志`.tool-cache/writing-b-full-backend.log`。新增用例先在旧实现缺接口时失败，日志`writing-b-red.log`。
- `pnpm --dir frontend build`退出0，包含TS检查；`test:unit`退出0、60通过。完整`test:e2e`退出0，206通过、6真实账号/邮件夹具条件跳过。日志`writing-b-build.log`、`writing-b-front-unit.log`、`writing-b-all-browser.log`。
- 浏览器为Mock API，包含12项B桌面/手机回归：慢查锁输入、未保存Back确认、游客登录取消/续保存、失败保留/冲突对照、刷新/取消、下架只读。最终增加实际「登录续保存→修改→Back取消→Back确认」检查后单独12项再次通过，日志`writing-b-browser-back-final.log`，无生产代码再变更。
- Reviewer三项P2（慢查覆盖、离开草稿丢失、登录续操作跨页弹窗残留）均修正并最终复核无新阻断。路由残留回归桌面/手机曾失败，见`writing-b-route-red.log`；修复后完整及补充测试通过。审查记录见reviews/2026-10-18-writing-stage-b.md。
- Flyway候选8082真实增量V11→V12成功。正式8080核验原A包进程后切换B包。`node .tool-cache/check-writing-b-live.cjs`最终退出0：真实1440/320px游客阅读、单份写作空态、Aa/重点词/生词保留、管理及三个个人GET均401，零游客业务写入/横向溢出/pageerror；已实际查看桌面截图。首轮该临时检查脚本存在拼接语法错误、未进入检查，修正脚本后重新执行成功。

## 运行与限制

正式8080 `.tool-cache/writing-b/english-hero-reading.jar`，SHA256 `28F3850F1E947A471E316E77D126E5BDC579BCDA375FA938CFC3F1CF9BB9B032`，启动PID17344；bootstrap关闭、已有ECDICT本地开关开启。4174服务最终构建、开发6380保持；本次候选8082及测试6381已按项目归属核验关闭。最终4174真实API200。

开发库仍未自动录入/发布写作素材，也未创建虚假学习记录。管理员须为相应文章版本录入校对并发布后，用户才会看到收藏/仿写条目；无资料时真实空态。真实个人数据保存/隔离由专用库集成验证，浏览器个人流程使用Mock，不声称开发库真实管理员录入或真实用户全链路人工验收。用户视觉确认待反馈；下一阶段C为题目和管理录入，之后D答案记录/解析与E打卡规则落地。无Git提交。
差异检查：全工作区 git diff --check 报告既有阶段1身份方案文件末尾空行；本次未修改该无关文件，不将其记为整体通过，也未提交。
