# 官方發布簽章

<p align="center">語言 / 语言 / Languages：<strong>繁體中文</strong> · <a href="zh-CN/SIGNING.md">简体中文</a> · <a href="en/SIGNING.md">English</a></p>

1.4.0 沿用 1.0.5 起的 RSA 4096 正式憑證，套件 `com.chimafok.todo`，versionCode 35。同一套件、同一簽章且更高版本碼可更新既有正式安裝；詳見[安裝與更新](INSTALL.md)。

## 憑證與檔案雜湊

正式憑證 SHA-256：

`a4b166d522cf41ce213923ef054da8e9b4644abf1888580f502002e2aadfd197`

這是簽署者憑證的指紋，**不是 APK 檔案雜湊**。每個 APK 的檔案 SHA-256 見該 Release 的 `SHA256SUMS.txt`。1.4.0 公開摘要見 [release-1.4.0.json](verification/release-1.4.0.json)，來源確認見 [approval-1.4.0.json](verification/approval-1.4.0.json)。

公開憑證或指紋不包含私鑰，不能用來簽署更新，也不能代替私鑰與密碼備份。正式私鑰、密碼與含私鑰的憑證儲存檔不在倉庫、下載資產或 GitHub Actions 中；維護者在倉庫外安全保管及備份。

## 測試與正式發布

App 改動先提供開發簽章的 `.debug` 測試 APK。chimafok 明確同意對應版本、來源 commit 與 APK SHA-256 後，才使用既有正式金鑰簽署、經 PR 合併對應來源至 main 並發布。流程見[發布確認](../CONTRIBUTING.md#test-apks-and-release-approval)。CI 建置 release 預設未簽署，不會取得官方身分。

自行編譯或自行簽署不代表官方簽章。不可為了繞過更新衝突而悄悄換金鑰或降低版本碼。1.4.0 簽署前後的 payload 一致；正式更新驗證範圍見 [TESTING](TESTING.md)。
