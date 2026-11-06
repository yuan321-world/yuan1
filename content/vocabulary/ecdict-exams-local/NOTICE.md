# 考试参考词书（本地使用）

沿用项目已固定的 [ECDICT 版本 bc015ed](https://github.com/skywind3000/ECDICT/tree/bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b)，按 `ky`、`cet6`、`cet4`、`gk` 标签分别收词。保留 ECDICT 的混合上游来源说明和本地使用范围，见 [原四级词表说明](../ecdict-cet4-local/NOTICE.md)及[上游许可证副本](../ecdict-cet4-local/LICENSE)。

| 考试 | 乱序版 | 核心词汇版 |
| --- | ---: | ---: |
| 考研 | 4,801 | 1,000 |
| 六级 | 5,406 | 1,000 |
| 四级 | 3,846 | 1,000 |
| 高考 | 3,674 | 1,000 |

乱序版包含该标签下符合现有学习格式的全部词条，顺序按固定哈希排列，续学顺序可复现。核心版在相同范围内按 BNC 通用语料词频选取前 1,000 个有排名的词。此口径不代表考试真题词频或官方考试大纲。无音标保留原数据，不生成音标。

生成及复核命令：`python scripts/prepare-exam-wordbooks.py .tool-cache/ecdict/ecdict.csv content/vocabulary/ecdict-exams-local/bundle.json --check`。去掉 `--check` 重新生成。包 SHA-256 为 `3bf70254cd188b7ed61712faf0657328f42849e927871bb7375c1014ea1c6017`。

后端使用 `english-hero.vocabulary.exam-wordbooks.enabled=true` 启用本地导入。八本词书在同一事务中导入，重复启动保留同一版本的 ID。已存在的词条与旧词书保留，重叠单词沿用同一词条和用户掌握状态。
