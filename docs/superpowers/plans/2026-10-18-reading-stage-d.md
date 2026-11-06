# 阅读扩展阶段D：正式轮次与恢复

用户在C完成后要求继续，执行已确认2026-10-17-reading-writing-practice-design.md中的D。使用writing-plans/executing-plans/TDD；原地增量，保留未提交工作，不从旧HEAD另建缺少现有实现的工作树，不自动提交。E打卡和AI不在本轮。

## 接口与数据契约

V14新增user_reading_attempt、user_reading_answer，引用现有quiz/version/question/user/article。每用户/题组最多一个进行中轮次（生成列唯一键+用户行锁），clientRequestId按用户唯一幂等首存；新轮次递增attemptNo，完整保留旧版成绩。逐题草稿与正式提交分离，提交后同答案幂等、改选409；每轮锁版本控制并发，服务端按冻结题目判分，最后一题与轮次完成同事务。

认证API：GET /reading/articles/{a}/versions/{v}/practice（发布题组、本人进行中或最近轮次、首次/最近成绩）；POST /reading/quizzes/{id}/attempts {quizVersionId,clientRequestId}（创建或恢复）；GET /reading/attempts/{id}；PUT /reading/attempts/{id}/answers/{questionId} {choice,expectedLockVersion}（草稿）；POST 同路径/submit（服务端判分）；GET /reading/articles/{a}/versions/{v}/practice/history?page&pageSize（本人分页摘要）。所有路径/api/v1，服务端身份/CSRF/no-store。

未提交题的答案/解析不进入DTO。已有进行中/历史轮次按本人归属可读、可完成原冻结版，题组下架不能新建；公开当前版发生更新不能把旧题ID提交到新轮次。首次成绩与本次成绩分别展示，重做显式确认，不覆盖首次。

前端：保留ArticleQuizPanel游客逻辑，登录分支新增AccountQuizPanel。选择选项后保存有意义的草稿（单次请求期间阻重复）；失败保留选择并允许重试，刷新从服务端恢复；提交/冲突加载保留未提交选择，已在另一页提交时展示真实锁定答案并说明本地选择未提交。私有数据不入公共接口或浏览器持久存储。身份/路由变化清理，失效不永久busy。模块失败不阻断正文。历史分页、按原版回顾、回到进行中和按当前已发布题组重做。

## 执行步骤与证据

- [x] 1. 真实库HTTP测试先失败：认证/隔离、重复/并发开始、草稿恢复、提交不泄漏未来答案、重复/改选/越题、完成成绩、重做、冻结旧版/下架。
- [x] 2. V14与Attempt DTO/Mapper/Service/Controller落地，更新专用测试清理与结构断言，测试通过。
- [x] 3. 登录分支浏览器先红；实现选择保存/刷新/局部错误/冲突/历史/重做与身份隔离，保留游客与全部旧功能。
- [x] 4. 全量验证/独立整体审查、修复重要问题，部署真实服务/桌面手机截图检查、文档交接并关闭临时服务。

Ruling：账号轮次开始不由打开文章自动触发，先GET恢复；点击开始/重做才写。草稿按选项变化保存（不是逐字请求），提交时包含当前选项，不信任前端correctCount/日期/用户ID。进行中冻结版可在发布更新/下架后继续，重做只能用当前可访问已发布版。

Pre-flight：不可变题目→轮次固定版本/总题数→答案归属FK+服务校验→提交事务→首次/本次摘要；页面仅把已提交题的反馈用于解析，数据库保存私人记录。不会用游客凭证迁移为正式成绩。

Ruling：V14增加第三张user_reading_attempt_request映射表，记录每个开始/恢复请求到实际轮次；并发恢复使用不同请求ID时，之后重试仍返回原轮次，不会在原轮次完成后意外创建新轮次。开始按文章→题组→用户锁顺序；所有决策读取当前数据，避免REPEATABLE READ旧快照漏掉并发创建或提交。

最终证据：62单元/MVC+65真实集成、60前端单元、232浏览器通过/6条件跳过；构建/类型通过。正式8080已更新D包，V14/27表，真实游客及独立Mock桌面/320px检查完成，临时8082/测试6381已关闭。详情见docs/acceptance/2026-10-18-reading-stage-d.md与对应审查。E未实施。
