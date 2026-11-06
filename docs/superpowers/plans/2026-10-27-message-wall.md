# 留言墙正式接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把已确认的便签墙接入真实账号、持久化、互动、管理员公告/处理和作者铃铛消息。

**Architecture:** 在现有模块化单体新增wall业务包，复用JWT/CSRF、MyBatis与BusinessException。按可运行全栈流程分步交付，数据库保存业务与共享布局，Pinia管理本次浏览/筛选/草稿；原HTML继续作为独立演示。

**Tech Stack:** Vue 3、TypeScript、Pinia、Java 21、Spring Boot、MyBatis-Plus、MySQL 8、Flyway、JUnit、Playwright；不增加框架或依赖。

**Spec:** `docs/superpowers/specs/2026-10-26-message-wall-design.md`，含后续公告、双击落点、处理通知和铃铛位置补充。

**状态:** 2026-10-27用户已确认并完成正式实现、本地部署。Task 1–6交付流程均已接通；具体验证范围、未逐项覆盖的边界及用户视觉待确认见 [验收](../../acceptance/2026-10-27-message-wall.md)。下方细粒度检查清单保留计划原文，不代表每个组合边界均已单独执行。主代理顺序实现，最终独立只读审查4项已修复。

## Global Constraints

- 保持Vue/Pinia + Java21/Spring Boot + MySQL模块化单体；接口统一 `/api/v1`。
- 旧功能与入口、原始原型保持；不覆盖工作区已有修改，不全量暂存或提交。
- 最高迁移已核对为V29，实施前重查，新增V30；不改旧迁移、不清库。
- Java启动/Maven必须实际使用21；开发 `english_hero_dev`，集成 `english_hero_test`，测试Redis6381。
- JWT权限与资源归属由服务端检查，写操作保留来源和CSRF校验；no-store，ID用字符串，错误沿用统一结构。
- 留言1000、评论500、公告标题80 Unicode码点；普通内容纯文本，公告仅允许受限加粗，不接HTML/图片。
- 用户帖子立即可见，管理员后处理；留言状态VISIBLE/HIDDEN/DELETED，逻辑删除；不自动封禁、不因举报自动隐藏。
- 铃铛仅墙内底部发布按钮旁，列表/手机底部跟随工具条；不放全局导航，不放管理端。
- 告知作者原因必填（1–200码点）、内部备注选填（最多500），二者严格隔离；处理通知仅接收者本人可见。
- 原积分及通用AI入口保留明确演示说明，不制造真实积分，不接助手、不调用DeepSeek、不动现有额度。

## Review Focus

1. 发帖已成功但响应丢失：原请求重试返回同一结果，不重复发帖/扣次数（任务1、2）。
2. 筛选隐藏了邻居或同时发帖：新便签仍避让全部公开便签，既有位置稳定（任务2）。
3. 切账号时慢请求返回：不得把上个用户草稿、点赞、举报、通知写入新用户页面（任务1、3、5）。
4. 本人删除与管理员迟到操作同时发生：旧版本409，不能恢复或覆盖已删除记录（任务4）。
5. “全部已读”期间新到通知：只标本次明确边界之前的本人消息，新消息保持未读（任务5）。

## 已核对的接入点和文件分工

后端新增 `backend/src/main/java/com/englishhero/wall/`：

- `WallDtos.java`：输入/公开/本人/管理/通知DTO，公开DTO禁止邮箱、登录名、举报人、内部备注和审计原文。
- `WallRules.java`、`WallLayout.java`：内容/枚举/UUID/游标校验，布局计算；无数据库副作用。
- `WallMapper.java`、`WallInteractionMapper.java`、`WallModerationMapper.java`：内容/布局、点赞评论举报、管理通知的SQL。
- `WallService.java`、`WallInteractionService.java`、`WallModerationService.java`、`WallNotificationService.java`：事务与身份归属，各自有限职责。
- `WallCommandService.java`：用户级幂等命令和限流；原请求完成查询，不把请求日志作为公开内容返回。
- `WallController.java`、`WallAdminController.java`：安全DTO输入、SessionPrincipal、no-store响应；管理员业务方法再校验角色。

前端新增 `src/api/wall.ts`、`src/stores/wall.ts`、`src/wall/{types,layout,format}.ts`、`src/views/{WallView,AdminWallView}.vue`、`src/styles/wall.css`；组件按职责拆为 `src/components/wall/{WallCanvas,WallNote,WallComposer,WallDetail,WallToolbar,WallNotifications,WallModerationForm}.vue`。API复用 `publicReadingGet/businessGet/businessWrite`；不另写Token/重试框架。

