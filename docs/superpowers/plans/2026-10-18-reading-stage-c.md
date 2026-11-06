# 阅读扩展阶段C：题组管理与游客体验

用户明确要求开始C阶段。沿用已确认设计2026-10-17-reading-writing-practice-design.md；使用writing-plans/executing-plans/TDD。原地增量实现，保留全部现有工作，不从缺少未提交功能的旧HEAD新建工作树，不自动提交。

## 范围与接口

每文章版本一个题组，1—10道四选一题；管理编辑草稿与发布冻结版分离，发布不改变已有体验依据。公开GET只有题干/选项，ADMIN接口才含答案/解析。C包含游客短期逐题判分；D才保存账号轮次、恢复与重做，E才打卡。

V13新增reading_quiz、reading_quiz_version、reading_question。reading_quiz保存有界draft_json及锁版本/发布指针；每次发布新增不可变version与question，不更新旧题。复用现有文章版本联合归属与管理员权限。管理GET/PUT /admin/articles/{a}/versions/{v}/quiz；POST /admin/reading/quizzes/{id}/publish、/unpublish。公开GET /reading/articles/{a}/versions/{v}/quiz，未配题为null。

游客POST /reading/articles/{a}/versions/{v}/quiz/guest-sessions {quizVersionId}；POST /reading/quiz-guest/submit {token,questionId,choice}。Redis独立前缀保存10分钟状态，随机256bit不透明凭证仅body/内存，键用SHA256；逐题有序、同选项重试幂等、改选409，只有已提交题返回正确答案与解析。每IP开启10次/小时，提交120次/分钟；使用真实remoteAddr、不信任XFF。Redis故障503，不降级绕过。体验不写学习记录。

Ruling：草稿整体JSON+发布冻结版/题目表，避免为每次击键产生版本；D可引用冻结题目ID。凭证采用服务端随机不透明token代替自带内容的签名token，校验与撤销全由Redis负责，无前端可改声明。

## 实施与验收

- [x] 1. 先写真实库HTTP测试并观察失败：角色/字段、发布无泄露、草稿隔离、换版/下架、游客顺序/重试/版本冻结/过期/限流。
- [x] 2. V13、quiz模块DTO/校验/Mapper/Service/Controller、Redis体验实现；专用测试清理与结构断言；对应测试通过。
- [x] 3. 管理AdminArticleQuizView、版本路由/入口；正文后ArticleQuizPanel；保留Aa/词汇/写作/进度。先浏览器红，再实现、检查局部失败/选项保留/重复请求/响应式及身份变化。
- [x] 4. 全量后端/前端、独立整体审查，修复重要问题；验证新版运行、真实游客只读冒烟与文档更新，关闭专用临时服务。

关键文件：backend/reading/quiz/*、V13__reading_quiz.sql、ReadingQuizIT、SchemaMigrationIT/ReadingHttpITSupport；frontend/api/quiz.ts、components/reading/ArticleQuizPanel.vue、views/AdminArticleQuizView.vue、ReadingDetailView/AdminArticleEditorView/router/safe-redirect及样式/测试。不得安装AI或其他新依赖、自动发布文章/题目、改认证体系或写虚假个人记录。

Pre-flight：管理发布生成不可变题目集合→公开DTO显式剔除答案→游客会话绑定版本→Lua原子逐题提交；四个接口约定必须一致。阶段D尚无实现，界面明确体验不计入个人成绩/打卡，不能假装保存成功。
