# 阶段 0 验证记录

日期：2026-10-13（Asia/Shanghai）
状态：**工程基础已实现，阶段 0 尚未完成**。数据库联调和用户页面确认仍待完成。

## 当前交付

Java 21 / Boot 4.0.8 / Maven Wrapper 3.9.9、Vue 官方工程及精确依赖锁、系统状态接口、
MySQL V1 迁移、最小访问边界、请求超时/取消/重试、history 路由和手机空布局已实现。
本地分支为 phase-0-foundation，未创建远程仓库、合并或发布。

[优化后的独立 HTML 原型](../prototypes/lingua-loop-preview.html) 直接基于原始文件，保留完整页面与演示交互，
先登录再首页；原始文件保持不变。它位于独立文档目录，不随 Vue 正式构建发布。
当前 Vue 空布局需在用户确认上述原型后调整，不是最终视觉验收结果。

## 实际验证

| 检查 | 命令 / 方法 | 结果 |
| --- | --- | --- |
| Java / javac / Maven | use-java21.ps1 后查询实际版本 | 均使用 Java 21.0.12.1 |
| Maven Wrapper | backend 中 .\\mvnw.cmd -version | 退出 0，Maven 3.9.9 / Java 21 |
| 前端冻结安装 | pnpm --dir frontend install --frozen-lockfile | 退出 0 |
| 前端类型检查 | pnpm --dir frontend typecheck | 退出 0 |
| 前端单元测试 | pnpm --dir frontend test:unit | 退出 0，15/15 |
| 前端构建 | pnpm --dir frontend build | 退出 0 |
| Vite 浏览器验证 | pnpm --dir frontend test:e2e | 退出 0，12 通过，2 个真实数据库用例明确跳过 |
| preview 默认端口 | E2E_PREVIEW=1，test:e2e | 退出 1，4173 已被现有服务占用；未停止该服务 |
| 构建产物浏览器验证 | 临时测试配置以 pnpm preview --port 4174 启动同一产物 | 退出 0，12 通过、2 个真实用例跳过；临时配置已删除 |
| 后端单元 / MVC | backend 中 .\\mvnw.cmd test | 退出 0，8/8 |
| MySQL 集成测试 | .\\mvnw.cmd verify -Pmysql-it | 退出 1，缺 config/application-test.yml，连接前失败，未迁移数据库 |
| 独立 HTML | Chromium file URL，1440x1000 / 390x844 | 登录前首页隐藏，账号按钮只填入，提交后进入对应端；主要导航、退出、刷新、无溢出通过 |
| 原型键盘 / 样式 | 手机 Enter/Tab/Escape，review-note computed style | 通过，关闭菜单恢复焦点，学习提示 position=static |
| 原型表单无障碍 | 空提交后切换登录/注册，检查 aria-invalid / skip target | 首次失败，修正后通过 |
| 原型隔离 | 检查 public / dist 及演示标识 | public 仅 favicon；dist 不含独立原型或其演示脚本 |

浏览器常规状态验证使用网络拦截：正常 200、失败 503 后重试、拒绝 200 HTML 回退，不能替代真实 MySQL 联调。
导航测试允许后端未启动，因此代理 ECONNREFUSED 8080 日志属于已覆盖的不可用状态。
Vitest 有非阻断的配置扩展名警告，未来 Vite native loader 默认值变更前需调整；测试与构建均退出 0。
启动和 Wrapper 下载从含空格的实际目录执行，中文用户路径可运行；Wrapper 用户目录在终端有编码显示问题。

## 独立审查与修正

Reviewer 审查后端、迁移、Java 脚本、原型差异和请求层，未发现其他 Critical / Important 缺陷。
以下问题均先复现失败再修正通过：

- HTTP 200 但 DTO 为 DOWN 时 Badge 不再误报连接正常。
- 预先取消的 AbortSignal 不再产生未处理 Promise 拒绝。
- 手机悬停词汇弹层不再遮挡菜单。
- 切换登录/注册重置 aria-invalid，跳转链接指向当前可见表单。

未判断事项：真实 MySQL/Swagger 运行结果（缺凭据）、用户视觉批准、继承的演示业务行为、未批准的 Vue 美学。
这些是明确的验收边界，不推定成功。原型只用于界面确认，不是服务端认证实现。
Boot 4 Security 测试模块依据：[官方迁移说明](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)。

## 未完成项

1. 在本地创建专用新库及账号、填写被忽略提交的开发和测试配置。
2. 核对 MySQL 服务端版本，验证首次/二次迁移、后端首次/二次启动。
3. 验证真实状态 API、OpenAPI 和 Swagger UI，运行 E2E_REAL_BACKEND=1。
4. 用户亲自确认独立 HTML，再按确认结果调整正式 Vue 页面。
5. 4173 的服务冲突解除后，按 README 在普通终端复验默认 preview 命令。临时 4174 已验证构建产物的路由刷新。

真实认证及数据库会话属于阶段 1。原型门槛和演示角色不代表真实账号/权限已实现。
