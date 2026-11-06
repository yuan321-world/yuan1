# 精读第二小阶段：离线词典与紧凑查词卡验收

## 后续修正：shared多候选单卡展示

用户反馈2026-10-17：shared上下重复。实际接口同时返回shared形容词及share动词，原模板v-for把整卡重复。WordLookupPanel现增加有aria-pressed的词条选择按钮，一次渲染当前candidate；原词和lemma相同不再显示冗余原型，保留真实歧义，不改后端结果或合并稳定entryId。词条变化停止旧语音并重定位，slot收藏始终使用选中的实际候选，结果更新后按entryId保持选择。

证据：旧构建新ambiguous word回归桌面/手机均因两张卡失败（lookup-candidates-red.log）；pnpm --dir frontend build退出0含类型检查（lookup-candidates-build.log）；详情/查词/笔记本/列表42项Mock浏览器通过（lookup-candidates-green.log），另选中词条收藏ID与状态隔离2项通过（lookup-candidates-collection.log）；合计44项。真实4174/8080，519x668 article10原生选中shared，切换shared/share后每类操作只一套、词形原型不重复、Escape正确、零阅读/词汇写入和pageerror（lookup-shared-live.log），退出0。实现者查看实际截图。本轮纯前端，无新的后端或数据库测试，不宣称重跑此前完整174项浏览器。

日期：2026-10-17。用户授权继续词典完善，选择ECDICT先用于本地开发并保留来源/许可；随后确认按所给图片紧凑布局，继续沿用品牌色。

## 交付

- 固定ECDICT bc015ed2e24a7abef49fc6dbbb7fe32c1dadaf8b，10,000常用词头/23,112词形，与原319去重10,006词头；annual、committee、elaborate已真实返回中文和音标。原项目定义、音标、ID保持，原有少量词条音标仍可能缺失。
- 本地显式开关导入，默认关闭；同一事务/既有字典锁，批量INSERT IGNORE公共词条/词形，独立版本/hash幂等校验。无新表或Flyway，不改个人词汇/来源，不删除已存在数据；关闭开关不清已导入数据。
- 查询复用原API：精确/显式形式优先，最多16个候选一次词头批查，词性和正向规则验证；已知范式由源数据paradigms.json约束，覆盖15,788条分类词形引用。保留原有公共有界缓存，词库manifest变化失效，私人collection逐请求独立取。
- 卡片压缩为词头、实际音标、英/美合成朗读、中文释义、折叠例句/英文释义/来源、原收藏/取消/恢复。例句来自当前文章原句；缺失英文释义/音标不造数据，短语/词根/近义词无来源不展示。桌面仍靠原词、手机底部，原生选词/权限/焦点/Escape保持。
- 许可证、原README、原CSV及派生hash在content/dictionary/ecdict-local；转换脚本固定输入哈希，--check可复现。原65MB CSV仅在忽略缓存目录，生成内容约4MB；不接任何在线/付费API，不提供任意新词兜底承诺。上游混合来源事实保留，不当作全面商用授权结论。

## 验证证据

命令加载scripts/use-workspace-tools.ps1；Maven再加载use-java21.ps1，实际JDK21.0.12.1。浏览器4174使用E2E_EXTERNAL_SERVER=1、E2E_PORT=4174。

| 项目 | 结果与日志 |
| --- | --- |
| 首轮RED | DictionaryExpansionTest缺类/接口先失败；英美语音新测试旧卡缺按钮先失败。dictionary-expansion-red.log、dictionary-card-red.log |
| 初次实现 | 53单元/MVC＋8词典/阅读/词汇真实MySQL/Redis集成通过；dictionary-expansion-it.log |
| 独立审查RED | 发现runing/stoped/goed等假词形，增加负向断言先失败；dictionary-morphology-review-red.log；随后使用类别范式约束，未逐个手工补定义 |
| 最终完整后端 | ./mvnw.cmd verify -Pmysql-it，53单元/MVC＋52真实MySQL/Redis集成全部通过，零跳过，退出0；dictionary-stage2-final-backend.log |
| Redis恢复复核 | 前一完整运行唯一TokenRevocationIT在测试Redis重启后暂态503；未改认证代码/断言，单项复核通过，最终完整回归亦通过。dictionary-auth-reconnect-recheck.log |
| 前端单元 | pnpm --dir frontend test:unit，60项/15文件通过，退出0；dictionary-stage2-frontend-unit.log |
| 完整浏览器 | pnpm --dir frontend test:e2e，174 Mock桌面/手机通过，6真实账号/邮件夹具条件测试跳过，退出0；dictionary-card-full-browser.log |
| 最终类型/构建 | pnpm --dir frontend build，vue-tsc/tsc/Vite成功，退出0；dictionary-card-final-build.log |
| 最终局部回归 | 展开内容后重定位补充检查；详情/笔记本/查词/列表共40项Mock桌面/手机通过，退出0；dictionary-card-final-regression.log |
| 数据复现 | bundled Python运行scripts/prepare-ecdict.py ... --check，两个派生文件与固定输入一致，退出0 |
| 实际开发服务 | node .tool-cache/check-dictionary-live.cjs，1440px与320px真实4174→8080：新增3词中文/音标/来源；7种错拼拒绝；真实annual点击/手机选择打开紧凑卡、英美按钮/例句/来源/Escape；零横向溢出/pageerror/游客阅读写入，退出0；dictionary-stage2/live.log |

Mock交互、真实数据库权限测试与用户视觉确认分别记录。实现者检查实际服务桌面/320px截图；用户视觉确认和词条人工语言校对待继续反馈。未发真实QQ邮件或操作开发库私人收藏；真实扬声器/本机口音可用性不由Mock语音参数测试代替。

## 审查

独立Reviewer发现一项P2：规则允许部分错拼，并可影响保存校验。先补负向测试，再保留明确双写并通过ECDICT已知past/ing/third/plural/comparison范式限制回退；覆盖两轮21个代表词。最终复核无剩余实质问题，检查15,788条范式都属于对应词条forms；审查者不写文件或运行并发DB测试。

## 运行与后续

正式8080固定运行包.tool-cache/dictionary-stage2/english-hero-dictionary.jar，SHA256 C6ADDE934A7BB7EB56AFF2B12AAB626CDFA2586DF176D9FEF423520A323D1B29；从backend目录运行，参数--english-hero.bootstrap.enabled=false --english-hero.dictionary.ecdict.enabled=true。开发6380和前端4174保留；临时8082候选及测试6381已经验证身份后关闭。没有自动发布文章、重建库或提交Git。

后续：在线外部补查/缓存尚未接入，仍需单独选定服务及缓存权利；剩余文章的参考译文属于第三小阶段，本轮没有自动扩展。词书/背词/AI模块不在本次范围。
