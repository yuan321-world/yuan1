# 真实可转载英文文章首批导入包

用户批准首批6篇，并优先选择允许转载使用的原文。交付文件 content/reading/reusable-20261030/articles.import.json 和 sources.manifest.json、6份纯正文txt、README中文导读/操作说明。NASA2篇科技，NIH News in Health4篇学习/生活。全批35000字节左右，实际166—1172英文词；B2为编辑估计，不把长原文硬裁为固定篇幅或冒充商业外刊。

## 来源与处理

NASA官网原文/视频文字稿按官方教育性事实用途使用指南整理，NASA署名与来源链接保留，不包含NASA标志、照片或第三方图像。NIH News in Health官方说明明确鼓励转载，署名并请求公开转载后寄送副本；本次没有公开发布、没有发送邮件。日期和政策链接逐篇记录，不声称网站上所有素材都可随意转载。中文标题/摘要/精读提示明确是Lingua Loop整理，不是原刊官方中文。

NIH普通Python下载返回403，使用可访问的官方网页检索文本；网站词汇解释浮层不是正文，genes/inflammation的浮层释义剔除并恢复术语连续性，Social Bonds断开的同一引语段合并；标题的异常空白清理。NASA的导航、重复嵌套段落、视频结束标记/播放列表和末尾下载链接不纳入正文。保留英文原句及NIH Wise Choices，不包含参考列表和网页杂项。源抓取留在 .tool-cache/reusable-reading，派生每篇正文SHA256在manifest。

## 验证

Java实际21.0.12.1。Maven离线运行ArticleRulesTest + ArticleImportIT/ReusableReadingImportIT：2单元、3真实专用MySQL/Redis集成通过，0失败/跳过、exit0。新增用真实六篇JSON经ADMINHTTP只读validate，confirm创建6DRAFT且原已发布数量不变，逐篇标题/URL/来源/字数有效，同clientImportId重试不重复。测试用户和导入数据由既有夹具清理，未访问其他项目库。

另检查6份txt与JSON正文逐字一致、SHA256相符、所有文本字段/请求体在既有界限内。日志 .tool-cache/reusable-reading/import-it.log。没有应用代码/前端变化，不需要本轮前端构建，也没有付费AI或翻译请求。

## 运行库状态和阻塞

**尚未导入运行中的 english_hero_dev，也没有发布新文章。** 不能把专用测试库导入成功描述为运行库已导入。

内置浏览器创建返回Browser is not available: iab；随后Windows浏览器控制报告无法可靠确认当前URL并停止。遵守停止UI输入，不尝试绕过保护、导出Cookie/Token、获取密码或直接SQL插入业务内容。源文件准备/测试已完成，后续通过已登录的正常管理员文章导入入口执行。README有4步导入方法及重复选择文件防重说明。打开说明文件到Codex面板的请求返回queued，未声称用户已看到。

## 独立只读复核

Reviewer核对六篇来源、纯正文、JSON和规范化SHA256一致，NASA嵌套重复段排除、NIH术语提示清理与断段恢复无误，未发现漏段/重复/残留；官方NIH/NASA转载条件与记录一致。没有运行测试或修改文件，结论不冒充完整人工法律或语言审校。
