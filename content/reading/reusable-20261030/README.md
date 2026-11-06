# 可转载英文精读首批：6篇导入包

状态：**文件已准备，真实测试库导入已验证；尚未导入运行中的开发库，未发布。**

正文为NASA/NIH真实英文，不是AI改写冒充外刊。中文标题、摘要和下方精读提示为Lingua Loop整理；B2为编辑估计。仅收录文字，不包含照片、标志或第三方配图。

## 导入方法

1. 登录项目管理员账号，打开“文章管理”。
2. 展开“批量导入草稿”，选择同目录 `articles.import.json`。
3. 点击“校验文件”，应显示6篇通过，再点击“确认导入6篇草稿”。
4. 保持草稿，逐篇预览、校对后再决定发布。重复导入前先查标题，避免用户重复选择文件产生新批次。

现有正式导入接口使用管理员鉴权、CSRF及批次幂等；没有为本批新增权限或绕过登录。分类沿用现有1学习、2生活、3科技。

## 来源及使用条件

- [NASA官方使用指南](https://www.nasa.gov/nasa-brand-center/images-and-media/)：本批按教育性、事实性使用条件保留NASA署名，不暗示其背书，不包含图片或第三方标注素材。
- [NIH News in Health转载说明](https://newsinhealth.nih.gov/about-us)：明确鼓励在社交媒体、博客和印刷/数字出版物转载文章，要求署名，并请求提供刊物副本。**本次未对外发布、未发送邮件。正式公开转载后需按其要求提供副本。照片不在本次范围。**

完整日期、正文SHA256、字数与政策链接见 `sources.manifest.json`。来源页的内嵌词汇提示和导航不属于正文；NIH的genes、inflammation词汇提示恢复为原术语；小标题仅做空白清理。英文句子不改写。NIH含Wise Choices侧栏，不含后续参考链接/参考文献；NASA不含外部下载入口。

## 文章与中文导读

### 1. 为什么在太空种植物？

英文：**Why Do We Grow Plants in Space?**  
来源：NASA · 2025-04-15 · 166词 · B2（编辑估计）  
[原文](https://www.nasa.gov/general/why-do-we-grow-plants-in-space/)

NASA专家解释太空植物研究如何支持航天任务，并改善地球上的农业技术。

观察文章如何从太空任务的直接用途，过渡到对地球农业的间接帮助。重点表达：versatile organisms、sustainable environment、adapt to、thrive in。

### 2. 韦布望远镜揭示深远宇宙

英文：**NASA’s Webb Delivers Deepest Infrared Image of Universe Yet**  
来源：NASA · 2022-07-12 · 750词 · B2（编辑估计）  
[原文](https://science.nasa.gov/missions/webb/nasas-webb-delivers-deepest-infrared-image-of-universe-yet/)

通过真实科学报道，了解红外观测、引力透镜和遥远星系；专业术语较多。

先识别观测对象、观测工具和研究目的，再阅读术语。重点表达：gravitational lens、at arm's length、wavelengths、spectra。

### 3. 睡一觉，让记忆更牢固

英文：**Sleep On It**  
来源：NIH News in Health · 2013-04 · 699词 · B2（编辑估计）  
[原文](https://newsinhealth.nih.gov/2013/04/sleep-it)

阅读睡眠与学习记忆的科普文章，练习区分研究发现、专家解释与建议。

区分记忆形成、稳定和关联三个过程，留意专家引语与研究结论的语气。重点表达：sleep on it、strengthen memories、fragile、REM。

### 4. 认识阅读障碍

英文：**Decoding Dyslexia**  
来源：NIH News in Health · 2024-02 · 648词 · B2（编辑估计）  
[原文](https://newsinhealth.nih.gov/2024/02/decoding-dyslexia)

了解阅读能力形成与阅读障碍支持，练习理解解释性文章和条件表达。

按定义、形成机制、识别与支持的结构阅读，不把文中的美国学校制度说明当作其他地区制度。重点表达：phonemic awareness、early intervention、reading comprehension。

### 5. 如何养成健康习惯

英文：**Creating Healthy Habits**  
来源：NIH News in Health · 2018-03 · 1111词 · B2（编辑估计）  
[原文](https://newsinhealth.nih.gov/2018/03/creating-healthy-habits)

从识别行为模式、制订计划到记录进展，练习阅读围绕问题与策略展开的文章。

用Know Your Habits、Make a Plan、Stay on Track等小标题梳理策略。重点表达：triggers、stay on track、self-control、delay discounting。

### 6. 社会联结与健康

英文：**Build Social Bonds to Protect Health**  
来源：NIH News in Health · 2025-03 · 1172词 · B2（编辑估计）  
[原文](https://newsinhealth.nih.gov/2025/03/build-social-bonds-protect-health)

了解社会关系与健康研究，练习区分相关性、风险因素和专家建议。

区分social isolation与loneliness，以及研究相关性与因果断言。重点表达：social bonds、generativity、perceived responsiveness。

## 验证与限制

真实专用测试库经标准管理员HTTP接口校验、确认6篇全部为DRAFT，重复同批次不新增，已发布数量保持；临时测试数据由既有夹具清理。运行中的开发库没有新增文章或用户。此文件不是“已经上线”的证明。

当前自动操作的阻塞：内置浏览器提供者不可用；Windows浏览器控制因无法可靠确认当前网址而停止。本轮停止UI输入，没有读取凭据、伪造管理员、关闭保护或直接SQL插入运行库。文件准备与测试不受影响。

文章是原刊旧文，日期明确保留，不标成最新新闻；本批学习导读不替代原文机构的专业建议。未新增付费翻译或AI调用。
