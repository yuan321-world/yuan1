# 背词卡的语料例句补充

语料来自 [Tatoeba](https://tatoeba.org/) 的英中句对，由 [ManyThings 整理](https://www.manythings.org/anki/)。使用 2026-02-13 版 `cmn-eng.zip`，SHA-256 为 `d606361aa7312dcdfb15ef4fc51cb1e7ed8bc2dc608a7b192626cd36e8403106`。遵循 [CC BY 2.0 FR](https://creativecommons.org/licenses/by/2.0/fr/)，界面展示每条英中原句的作者和链接。

先使用词条已收录的例句，其次查找本地补充原创例句及语料句对，再使用保存的阅读原句。语料按完整词头或词典已知词形匹配，优先短句，每词最多选一组。句对可能展示该词的其他用法，未逐条与释义编号配对。语言学习内容经自动筛选，仍可能含传统字形或社区译法。

考试词书另外使用 [Tatoeba 官方下载](https://tatoeba.org/en/downloads) 的 Mandarin、直接链接和带作者的详细句子快照。仅收录可在两端确认作者的直接关联句对，界面保留逐句署名及链接。

| 官方输入 | SHA-256 |
| --- | --- |
| `cmn.bz2` | `d603c1b9c67fb7a5a68c56fe6df5c935025ca1f36a1f9a912c33ed6dbed91743` |
| `links-assembled.tar.bz2` | `69a55057566127a4c2f5cd618ae47167cb66972d67a3f8f39f368c106cd6f50c` |
| `detailed-assembled.tar.bz2` | `8080a162f74f427d96154d8e0676d94d3c0b80f7642876f02fb5ed2fce6d017f` |

复现前端语料使用 `python scripts/extend-corpus-examples.py <cmn-eng.zip> <cmn.bz2> <links.tar.bz2> <sentences_detailed.tar.bz2> <输出JSON>`。前端按需加载，无须调用外部服务。

无公开语料匹配的考试词可使用原创双语例句。部分由本地模型辅助起草，按词头、已知词形及双语完整性自动检查，抽查译文及语义。已发布数据可用 `python scripts/prepare-dictionary-examples.py --check` 核对。
