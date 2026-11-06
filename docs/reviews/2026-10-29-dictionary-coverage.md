# 本地词典音标缺项排查与修复（2026-10-29）

结论：主要故障是启动配置回退。最新运行命令未包含已确认的 `english-hero.dictionary.ecdict.enabled=true`，本地配置也未启用。数据库保留已导入词条，但 DictionaryLexicalCatalog 在开关关闭时不加载 ECDICT 音标补全和短语，所以319个原始编辑词条音标全部为空，而大多数词的补充数据其实已随JAR存在。不是先前“数据源缺失”一个原因即可解释。

## 处理

真实本地配置追加该布尔开关（未读取/输出凭据）；忽略的项目启动脚本也显式保留参数。更新本地配置示例与README启动说明，产品默认/生产配置不变。仅重启核对路径和Java身份的本项目8080（旧PID38140，新PID6968），使用原已校验v2 JAR，未改业务代码、词库内容、数据库或前端。现有补充导入幂等，未重写原词义、词条ID、收藏及学习记录。

## 真实运行核对

匿名4174→8080逐一查询319个原始词头：修复前319找到、0有音标、0有短语；修复后319找到、311有音标（97.49%）、296有短语。study/studies、library/libraries、look/looking等词形命中正常；thought自身得到 θɒ:t，而think保留自己的 θiŋk，没有把词根音标误充作变形词音标。runing、goed、studys继续拒绝。离线短语in advance恢复found=true。逐词结果保存在忽略目录 .tool-cache/dictionary-coverage/live-{before,after}.json。

读取完整固定CSV和当前派生子集：10000词头中503个无音标，这503个在固定CSV中也没有同词头音标，不能通过简单匹配补出。原始319中剩余8个：can't、well-being、analyze、neighborhood、online、reuse、workplace、handoff。其中 can't/well-being/analyze/neighborhood/reuse/handoff 六个不在当前10000子集，但固定CSV有音标；online/workplace属于503个源缺项。未擅自新增词条或使用模型编音标。

按原始+补充词库静态并集10006词头计算，音标补全后仍有509个缺项（503源缺失+6子集未覆盖）；这是静态数据审计，不冒充整库逐词真实API验证。完整清单见同目录 2026-10-29-dictionary-missing-phonetics.tsv。

## 验证与边界

Java实际21.0.12.1；Maven运行 DictionaryRulesTest/DictionaryExpansionTest/DictionaryLexicalTest 与 mysql-it DictionaryExpansionIT/DictionaryLookupIT：10单元、6真实专用库集成通过，0失败/跳过，exit0。覆盖音标原值优先、缺失补全、短语开关、词形/错拼、私人词汇隔离和导入幂等。日志 .tool-cache/dictionary-coverage/backend-tests.log。固定数据生成 --check（含CSV哈希、词条/范式字节一致性）通过、exit0，见 .tool-cache/dictionary-coverage/reproducibility.log。

修复前后68个既有业务表CHECKSUM完全相同（排除selection translation任务/回执及Flyway，与既有指纹工具定义一致）；因此本轮没有改变被核对的词典、词汇、学习、题库、账户等表。健康 service=english-hero/database=UP。无付费API调用，不需要前端构建；用户刷新即可看到补全响应。未扩充整词库，尚存缺项应后续按来源补数据。
