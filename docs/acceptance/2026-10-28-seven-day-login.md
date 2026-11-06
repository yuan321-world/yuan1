# 默认七天登录调整

用户反馈数小时未访问便需重登，要求七天保持登录/设计建议。检查源码发现已使用项目Redis6380（本地AOF开启）和JWT/HttpOnly刷新Cookie；AuthForm记住登录默认false，普通登录空闲7200秒/绝对86400秒、非持久Cookie。记住登录已为绝对604800秒、空闲604800秒，Cookie Max-Age只取原截止时间的剩余秒数。截图未勾选支持普通登录解释；未读取或输出用户实际会话/凭据，不能断言其历史每次退出都是此原因。

本轮仅将共享登录表单remember默认true，并加“公共设备请取消勾选”提示，默认提交rememberMe:true，用户可主动取消选择，短期模式保持。固定从登录起七天，不滚动续七天；10分钟access不变，通过现有刷新恢复。退出、改密、停用、清除Cookie/Redis数据丢失仍可使会话提前失效。旧短期family不延长，需重新登录生效。注册流程仍先注册再登录。无数据库/后端业务逻辑变化，无现有会话清理，无模型调用。

## 验证

前端新增双端用例在旧版因checkbox未勾选先失败。修改后首次联合auth-modal/wall/navigation-stability为35通过/1手机画布专属跳过，新增2例因第一次登录失败后表单清空密码、测试第二次未填密码而失败；修正测试重新输入密码后，新增2例退出0，验证默认checkbox、默认true请求、取消后false请求及公共设备提示。已有35例不重复运行。生产类型检查及构建退出0，4174预览使用最新dist。

backend `./mvnw.cmd -o -Dtest=JwtServiceTest -Dit.test=RedisFamilyRepositoryIT -Pmysql-it verify` 退出0，2JWT单元/2真实Redis测试通过，包含记住登录七天前最后一秒轮换成功、精确七天边界拒绝、轮换不延期，以及普通模式两小时空闲拒绝。测试时钟模拟边界，不等待真实七天，不读取真实用户会话。日志remember-default-red/green/final/build、remember-redis-check在忽略目录。

## 后续按用户反馈移除选项

用户明确要求默认七天且不显示勾选项。AuthForm已移除remember状态、checkbox及公共设备提示，直接signIn(...,true)，清理对应无用CSS；登录页与共享弹窗一并生效。保留服务端普通模式兼容，不修改后端或现有会话。测试调整为验证选项/提示不存在且提交rememberMe=true。

最终生产构建/类型检查退出0，auth-modal.spec双端20项通过，4174最新dist生效。日志remove-remember-build/ui.log存忽略目录。
