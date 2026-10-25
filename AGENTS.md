# Todo App 協作規則

本倉庫是主要開發位置，公開原始碼採 MIT。產品行為以 docs/PRD.md、實際程式與後續明確需求共同維護；不要重新進行需求訪談。README、USER-GUIDE、KNOWN-ISSUES 與 TESTING 應反映實際行為與驗證限制。

## 分支與同步

main 是發布基準，dev 用於整合；獨立工作使用 codex/*，經適當驗證後透過 Pull Request 合併。使用者已要求完成的改動持續同步 GitHub，無須為同一次同步重問；不要強制推送或覆寫公開歷史。新建 Pull Request 後附加到目前工作。

## 公開內容與發布

所有分支與 CI 日誌均公開。提交前檢查實際檔案清單、內容與作者 metadata；不提交本機設定、個人路徑、原始訪談、私人備份／資料庫、金鑰、密碼、token、建置快取或 APK。使用虛構資料展示。APK 僅以經確認的 Release 資產發布。

release 使用 com.chimafok.todo／正式發布金鑰；debug 為 .debug，QA 為 .qa。正式金鑰由維護者在倉庫外保管，CI 僅產生未簽署 release，不得偷偷改用 debug 簽章或建立新發布身分；未獲授權不產生或輪替私人金鑰。後續更新遞增 versionCode，維持既有正式簽章。

## 驗證

使用 tools/build.ps1 或 Gradle wrapper。Kotlin／Compose 變更依範圍執行建置、相關單元／QA 測試及 Lint；僅文件變更不必重跑全部功能。Room schema 與 migration、備份格式及交易版本檢查必須維持。

宣稱測試通過前檢查當輪實際產生的 XML：案例數大於零，無 failure／error／skipped，類別符合選取，修改時間不早於執行起點。編譯失敗或沒有新 XML 不得沿用舊結果。保留初始失敗及補驗來源，不把不同輪偽造成一次完整回歸。極大量資料、實機／背景／隔夜與無障礙未完成項保持明示。

## 裝置與私人資料

預設使用無視窗模擬器並在完成後關閉，保留 userdata；使用者要求時才開前臺。QA 只操作隔離 .qa 與虛構資料，不清除正常 App。不得未經授權操作使用者手機、其他 App、檔案、通知或完整畫面；敏感內容須先取得具體同意。手機驗收由使用者自行執行，未回報不當成已通過。
