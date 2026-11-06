# ECDICT 本地开发离线子集

用户于2026-10-17选择本地开发使用，保留来源/许可说明。此目录不宣称每个上游词条已经逐一授权审查，也不作为上线/商业分发授权结论。

- 项目：https://github.com/skywind3000/ECDICT
- 固定版本：bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b。
- 原数据：该版本ecdict.csv，65,933,428字节；SHA256 `1a6947e04785db63613a92e14903cdae7954f7e84860b10e68e5c7cbb3f9c3cf`。
- 生成数据entries.json：10,000词头、23,112条词形（包含词头本身），SHA256 `5c9720461a258635b24762975ae49dca1a1b06de1de22729e08c12e6c6efc4e8`。
- 同次转换paradigms.json保留past/ing/third/plural/comparison已知范式，SHA256 `fb0279be5c67e8a21a363b2b7c6286acda9c025dc78eb1d5f7f78677af6dea8e`。它约束规则回退，不让规则覆盖数据已声明的不规则/双写形式；同样可用--check复现。
- LICENSE原样保留仓库MIT声明，UPSTREAM-README.md原样保留该版本来源、字段说明。仓库说明汇集EDictAZ、cdict及其他资料/贡献，并提到音标采集；仓库声明不等于本项目验证了所有上游权利。

生成：用scripts/prepare-ecdict.py校验原CSV哈希后，筛选当前项目单词格式支持的、含中文释义且字段长度符合接口的数据，按正BNC排名及词头稳定排序取前10,000条，再按词头输出。清理不支持的exchange类型、词组与重复形式；只保留现有模型可表达的最多16条释义、64词形。没有英文释义时保留空字符串，不编造释义或近义词/词根；没有把BNC排名冒充当前文章词频或CEFR等级。

复现（Python标准库，无额外依赖）：

    python scripts/prepare-ecdict.py .tool-cache/ecdict/ecdict.csv content/dictionary/ecdict-local/entries.json --check

`--check`仅校验，不写库；去掉它可重新生成固定输入的派生数据。原65MB CSV仅在忽略目录保存，不进入项目内容目录。

运行：必须显式设置`--english-hero.dictionary.ecdict.enabled=true`才执行补充导入，默认关闭。导入复用公共dictionary_entry/dictionary_form和system_metadata，事务、批量、幂等；已有词头的释义/音标/ID不覆盖，原项目释义优先，因此原有少量词条仍可能显示“音标暂缺”。导入完成后关闭开关不会清除数据库已有词条，本项目不自动删除引用中的词典数据。

本地查询不按次计费，不调用外部API，也不需要密钥；下载、存储和维护仍有成本。此子集不覆盖任意专有名词、词组、所有不规则变化或完整英语词汇。未来外部补查/缓存/商用部署另行确认供应商和许可。
