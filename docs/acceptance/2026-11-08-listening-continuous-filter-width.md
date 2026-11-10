# 听力连续题目与筛选栏宽度修复

日期：2026-11-08。用户授权：听力专练仿整卷连续展示；月份和年份展开后右侧线条及布局收缩修复。

## 实现

- PracticeListeningPlayer：专练连续渲染全部片段/题目，题干显示全局序号。桌面右侧题号定位，手机端题号可展开；保留上一/下一片段定位。全程不重建整套音频节点，点击选项不会自动滚动到下一题。
- 交卷前不展示原文/解析，交卷后保留答案解析与总览；整卷继续复用同组件。未修改后端、接口、存储或判分。
- practice-compact.css：内部筛选滚动区域隐藏占宽滚动条，不禁用滚动，月份/年份同时展开时不挤窄面板；底部清空按钮仍可到达。

## 验证

- RED：旧实现新增测试失败（连续8片段与筛选栏宽度/滚动条断言），退出1。
- `pnpm --dir frontend test:unit`：52文件、205测试通过，退出0。既有单片段断言更新为全部片段断言。
- `pnpm --dir frontend build`：类型检查及生产构建通过，退出0；仍有既有大包提示。
- E2E external server4174：practice-listening、practice-filter-width、practice-paper-layout、practice-paper-sheet、practice-filter-folding、practice-compact-ui，桌面/手机共26项通过，退出0。覆盖11年份+5月份真实溢出、展开宽度稳定、滚动到清空、无横向溢出、25题/8组、音频节点保留、选项/待检查保存、题号定位及整卷回归。
- 独立审查未发现实质回归，复跑其中10项通过。
- 日志：.tool-cache/listening-continuous-{red,green,unit,build}.log；展开截图 practice-filter-expanded.png 已检查。

本轮为Mock浏览器回归与本地构建，不等同于真实音频服务/数据库联调。4174静态dist已更新，未写真实练习数据，未提交或推送。
