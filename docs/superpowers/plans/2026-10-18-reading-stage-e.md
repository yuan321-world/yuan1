# 阅读扩展阶段E实施计划

Goal：交付阅读+正式练习自动打卡及本人本周真实统计，完成本轮A—E。

Spec：2026-10-17-reading-writing-practice-design.md第6节；用户2026-10-18已明确确认上海日期、每天一次、同篇一次、不设正确率、统计页而非首页大卡。

Architecture：V15新增一次性文章达成证据与每日打卡两表。Progress完成事务和Attempt最后提交事务调用统一资格服务，同一用户行锁串行化两个入口；先锁用户再锁进度/轮次，沿用开始文章→题组→用户顺序。资格按相同article_version_id的COMPLETED阅读和完整正式轮次匹配；曾发布的冻结旧轮次可完成，不因后续下架取消真实学习证据。时间由Clock的UTC instant转换Asia/Shanghai，周一至周日。

当天第二篇也记录文章已达成但不增加当天打卡；以后重做不把它当新文章补打。只在真实状态从未完成→完成的动作检查，不通过GET或重复已完成请求补旧成绩。历史D成绩不自动迁移打卡。无题文章可读完但不具备打卡资格。无正确率门槛，不能证明认真阅读，不宣称防作弊考试。

## 文件与接口

- 新增reading/checkin/CheckinService、CheckinController；V15__reading_checkin.sql。复用JdbcTemplate/Clock/身份与阅读访问。修改ReadingProgressService、AttemptService（注入、用户锁、完成节点调用），不变更原响应契约。
- GET /api/v1/reading/checkins/week：本人today/weekStart/days[date,checked]/totalDays/todayChecked/qualifiedArticles/completedReadings/completedAttempts；GET /reading/articles/{a}/versions/{v}/checkin：readCompleted/practiceCompleted/hasQuiz/articleQualified/qualifiedDate/todayChecked。no-store，认证，无userId/客户端日期输入，不提供直接写打卡接口。
- 前端api/checkins.ts、ReadingCheckinCard.vue、LearningStatsView.vue；路由/stats替换占位，详情保留原完成按钮并在其后展示资格卡。练习完成/阅读状态变化通过显式事件刷新资格，失败局部重试，不让新卡阻断旧功能。登录/身份代次守卫与取消旧请求。
- 测试ReadingCheckinIT、CheckinDateTest；专用清理先删打卡及资格再删轮次，SchemaMigrationIT 29表。前端reading-checkin.spec.ts覆盖空态/两条件/错误重试/统计和小屏，旧Mock夹具提供空统计/资格默认值。

## 执行任务

- [x] 1. 后端真实HTTP用例先红：匿名拒绝、无题/仅阅读/仅练习不打卡、两个顺序均可、错误答案也可、同天多篇/同篇不重复、GET不补旧记录、另一用户隔离。日期单元固定Clock检查上海午夜/周一。实现V15与资格服务、接入事务，跑绿。
- [x] 2. 前端用例先红：保留原工具和完成入口，资格卡读完+练习反馈、失败重试、统计7日/今日/空态、320px无溢出。实现组件/API/事件/路由并跑绿；不会恢复首页大卡或增加假数据。
- [x] 3. 全套后端Java21真实MySQL/Redis、前端单元/类型构建/浏览器；一次整体独立审查，修复重要问题并回归。用真实游客和独立Mock账号检查桌面/小屏，切换正式包及V15，不发布假内容。
- [x] 4. API/数据库/决策/进度/验收同步，核验运行服务，关本轮临时8082/测试6381，保留4174/8080/开发6380。不提交工作区其他任务文件，不自动开始新阶段。

Pre-flight：两个完成入口必须先锁同一user，资格读取使用当前读以避免旧RR快照；唯一(user,article)和(user,date)配合事务；重复最后答案不调用资格服务；日期来自同一次Clock instant；个人查询不写；旧发布轮次仍受本人归属约束。

执行采用当前会话inline，已有未提交全栈依赖原地保留，不从旧HEAD创建缺少A—D的工作树，不自动Git提交。完整审查允许按executing-plans技能使用一个只读Reviewer，不委派实现。

审查修正：缺失资格/日期的SELECT FOR UPDATE会在不同用户之间产生RR间隙锁升级死锁，改为原子upsert保留原资格及日期，再当前读已存在证据；每日upsert不覆盖首个来源。新增不同用户并发屏障回归先红(500)后绿。旧D完成记录GET/同提交重试不补卡；若此前未达成过E资格，新一轮真实完成可贡献首次资格，之后重做不增加日期。

最终证据：63后端单元/MVC+72真实集成，60前端单元，类型/构建通过，238完整浏览器通过/6条件跳过。真实游客与独立Mock1440/320px已查看截图；开发V15、正式8080 E包。详见同日E验收，A—E收尾不自动开启其他阶段。

最终交接：本次候选8082及测试Redis6381已核验归属并关闭；正式8080 PID4900、前端4174 PID86840、开发6380 PID14372保留。限定任务差异/控制字符检查及正式阅读页/API健康200检查完成，其他未提交工作保留。
