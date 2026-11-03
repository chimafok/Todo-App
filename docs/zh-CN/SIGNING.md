# 官方发布签章

<p align="center">語言 / 语言 / Languages：<a href="../SIGNING.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/SIGNING.md">English</a></p>

1.4.0 沿用 1.0.5 起的 RSA 4096 正式凭证，套件 `com.chimafok.todo`，versionCode 35。同一套件、同一签章且更高版本码可更新既有正式安装；详见[安装与更新](INSTALL.md)。

## 凭证与文件杂凑

正式凭证 SHA-256：

`a4b166d522cf41ce213923ef054da8e9b4644abf1888580f502002e2aadfd197`

这是签署者凭证的指纹，**不是 APK 文件杂凑**。每个 APK 的文件 SHA-256 见该 Release 的 `SHA256SUMS.txt`。1.4.0 公开摘要见 [release-1.4.0.json](../verification/release-1.4.0.json)，来源确认见 [approval-1.4.0.json](../verification/approval-1.4.0.json)。

公开凭证或指纹不包含私钥，不能用来签署更新，也不能代替私钥与密码备份。正式私钥、密码与含私钥的凭证储存档不在仓库、下载资产或 GitHub Actions 中；维护者在仓库外安全保管及备份。

## 测试与正式发布

App 改动先提供开发签章的 `.debug` 测试 APK。chimafok 明确同意对应版本、来源 commit 与 APK SHA-256 后，才使用既有正式金钥签署、经 PR 合并对应来源至 main 并发布。流程见[发布确认](CONTRIBUTING.md#test-apks-and-release-approval)。CI 建置 release 默认未签署，不会取得官方身分。

自行编译或自行签署不代表官方签章。不可为了绕过更新冲突而悄悄换金钥或降低版本码。1.4.0 签署前后的 payload 一致；正式更新验证范围见 [TESTING](TESTING.md)。
