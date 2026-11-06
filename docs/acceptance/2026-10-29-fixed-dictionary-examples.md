# 固定双语词条学习卡验收（2026-10-29）

用户确认的边界：固定双语例句1—2条、常用短语、按义项/词性分组近义词、单列当前文章原句，缺少资料逐步补齐。保留完整词典/发音/收藏/显式翻译；不自动新增词根解析或在每次点击时生成内容。

## 实现与数据

DictionaryLexicalCatalog读取随包 fixed-examples/entries.json，不依赖当前文章、无外部网络和数据库写入。candidate新增 examples:[{text,translationZh}]、exampleSourceNote，按实际候选lemma读取、最多2条，缺失[]。前端复用原始候选，固定例句默认展开（无资料时折叠待补充），其后短语/近义词，当前文章原句与英文释义单列；候选切换隔离、收集来源和收藏归属保持原逻辑。

首批116组原创/AI辅助双语句对，显式关联395词头、456条句例关联。源TSV列为English/中文/明确目标lemma；生成器校验已知词头和显式词形并限制每词1—2例，不靠任意词形交集自动派发。全部句对经独立AI语言/关联审阅，仍待人工语言校对；不冒充权威外部词典或已完成人工审核。不将功能词、没有自然近义词和缺失资料用占位条目凑齐。没有全一万词覆盖，未覆盖词明确待补充。

初版自动匹配候选499词，审阅发现closer名词/close比较级、mean/meaning及原始编辑词条有限义项错配；改为明确关联并逐词核对原始319优先释义，移除不兼容关联，增写9条适合close/work/help/support/focus/respect/online/suggest/difference等已有释义的句对，最终395词。affordable因本地中文主要字段不适合教学，保守不挂例句；不声称其英语词义错误。

## 验证

- RED：新增 catalog.examples 缺方法，编译失败；新双端固定例句UI找不到栏目，先失败。审阅后补充closer/mean/affordable排除和close/work语义断言。
- Java21 Maven mysql-it verify：117单元，DictionaryExpansionIT5/DictionaryLookupIT2/PracticeSelectionIT1共8真实专用库集成，0失败/跳过，exit0。验证稳定公共例句、同词条读写隔离及既有查词/词形/音标/短语/收藏。
- 前端40文件162单元，类型/构建成功；桌面1440/手机320共66项Mock界面通过。固定双语与文章原句分开、候选切换、语音参数/收藏/缺词翻译、选区/试卷锁保持。首轮旧测试要求卡片必然增长高度，新增栏目触及受控最大高度；改为验证高度不缩小且展开原句可滚动到卡片内，保留视口边界规则。
- 生成 --check 通过，固定TSV/派生JSON一致。截图 .tool-cache/fixed-examples-{1440,320}.png 已查看手机卡片，未横向溢出；Mock数据不冒充实际词条或用户视觉验收。
- 本地真实匿名接口：earth返回2例/5短语/4近义词组；study与studies例句完全一致；step/look各自稳定；closer自身不挂close例句（另一个close候选仅自己的动词例句）；未知词仍未收录。结果 .tool-cache/fixed-examples-live.json。

日志均在忽略目录 .tool-cache/fixed-examples-{backend,unit,build,ui}.log；供应商0调用，不占百度字符或DeepSeek预算。未新增数据库迁移，原34迁移保持。

## 本地交付

仅重启核对身份的旧项目PID6968，当前使用 .tool-cache/20261029-practice-translation-live/english-hero-fixed-examples-v3.jar，SHA256 29986F29A0D3AD28D2884591DCEF2C0D871D7EDC274E7D55276ACC38343BF0B8；启动脚本保留ECDICT和既有AI/CET4开关。4174为新dist，前后端健康已确认。68个既有业务表指纹前后相同，无私人学习/词典/题库数据变更。

后续仅按用户反馈继续补充可信词条例句、短语与近义词覆盖，不自动进入其他模块或一次宣称全部词库完成。
