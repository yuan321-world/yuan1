# 2026-10-19 个人生词学习与到期复习验收

## 交付

按已确认HTML，/vocabulary替换生产占位：本人收藏分页候选、明确加入、暂停/恢复学习、目标设置、认词/中文选义融合、逐字中文拼写、释义主动下一词、失败本轮队尾、暂停/刷新恢复、到期复习及本轮结果。Notebook保留来源和取消收藏，新增加入学习。原阅读、认证和打卡规则保留。

真实数据库记录：同用户/词条去重；一次未结束轮次；命令hash与原响应保证重试幂等；版本冲突409；归属与CSRF在服务端检查。正确反馈与next分离，刷新释义不二次排期。新词1天后复习，复习通过逐级3/7/14/30天，stage5以后30天；本轮出错最终通过重置1天。只取到期词，每批最多20。

V16只增六表，旧用户、收藏、来源、文章、阅读历史不删改；开发与测试库均迁移至16。不存在真实学习记录则空态，未预置用户任务。取消收藏不取消学习，可在学习页单独暂停。

## 验证证据

工具先加载scripts/use-workspace-tools.ps1和use-java21.ps1；Maven -version实际JDK21.0.12.1、Maven3.9.9。

| 检查 | 命令与结果 |
| --- | --- |
| 后端完整单元/数据库HTTP集成 | `backend/mvnw.cmd -o verify -Pmysql-it`，退出0；64单元、79真实MySQL/Redis集成，零失败/错误/跳过；日志.tool-cache/vocabulary-full-backend-final.log |
| 追加分批/跨日边界 | `mvnw.cmd -o test-compile failsafe:integration-test failsafe:verify -Dit.test=LearningBoundaryIT`，退出0，1项真实MySQL/Redis边界测试；固定Clock验证21词拆20+1、上海23:59:59→次日00:00计数重置、21个到期词分批20+1且不产生阅读打卡；日志vocabulary-boundary.log |
| 前端单元 | `pnpm --dir frontend test:unit`，退出0；18文件65用例；日志vocabulary-all-unit-final.log |
| 类型与生产构建 | `pnpm --dir frontend build`，退出0，包含vue-tsc及tsc；日志vocabulary-build-final.log |
| 浏览器定向回归 | `pnpm --dir frontend test:e2e vocabulary-learning.spec.ts notebook.spec.ts navigation.spec.ts reading-lookup.spec.ts reading-checkin.spec.ts auth-modal.spec.ts`，5174受控Vite，退出0；桌面/手机46用例；其中含768px布局用例；日志vocabulary-regression-final.log |
| 学习页面专项 | 同一配置vocabulary-learning.spec.ts，12用例通过；含输入法、非法粘贴、首字母提示、合法前缀暂停/刷新恢复、错误请求同ID重试、自动释义与手动下一词 |
| 运行服务 | 后端8080/数据库UP，4174/vocabulary为200，游客经4174访问私人overview为401；V16启动日志确认仅专用english_hero_dev新增迁移 |
| 差异 | 本批所触及已跟踪文件`git diff --check -- ...`退出0；保留既有无关修改，未提交 |

数据库用例覆盖不同用户隔离、重复加入、混合他人词整批回滚、取消收藏保留学习、暂停恢复与范围校验；并发start只一轮、并发反馈一次成功、同命令重放/异参数冲突、错误选项/拼写服务端拒绝、无到期不生成复习、释义刷新与快照读取、当前轮次不可暂停词条、CSRF拒绝。ReviewScheduleTest覆盖全部固定间隔、stage5上限与失败重置。

已查看768px首页与320px拼写错误截图；修正手机暂停按钮折行。截图.tool-cache/vocabulary-live-home-768.png、vocabulary-live-spelling-mobile.png。浏览器测试为Mock响应；数据库/HTTP集成为真实测试库，二者分别记录，未声称浏览器已登录真实后端跑完学习轮次。用户最终视觉确认仍待体验。

## 失败记录与修复

先新增ReviewScheduleTest与前端拼写/store测试，缺实现而失败；补实现后通过。初次数据库套件因测试Redis未启动而阻塞；启动专用6381后发现Repository代理字段NPE，定位并改为方法访问，5项早期IT通过。

独立审查发现空HTTP200、pending离页失去重试入口，均补失败断言再修复。完整套件第一次因已有TokenRevocationIT在沙箱关闭测试Redis后无权恢复，连带后续用例失败；并发现SchemaMigrationIT精确表清单需补新增六表。补清单后，经工具审批在原专用库运行完整回归，故障测试能实际恢复6381，最终全部通过。未关闭沙箱、未改全局权限或其他项目库。

## 边界与下一步

本批完成个人来源闭环；词书来源核验/导入、/stats词汇扩展尚未实施，入口明确待开放。现有/stats阅读数据仍可用。无付费API或新第三方依赖。

逐字标准词形在本人客户端可见，服务端校验最终拼写，不将学习模式宣传为保密考试。拼写失败标志仅使排期更保守，客户端计数不是权威成绩。正确前缀存本标签页本人缓存；换设备只恢复后端词/释义/轮次，不恢复逐字草稿。

本地新后端PID见.tool-cache/vocabulary-runtime.pid，日志vocabulary-runtime.log，工作目录backend；4174继续使用已有预览服务和最新dist。未推送、提交或部署外网。
