# 写作积累阶段B实施计划

使用writing-plans细化，executing-plans原地执行；用户已确认方案并要求继续，不再重复确认。保留大量既有工作、不新建缺少当前代码的旧HEAD工作树，不自动提交。

Goal：真实本人收藏/取消、个人素材库分页、仿写保存/修改/刷新恢复，复用阶段A文章版本与权限。

Spec：2026-10-17-reading-writing-practice-design.md；阶段B不实现C—E或AI。

架构：V12新增user_writing_favorite、user_writing_imitation，联合唯一用户/素材；新增PersonalWritingMapper/Service/Controller。发布资料与个人数据分开接口，仿写首次记录素材revision/快照，修改仿写保留原参考；素材更新后提示差异，不悄悄替换依据。收藏快照用于不可用资料的标题展示，不给无既有个人记录的人泄露下架资料。

API：GET /writing-materials/{id}/personal；PUT/DELETE /writing-materials/{id}/favorite；PUT /writing-materials/{id}/imitation；GET /account/writing-materials?view=favorites|imitations&page&pageSize。身份取服务端、CSRF/锁/参数校验，取消允许不可用素材，新增/保存需素材可访问，预期revision及lockVersion防并发覆盖。

前端：可复用WritingMaterialDetail接管原解析，添加收藏/仿写交互与本地错误/未保存保护；ArticleWritingPanel继续原布局，增加个人素材库入口；WritingLibraryView新增/writing及账户菜单入口，保留生词笔记本。游客输入仅内存，登录后复验继续，不自动覆盖已有仿写；关闭登录返回原草稿。账号切换/退出清理私有数据，不缓存到公共响应。

- [x] 1. 先写真实库测试：唯一/幂等、跨用户、素材更新快照、下架保留/拒新、并发首存、非法body；旧实现失败。
- [x] 2. V12/Mapper/Service/Controller落地，更新专用测试清理与结构断言；验证DB与权限。
- [x] 3. 写前端回归并观察旧界面失败；解析/收藏/仿写/库页面与登录续操作，测试保留输入、刷新、冲突/下架、账号切换。
- [x] 4. 类型/构建、完整回归、独立审查；真实服务切换、只读冒烟、文档交接。

Ruling：素材下架/归档后已有仿写只读保留，仍可取消已有收藏；不接受新的收藏/仿写修改。新素材发布不会覆盖旧仿写快照；编辑原仿写继续沿用其旧依据，用户可对照查看当前资料。
Ruling：游客登录后若已有仿写，保留游客草稿但不自动覆盖，提示用户确认后手动保存。列表两种视图分别显示收藏与仿写，不将生词混入写作素材表。
