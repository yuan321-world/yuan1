# 查词朗读错误提示修复（2026-10-31）

用户反馈有声音却显示“朗读暂不可用”。代码根因：切换英美音/重复点击会cancel上一utterance，所有onerror原本一律写不可用；旧utterance的迟到onerror/onend也能覆盖当前状态。SpeechSynthesis的canceled/interrupted属于取消事件，参考官方开发文档 https://developer.mozilla.org/en-US/docs/Web/API/SpeechSynthesisErrorEvent/error 。

修复仅WordLookupPanel：统一stopSpeech先递增generation再cancel（覆盖同步回调）；onstart/onend/onerror均核对当前轮次；取消事件不误报；词条/选中文字变化和卸载使旧回调失效。当前真实错误、API不支持和同步异常仍显示可操作错误，重试/成功启动清除旧提示。发音语言参数、词义/例句/近义词/短语/收藏/翻译保留。

新增5项组件单元（参数化取消2例）：旧代码4失败/1通过，修复后通过。完整41文件167单元exit0；类型/构建exit0；reading-detail-upgrade + practice-selection桌面1440/手机320共68项Mock界面exit0。新UI回归模拟同步interrupted、异步旧synthesis-failed、连续英美切换及真实audio-hardware错误/重试；原音频口音参数测试保留。首次类型构建发现测试SFC导入缺类型声明，按项目既有测试模式注明由vue-tsc单独检查SFC后通过，不关闭类型检查。日志 .tool-cache/speech-error-{red,unit,build,ui}.log。

4174已读取最新dist，后端健康service=english-hero/database=UP；没有后端、配置、数据库变更，不需要服务重启，无百度/DeepSeek调用。测试模拟浏览器事件，不声称代替用户设备实际扬声器/语音引擎验证。用户已报告能听到声音，本轮修复错误状态判断。
