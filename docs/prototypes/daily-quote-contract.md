# 每日名言原型：数据与后续接口

更新日期：2026-10-14。范围仅为独立 HTML，尚待用户视觉确认；不进入 Vue 生产构建，不代表 Java API 已实现。

## 数据与来源
每条记录包含 id、english、chinese、author，以及 source { title, url, speaker }。中文为本项目展示译文，作者字段是原著作者；作品人物台词通过 speaker 及来源提示明确区分，不把人物台词当作作者本人生活格言。

六条英文均核对公版原著文本（空白归一化、忽略大小写，独立摘句调整首字母和末尾标点）：
- William Shakespeare：[Hamlet](https://www.gutenberg.org/cache/epub/1524/pg1524.txt)，I.3 Polonius “This above all: to thine own self be true;”；II.2 Hamlet “There is nothing either good or bad but thinking makes it so.”；II.2 Polonius “Brevity is the soul of wit.”；IV.5 Ophelia “We know what we are, but know not what we may be.”
- Jane Austen：[Pride and Prejudice](https://www.gutenberg.org/cache/epub/1342/pg1342.txt)，第36章 Elizabeth Bennet “Till this moment, I never knew myself.”
- Arthur Conan Doyle：[The Adventures of Sherlock Holmes](https://www.gutenberg.org/cache/epub/1661/pg1661.txt)，A Scandal in Bohemia，Sherlock Holmes “It is a capital mistake to theorise before one has data.” 保留原著英式拼写 theorise。

原著下载及逐句校验命令退出码 0。原型仅内置上述摘句，不运行时下载来源。

## 日期和本地交互
日期键由 Asia/Shanghai 日历生成；UTC 日历天数对数组长度取模选句，同日刷新固定。每分钟和页面恢复可见时检查日期；旧请求或系统时间回退不能覆盖当前日期。
中文开关保留文字高度，避免后续模块移动；收藏完整记录按演示账号保存在 localStorage。损坏数据忽略，存储被禁止时退回本次内存并明确提示。演示账号分键不是服务端权限隔离。
朗读依赖浏览器和系统英语语音，仅点击启动，离开首页即停止。自动化使用语音 API 替身验证调用与停止，未验证每台设备真实出声。
复制优先 Clipboard，随后浏览器兼容复制，再提供手动复制弹窗。过期复制操作不在其他页面显示弹窗。
划词为本地词典，覆盖内置名言词汇及部分词组，支持整句；未收录片段明确提示。未来新增句子需补词典或提供真实翻译接口。

## Java 接口预留（未实现）
GET /api/v1/quotes/daily?date=2026-10-14&timezone=Asia%2FShanghai

建议响应：
```json
{
  "date": "2026-10-14",
  "timezone": "Asia/Shanghai",
  "quote": {
    "id": "pride-self",
    "english": "Till this moment, I never knew myself.",
    "chinese": "直到此刻，我才真正认识自己。",
    "author": "Jane Austen",
    "source": {
      "title": "Pride and Prejudice · Ch.36",
      "url": "https://www.gutenberg.org/cache/epub/1342/pg1342.txt",
      "speaker": "Elizabeth Bennet"
    }
  }
}
```
默认本地提供器不请求后台。模块导出 window.LinguaLoopDailyQuotes 的 getDateKey、selectLocalDailyQuote、createJavaQuoteProvider；可在模块初始化前注入 window.LinguaLoopQuoteProvider，形状为 getDailyQuote(dateKey, signal): Promise<Quote>。以后在 HTTP 同源环境连接 /api/v1，校验日期、时区和数据字段；失败显示本地句并提示。file URL 原型不直接接 Java 接口。

## 验证记录
验证日期：2026-10-14。原型 Chromium 自动化覆盖 1440、582、390px；检查日期刷新/午夜/回退、中文开关、收藏/坏数据/账号分键、复制成功/失败/过期回调、朗读启动停止/不支持、划词范围/未收录、减少动画、溢出和六个静态模块。
独立审查发现两项异步问题，均先以失败用例复现后修正，审查复核未发现新重大问题。
原型登录、学习页导航、管理页、退出、刷新门槛、键盘导航与表单可访问性脚本退出码 0。手机和桌面动画结束后的排版已人工核对。用户最终视觉确认仍待完成。


本轮最终验证：pnpm --dir frontend exec playwright test --config=playwright.prototype.config.ts：39 passed，退出码 0；pnpm --dir frontend typecheck：退出码 0。旧登录/导航、键盘与可访问性检查退出码 0。以上为独立 HTML 自动化与类型检查，不代表真实后端联调或用户视觉验收。
