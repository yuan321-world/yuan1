# 阅读选词翻译实施计划

Goal: 完成用户已选定的本地优先、点击调用DeepSeek及选区下方按钮。Spec: ../specs/2026-10-28-reading-selection-translation.md。当前会话主代理顺序执行，授权来自本次用户请求及推荐选项答复，原位保留已有工作。

- [x] 1. 新增selection-action.ts几何计算与单元边界测试；ReadingDetailView使用Range引用、fixed坐标和滚动监听替代底部固定；双端UI验证紧贴选区，不跨文章。
- [x] 2. V32新增reading_selection_translation与request映射。reading/translation独立DTO/mapper/service/controller/worker复用PracticeReviewProperties预算上限和DeepSeekReviewClient受控HTTP。POST /api/v1/reading/selection-translations {requestId,articleId,versionId,text}返回任务；GET /{id}仅本人；GET /requests/{uuid}用于未知响应确认。状态QUEUED/RUNNING/SUCCEEDED/FAILED；译文纯文本。
- [x] 3. service真实库TDD：版本授权/选文来源、1000字符/2词、UUID、本人读取、缓存/幂等/重复点击、共享预算与预留、超时/明确拒绝/可信用量结算和过期恢复。worker单并发/lease，停用排队任务释放。provider添加translate方法，不改变review方法和原批改DTO。
- [x] 4. 前端useSelectionTranslation：已有整句/短语先查，未命中登录用户POST任务，按1秒/3秒退避最多60秒轮询后给出显式查询按钮；关闭/选区/身份变化abort与generation保护，pendingUUID按账号+版本+文本保留，不自动重复POST。来源显示本地/参考/DeepSeek与预算状态。
- [x] 5. 真实DB+Mockprovider、浏览器选择范围/上下定位、旧阅读与认证回归、类型/构建；独立只读review。最多2次原创短文本真实模型校准，shared1CNY上限不改。没有额度不改限额绕过。
- [x] 6. 打包不可变JAR并核对旧PID/哈希再替换8080，更新启动脚本，V32按Flyway应用。原业务只读指纹、接口归属/HTTP/serviceUP、AI调用费用证据记入验收；同步docs/api/database/decisions/progress。

2026-10-28执行完成；确切验证命令、计数、失败修正、费用和指纹范围以 docs/acceptance/2026-10-28-reading-selection-translation.md 为准。无提交/推送。
