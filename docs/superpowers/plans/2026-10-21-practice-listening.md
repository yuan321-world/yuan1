# 四级整套听力 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. 用户希望减少代理/重复读取；本计划建议当前会话集中执行，不逐任务创建代理。

**Goal:** 接通一套四级完整音频的管理录入/校对发布与学员播放、保存续答、整套判分，保留所有既有专项阅读。

**Architecture:** 在practice模块扩展听力分支，共用题组、题目、版本、轮次、校对及幂等事务。新增不可变本地音频资产与短期播放凭证，不开放文件静态目录；配套原文复用PRACTICE材料，仅交卷后给学员。迁移新增section和音频快照引用，旧默认阅读行为不变。

**Tech Stack:** Vue 3 + TypeScript + Pinia；Java 21 + Spring Boot + MyBatis-Plus + MySQL/Flyway；既有Redis；浏览器HTMLAudioElement；不安装新依赖/转码工具。

**Spec:** [2026-10-21-practice-listening-design.md](../specs/2026-10-21-practice-listening-design.md)，用户已回复“可以”确认。

## Global Constraints

- 仅CET4和本地个人学习；CET6、整卷考试、AI生成/校对、普通用户上传发布、远程音频抓取、云存储、自动语音转写后续。
- 首版每套一份完整MP3，单文件上限80MiB（可配置），不接受客户端文件路径/远程URL。
- 所有业务接口保持 /api/v1；鉴权、CSRF、资源归属和校对校验在服务端。
- 进行中接口不返回正确答案、解析、原文或包含答案的分组文本。
- 刷新或退出续答恢复同一冻结音频、题目、选择/标记和位置，但保持暂停。
- 下架后禁止新开始，已有本人轮次仍能访问其冻结音频与结果。
- 保留既有工作区修改；不覆盖/回退/提交无关文件，不改已执行迁移、不清库。
- 不将开发授权当作代替用户人工校对、来源确认或发布真题的授权。
- 本计划默认不提交；完成后只核对本任务差异。用户节省额度的指令优先，不逐步反复确认。

## Review Focus

1. 伪装MP3、超限与失败中断：拒绝并清理本次临时文件，既有资产不受影响（任务1）。
2. 多设备/凭证过期/播放器Range重取：不能访问他人或其他资产，续签恢复但不自动播放（任务1、3）。
3. 草稿换音频、发布后下架：旧轮次保持旧音频和题目，不泄露配套原文（任务2、3）。
4. 拖动进度与选择同时发生：串行保存，失败保留本地，冲突不能静默覆盖（任务3）。
5. 听力题数扩大不能放宽原有阅读、导入不能引用无权资产、读取不创建学习记录（任务2、4）。

## 文件和接口分工

既有后端路径均在 backend/src/main/java/com/englishhero/practice/：
- 新增 PracticeAudioProperties / PracticeAudioMapper / PracticeAudioStorage / PracticeAudioService：配置、资产元数据、文件操作、授权与票据。
- 新增 PracticeAudioController（媒体/本人票据）与 PracticeAdminAudioController（ADMIN上传/预听），PracticeListeningRules（分组/音频及听力规则）。
- 扩展 PracticeDtos、PracticeDraftRules、PracticePublicationChecks、PracticeMaterialService、PracticeAdminService、PracticeImportService、PracticeCatalogService、PracticeAttemptService 及其Mapper/Controller，分别沿用原有职责，不重构阅读模块。
- 新增 Flyway V23__practice_listening.sql（执行前确认无其他V23），保留此前全部迁移；配置仅增加无秘密的默认/示例值。
- 前端新增 src/components/practice/PracticeListeningEditor.vue、PracticeListeningPlayer.vue、src/practice/listening-media.ts；扩展既有API、store、专项编辑/作答/目录和筛选分支。
- 测试新增 backend/.../PracticeAudioTest、PracticeListeningRulesTest、PracticeListeningIT；frontend/tests/unit/practice-listening-*.spec.ts 与 tests/e2e/practice-listening.spec.ts。
- 文档更新 docs/api.md、database.md、practice-guide.md、progress.md、acceptance/2026-10-21-practice-listening.md。

接口统一约定：
- section对外为reading/listening，旧请求省略为reading。数据库新增section默认READING；内部reading_type历史列可以用LISTENING判别听力，公开readingType在听力分支不作为筛选。复用DTO增加兼容构造，旧JSON不要求新增字段。
- ListeningGroup={key,title,questionKeys:string[]}；各组顺序拼接必须恰好覆盖题目key顺序，无遗漏/重复；标题不包含答案。真实显示题号显式存于题目元数据，不从原型编号推断。
- ListeningData={audioAssetId,groups,audioReviewed}；引用资产ID而非路径。发布版同时冻结资产ID/哈希、分组及原文/题目版本。
- PlaybackState={positionSeconds:number,rate:number}；位置有限非负、上限6小时；倍速范围0.5–2；未知时长不强行填0覆盖旧位置。
- AudioAssetDto={id,originalName,byteSize,sha256,mediaType}；AudioTicketDto={url,expiresAt}；不返回绝对存储路径。

