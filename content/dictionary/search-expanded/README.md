# 主动查词扩充

固定来源沿用相邻 `ecdict-local/NOTICE.md` 的 ECDICT bc015ed 原许可及混合上游来源说明，仅作为当前本地开发离线数据，不新增转载授权声明。

- 原始 CSV SHA-256：`1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf`。
- 50,000 个有 BNC/FRQ 正词频且具有中文释义的词头，按两种词频排名较小值选取。分为五个独立版本的 10,000 词分片，复用增量导入；已有词条 ID、释义、收藏和学习记录不替换。
- 按词频代理选取 30,000 条并保留常用示例，最终30,005条（含3条原创释义补充），其中源数据为同一 CSV、由上述词头组成的 2—5 词表达，长度不超过80字符、中文释义不超过250字符。按组成词最大词频排名和总排名挑选，非完整专业词组词典。
- `play a role`、`play a trick`、`play against` 的简短中文释义为本项目原创补充。九条常用搜索示例提前排序，其余沿用组成词频顺序；词频代理不代表实际短语频次。
- 不调用翻译/生成 API，不为缺词虚构例句或词源。启用开关仍为 `english-hero.dictionary.ecdict.enabled`。

复现：`python scripts/prepare-dictionary-search.py .tool-cache/ecdict/ecdict.csv --check`。原 10,000 词基础子集保持不变。
