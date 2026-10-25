# 驗證與回歸

公開倉庫提供原始測試與 CI。請區分單元、隔離裝置、手動實機及效能驗證；版本名稱與成功建置不代表全部驗收完成。

## 執行

- 純規則：testQaUnitTest。
- 靜態檢查與編譯：lintDebug／lintRelease、assembleDebug／assembleQaAndroidTest／assembleRelease。
- 自有模擬器：connectedQaAndroidTest；資料與 UI 只使用 .qa。
- 特定回歸：透過 AndroidJUnitRunner 的 class 或 annotation 選取，不把選測當全套。

每輪開始記錄時間，完成後核對新 XML 的類別、案例數與 failure／error／skipped。初始失敗及補驗分開保存；當次沒有新結果不得使用舊檔。CI 保留可下載的當輪報告，不上傳私人資料或金鑰。

## 本次公開版本

1.0.5 的功能程式沿用 1.0.4，變更版本與公開發布設定。公開前實際完成 86 項純規則測試、7 項備份還原 UI 與 1 項展示 UI（分開選取）、release 建置與 Debug／Release Lint（各 0 errors／10 warnings）。在專用空白 API26／37 模擬器確認舊簽章覆蓋被拒、正式 APK 新安裝及啟動成功、相同 APK 重裝保留安裝識別；相同版本重裝不是未來版本升級測試。新簽章不相容舊開發簽章的直接更新，這是預期的首次搬移界線。

先前 1.0.4 開發驗證涵蓋 97 個不同案例：84 UI／輸入、8 批次資料、5 分頁資料；完整 UI 初輪 84 有 2 項失敗，修正測試與補驗 24 項後各案例最後通過，最低 API 26 重點 24 項通過。這些是歷史摘要，不是宣稱本次重新跑過全部案例；原內部輸出留在私人歷史，不公開整批診斷檔。

當前發布的實際結果另見 [verification](verification/tests-1.0.5.json)；具體缺口見 KNOWN-ISSUES。新簽章版手機端的資料還原、通知與背景行為仍由使用者自行確認。

## 展示截圖

PublicScreenshotsTest 僅允許在 .qa、API 37 的自有模擬器執行，建立帶固定前綴的虛構任務，使用後清理自己建立的資料。截圖不來自私人手機或日用資料。