## Task 1：音频上传和安全预听

**Files:** 新增上述Audio类、V23资产表部分、PracticeAudioTest/IT及配置示例；在SecurityConfig仅为媒体票据验证端点配置恰当入口，不放开管理路径。

**Interfaces:**
- PracticeAudioStorage.store(MultipartFile file): StoredAudio；openRange(storageKey,start,end): Resource；服务端生成随机键，固定项目目录内解析。
- PracticeAudioService.upload(long adminId,MultipartFile file): AudioAssetDto。
- PracticeAudioService.issueAdminTicket(long adminId,long assetId): AudioTicketDto。
- PracticeAudioService.verifyTicket(long assetId,String ticket): VerifiedAudio；票据10分钟、只读、限资产、使用项目Redis有TTL记录和随机令牌摘要；令牌不输出日志。
- POST /api/v1/admin/practice/audio-assets（multipart）；POST /admin/practice/audio-assets/{id}/playback-ticket；GET /practice/audio/{assetId}?ticket=...。

- [ ] 编写上传/媒体测试：80MiB允许、80MiB+1拒绝；空/伪造非MP3拒绝；随机文件键留目录内；取消/失败无本次临时残留；非ADMIN拒绝上传及预听；A资产票据取B拒绝；过期拒绝；Range 0–99返回206与精确100字节，越界416，无Range为200，HEAD不返回正文。
- [ ] 用既有缓存运行定向测试，确认因缺少接口/实现失败；不将环境连接错误算RED。
- [ ] 实现MP3头识别和流式80MiB限制、临时→不可变文件、资产记录、短期票据、单Range流式响应。服务器不抓远程音频；请求/日志不记录票据值。multipart服务上限同步，超限统一错误。
- [ ] 同一测试复验通过；记录Java21及项目测试库/文件目录隔离。仅测试夹具写入测试音频目录。

## Task 2：整套录入、检查与冻结发布

**Files:** V23听力字段/约束；PracticeDtos、ListeningRules、DraftRules、PublicationChecks、MaterialService、AdminService、ImportService及Mapper；前端admin-practice.ts、AdminPracticeEditorView.vue、PracticeListeningEditor.vue；PracticeListeningRulesTest/IT。

**Interfaces:**
- POST /admin/practice/listening-sets、PUT /admin/practice/sets/{id}/listening：{requestId,expectedLockVersion,metadata,body,questions,listening}；body为实际配套原文。新建限定CET4、section=listening，draft题数0–50，发布1–50且必须与录入套组结构一致；阅读原上限10不变。
- ListeningRules.validateDraft(input)、publicationChecks(input)：返回与现有Check(path,questionKey,message)兼容的定位结果。
- 校对复用 /sets/{id}/review，新增AUDIO：只确认当前音频/分组；QUESTIONS仍整组人工确认；SOURCE独立；检查通过才能发布。

- [ ] 编写规则/集成失败测试：同题key重复、分组缺题/重复/乱序、无效资产、缺原文、重复选项/答案解析缺失均不能发布；新建/导入仍DRAFT；新音频撤销相关校对；旧发布版和旧音频不变；51题拒绝；阅读11题仍拒绝。
- [ ] 定向运行确认RED，补齐迁移和各服务分支；只扩展共享题库设施的听力通路，不放宽阅读专用业务。同步新增听力创建/编辑UI，一套完整音频上传预听+分组/真实题号+题目总览及可定位校对检查。
- [ ] JSON导入仅引用已上传且当前管理员可用资产，未知/无权资产拒绝，不自动导入外部文件；保留现有阅读JSON。
- [ ] 实现发布快照的音频/分组/原文绑定，单事务锁和校对哈希纳入音频身份；题目编辑不替换已发布文件。
- [ ] 定向验证GREEN，并实际用管理端测试账号录入/预听/校对/发布测试套组。只用标注测试的夹具，运行后清理专用测试数据，不触碰已有4组业务内容。

## Task 3：学习目录、播放保存与交卷

**Files:** Catalog/Queries/Attempt服务与Mapper/Controller/DTO；frontend/src/api/practice.ts、stores/practice-attempt.ts、PracticeAttemptView.vue、PracticeLibraryView.vue；新增ListeningPlayer和listening-media；前端unit/e2e及ListeningIT。

