# 驗證與回歸

## 1.1.0-dev02 欄位位置修正（尚未合併）

學習開始日期／時間移到「分類與狀態」的分類按鈕下方。Debug／QA／QA 測試 APK 建置成功，Debug Lint 通過（0 errors／10 warnings，Gradle 判定報告為最新並沿用）。API37 專用背景模擬器重新執行 2 項新增表單測試，核對本輪 XML 時間、類別、案例數及零 failure／error／skipped；包含開始欄位在學習分類按鈕下方的位置斷言，以及日期／時間停用、清除與保存行為。另保留虛構測試畫面供位置檢查。

本輪局部驗證不是全套回歸；以下 dev01 的 91 項單元與 38 項裝置測試是前輪紀錄。沒有操作使用者手機。交付版本為 1.1.0-dev02／code29，沿用 dev01 的 `.debug` 識別碼與開發憑證，可覆蓋安裝。依最新指示保留工作分支與未合併 PR，未正式簽署，也未合併 dev 或 main。摘要見 [development-1.1.0-dev02.json](verification/development-1.1.0-dev02.json)。

## 1.1.0-dev01 既有驗證（等待使用者同意）

本輪 91 項單元測試通過，核對新 XML 時間、類別與案例數；Debug／QA／未簽署 Release 建置及 Debug／Release Lint 通過（各 0 errors／10 warnings）。API37 隔離模擬器分兩組執行 28 項資料／遷移／新增表單及 10 項草稿／備份介面測試，合計 38 個不同案例，最後均無 failure／error／skipped；不是一次完整裝置測試全套。

初輪 27 項有 1 項失敗：舊 schema 測試建置未處理無 indices 的資料表，修正測試後重跑 28 項通過。另一組初輪 10 項有 1 項失敗：預覽錯誤提示被新增資訊擠出可見捲動範圍，移至固定控制區後重跑 10 項通過。原始初輪與補驗輸出分別保留於本機；公開去識別摘要見 [development-1.1.0.json](verification/development-1.1.0.json)。

Gradle 裝置測試在獨立連線環境未產生有效案例，未當作成功；改用明確指定隔離連線與模擬器的 AndroidJUnitRunner，保留原始狀態輸出並轉成 JUnit XML，核對兩組實際案例、類別及零失敗。沒有操作使用者手機。本輪尚未重跑 API26 裝置矩陣；真機、背景／隔夜、TalkBack、大量資料限制仍明示。

交付 .debug 開發簽章 APK，schema8／格式2；可匯入格式1／schema7。尚未正式簽署、合併 main 或取得使用者對本測試版的發布同意。


公開倉庫提供原始測試與 CI。請區分單元、隔離裝置、手動實機及效能驗證；版本名稱與成功建置不代表全部驗收完成。

## 執行

- 純規則：testQaUnitTest。
- 靜態檢查與編譯：lintDebug／lintRelease、assembleDebug／assembleQaAndroidTest／assembleRelease。
- 自有模擬器：connectedQaAndroidTest；資料與 UI 只使用 .qa。
- 特定回歸：透過 AndroidJUnitRunner 的 class 或 annotation 選取，不把選測當全套。

每輪開始記錄時間，完成後核對新 XML 的類別、案例數與 failure／error／skipped。初始失敗及補驗分開保存；當次沒有新結果不得使用舊檔。CI 保留可下載的當輪報告，不上傳私人資料或金鑰。

## App 改動的使用者測試與確認

每次 App 改動完成必要工程檢查後，交付開發簽章的 `.debug` 測試 APK，附版本、來源 commit、SHA-256、改動摘要及已知限制，由 chimafok 自行測試。未明確同意該測試版本前，不正式簽署、不合併相關 App 改動到 `main`、不發布正式 APK；已授權的開發與 `dev` 同步照常進行。

使用者確認與 CI／模擬器結果分開記錄，不推定未回報的手機驗收已通過。後續修改 App 內容須重新交付並確認；release 變體的簽章、安裝／更新與資料安全檢查仍須完成。流程見[發布確認](../CONTRIBUTING.md#測試-apk-與發布確認)。純文件小修沿用貢獻指南的例外。

## 本次公開版本

1.0.5 的功能程式沿用 1.0.4，變更版本與公開發布設定。公開前實際完成 86 項純規則測試、7 項備份還原 UI 與 1 項展示 UI（分開選取）、release 建置與 Debug／Release Lint（各 0 errors／10 warnings）。在專用空白 API26／37 模擬器確認正式 APK 新安裝及啟動成功、相同 APK 重裝保留安裝識別；相同版本重裝不是未來版本升級測試。完整安裝驗證事實保留於 verification 原始摘要。

先前 1.0.4 開發驗證涵蓋 97 個不同案例：84 UI／輸入、8 批次資料、5 分頁資料；完整 UI 初輪 84 有 2 項失敗，修正測試與補驗 24 項後各案例最後通過，最低 API 26 重點 24 項通過。這些是歷史摘要，不是宣稱本次重新跑過全部案例；原內部輸出留在私人歷史，不公開整批診斷檔。

當前發布的實際結果另見 [verification](verification/tests-1.0.5.json)；具體缺口見 KNOWN-ISSUES。手機端的資料還原、通知與背景行為仍由使用者自行確認。

## 展示截圖

PublicScreenshotsTest 僅允許在 .qa、API 37 的自有模擬器執行，建立帶固定前綴的虛構任務，使用後清理自己建立的資料。截圖不來自私人手機或日用資料。
