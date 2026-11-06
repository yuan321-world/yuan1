# 写作积累阶段A实施计划

> 使用executing-plans逐任务执行；用户已确认设计/原型并要求执行。保留当前含大量既有修改的工作目录，不切换到缺少当前实现的旧HEAD工作树，不自动提交。

**Goal:** 管理员录入、保存、预览发布/下架素材，公开阅读具体文章版本的素材摘要/详情。

**Architecture:** reading/writing模块，V11新增一张素材表。稿件与发布快照分离；public DTO不包含草稿。复用文章版本访问、管理员权限、事务及既有前端反馈。

**Tech Stack:** 当前Vue3/TS、Java21/Spring Boot/MySQL/Flyway，不加依赖。

**Spec:** docs/superpowers/specs/2026-10-17-reading-writing-practice-design.md及已确认reading-learning-preview.html。

约束：本阶段不假装实现B—E收藏/仿写/练习/打卡，不删除既有功能；不自动发布文章/素材，不清库；原型与生产分离。

审查重点：跨文章版本归属、草稿泄露、并发覆盖、旧版访问权限、前端过期响应/未保存草稿。

- [x] 1. 先写真实库HTTP测试：管理员创建/修改/发布/下架/归档、游客可见边界、USER禁写、版本错配404、重复提交409；先观察失败。
- [x] 2. V11 reading_writing_material，DTO/Rules/Mapper/Service/Controller。草稿content_json与published_content_json分离，发布序号、lock_version、文章版本联合外键；最多50条，纯文本/数组有界。状态变更和更新均带expectedLockVersion。
- [x] 3. 前端API、ArticleWritingPanel（单DOM桌面侧栏/手机正文后）、只读详情dialog；管理员独立素材编辑页及现有文章编辑入口，字段/局部错误、重试/并发、未保存保护。不删除阅读工具栏。
- [x] 4. 浏览器测试管理员流程/公开素材/移动布局及既有功能；完整后端专用库回归、类型/构建；独立审查后真实服务切换，文档记录。

## 执行记录

Ruling: 阶段A只交付素材展示/管理，收藏/仿写等控件在B实现后接入；不把原型模拟状态复制进生产。
Ruling: 发布快照独立于编辑草稿，避免素材修改立即影响公开页面；归档保留记录，为后续个人引用留边界。

完成：58单元/MVC、56真实库集成、60前端单元、类型/构建、194浏览器通过/6条件跳过；审查修正两项，真实1440/320只读检查通过，服务切换完成。详见docs/acceptance/2026-10-18-writing-stage-a.md。
