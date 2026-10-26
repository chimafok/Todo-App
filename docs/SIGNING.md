# 官方發布簽章

1.0.5 起官方 APK 使用獨立正式發布金鑰，RSA 4096，識別碼 com.chimafok.todo。

憑證 SHA-256（不是 APK 檔案雜湊）：

`a4b166d522cf41ce213923ef054da8e9b4644abf1888580f502002e2aadfd197`

公開憑證與上述憑證指紋可用於辨識簽署者，不包含用來簽署 APK 的私鑰。它們不能代替私鑰與密碼的備份，也不能僅憑指紋重新建立相同更新身分。

每個 APK 的檔案 SHA-256 見該 Release 的 SHA256SUMS.txt；原始碼中的公開驗證摘要見 [1.1.0 驗證摘要](verification/release-1.1.0.json)（[1.0.5 歷史摘要](verification/release-1.0.5.json)）。

App 改動先交付開發簽章的 `.debug` 測試 APK；只有 chimafok 明確同意對應測試版本後，才可使用本正式金鑰簽署、合併對應原始碼到 `main` 及發布正式 APK。確認須對應來源 commit 與測試 APK SHA-256，詳見[發布確認](../CONTRIBUTING.md#測試-apk-與發布確認)。

官方後續版本沿用此正式金鑰並遞增版本碼，讓既有安裝可直接更新。安裝與更新流程見 [INSTALL.md](INSTALL.md)。

正式私鑰、密碼與本機加密憑證儲存不在倉庫或 GitHub Actions。自行編譯或自行簽署不代表官方簽章。維護者須在倉庫外安全保管金鑰與可攜離線備份；更換或遺失金鑰不得悄悄破壞既有更新身分。
