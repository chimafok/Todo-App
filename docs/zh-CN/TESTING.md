# 验证与回归

<p align="center">語言 / 语言 / Languages：<a href="../TESTING.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/TESTING.md">English</a></p>

本页区分正式版、开发阶段与历史结果；不同轮不合并成一次全套回归。完整初始失败／补验来源保留于共用 verification JSON 及[历史纪录](../history/TESTING-HISTORY.zh-Hant.md)。本次文件翻译不代表重新执行以下 App 测试。

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
- 涵盖切换与重建／草稿保存、自订内容与时区不变、通知文字、格式 6 往返／设置冲突、旧档语言保留、无效语言拒绝、仅语言还原不重设保留锚点。API 26 另人工检查三语设置页。

这份测试 APK 已由维护者确认并于 2026-11-03 发布为正式 1.4.0，不再标为「待确认／目前正式版 1.3.1」。完整初始失败与补验见 [development-1.4.0.json](../verification/development-1.4.0.json)。

## 尚未完成

未全面完成独立母语审校、真机、隔夜／长背景、TalkBack／200% 字号／RTL、全套历史及大量数据效能。历史效能不佳的量测仍有效，见[已知限制](KNOWN-ISSUES.md)。使用者未反馈的手机案例不得推定通过。

## 执行与记录

执行命令见[开发指南](DEVELOPMENT.md)：testQaUnitTest、适当变体的建置／Lint，及只针对自有／已授权装置 `.qa` 的 connectedQaAndroidTest。每轮记录起点，检查新 XML 类别、案例数大于零、无 failure／error／skipped，且时间不早于起点。没有新结果不能沿用旧档；初始失败及补验分开保存。选测不可写成全套，CI 编译装置测试 APK 不等于执行装置案例。

展示画面只用隔离 QA 的虚构数据；不操作私人手机或日用 App。App 改动须先交付可识别来源／杂凑的开发签章测试 APK，明确确认后才正式签署／合并 main／发布；流程见[贡献指南](CONTRIBUTING.md#test-apks-and-release-approval)。纯文件修改不需产生 APK。
