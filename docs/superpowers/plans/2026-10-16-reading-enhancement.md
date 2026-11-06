# 阅读模块增量完善实施计划

> 按executing-plans逐项执行；保留既有工作区修改，不自动提交。用户已确认reading-preview.html及每日精选独立于筛选。

目标：将已确认原型接入现有阅读业务，分三个可验证小阶段，不重写认证、版本、进度和生词体系。

技术：现有Vue3/TypeScript/Pinia、Java21/Spring Boot、MySQL/Flyway，现有JWT+Redis及CSRF。

## 边界

- B1/B2、五类现有原创内容；不新增虚假文章或个人数据。精选只受当天已发布集合影响，不随筛选/标签变化。
- 公共列表/当前正文/基础查词不产生个人记录；旧版本、私人收藏/历史/进度继续查归属。
- 收藏归属文章；阅读进度归属不可变版本。后端从principal取用户，不接收任意userId。
- 登录后只重放仍然有效的明确收藏意图；取消、跳页、切换身份使意图失效，普通表单写操作不自动重放。
- 修改SQL新增V10，不改旧迁移；所有读写按公开/个人边界验证，保留Bearer+CSRF。

## 小阶段1：公开阅读与列表

- [x] 先写失败测试：游客列表/正文、私人历史拒绝、草稿/非本人旧版拒绝、组合筛选和排序。
- [x] 修改ReadingController/Service/Mapper与SecurityConfig：可选身份的只读接口、GET articles/{id}，保留私有start。
- [x] ReadingListView和reading样式采用原型的标题/精选/筛选/卡片/分页；公开路由仅reading及详情，B1/B2和newest/short/long参数后端校验。
- [x] 公开详情不调用start；登录用户复用原start/恢复机制。验证无私人数据泄漏与游客零写入。

## 小阶段2：文章收藏

- [x] V10新增user_article_favorite，user/article唯一；活动状态与时间，保留已有数据。
- [x] 新FavoriteService/Controller和测试：PUT/DELETE articles/{id}/favorite、GET favorites、GET favorites/states（最多50ID），同用户幂等、不同用户隔离、取消/下架边界。
- [x] 新前端收藏API/store与按钮，列表/详情同步、局部错误、忙状态、登录后意图恢复；私人标签页游客只显示登录入口。

## 小阶段3：阅读体验与交付

- [x] 公共查词返回collection=null；登录仍组装本人状态。复用原有生词收藏，游客登录后重新选词再保存，避免旧上下文跨身份重放。
- [x] 字号/行距、历史最近时间、现有完成按钮/进度节流/旧版本回归；无匹配/加载/失败本地反馈。
- [x] 后端单元与真实专用库/Redis集成、前端单元/类型/构建、桌面/手机浏览器回归；实际启动新版，更新api/database/progress/acceptance。

主要文件：backend reading/dictionary及SecurityConfig、V10迁移；frontend api/reading、dictionary、收藏store/component、ReadingListView/ReadingDetailView/reading.css/router。独立HTML不进生产构建。
