# 离线近义词与短语数据

用途：用户于2026-10-17确认，在现有查词卡增加近义词和短语搭配，来源收至底部。没有在线API、密钥或按次费用，不自动将相关词或短语保存到个人词汇。

## WordNet 3.0

- 官方发布：https://wordnet.princeton.edu/ ，许可说明：https://wordnet.princeton.edu/license-and-commercial-use 。
- 数据库归档：https://wordnetcode.princeton.edu/3.0/WNdb-3.0.tar.gz ，10,518,425字节，SHA256 `658b1ba191f5f98c2e9bae3e25c186013158f30ef779f191d2a44e5d25046dc8`。
- 原始源码发布归档：https://wordnetcode.princeton.edu/3.0/WordNet-3.0.tar.gz ，11,537,239字节，SHA256 `640db279c949a88f61f851dd54ebbb22d003f8b90b85267042ef85a3781d3a52`。
- LICENSE-WordNet.txt为后者WordNet-3.0/LICENSE原样副本。Copyright 2006 Princeton University；原许可及免责声明随派生数据一起保留。来源署名不是宣传或背书。
- 读取index及data文件的实际synset关联，同义词按词性/词义分组，遵循index中的词义顺序。去除当前词本身及重复词，每个词性最多4组，每组最多6个词；服务端只返回当前候选词实际词性的最多6组。原英文词义用于区分语境，不编造中文词义映射或断言所有近义词可互换。

## ECDICT短语

复用用户已确认用于本地开发的bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b CSV，原始hash及MIT仓库声明/混合上游来源见../ecdict-local/NOTICE.md、LICENSE及UPSTREAM-README.md。只在已有本地ECDICT开关开启时返回短语。

从源文件挑选含中文解释、2—6个英文词且在当前词头上有完整词边界的表达。过滤以领域方括号标签开头的专业条目，先考虑动词搭配/常见介词开头，再按来源BNC值、长度和文本稳定排序去重，每词头最多5条。不是对短语常用程度或中文释义逐条人工背书，界面称“短语搭配”。不跨词形盲目匹配，不把share的搭配混入shared独立词条。

## 派生文件与复现

- entries.json schemaVersion1、dataVersion `wordnet30-ecdict-bc015ed-v1`。
- 索引9,146个当前已存在词头，包含21,976个WordNet词义组，以及25,861条词头到短语的引用（同一短语可能关联其中多个词头，此数不是独立短语总数）。
- SHA256 `fa257051346687c0abdef52329a7b6f7e5c2bf3b90398700e9d84df4c96a4e74`。
- 用Python标准库运行，--check同时验证派生数据和原LICENSE副本：

    python scripts/prepare-lexical-extras.py .tool-cache/lexical/WNdb-3.0.tar.gz .tool-cache/lexical/WordNet-3.0.tar.gz .tool-cache/ecdict/ecdict.csv content/dictionary/lexical-extras/entries.json --check

三个源归档/CSV均要求固定SHA256。不执行网络或数据库写入；归档只读取白名单成员，从不extractall。原始包在忽略缓存目录，不进入前端构建；规范化只读资源随后端JAR加载，无数据库结构变化。
