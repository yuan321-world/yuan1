# 四级整卷正式接入验收

实施2026-10-22开始，2026-10-23完成本地部署与收尾。用户已确认布局、书面设计、实施计划并“确认实现”。本轮主代理直接实施，一次独立最终审查；不提交、不安装依赖、不进入六级或AI批改。

## 交付

- 管理端整卷列表、新建/编辑、七槽已发布版本选择、保存、预览组成及参考、确认组合来源、发布新版本、下架、发布版本记录。修改草稿撤销组合确认，旧发布版不变。
- 四级整卷目录，类型与实际内容年月筛选（年月默认折叠）、全部试卷/我的练习、分页；同路径查询保留滚动，加载保留旧结果。六级明确未开放。
- 本人整卷开始/恢复、七部分统一服务器草稿、约900ms合并串行保存、最终快照事务交卷、缺答检查及确认。听力完整音频、同片段多题，切部分保持audio实例；阅读选词/匹配/仔细阅读复用既有播放器与校验。
- 完成后只读：听力与阅读参考判分、解析及原文；写作/翻译文本、词数与参考。不算考试标准总分，不插入单项历史/错题或阅读打卡。再次练习新建轮次，不覆盖完成记录。
- 新增V26与六表，test/dev当前均V26，未改写此前成功迁移。鉴权、本人归属、CSRF、乐观锁、幂等与跨组件题ID检查在服务端。

## 实际验证

缓存证据：`.tool-cache/practice-paper-implementation/`，不含聊天/终端输出的密码、Cookie或Token。测试凭据在进程内存传递，未写入内容包。

| 检查 | 命令/日志 | 结果与边界 |
| --- | --- | --- |
| 实际工具 | workspace tools + `mvnw -v` | Java21.0.12.1、Maven3.9.9，非仅检查安装目录 |
| 后端相关真实库 | `mvnw -f backend/pom.xml -o '-Dtest=PracticePaperRulesTest,PracticePaperIT,PracticeSubjectiveIT,PracticeClozeIT,PracticeMatchIT,PracticeListeningIT,SchemaMigrationIT' '-Dpractice.paper.browser=true' test` / regression.log | 16 tests，0失败/错误/跳过，退出0；english_hero_test/6381；53表，重复migrate执行0次 |
| 整卷真实浏览器 | 上述PracticePaperIT触发`frontend/tests/real/practice-paper.cjs` / real-results.json | 普通USER在1440/320px目录开始、作文/译文/客观选择保存、刷新恢复、完整音频票据及实例保持、确认交卷、参考、历史通过；无页面异常/横向溢出。仅测试库合成夹具，不是开发用户成绩 |
| 保存状态单元 | `pnpm --dir frontend test:unit tests/unit/practice-paper.spec.ts tests/unit/practice-subjective.spec.ts` / conflict-green.log | 修复后11项退出0，覆盖串行保存、超时同命令重试、切账号清除和本地恢复副本 |
| 相关Mock浏览器 | `test:e2e practice-paper.spec.ts practice-listening.spec.ts practice-subjective.spec.ts practice-catalog-list.spec.ts --workers=1` / ui-regression.log | 28项退出0；仅Mock，不代替真实库验证 |
| 最终整卷Mock复验 | `test:e2e tests/e2e/practice-paper.spec.ts --workers=1` / ui-reviewed.log | 最终store修复与构建后6项退出0，含实际分页回归 |
| 最终前端构建 | `pnpm --dir frontend build` / build-reviewed.log | 类型与生产构建退出0，4174预览最终dist |
| 后端打包 | `mvnw -f backend/pom.xml -o '-DskipTests' package` / package.log | 退出0；打包不算再次执行测试，测试证据见上述16项 |
| 开发部署核对 | dev-check.py / dev-check-result.json | 原10组完整管理响应、发布版本、阅读目录和四个单项目录与部署前基线相同；新整卷仅草稿；0开发学员练习写入 |
| 实际开发界面 | dev-browser.cjs / dev-browser.log | 管理草稿与七组成预览、未校对发布按钮禁用、匿名用户目录不泄漏草稿，1440/320px均无溢出/异常，0学员练习写请求。已看桌面管理与手机真实测试截图，仍不代替用户视觉验收 |