**Interfaces:**
- GET /practice/sets?section=listening、facets/catalog支持整套；省略section继续reading；只有发布的听力才算内容，目录原型不计数。
- POST /practice/attempts/{id}/audio-ticket：校验本人轮次及冻结资产，再签票据；下架不剥夺已有本人轮次。
- DraftAnswersInput新增可选playback；省略不修改旧值；与答案/标记/当前题在同一expectedVersion事务保存。Attempt增加可选listening/playback，听力IN_PROGRESS的article=null，完成才返回配套原文。
- 前端listening-media负责取得票据、过期重取和播放器连接，不保存短期凭证到storage；Pinia现有保存队列增加播放状态，约5秒合并，暂停/退出/交卷前flush。

- [ ] 先写后端失败测试：跨用户轮次/音频拒绝、进行中无原文答案解析、音频版本冻结、并发保存409且无部分写入、忽略playback不清旧值、幂等交卷/未答确认、下架前后本人续答/新开始限制。读取不创建轮次。
- [ ] 实现服务端目录/作答分支，听力A–D判断和现有计分复用；重练完整冻结套组，不改变DETAILED仅错题和CLOZE/MATCH整组语境行为。
- [ ] 前端先写失败检查：上次位置恢复后暂停、选择/标记失败保留、拖动与选择合并串行、过期票据续签保持位置但不抢自动播放、后台暂停、不自动交卷、整套提交后才看原文解析。
- [ ] 实现播放器、逐题/题号/标记/总览、±5秒/倍速/A–B循环/进度，手机单列；复用已确认风格。旧阅读分支不挂载听力媒体组件。
- [ ] 验证定向单测、MySQL/Redis集成与Mock浏览器GREEN；人工/真实浏览器测Range拖动/播放、保存退出刷新续答及交卷；没有真实音频时用明确校音夹具验证媒体能力，不声称英语试题已核实。

## Task 4：相关回归、真实资料准备与交接

**Files:** 上述专项测试与文档；内容来源说明放docs/content或content既有目录，原始有使用限制的资料保留Git忽略缓存。

- [ ] 运行 ` . ./scripts/use-workspace-tools.ps1` 后，在backend执行 `./mvnw.cmd -version`，核实实际Java21；运行定向规则/集成及已有专项阅读的相关回归，使用 `./mvnw.cmd verify -Pmysql-it` 执行所需集成。只检查实际启动的项目测试库/Redis，不访问其他项目。
- [ ] 在frontend运行 `pnpm test:unit`、`pnpm build`（含类型检查），受控4174构建预览运行专项listening/attempt/cloze/match/admin/import/history定向浏览器检查。无需重复全项目截图或扫描未改模块。
- [ ] 真实浏览器1440/768/390/320px覆盖管理员录入到学员续答交卷；保留原有题组内容/版本/状态只读基线，结束时核对不变。身份、判分、并发、泄漏和资产隔离问题必须解决后交付。
- [ ] 开发完成后再查首套真实资料：仅有完整音频、同套试题/答案/原文和来源对应时才新增未校对草稿；缺资源则明确记录“技术接通，真实资料未齐”，不得伪造内容或代用户发布。
- [ ] 更新API/数据库/用户指南/验收/进度，记录真实命令、退出码、初期失败修复及未验项；打开正式听力页面供用户检查。保留旧JAR，需部署新包时先核对项目进程/端口；不擅自重启未知服务。
- [ ] 审查本次限定差异，验证五类Review Focus均有对应证据；如确需独立审查只安排一次限定审查，不逐任务启动代理。不暂存/提交无关文件。

## 自检与交接状态

- 已覆盖方案音频、权限/Range、录入发布、冻结、作答保存、泄漏防护、内容来源和相关回归，各任务接口一致；没有加入云存储/转码/AI/整卷。
- section显式分流；配套原文继续内部版本复用但进行中不返回；MP3不是匿名静态资源；播放位置与答案共用乐观版本队列。
- 真题来源检索和正式草稿发布独立于夹具技术验收，避免假完成。
- 用户已确认并集中执行；技术范围完成并在本地运行。逐条原计划复合检查未全部单独执行，不批量勾选以免混同证据；实际命令与未验边界以正式验收为准。首套真实内容核实/录入尚未完成，不能将技术夹具当作真实套题。

## 执行结果（2026-10-21）

任务1–3技术通路已接通，任务4相关验证/文档/部署完成；真实资料仅候选入口未配齐，不新增草稿或发布。V23执行后发现答案ordinal旧上限，另增V24修复；AudioController合并管理/媒体入口、配置沿用@Value，未额外拆空壳类或新依赖。实际验证含79后端单测、106前端单测、18相关Mock浏览器及四宽度真实播放器，详细失败修复与未验项见正式验收。
