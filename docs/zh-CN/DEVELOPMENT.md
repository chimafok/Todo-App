# 开发与建置

<p align="center">語言 / 语言 / Languages：<a href="../DEVELOPMENT.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/DEVELOPMENT.md">English</a></p>

## 环境

- Android Studio，或具备 JDK 17 以上的命令列环境；本机亦以 Android Studio 的 JBR 25 建置验证。
- Gradle 9.6.0 wrapper（含下载 SHA-256）、Android Gradle Plugin 9.4.1。
- Android SDK Platform 37、Build Tools 36.0.0、Platform Tools；最低执行 API 26。
- Compose BOM 2026.09.00、Room 2.8.5、KSP 2.3.12；完整版本见 gradle/libs.versions.toml。

下载并开启专案，让 Android Studio 设置本机 SDK，或自行设置 JAVA_HOME／ANDROID_HOME。local.properties 不入 Git。首次需要连线下载 SDK、Gradle 与 Maven 依赖；App 执行本身不做云端同步。

## 分支与检查

一般功能从最新 `dev` 开工作分支，PR 合并到 `dev` 整合；交付开发签章的测试 APK 并获 chimafok 明确同意后，才正式签署、PR 合并对应源代码到 `main` 及发布；纯文件小修的例外与合并权限见[贡献指南](CONTRIBUTING.md#branches-and-merges)。CI 目前会建置、执行单元测试与 Lint，并编译装置测试 APK；不会执行装置测试、完成真机验收、正式签署或发布 APK。

## 命令

Windows PowerShell：

```powershell
.	oolsuild.ps1 -GradleTask @('assembleDebug', 'testQaUnitTest', 'lintDebug')
```

macOS／Linux：

```sh
chmod +x gradlew
./gradlew assembleDebug testQaUnitTest lintDebug
```

隔离装置测试：先启动你自己的模拟器，明确指定其 serial，然后执行：

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.	oolsuild.ps1 -GradleTask @('connectedQaAndroidTest')
```

SDK／JDK 不在默认位置时可使用 build.ps1 的 -SdkPath／-JavaPath。不要用用户的日用安装作自动测试。

## 安装变体

| 变体 | applicationId | 用途 |
|---|---|---|
| debug | com.chimafok.todo.debug | Android Studio 日常开发 |
| qa | com.chimafok.todo.qa | 自动化与虚构数据展示 |
| release | com.chimafok.todo | 官方发布，建置结果默认未签署 |

CI 建置与测试不使用正式私钥。自行签署的 release 不会取得官方更新身分。

## 测试 APK 交付

App 有改动时，先完成必要检查并从待验证的 `dev` 来源建置 `assembleDebug`，提供 `com.chimafok.todo.debug` 测试 APK；使用开发签章并与正式 App 的数据隔离，`.qa` 仅用于自动化。记录来源 commit、版本与文件 SHA-256，并提供改动摘要、测试范围与限制。测试版可在本地交付或放入明确标示的测试 Pre-release，不提交至 Git。

目前 CI 不会自动上传可下载的测试 APK，交付仍由开发流程另行完成。等待维护者明确同意测试版本期间，可继续已授权的开发、检查与同步，但不得正式签署、合并相关 App 改动到 `main` 或发布正式版；具体流程见[发布确认](CONTRIBUTING.md#test-apks-and-release-approval)。

## 正式 APK

正式签署前须已取得对应测试 APK 的明确同意；`assembleRelease` 可预先产生未签署 APK 供工程检查。维护者在安全环境设置 TODO_KEYSTORE、TODO_KEY_ALIAS、TODO_STORE_PASSWORD 与 TODO_KEY_PASSWORD，然后执行 tools/sign-release.ps1；密码只经环境变数交给 apksigner，不写入专案或日志。先完成 zipalign 检查，再签署并验证；任何 APK 内容变更都必须重新签署。

正式发布需使用既有官方金钥、递增版本码、核对 package／不可侦错／schema／权限、核对 XML 及 SHA-256，并执行适当的安装或更新验证。APK、杂凑档与版本说明以 GitHub Releases 提供。

## 结构

- app/src/main：domain 纯规则、data Room 交易、reminders 系统排程、ui Compose。
- app/src/test：纯规则单元测试。
- app/src/androidTest：数据库、备份、提醒与 UI／大型数据测试。
- app/src/qa：仅 QA 包含的测试 provider，不进入正式 APK。
- app/schemas：历史 Room schemas，供非破坏式升级测试。

数据模型见 [DATA-MODEL](DATA-MODEL.md)，产品规则与验收条件见 [PRD](PRD.md)。

提交前亦执行 `python tools/check-public-content.py` 与 `python tools/check-translations.py`。公开内容检查读取 Git 暂存内容，新增文件须先暂存；不要暂存本机设置或私人数据。文件目录与翻译规则见 [TRANSLATING](TRANSLATING.md)。