后端整卷集成包含：ADMIN/USER/匿名界限、他人轮次/音频404、进行中参考及听力原文隐藏、旧锁冲突、非法跨组件ID拒绝且草稿不变、新版不改变已有轮次、组件下架阻止新开始、已开始仍可交卷、同UUID并发提交复用结果、不同内容复用冲突、注入完成指针失败后responses/result/status全部回滚、缺答确认与空白交卷总题数55、原单项轮次未新增。

## 已修复及限制

- 初次规则RED为缺少规则类；初次Maven未载入workspace工具导致wrapper失败，不作为RED。后续规则2项通过。
- V26最初在测试库因唯一键与外键同名失败，仅形成前三张空表。测试账号不允许DROP，未提权改权限或清库；确认专用测试库、失败V26、三表全空且无后续表后，仅移除失败历史记录，使尚未成功的V26能幂等重试，修正约束名并保留已有空表。最终成功后不再编辑V26。开发库从V25一次成功应用V26。
- 合成夹具最初CLOZE题干、仔细阅读类型不符合既有规则，修正夹具；隐私断言发现公开原文测试文本含SECRET标记，调整标记仅用于真正应隐藏的听力/参考，不放宽字段隐藏要求。
- 目录分页按钮曾清除自己的page参数，桌面/手机先RED后修复为仅其他筛选重置页码，最终6项回归通过。
- 独立审查Important：仍进行中的冲突重载会重基旧七槽快照覆盖另一页作文。已先RED后改为采用服务器快照，保留独立可复制本地副本；新增“另一页作文+本页译文”单元，确认后续显式编辑不会清空另一页作文。完成态恢复副本测试仍通过。
- Deferred minor：交卷对话框字数提示仍采用少于120词的文案，没有动态覆盖管理员建议上下限；输入区已显示实际范围，提交不受该提醒阻断。此项不影响保存、判分或原文，留待提示细化。
- 播放位置在当前页面保持，刷新允许重听，未实现跨设备音频秒级同步。没有六级、AI评分或正式机构总分。

## 本地交接

开发后端8080新JAR位于`.tool-cache/practice-paper-implementation/english-hero-paper.jar`，启动PID96236；Redis dev6380/test6381分别PID104952/69548（2026-10-23本次启动），日志backend.log/backend-error.log。前端4174预览最终dist；PID可能随重启变化，后续启动按端口和进程归属确认。

已创建整卷草稿1，来源题组8/5/3/4/2/10/9，对应写作/听力/选词/匹配/阅读两篇/翻译。原参考的非官方属性及四合院套号差异保留在组合说明。草稿DRAFT、sourceReviewed=false、revision=0，未自动校对/发布，因此普通用户整卷目录暂空是预期状态。

[管理草稿](http://127.0.0.1:4174/admin/practice/papers/1)核对组成、来源与预览后，可确认来源并发布；[正式整卷入口](http://127.0.0.1:4174/practice/cet?exam=CET4&source=PAST&collection=PAST&section=paper)。现有单项练习继续可用。没有提交、推送或对外部署。

## 2026-10-23 用户反馈入口不可见后的补修

上一轮只准备草稿，且管理侧边栏没有整卷直达入口，导致用户在两端均未找到。按本次反馈增加侧边栏“整卷管理”（专项列表旧链接保持），定向Mock桌面/手机导航先RED2项、修复后GREEN2项，最终类型/构建退出0；未改鉴权或后端代码。

核对整卷1与已保存组卷组成一致、7槽完整、55道客观题均具参考/解析、听力使用已有资产、来源与四合院套号差异/非官方参考说明保持，经现有review/publish接口发布v1。该确认仅为已发布组件的组合核对，不重新宣称真人听审或考试机构认证。公开目录返回1套可用；原10组及发布版不变，未创建开发学员成绩。证据publication-result.json。

实际匿名浏览器1440/320px均显示标题和“开始练习”，0页面异常、溢出或练习写请求，visibility.cjs退出0。原先“尚未发布/用户目录为空”的交接已被本节替代。[管理列表](http://127.0.0.1:4174/admin/practice/papers)与[用户目录](http://127.0.0.1:4174/practice/cet?exam=CET4&source=PAST&collection=PAST&section=paper)现已可直接使用。
