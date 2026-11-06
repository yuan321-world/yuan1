# 阶段2：阅读、查词与笔记本

## 当前运行：参考译文与阶段2集中收尾（2026-10-17）

当前8080使用固定包.tool-cache/reading-translations/english-hero-reading.jar，工作目录backend，启动参数--english-hero.bootstrap.enabled=false --english-hero.dictionary.ecdict.enabled=true。4174/6380保持，临时8082和测试6381已关闭。无需修改本地账号、邮箱、数据库配置；10篇60段参考译文随包备好，仍只有管理员已发布文章可公开查看，不自动发布草稿。验收与明确未验事项见acceptance/2026-10-17-phase-2-final.md。下方运行包为历史。

## 当前运行：词典第二小阶段（2026-10-17）

当前8080包为.tool-cache/dictionary-stage2/english-hero-dictionary.jar，启用本地ECDICT导入；4174/6380保持运行，临时8082/测试6381已关闭。启动命令及许可边界见本文件末尾“本地词典第二小阶段启动补充”，完整结果见acceptance/2026-10-17-dictionary-stage-2.md。下方旧运行包与词库范围均为此前交付记录。

## 精读详情第一小阶段当前运行入口

当前8080使用.tool-cache/intensive-reading/english-hero-reading-v2.jar（从backend目录启动），4174提供最新dist，开发Redis6380保持。访问http://127.0.0.1:4174/reading后打开已发布文章即可体验双栏、Aa、查词、本篇词汇及参考译文。当前两篇已发布原创均已准备6段参考译文；未开通外部翻译，不会产生付费请求。其他尚未准备译文的文章明确显示状态，不自动发布草稿。见本轮验收。


当前8080运行包已更新为 .tool-cache/reading-tabs/english-hero-reading.jar（从backend目录运行）；4174仍为原预览服务，刷新即可加载新界面。旧reading-enhancement包为此前版本。


## 2026-10-17 阅读标签修复

全部/收藏/历史切换保留足够列表区域高度，避免列表变短触发浏览器滚动位置回夹、导致精选视觉跳动。历史可使用搜索、分类、难度、篇幅及发布时间排序，默认最近阅读；原有历史筛选禁用说明已失效。游客仍需登录查看私人数据，控件启用不代表放宽接口权限。


## 当前交付：阅读增量优化

日常前端 http://127.0.0.1:4174/reading ，后端8080，开发Redis6380。当前后端使用已忽略的固定运行包 .tool-cache/reading-enhancement/english-hero-reading.jar，从 backend 工作目录启动；测试重新打包不会覆盖运行包。迁移已至V10（下文V9为此前记录）。

游客可筛选、阅读当前公开正文并基础查词，不写私人进度；收藏/历史/保存生词使用原认证弹窗。登录后可继续收藏，阅读复用已有进度、完成按钮与旧版恢复。历史标签按最近阅读排序，搜索筛选在历史中禁用。字号与行距可调整。精选按上海日期从已发布集合稳定选择，独立于列表筛选；已发布集合变化可能影响当天推荐。

本轮只读检查时开发库有2篇已发布文章，其余草稿由管理员自行审阅发布，没有为填充界面自动发布。基础词典仍为本地有限词库，不包含外部翻译或AI服务；游客登录后保存生词需重新选词。自动验证与人工视觉/内容确认分开记录，详见本轮验收。


阶段2沿用原登录、管理员账号、MySQL与JWT + Redis配置。管理员登录进入`/admin`，学习者进入首页；无需新建数据库、重装项目或填写AI密钥。

## 启动

先按[认证配置](auth-jwt-redis-setup.md)启动开发专用Redis6380，保持现有QQ邮件配置文件。PowerShell7下在项目根目录执行：

```powershell
. ./scripts/use-workspace-tools.ps1
. ./scripts/use-java21.ps1
Push-Location backend
./mvnw.cmd -version
./mvnw.cmd package -DskipTests
# 从backend工作目录运行；不要在服务使用target JAR时重新打包。
java -jar target/english-hero-0.0.1.jar
Pop-Location
```

另一个终端启动前端开发服务器：

```powershell
. ./scripts/use-workspace-tools.ps1
pnpm --dir frontend dev --host 127.0.0.1
```

已有4174预览可继续使用。自动测试和重新打包时，建议将JAR复制到已忽略的固定运行目录后再启动，避免Windows文件锁。数据库迁移自动按V1—V9顺序验证并执行；保留已有数据，不修改历史迁移、不清库。DDL不能承诺事务回滚，发生故障先核对真实迁移状态。

## 首批内容

管理员打开“文章管理”→“批量导入草稿”，选择[原创文章JSON](../content/reading/original-b1-b2.v1.json)→“校验文件”→“确认导入”。每批最多20篇、1MiB；网络失败可直接重试，同一次文件选择保留请求ID。重新选择文件表示新导入，可能产生另一批草稿，不是全局按标题去重。

导入后逐篇查看分句、标题、来源和编辑难度，再点“发布草稿”。已发布文章保存了新草稿时，可预览并“发布新草稿”，无需先下架；下架会阻止新的开始阅读。没有已发布文章时，学习者列表和今日推荐保持真实空状态。

[内容说明与待审事项](../content/README.md)包含10篇文章、319词词典、重点词清单及版本维护方式。相邻manifest用于审阅，不作为业务导入文件。词典随JAR加载，文章不会随迁移自动发布。

## 学习流程

登录后从“读今天的文章”或“外刊精读”进入；今日推荐按上海日期和当前发布集合稳定选择。进入文章后服务端创建本人当前版本的记录。位置节流保存，只有响应成功才提示已保存；失败可重试，其他页面更新冲突需选择重载。读完需明确点击“标记为已读”，不靠滚动到底判定。

桌面点击单词查词；手机原生选中一个完整词后点“查询选中单词”。只查本地已收录词；未命中不编造释义。收藏必须另点“收藏生词”；已有词可“记录此处来源”，取消后可恢复。笔记本展示原文与版本链接，取消收藏需要确认，只软取消、不删原文来源。

阅读历史始终恢复本人原先版本。新版发布或下架后，已有本人记录仍可继续；其他用户不能通过复制旧版链接取得权限。管理员只通过后台预览草稿，不使用角色开关模拟学习者。

## 验证边界

按README运行后端`verify -Pmysql-it`、前端unit/typecheck/build与Playwright。真实测试只用english_hero_test、6381及loopback SMTP2525/2526；故障测试会短暂关闭并重启测试Redis，Windows需允许脚本核对项目进程，不能关闭沙箱保护绕过。真实配置、Token与验证码不出现在命令行输出或报告。

自动验证、独立代码评审、内容人工校对和用户视觉确认分列在[验收记录](acceptance/phase-2.md)。后续复习/考试/AI不在本阶段实现。
# 2026-10-17 本地词典第二小阶段启动补充

8080当前固定包为.tool-cache/dictionary-stage2/english-hero-dictionary.jar，从backend目录运行：

    java -jar ../.tool-cache/dictionary-stage2/english-hero-dictionary.jar --english-hero.bootstrap.enabled=false --english-hero.dictionary.ecdict.enabled=true

这是用户确认的本地开发导入开关，默认关闭，不自动用于生产。启动会幂等补充ECDICT离线子集，已有词条不覆盖；关闭开关不删除已导入条目。无需新数据库表、API密钥或收费服务。前端仍4174、开发Redis6380，临时8082与测试6381关闭。词典来源/限制见content/dictionary/ecdict-local/NOTICE.md。下方其他运行包为历史交付。