需修改：`SecurityConfig.java` 精确放行公开GET、保护wall其余路径；`frontend/src/router/{index,navigation,safe-redirect}.ts` 增加墙与管理路由/登录回跳；`HomeView.vue` 打开现有墙占位；`AdminLayout.vue` 增加管理入口；`LearnerLayout.vue` 只对 `/wall` 扩大画布容器。保留其他菜单和布局。

测试：新增 `backend/src/test/java/com/englishhero/wall/{WallRulesTest,WallLayoutTest,WallHttpITSupport,WallPublishingIT,WallLayoutIT,WallInteractionIT,WallModerationIT,WallNotificationIT}.java`；前端 `tests/unit/{wall-api,wall-store,wall-layout,wall-format}.spec.ts`、`tests/e2e/{wall,wall-admin,wall-notifications}.spec.ts`；真实库UI独立 `tests/real/wall.spec.ts` 与独立配置，不混充Mock验证。

## 接口与事务约定（供各任务共用）

所有路径下文省略 `/api/v1`。`MessageInput={requestId,kind:MESSAGE|ANNOUNCEMENT,category,color,text,title?,preferredPosition?:{x,y}}`；公告只能走ADMIN创建，普通端不能自报身份/状态/计数。`MessageDto={id,version,kind,category,color,text,title,author:{displayName,isAdmin},createdAt,editedByAdmin,position:{x,y,width,height},likes,liked,commentCount,owned}`；状态/作者处理原因仅通过本人专用DTO返回。

`Page<T>={items,nextCursor,hasMore}`，默认20最多50，按ID倒序keyset、明确校验游标和筛选，关键词最多100码点。过滤变化重新分页但保持已知位置，返回不改变稳定ID。

`Command={requestId,expectedVersion}`；同actor/requestId全局唯一，存命令种类、规范化载荷SHA256和结果标识；相同载荷重放先查已完成结果，不再次检查冷却；异载荷409。`GET /wall/requests/{requestId}` 只对本人返回 `{completed,operation,targetId}` 或404，响应不含旧内容快照。超时先查结果，未找到才用同UUID同载荷重试。

管理 `ModerationInput={requestId,expectedVersion,action:EDIT|HIDE|RESTORE|DELETE|KEEP,text?,title?,authorReason,internalNote?,reportIds:string[]}`；服务端校验显式reportIds均属目标且待处理，只关闭这些举报，不能批量清掉其他评论/目标举报。

建议限流本计划落实为：发帖30秒/北京时间每日10条，评论5秒/每日60条，举报每日10条；重复已完成请求和复用同目标待处理举报不再扣次数。使用库内用户计数行锁与Clock，不把这些限额混进AI账本。返回429和Retry-After；留配置入口，测试用可控Clock，不修改系统时间。

## Task 1: 发布、浏览与本人删除的真实闭环

**Files:** 新增V30迁移、WallDtos/Rules/Mapper/CommandService/Service/Controller、WallHttpITSupport/PublishingIT；前端wall API/store、WallView/Composer/Note基础、路由/首页/安全配置。同步 `docs/api.md`、`docs/database.md`。

**Interfaces:** `WallService.list(SessionPrincipal nullable, WallQuery):Page<MessageDto>`，`create(SessionPrincipal,MessageInput):MessageDto`，`deleteOwn(SessionPrincipal,String id,Command):void`；前端 `loadMessages(query,signal)`、`createMessage(input)`、`deleteOwnMessage(id,command)`。

- [ ] 先写失败测试：匿名GET200/写401、CSRF失效403、普通用户创建ANNOUNCEMENT403；用户A发帖后B可读displayName而读不到username/email；B删除A为404且原文不变；emoji1000通过/1001为400；脚本字符串按文本展示。
- [ ] 运行 `./mvnw.cmd -o -Pmysql-it -Dit.test=WallPublishingIT verify`，确认因业务尚不存在失败，不把配置错误当RED。
- [ ] 新增V30：wall_message/comment/like/report/moderation_log/request/notification/user_quota/layout_state。表间FK、点赞唯一、用户命令唯一、版本/状态检查；举报用可空generated pending-key约束实现同用户同目标仅一条待处理。多态目标使用message_id/comment_id外键及恰好一种非空检查。schema默认不插演示留言，不创建默认管理账号。
- [ ] 完成创建/列表/详情/本人列表/删除及请求结果查询；昵称从IdentityMapper.byId读取displayName，空白时用“学习者”，绝不回退username/email，不返回凭据。公开GET仅VISIBLE，隐藏本人记录单独受保护列表；直接公开详情隐藏404。
- [ ] 完成 `/wall` 真实发布、列表、详情、本人删除及现有登录弹窗回跳；提交错误保留草稿/UUID，确认成功才清空。Pinia按用户与generation清理/拒绝迟到响应，不跨账户恢复草稿。
- [ ] GREEN：WallPublishingIT及wall API/store单元通过；前端构建；真实测试库两个用户发帖→刷新→查看→越权拒绝→本人删除走通，退出码与样本ID入验收。先不部署未完成版本。

