# 收藏词组按钮收紧

用户指出专项练习词典的收藏按钮过大、挤入中文译文。本轮只优化共享按钮和词典搭配列表，不改收藏业务。

- PhraseSaveButton.vue：触发按钮使用独立小按钮样式，12px字/最小28px高/4px×9px内边距；悬停浅绿、焦点和禁用态保留。保存表单按钮与确认流程不缩改。
- WordLookupPanel.vue：每条搭配以两列展示，左侧英文及中文、右侧收藏；自动换行，无按钮遮住释义，已保存反馈仍显示。
- reading-detail-upgrade.spec.ts：在原有词典搭配用例补按钮高度≤32px/宽度≤100px断言及截图，无新增重复用例。

验证：原按钮44px高触发新断言失败；修改后`pnpm --dir frontend build`退出0（包含类型检查），外部4174下`pnpm --dir frontend test:e2e reading-detail-upgrade.spec.ts -g 'lexical sections|local word fragments'`退出0，桌面及320手机4项通过。日志`.tool-cache/phrase-save-compact-{red,build,e2e}.log`，双端截图`.tool-cache/phrase-save-compact-{desktop,mobile}.png`，手机截图已人工核对。

仅前端样式/布局及原有测试断言，不写用户收藏，不改接口/数据库，不代用户刷新可能存在未保存作文的当前练习页。4174构建已更新，刷新页面后可见。
