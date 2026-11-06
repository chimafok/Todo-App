# 验证与回归

<p align="center">語言 / 语言 / Languages：<a href="../TESTING.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/TESTING.md">English</a></p>

本页区分正式版、开发阶段与历史结果；不同轮不合并成一次全套回归。完整初始失败／补验来源保留于共用 verification JSON 及[历史记录](../history/TESTING-HISTORY.zh-Hant.md)。本次文件翻译不代表重新执行以下 App 测试。

## 1.4.1 正式发布（2026-11-06）

维护者确认 1.4.1-dev02 后，以相同 App／构建来源重新构建正式包；104 项本轮单元测试通过，Release Lint 0 errors／13 warnings，新 XML 已核对数量、类别、时间及零 failure／error／skipped。沿用 RSA4096 正式凭证；签名前后 payload 一致，正式包不可调试，不含 QA provider 或网络／广泛相册权限，附带授权文件。

专用 API37／26 模拟器完成正式 1.4.0→1.4.1 覆盖更新和启动，UID／首次安装时间保留，schema10 不变。API37 另检查虚构任务、学习进度／开始日期、改名分类及英文偏好保留；正式表单验证默认 10:00、两位小时自动换栏、分钟输入、回填、已有值重开和取消，测试表单已丢弃。开发阶段 22 项设备案例为之前分轮结果；本轮更新／表单冒烟检查不代表完整历史回归或手机验收。

见 [approval-1.4.1.json](../verification/approval-1.4.1.json)、[release-1.4.1.json](../verification/release-1.4.1.json) 和 [tests-1.4.1.json](../verification/tests-1.4.1.json)。APK SHA-256：`a647bd92a6ab58cbc395a230950b43c9a853bea8118a74b4f35a5243211e0258`。未测正式全新安装、完整手机／键盘／无障碍／语言矩阵或长后台；专用后台模拟器完成后关闭，未操作用户手机。

## 1.4.1-dev02 开发阶段（已确认，2026-11-06）

104 项本轮单元测试通过；Debug 构建及 Lint 0 errors／13 warnings。API37／API26 各分开执行 7 项焦点和表单案例、4 项布局案例（每个版本 11 项，共 22 项），新 XML 均核对案例数、类别和时间，零 failure／error／skipped。涵盖未修改的默认 10:00、已有时间、前导零、选取／光标位置及实际系统 100%／140%／200% 字号；已检查专用 QA 画面。不是完整历史回归、手机验收或完整无障碍／语言矩阵。

初始测试编译类型错误已修复；首轮选择只运行焦点类别，另分轮补跑布局类别。初版字号测试的外层密度不代表对话框内实际字号，已改为调整并恢复专用模拟器系统字号、断言对话框实际密度后复验。详见 [development-1.4.1-dev02.json](../verification/development-1.4.1-dev02.json)。测试 APK 为 .debug 开发签名、code37；已获维护者确认，并以相同 App／构建来源发布正式 1.4.1；本段保留开发阶段结果。

## 1.4.1-dev01 历史 —（2026-11-06）

104 项新单元测试通过；Debug 构建及 Lint 0 errors／13 warnings。API37／API26 各执行 7 项时间输入案例，均无失败／错误／跳过，并核对本轮 XML。涵盖两位换栏、前导零／午夜／相同初始小时、无效输入／删除、时钟共享值、取消与截止表单回填；不是完整历史回归。初轮 API37 有 2 项失败，修正焦点时序与测试重开方式后复验；数字裁切和分隔符亦经最终画面核对。详见 [development-1.4.1.json](../verification/development-1.4.1.json)。测试 APK 为 .debug 开发签名，正式目标 1.4.1，仍待维护者确认；未正式签名、未验证正式覆盖更新或手机／完整无障碍矩阵。

## 1.4.0 正式发布（2026-11-03）

| 检查 | 实际结果 |
|---|---|
| 当轮单元测试 | 104 项；新 XML 核对类别、数量、时间；零 failure／error／skipped |
| Release 建置／Lint | 建置通过；0 errors／13 warnings |
| 签章与 payload | 沿用 RSA 4096；签署前后 payload 一致 |
| 正式包 | com.chimafok.todo／code35，非可侦错，无 QA provider／网络／广泛相册权限，包含授权原文 |
| API 37／26 覆盖更新 | 正式 1.3.1→1.4.0、schema9→10，启动成功，UID／首次安装时间保留 |
| API 37 数据／语言 | 虚构任务、学习进度／开始日期、改名分类保留；三语切换及重启后偏好保存 |

APK SHA-256：`2e8532687e01d5a16d1b25ee8f715a5fabf97582ef6287ad6364f25ac29e88ce`。来源／授权见 [approval-1.4.0.json](../verification/approval-1.4.0.json)，正式摘要见 [release-1.4.0.json](../verification/release-1.4.0.json)。这是专用模拟器的正式更新冒烟验证，不是本轮全套历史回归或真机验收；本轮未测正式新安装。

## 1.4.0-dev01 开发阶段（历史）

- 104 项单元测试、建置及 Debug Lint（0 errors／13 warnings）。796 个资源键的四份完整目录及位置参数检查通过。
- API 37 分轮：语言 UI 3、语言备份／迁移 2、备份回归 19、学习 6、图片／旧格式 9，共 39 项。
- API 26 分轮：语言 UI 3、语言备份／迁移 2，共 5 项。44 项全部零 failure／error／skipped，不是一次全套历史执行。
- 涵盖切换与重建／草稿保存、自定义内容与时区不变、通知文字、格式 6 往返／设置冲突、旧档语言保留、无效语言拒绝、仅语言还原不重设保留锚点。API 26 另人工检查三语设置页。

这份测试 APK 已由维护者确认，并于 2026-11-03 发布为正式 1.4.0。开发测试结果保留为历史，完整初始失败与补验见 [development-1.4.0.json](../verification/development-1.4.0.json)。

## 尚未完成

未全面完成独立母语审校、真机、隔夜／长背景、TalkBack／200% 字号／RTL、全套历史及大量数据效能。历史效能不佳的量测仍有效，见[已知限制](KNOWN-ISSUES.md)。用户未反馈的手机案例不得推定通过。

## 执行与记录

执行命令见[开发指南](DEVELOPMENT.md)：testQaUnitTest、适当变体的建置／Lint，及只针对自有／已授权装置 `.qa` 的 connectedQaAndroidTest。每轮记录起点，检查新 XML 类别、案例数大于零、无 failure／error／skipped，且时间不早于起点。没有新结果不能沿用旧档；初始失败及补验分开保存。选测不可写成全套，CI 编译装置测试 APK 不等于执行装置案例。

展示画面只用隔离 QA 的虚构数据；不操作私人手机或日用 App。App 改动须先交付可识别来源／杂凑的开发签章测试 APK，明确确认后才正式签署／合并 main／发布；流程见[贡献指南](CONTRIBUTING.md#test-apks-and-release-approval)。纯文件修改不需产生 APK。
