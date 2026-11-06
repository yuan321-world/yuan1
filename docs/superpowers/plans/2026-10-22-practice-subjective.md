# 写作翻译正式实施计划

用户2026-10-22已授权正式接入，方案见同日practice-subjective-design。采用原compact-3布局，不再新建原型。

## 范围与执行
在当前有大量未提交实现的工作区就地增量修改，不切换或复制旧HEAD，以免遗漏用户已有功能；不提交。按writing-plans/executing-plans内联执行，保留缓存证据与台账，只在最后进行一次独立审查。

1. 后端与迁移：V25扩展共用题组/冻结版本的主观内容，保持客观题quiz约束；专用本人草稿表及幂等命令表；管理创建、编辑、CONTENT/SOURCE校对、发布/下架与版本；公开DTO不含参考。接口独立subjective-attempts，草稿乐观锁，提交幂等，修改创建同版本新稿。先写并运行真实库HTTP集成测试，再实现。测试覆盖身份、跨用户、参考隔离、冻结、冲突、幂等、下架与修订。只用english_hero_test。
2. 前端：api/practice-subjective.ts、stores/practice-subjective.ts、学习作答和后台编辑视图；对接现有目录/历史/后台列表和路由。写作/翻译无错题本，无分数；参考仅提交后显示。保存失败保留文本，冲突显式处理，离开先保存。范围提醒与20000字符限制。定向组件/浏览器测试及类型构建。
3. 交付：针对客观阅读/填空/匹配/听力运行已有相关回归，单次独立审查重点权限、引用泄漏、并发与旧功能；修复重要问题后定向复验。更新api/database/guide/progress/验收。保留现有内容数据，构建开发服务并实际检查桌面手机。不自动发布演示或真题，不生成用户记录。

## 共享接口与审查重点
类型WRITING/TRANSLATION、section=writing/translation；AdminSet.subjective及PublishedVersion.subjective仅管理员；公开SetDetail只有prompt/requirements/minWords/maxWords/minutes。Attempt独立ID路由，有answerText/wordCount/version/status，完成才含reference/notes。前后统一英文词计数。旧客观目录省略section保持阅读；原创入口明确section=all避免漏主观题。幂等命令按用户隔离且绑定操作/请求摘要/目标ID，不复用objective request FK。

验证命令与结果保存在.tool-cache/practice-subjective-implementation/ledger.md。未运行不标通过。

## 执行结果

三项已完成，具体命令、失败与修复及实际证据见同日practice-subjective验收与缓存ledger。主观原型两份示例只保存为6/7原创草稿待人工核对，未发布。共用旧功能基线保持；单次审查重要问题已修复，无次要延期。
