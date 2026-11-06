# 每日名言原型 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox syntax for tracking.

**Goal:** 按用户浏览器批注增强现有名言区域，不修改其他模块结构和布局。
**Architecture:** 独立 HTML 内使用本地名言数据和纯浏览器交互。按 Asia/Shanghai 日期稳定选择，数据提供器以后可接 Java API；本轮不接真实后端。
**Tech Stack:** 原生 HTML/CSS/JavaScript、Web Speech、Clipboard、localStorage、Playwright。
**Spec:** 用户 2026-10-13 对 blockquote.hero-quote 的浏览器批注（本文件保留全部实施约束）。

## Global Constraints
- 保留黑白灰与 #c0324f；Georgia 大字号英文、低调作者及作品链接、柔和渐变与充足留白，样式只作用于名言区域。
- 六个模块仍不可跳转，“每日一篇”卡不恢复；登录、主要导航及其他模块不变。
- 名言对象存储 id / english / chinese / author / source；只用已在原著核对的引文，标明台词出处。
- 日历以 Asia/Shanghai 日期稳定选句，刷新不换；跨午夜或恢复标签页更新日期。
- 收藏本机保存并按演示账号区分；存储不可用时说明仅本次有效，不假报持久保存。
- 朗读仅点击后启动；关闭、退出或离开首页停止；设备不可用有明确反馈。
- 复制失败提供手动复制弹窗；划词用覆盖当前名言词汇的本地词典，未收录片段不捏造结果。
- 实际产物仍为单文件 docs/prototypes/lingua-loop-preview.html，可 file URL 独立打开。
- Java 预留 GET /api/v1/quotes/daily?date=YYYY-MM-DD&timezone=Asia%2FShanghai，响应 date/timezone/quote；本轮默认本地提供器，绝不暗中请求未实现接口。

## Task 1：数据、版式与交互
Files: 修改独立 HTML；新增 frontend/playwright.prototype.config.ts、frontend/tests/prototype/daily-quote.spec.ts；记录来源与接口于 docs/prototypes/daily-quote-contract.md。
Interfaces: selectLocalDailyQuote(dateKey): Quote；getDailyQuote(dateKey, signal?): Promise<Quote>；Quote.source = { title, url, speaker }。
- [x] 核对 Gutenberg 公版原著原文、作者和作品，记录实际文本 URL。
- [x] 写失败浏览器测试：UI 及字段、刷新与北京时间午夜、翻译开关、收藏持续/取消/账号隔离、复制成功/降级、朗读/停止/不支持、划词/清空/关闭、禁用存储与坏 JSON。
- [x] 运行 pnpm --dir frontend exec playwright test --config=playwright.prototype.config.ts，确认缺少功能失败。
- [x] 实现上述提供器及限定于名言区的 CSS、语义 DOM、事件和本地数据。
- [x] 运行同一套测试与已有原型登录/导航检查，检查 1440 / 582 / 390px 和 reduced-motion，保留真实功能限制。

## Review Focus
1. UTC/北京时间日期不同，系统时间后退或跨午夜：稳定日期键且不被旧请求覆盖。
2. file URL 权限及 localStorage/Clipboard 失败：不报虚假成功，仍可浏览与手动复制。
3. 朗读的取消事件晚到：不能覆盖新朗读状态或离开首页继续朗读。
4. 选择跨出名言段、标点和未收录词组：只在名言内显示，明示未收录，不修改阅读模块浮层。
5. 原型原有脚本与本轮代码共存：其他模块 DOM 不变，局部选择器不污染全局。