## Task 2: 画布落点、避让与手机列表

**Files:** WallLayout、WallLayoutTest/LayoutIT、布局SQL/Service；WallCanvas/Toolbar、wall/layout.ts和wall.css。

**Interfaces:** `WallLayout.allocate(preferredX,preferredY,List<Rectangle>):Position`；服务端保存最终position，前端只提交偏好坐标；`WallCanvas`发出 `{x,y}` 画布坐标，不改变他人坐标。

- [ ] RED：缩放/平移后双击坐标转换、取消无记录、滤掉邻居仍避让、两个用户并发同落点不相交、发帖刷新不变、搜索/点赞不重排。
- [ ] 使用单例layout_state行锁分配位置，查询全部公开布局包围盒（不把正文发到客户端）。每条保留280×420安全包围盒，邻距30画布像素，内容卡宽238、最大高390/旋转不超过1.4度确保落在盒内，长内容在详情展开；位置整数且偏好坐标绝对值最多1,000,000，非法/NaN拒绝，不接受客户端宽高。从偏好点按24单位扩张搜索；最多2400半径后在最右侧加30放置，避免无界计算。
- [ ] 同事务固定锁顺序：用户配额/幂等→layout_state→目标内容；创建、隐藏恢复涉及布局均遵守。恢复原位已占用时重新避让，原有便签不移动。ADMIN编辑限制卡片尺寸，不引发邻居位移。
- [ ] 桌面点阵、拖动、缩放/查看全部、定位我的、分类搜索；底部dock与列表可键盘使用，手机默认列表，长列表无限制最大高度。手机不依赖二维坐标；“我的”包含隐藏项时改用本人列表展示原因，不将隐藏正文插回公开画布。
- [ ] GREEN：WallLayoutTest/LayoutIT、wall-layout单元和1440/690/320浏览器检查通过；模拟响应丢失重放仍只有一个位置和一次配额；检查新卡片与既有矩形的间隙。

## Task 3: 点赞、评论与举报

**Files:** WallInteractionMapper/Service、WallInteractionIT、WallDetail、互动API/store；测试wall.spec.ts。

**Interfaces:** `PUT /wall/messages/{id}/like {liked}`、`GET/POST /wall/messages/{id}/comments`、`DELETE /wall/comments/{id} Command`、`POST /wall/reports {requestId,targetType,targetId,reason,detail}`；评论DTO不含内部状态/他人资料。

- [ ] RED：并发/重复liked=true只一赞，false不为负；他人删评论404；父帖隐藏/删除后拒绝评论/点赞/举报；同目标未处理举报复用，举报不自动隐藏；空白/超500评论400；账号切换迟到评论拒绝。
- [ ] 实现事务内父帖可见性与版本检查、评论分页/本人删除、点赞唯一记录、举报幂等/目标FK/去重及服务端限流。日计数重试不重复扣；状态冲突不产生孤立评论/举报。
- [ ] 前端提交时禁重复，失败保留输入、分页不重置上下文；服务器确认后更新计数，失败恢复原点赞状态，隐藏/删除返回清晰状态并禁止继续交互。
- [ ] GREEN：真实库WallInteractionIT含并发断言；前端Mock互动、限流/错误恢复和generation单元通过，真实测试库A评论/B举报→刷新恢复可复验。

## Task 4: 管理端、公告与处理审计

**Files:** WallAdminController/ModerationService/Mapper、WallModerationIT；AdminWallView、WallModerationForm、wall-format.ts、AdminLayout入口。

**Interfaces:** ADMIN `/admin/wall/messages|comments|reports` GET；`POST /admin/wall/messages`公告；`POST /admin/wall/messages/{id}/moderation`和comments对应端点接收ModerationInput。

