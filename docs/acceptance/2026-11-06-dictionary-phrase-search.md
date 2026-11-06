# 主动查词扩充验收

用户批准：扩充本地词库、词形及词组；两三个英文词组成的表达收藏进词组本，单词收藏仍进生词本。

## 实现

- prepare-dictionary-search.py复现五个10k版本分片、30,005条词组。与原词组索引合并后49,271个唯一表达。原10k构建参数及文件保持；源SHA与来源范围见content/dictionary/search-expanded/README.md。
- EcdictDataLoader复用导入事务锁、校验与INSERT IGNORE；独立metadata键，manifest纳入五个新hash。未改数据库结构或词书与复习算法。
- 搜索支持80字符内单词/多词、实际词形及短语，类型与canonicalLemma/relatedEntryIds明确。词组收藏复用原鉴权、CSRF、归属、幂等和恢复接口，不自动加入学习，不覆盖已有个人释义及笔记。
- WordSearchDialog保留固定框尺寸、内部滚动、关闭重置、最近查询、英美朗读；词组使用“加入词组本”。
- 独立只读审查发现词组未记录当前复习词辅助，已修复建议/详情/历史与大小写词组关联。失败用例先确认，再修复并回归。

## 验证

- Java21：mvnw -o -Dtest=DictionaryRulesTest,DictionarySearchIT,DictionaryExpansionIT,LearningV2IT,PhraseCollectionIT test，退出0，4单元+27专用MySQL集成全通过。
- pnpm build退出0，保留项目已有大chunk提示。
- vocabulary-v2.spec.ts桌面/320宽手机Mock32项通过，含词组收藏类型及LOOKUP输入保留。
- 两个生成脚本--check退出0，固定源复现及原10k不变。
- package -DskipTests退出0（测试结果取上方独立命令，不用打包替代测试）。
- 本地8080已核验旧Java进程并切换dictionary-expanded-20261106.jar；4174加载新dist。实际登录页面核对play a role/part/trick、加入词组本入口，apple混合单词/词形/词组结果。未替当前账号新增收藏或修改学习记录；实际收藏写入由专用测试账号验证。
- 实际截图：忽略目录.tool-cache/phrase-search-live.jpg；日志：.tool-cache/phrase-search-*。用户视觉认可仍待体验。

仅本地开发离线扩充，不宣称穷尽词库、每词都有例句或源数据获得新的转载许可。未调用付费接口。


## 词组双语例句追加

用户参考图要求适当增加词组例句，沿用紧凑详情结构，在释义和收藏按钮之间显示最多两条英中句对。新增PhraseExamples.vue、phrase-examples.ts/json及4项规则测试；WordSearchDialog只接入展示组件，既有接口、收藏和辅助逻辑保持。

30组常用词组共60条原创句对优先；不足时仅匹配既有固定/公开语料的完整连续词组，保持句子配对及Tatoeba署名和许可。未收录不编造、不调用付费API。首屏动态加载，切换/卸载取消旧渲染，最多100项只读查询缓存。

验证：新增用例先确认双端缺少“双语例句”而失败；修复后4项Vitest定向单测通过，原196项单元另一次回归通过；vocabulary-v2.spec.ts桌面/320宽手机32项Mock通过，新增核对两条例句、中文及词组收藏。pnpm build退出0（既有chunk提示保持）。实际当前账号play a role页面显示两条中英文及已加入词组本状态，未执行收藏写入。未改后端，无新数据库验证要求；无新增外部调用。
