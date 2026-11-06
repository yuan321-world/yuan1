# 阅读模块增量优化验收

日期：2026-10-16。用户已确认独立HTML设计及精选固定展示，本记录区分自动测试与人工确认。

## 交付

- 复用原系统实现公开列表/正文/查词；精选独立于搜索、分类、难度、排序与标签。
- 列表采用标题、精选、组合筛选、全部/收藏/历史、卡片与后端分页；卡片含分类、B1/B2、篇幅、阅读时长、真实状态及入口。
- V10文章收藏：唯一约束、幂等及并发处理、用户隔离、列表/详情同步、局部失败重试，游客登录后继续收藏。
- 现有版本进度、历史与主动完成机制保留；游客不调用start、不写账号记录。字号/行距、响应式排版、查词浮层优化。
- 认证系统、邮件配置、学习业务及草稿发布规则保持原有实现。

## 验证与命令

在项目根目录先加载 scripts/use-workspace-tools.ps1；Maven另加载 scripts/use-java21.ps1 并验证实际使用Java21。各项成功命令退出0。

| 检查 | 命令/范围 | 结果 |
| --- | --- | --- |
| 后端完整测试 | backend 下 ./mvnw.cmd verify | 48单元/MVC、47真实专用MySQL/Redis集成，零失败 |
| 并发收藏补充 | ./mvnw.cmd verify '-Dit.test=ReadingEnhancementIT' | 48单元、2集成，含同用户并发幂等及归属断言 |
| 前端单元 | pnpm --dir frontend test:unit | 57项、14文件通过 |
| 类型及构建 | pnpm --dir frontend build | 类型检查及生产构建通过 |
| 完整浏览器 | pnpm --dir frontend test:e2e | 148 Mock通过、6真实账号/SMTP夹具依赖测试跳过，桌面及手机 |
| 实际服务只读冒烟 | node .tool-cache/check-reading-live.cjs | 4174代理8080，1440x1000与320x844；公开列表/正文、登录拦截、零横向溢出和pageerror，游客无私人写入 |

后端完整日志 .tool-cache/reading-enhancement-full-backend.log，定向日志 .tool-cache/reading-enhancement-concurrency.log，最终浏览器日志 .tool-cache/reading-enhancement-final-browser.log。首次受限沙箱完整测试因TokenRevocationIT关闭测试Redis后无法通过CIM重启而失败；按工具审批在沙箱外重跑完整测试成功，不记首次为通过。没有关闭安全机制。

新回归覆盖精选筛选稳定、游客拒绝私人写入、草稿/旧版权限、收藏幂等/隔离/失败重试、登录续收藏、身份切换忽略迟到请求、正文登录后重建、字号控制。Mock浏览器不等同真实数据库联调；真实数据库由后端集成验证，实际开发服务只读冒烟未进行真实用户收藏写入或QQ投递。

## 部署与交接

8080运行 .tool-cache/reading-enhancement/english-hero-reading.jar，SHA256为7FE4241F8B9C5B4A2C898D931D4C84866B3E4D5F80B95D0D5B738FED050045B6；4174读取最新frontend/dist，开发Redis6380保留。开发/测试库V10、18表。没有自动发布文章、删除旧数据、提交Git或进入下一阶段。临时8082和测试Redis6381在验收后关闭。

独立审查结论见对应review。实现者检查桌面/手机排版，用户正式页面视觉与文章内容确认尚待进行。当前仅B1/B2；历史按最近阅读排序，不套列表筛选；基础词典有限，未接外部翻译/AI。游客登录后保存生词需重新选词。以上明确边界不记为已实现的跨模块扩展。

最终检查：本任务涉及的已跟踪前后端/进度文件 git diff --check 退出0。全工作区检查退出1，定位为既有 docs/superpowers/specs/2026-10-14-phase-1-identity.md 第56行末尾空行，未改动无关历史文件。最终4174/reading与8080公开文章接口均HTTP200。临时8082已关闭、项目测试Redis6381已正常持久化停止；开发服务保留。应用浏览器打开请求已排队。