- [ ] RED：普通用户管理GET/POST403；普通昵称叫站长不获权限；公告标题80/81边界、HTML转义与加粗；旧版本EDIT409；本人DELETE与ADMIN RESTORE/EDIT并发只一个有效，删除不可恢复；举报必须对应目标/明确ID。
- [ ] 后端ADMIN角色复核，公告title/格式白名单与状态验证；受限 `**文字**` 拆为文本/strong片段，普通留言永远文本，前端不用v-html。公开editedByAdmin来自记录而非作者身份。
- [ ] 管理列表巡查、上下文/评论、显式选举报、EDIT/HIDE/RESTORE/DELETE/KEEP。作者原因必填，内部备注仅管理DTO；记录前后内容、操作者、时间和命令ID。删除确认，无硬删除；KEEP不生成负面提醒。
- [ ] 内容变更、选中举报处理、审计与作者通知同事务；通知失败则回滚内容。编辑无论是否发现问题都用中性文案“已由管理员修改”，不自动宣告违规。公告创建/管理员预览不发送违规通知。
- [ ] GREEN：WallModerationIT真实库与后台Mock双端通过，含审计前后/举报隐私/事务回滚、标题加粗展示和冲突保留输入；后台无任何原型角色切换授权。

## Task 5: 作者铃铛消息

**Files:** WallNotificationService、通知SQL/Controller、WallNotificationIT、WallNotifications/Toolbar、通知store与E2E。

**Interfaces:** `GET /wall/notifications {cursor}` → Page加`unreadCount,readBoundary`；`PUT /wall/notifications/{id}/read`；`PUT /wall/notifications/read-all {throughId}`。NotificationDto仅`id,targetType,targetId,action,authorReason,excerpt,createdAt,readAt,canOpen`。

- [ ] RED：A的通知B读/标已读404且A不变；公开DTO不含internalNote/reporter；重复管理命令只一通知；全读throughId不影响后到记录；匿名401；恢复有通知；评论通知归评论作者而非父帖作者。
- [ ] 同管理审计ID唯一建通知，按服务端接收者查询/更新；throughId严格数字且按本人边界处理。隐藏/删除无公开查看链接；本人短摘要不等于获得公开详情权限，内部备注不进通知表/响应。
- [ ] 墙底加号旁铃铛/未读角标、我的消息/逐条与全部已读；手机列表底部跟随，管理和其他页面不显示。进入墙/完成本人操作/重新打开消息时刷新，不增加WebSocket或后台轮询。迟到消息响应受账号generation保护。
- [ ] GREEN：WallNotificationIT、Mock及真实测试库管理员处理A→A重登录收到通知→B看不到→标已读刷新仍有效，全流程覆盖320px，不调用AI。

## Task 6: 集成验收与本地交付

**Files:** `docs/api.md`、`docs/database.md`、`docs/decisions.md`、`docs/progress.md`、`docs/README.md`、新增 `docs/acceptance/2026-10-27-message-wall.md`，真实UI配置/fixture。

- [ ] 使用README工具脚本后运行Maven `-version`确认21；启动测试Redis6381，WallHttpITSupport只接受english_hero_test。fixture清理只删除本轮UUID关联数据，不清库、不删其他样本。
- [ ] 根目录：`pnpm --dir frontend test:unit wall`、`pnpm --dir frontend build`；backend目录：`./mvnw.cmd -o test`、`./mvnw.cmd -o -Pmysql-it '-Dit.test=Wall*IT' verify`。记录完整命令/退出码/计数；配置失败不标通过。
- [ ] 受控preview上跑 `wall.spec.ts wall-admin.spec.ts wall-notifications.spec.ts`，1440/320两项目。另用独立测试库后端/真实浏览器验证发布持久化、A/B隔离、公告、管理和作者通知；明确Mock与真实库分别记录。
- [ ] 对首页、阅读/词汇、专项/整卷、AI历史入口做必要导航回归；开发学习表先后只读指纹一致，wall新表允许变化。检视当前任务差异，保留所有无关原型与工作区修改，不全仓add/commit。
- [ ] 通过后打包新JAR至不可变路径、记录SHA256，核对旧PID再平滑替换本项目8080/4174；保持既有AI开关/限额/账本，不自动外呼、不创建假管理员。验证HTTP200、数据库UP、 `/wall` 与 `/admin/wall` 路由刷新、旧业务入口正常。
- [ ] 更新验收/交接并打开正式墙给用户；未完成项明确列出，不将独立原型成功描述为正式鉴权/数据验证，不自动开始通用AI阶段。

## 计划自查

已逐项覆盖原型确认范围与五项Review Focus；全部六个任务按实际流程连接前后端。无新框架、外部服务、默认账号或演示内容迁入正式库。共享坐标、配额、幂等、审计/通知原子性和恢复碰撞已给出具体选择；对正式上线结果尚无通过声明。实施前仍需核对V30未被并行任务占用。
