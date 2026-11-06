# 四级参考词表：本地开发

用户于2026-10-19明确选择采用ECDICT四级参考词表并集中接入。此目录不称官方大纲、指定出版物词书或完整商用授权数据。

来源与固定版本： https://github.com/skywind3000/ECDICT/tree/bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b 。原CSV及SHA256沿用content/dictionary/ecdict-local/NOTICE.md；筛选独立cet4标签，不用BNC排名充当考试标签。

原3849条，当前可学3846词；B.C./i.e./P.M.三项因当前格式限制明确排除。10个可学词无音标，不补造音标，仍可尝试浏览器合成朗读。派生文件hash见manifest.json。

LICENSE原样保留仓库MIT声明。数据混合EDictAZ、cdict及其他资料/贡献，未逐词核查上游权利；现有本地开发选择不构成外网上线或商业分发授权结论。无在线API、按次费用或第三方密钥。

生成/复现：`python scripts/prepare-cet4-reference.py .tool-cache/ecdict/ecdict.csv content/vocabulary/ecdict-cet4-local [--check]`。只操作文件，不连接数据库。导入默认关闭，须显式开启english-hero.vocabulary.cet4-reference.enabled；只新增公共缺词、保留旧ID/释义和个人进度。
