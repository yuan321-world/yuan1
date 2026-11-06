# 百度阅读翻译配置

本次切换阅读页的实时选词/选段翻译为百度翻译开放平台“通用文本翻译”。语法解析与作文/译文AI批改需要生成分析内容，仍是独立大模型服务；通用翻译接口不能直接替换这些能力。

编辑已忽略Git的 `backend/config/application-translation.yml`，填写 `english-hero.reading.translation.baidu.app-id` 和 `secret-key`。模板见同目录 `application-translation.example.yml`。不要把密钥发到聊天、前端或提交Git。修改后重启后端。

```yaml
english-hero:
  reading:
    translation:
      baidu:
        app-id: "填写百度APPID"
        secret-key: "填写百度密钥"
        monthly-character-limit: 1000000
```

译文优先复用本地参考/离线短语，未命中才发送百度。在线任务显示“百度翻译”和本次源文字符数，不请求DeepSeek、不自动回退。不再占用每日1元AI金额额度。单词词典、静态参考译文、语法解析和批改入口保持。

月度配额为本项目共享的100万字符，按北京时间月份统计源文Unicode字符，包含空格标点。创建时预占，缓存不重扣，明确未发送释放；超时或结果不确定保留用量，不自动重发。跨月排队按实际开始调用的月份核算。配额满返回429；不自动发起超额付费调用。

本地计数无法读取百度账号已在其他项目消耗的免费额度。如相同APPID也给其他应用使用，应相应调低本项目monthly-character-limit，并在百度控制台查看总用量。语法解析/AI批改的既有DeepSeek每日金额保护独立保留。

资料：[通用翻译接口](https://fanyi-api.baidu.com/doc_bd/21)、[百度官方字符权益与定价](https://api.fanyi.baidu.com/product/112)。接口POST表单，签名按未编码UTF8原文计算，服务端保管凭据；前端不会直接请求百度。
