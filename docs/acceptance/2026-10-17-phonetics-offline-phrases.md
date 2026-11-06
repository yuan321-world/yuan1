# 音标补缺与选中短语离线查询

用户确认先用已有离线数据完善，不接收费或外部实时翻译服务。

## 范围与实现

- DictionaryLexicalCatalog读取已有10,000词头ECDICT资源，在本地开关开启且原词条phonetic为空/空白时补充查询响应的音标。原始非空音标优先；数据库ID、原释义、收藏及来源记录不变。新增phoneticSourceNote独立说明补充来源，避免把原创释义音标缺失声明误用于补充音标。
- 聚合已有近义词/短语资源中的23,591条去重表达，提供公开只读GET `/api/v1/dictionary/phrase?text=...`。完整表达精确匹配，忽略大小写/多空白、规范化弯引号；不做相似猜测。最多100字符、2—6个英文词；更长合法选区返回未命中。空白或超过2000字符拒绝400。关闭本地开关返回未命中。
- ReadingDetailView先匹配已准备的完整句子/段落译文，未匹配再查询有效短语。加载/失败重试/明确未命中提示位于当前弹窗，关闭/换文章/卸载中止请求，以generation阻止过期响应覆盖。没有把段落译文冒充短语释义。
- 原单词查词、候选切换、收藏/取消、英美浏览器语音、公开阅读及私人接口权限保持。没有新增表、迁移或费用。

## 验证

Maven实际Java21，项目工具及缓存由workspace脚本设置。新增后端和桌面/手机用例先在旧实现缺方法/界面无短语结果时失败，日志`.tool-cache/phonetic-phrase-red.log`、`phonetic-phrase-ui-red.log`。

- backend `./mvnw.cmd verify -Pmysql-it`退出0，57单元/MVC、54专用MySQL/Redis集成通过；覆盖resource音标补缺、原音标保留、本地开关、精确短语/未知短语/非法参数、原数据库记录不变与既有权限回归。日志`phonetic-phrase-backend.log`。
- `pnpm --dir frontend build`、`test:unit`退出0，含类型检查、构建及60单元；相关详情/查词/笔记本36项桌面/手机浏览器通过。日志`phonetic-phrase-build.log`、`phonetic-phrase-front-unit.log`、`phonetic-phrase-browser.log`。
- 独立Reviewer发现弯引号漏查，已修复并增加`at arm’s length`等价断言；最终`./mvnw.cmd package -Dtest=DictionaryLexicalTest`3项测试与打包结果见`phonetic-phrase-package.log`。后续最终浏览器与真实运行结果在交接段补录，不以旧服务验证新接口。

## 最终运行交接

完整Playwright退出0：184通过、6项真实账号/邮件夹具条件跳过，日志`.tool-cache/phonetic-phrase-full-browser.log`。弯引号修复后最终3项词汇单元与package退出0；Reviewer只读复核确认已解决，限定范围无剩余实质发现。

8082候选真实接口验证匿名短语命中、resources音标/补充署名与collection=null后，按原JAR路径核验切换8080。最终包`.tool-cache/phonetic-phrase/english-hero-reading.jar`，SHA256 `CA0A36743B53E6EAF50F06FE305CB2E6CD70D85397D8D4EA0EF3FC2C466E4D94`。

`.tool-cache/check-phonetic-phrase-live.cjs`退出0，真实1440x1000和320x844浏览器通过resources音标/署名、原生选中in advance返回中文、来源说明、Escape、未收录短语200 found=false；零游客阅读/词汇写入、横向溢出及pageerror。实际查看320px截图确认弹窗及说明可见。真实账号收藏本轮不写入，用户人工语言/视觉验收仍待反馈。

临时8082按新JAR路径及端口参数核验后关闭，测试Redis6381按项目所属核验后关闭；正式8080、4174、开发Redis6380保留。未提交或清理无关既有修改。

## 能力边界

这是已收录表达的离线词典查询，不是任意句子的机器翻译。23,591条表达来自此前已确认的有限推荐索引，不等于ECDICT全部短语。缺失音标只补来源已有数据，不自行猜发音；公共词库与私人收藏保持分离。ECDICT授权仍按已有本地开发边界，不视为所有上游逐条生产授权完成。
