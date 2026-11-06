# 双口音发音控件统一

用户要求所有“英/美”类发音按钮改成参考图UK /音标/ 扬声器与US /音标/ 扬声器。

新增shared PronunciationControls，仅负责展示和发出en-GB/en-US事件，五个既有父组件保留原发音实现、错误处理、取消与朗读生命周期。范围：LearningCard、WordNotebook、WordSearchDialog的单词和词组、PhraseBook、WordLookupPanel（阅读及练习共用）。无边框小按钮，自动换行，保留键盘焦点与原可访问标签。词库当前只有通用phonetic字段，两处沿用，不伪称分别采集英美音标；title注明合成语音/通用标注，缺项不补造。

验证：新生词详情断言先在旧界面缺UK而双端失败；实施后完整51文件203项单元通过（含新增3项音标格式/缺项/locale事件验证），构建与类型检查退出0。20项桌面/手机定向Mock通过，覆盖生词弹窗格式、词组备注保存、查词收藏、学习卡UK/US合成locale和阅读朗读取消/过时错误处理。仅对新增测试的DOM空白断言作结构化调整，实际CSS gap保留视觉间距。

4174已加载新dist，实际当前账号banana弹窗展示UK/US各带/bә'nɑ:nә/与扬声器，参考截图位于忽略目录.tool-cache/pronunciation-unified-live.jpg。未代用户点击朗读或写入收藏/学习数据；口音动作由自动化合成语音Mock验证，不将其声称为本机真实口音试听。未修改后端、API格式或数据库。用户视觉确认待体验。
