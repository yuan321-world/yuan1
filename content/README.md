# 首批阅读与本地词典

原始十篇文章、319词基础词库和编辑参考译文为本项目原创、AI辅助编写的学习材料，未复制媒体文章。后续新增ECDICT离线子集属于第三方数据，来源及许可独立记录在dictionary/ecdict-local/NOTICE.md，不与原创词条混称。十篇文章均使用虚构人物和情境；署名 `Lingua Loop Editorial Team` 表示项目编辑来源，不代表外部出版机构。B1/B2是编辑估计，非官方评级，也非考试真题。

## 2026-10-17 参考译文覆盖完成

reading/editorial-translations.v1.json的dataVersion=1.1已覆盖全部10篇原创、60段中文参考译文、13条已准备的句级参考。正文快照和标题与原始十篇完全对应；服务端仍按规范化原文匹配，不把旧译文套到修改后的文章。其余任意新句实时翻译未启用；选中完整段落可复用段落译文，准备的句子可直接匹配。

新增7篇42段已做独立AI语言/数据复核，无实质遗漏/误译；不冒充独立人工校译，界面继续标注待人工校对。既有3篇译文保留，7篇初稿仅将一处“没有必要的数据”润色为“缺少所需数据”消除歧义。无收费API调用，无文章自动发布、正文修改或数据库结构变化；下方早期覆盖3篇的说明为历史记录。

## 使用与版本

- `reading/original-b1-b2.v1.json` 是可直接上传的严格导入文件：schemaVersion=1和articles。不包含发布状态；管理端先校验，再确认创建草稿，逐篇预览后由管理员明确发布。
- 相邻 `.manifest.json` 保存dataVersion=1.1、编辑说明、每篇重点词及基本词形。将审阅元数据分开，可保持接口拒绝未知业务字段。
- `.source.md` 是可编辑原稿；`dictionary/editorial-supplement.v1.tsv` 为307个补充基本词条的原创中英释义和显式形式。初始12个词条保留，最终319个，不以重复词或占位解释凑数。释义是有限学习义项，不承诺完整词义覆盖；音标未独立校对，暂不提供。
- 在项目根目录运行 `./content/build-content.ps1` 生成固定顺序JSON。生成器保留初始12词条并检查重复基本词形。装载器用版本和SHA-256保护数据：已装载版本不能原地修改；以后编辑须同步提升生成器/manifest中的dataVersion并重新验收。
- 词典从JAR的classpath:dictionary加载，不依赖运行目录。版本升级保留已有词条ID、显式词形与个人来源引用。不自动导入或发布文章。

## 本轮编辑检查（2026-10-16）

已由实现者逐篇检查人物/情境连贯性、段落衔接、来源标注和30个重点词的所选义项，逐条检查补充释义与显式形式；评审补充review/repair在原文中的名词义项并升为1.1；未借用未经授权的媒体归属。程序检查与编辑检查分别记录，不宣称已有独立人工审校。

| 分类 | 原创文章 | 难度 | 词数 |
| --- | --- | --- | --- |
| 学习 | A Notebook That Makes Room for Tomorrow | B1 | 391 |
| 学习 | The Question at the Back of the Classroom | B2 | 394 |
| 生活 | Saturday at the Repair Table | B1 | 397 |
| 生活 | A Different Route Home | B1 | 395 |
| 科技 | Before You Press Allow | B2 | 394 |
| 科技 | Choosing a Journey, Not Just a Vehicle | B2 | 398 |
| 文化 | The Recipe with Three Different Endings | B1 | 398 |
| 文化 | A Library Card and an Empty Chair | B1 | 396 |
| 职场 | The Note That Helped the Next Shift | B2 | 404 |
| 职场 | A Meeting with Time to Think | B2 | 396 |

自动验收由ContentQualityTest检查10篇/五类各2篇/350—650词、来源、319词基本词形唯一、全部重点词在正文出现并能映射；共同的英文token样例覆盖撇号、连字符和UTF-16位置。词数采用项目统一分词规则，预计阅读分钟仅为估算。

待确认：用户对十篇内容的兴趣与难度反馈；独立人工英语校对；实际阅读/选词界面的视觉验收。以上不记为通过，也不阻塞已授权的功能测试与草稿导入。

## 精读参考译文（2026-10-17，第一小阶段）

reading/editorial-translations.v1.json保存首篇《A Notebook That Makes Room for Tomorrow》6段中文与4句参考译文，为项目编辑/AI辅助材料，尚待独立人工校对。每条保存对应原文，服务端只在标题与规范化正文完全一致时匹配到当前版本段落/句子key；不把旧译文套到已修改文章，不声称其余9篇已有译文。

正文和manifest只读打包进JAR供工具上下文比对，不自动导入/发布文章。重点词复用现有编辑manifest的3个词/篇，不标为未经验证的四级词书。未接外部翻译，不产生API费用。

同轮交付补充：参考译文扩充到当前开发库已发布的两篇原创《The Note That Helped the Next Shift》《A Meeting with Time to Think》，与首篇合计3篇/18段/6句。没有改变文章发布状态；逐篇原文绑定与待人工校对标记不变。


## 2026-10-18 阅读题待审核稿

按用户要求每篇1题，共10道；见reading/comprehension-questions.v1.draft.json及docs/content/2026-10-18-reading-questions-review.md。原创AI辅助学习题，待用户审核，不是考试真题。题干、四选项、唯一答案、中文解析、原文段落依据与干扰项说明已准备；保存原稿文件/逐篇正文SHA256，当前公开两篇另保存版本绑定及正文匹配结果。程序校验不等同于独立人工审校。

审核稿包含证据/校验元数据，不是可直接提交的文章导入JSON或管理API请求；未进入生产查询接口、不自动装载数据库或发布。后续经用户确认，重新核对实际正文/版本及题组锁版本，再由现有管理流程录入业务字段并校对发布，不覆盖未知已有题组。


2026-10-18发布更新：用户已确认10题，经管理API录入；公开文章9/10配套题组已发布，其余8篇文章/题组保持草稿。发布状态、实际ID绑定与审核稿SHA256见reading/comprehension-questions.v1.publication.json；上方及原审核JSON的待审核标记是初稿历史快照，不是当前线上状态。无应用代码/迁移修改，不自动加载新数据文件。


## 2026-10-18 首批写作积累审核稿

reading/writing-materials.v1.draft.json为当前公开文章8/9/10各一条写作素材，docs/content/2026-10-18-writing-materials-review.md为审阅版。保留原文证据和版本/hash，第10篇例句明确标为编辑改写；仅准备稿，未调用管理写入/发布，不能把页面空态视为自动补齐。用户校对后再录入，其他文章/题组状态保持。
