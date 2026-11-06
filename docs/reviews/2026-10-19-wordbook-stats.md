# 参考词书与词汇统计独立审查

独立Reviewer只读检查V17、Wordbook相关服务/导入器、LearningService增量、字典manifest、统计服务/界面、生成脚本/固定数据和前端来源选择；未修改文件或审查无关脏工作区。

两项P2：

1. 来源PUT期间Escape关闭会中止/忽略已提交响应，页面旧来源与实际新来源不一致。已保存期间防Escape/关闭，网络不确定失败后重新读取overview；延迟提交浏览器回归先失败2项，再桌面/手机通过。
2. 候选词只看learning_word存在，未看personal_enrolled，阻止用户把书分配词明确加入个人来源。已按personal_enrolled=TRUE判定joined；真实IT先复现“期待false实际true”，修复后完整套件通过。

Reviewer未发现其他明确P1/P2归属、进度共享、导入/缓存或统计问题；独立运行新参考词表和旧一万词--check均退出0。数据库及浏览器回归由实现者执行，见验收记录；审查不替代测试或用户视觉验收。
