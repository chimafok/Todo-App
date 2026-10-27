# 驗證與回歸

## 1.2.0 正式發布驗證

維護者於 2026-10-27 確認 1.2.0-dev01 並同意正式簽署及合併 main。來源 commit、測試包 SHA-256 與確認範圍見 [確認紀錄](verification/approval-1.2.0.json)；App／建置來源與已確認版本一致，核對見 [source-parity](verification/source-parity-1.2.0.json)。

本發布輪重跑 95 項單元測試，核對新 XML 時間、類別、案例數及零 failure／error／skipped；Release 建置與 Lint 通過（0 errors／10 warnings）。正式包為 com.chimafok.todo／1.2.0／code30，沿用原 RSA4096 憑證、不可偵錯、不含 QA provider 或網路權限。

API37／API26 專用背景模擬器完成正式 1.1.0→1.2.0 覆蓋更新與啟動，安裝 UID／首次安裝時間不變。API37 另確認虛構一般／學習任務及開始日期保留，三態選單可操作，進行中狀態在強制停止後重新開啟仍保存。初次測試因模擬器 System UI 啟動 ANR 卡住，先關閉閒置 AVD、選 Wait 並以較充足記憶體冷啟動測試機後，依序補驗；App 原始碼未改動。

前輪 76 個不同裝置案例的回歸保留為下方歷史，不宣稱這輪重跑；本輪未測正式新安裝，也未操作使用者手機，背景、隔夜、無障礙及大量資料缺口保留。結果見 [正式包](verification/release-1.2.0.json)、[本輪測試](verification/tests-1.2.0.json)、[API37 更新](verification/install-api37-1.2.0.json)及 [API26 更新](verification/install-api26-1.2.0.json)。

## 1.2.0-dev01 學習三態（歷史測試）

重複任務新欄位名稱及學習三態已完成。95 項單元測試通過，核對本輪新 XML 時間、類別與零 failure／error／skipped；Debug／QA／QA 測試 APK／未簽署 Release 建置及 Debug／Release Lint 均通過（各 0 errors／10 warnings）。

API37 專用背景模擬器執行資料組 71 項、介面組 5 項，共 76 個不同案例。資料初輪 3 項失敗為歷史遷移測試仍預期 schema7，改為 schema8 並加驗舊開始欄位為 null 後，受影響三類 22 項補驗通過。介面初輪 1 項失敗為測試查找已清除語意的裝飾標籤，改驗欄位的無障礙標籤後，4 項學習編輯測試補驗通過；作業介面回歸 1 項已在首輪通過。以類別與案例名稱去重，每個案例最後結果均通過；不是宣稱一次完整全套成功。

測試涵蓋新增禁止完成、原學習 OPEN／完成保留、進行中與完成／重開、過期 revision 拒絕、垃圾桶、分類搬移／刪除、重複首筆與未來進度、系列編輯、備份格式3往返及格式1／2相容。初次測試程式編譯的兩處錯誤亦已修正，初輪及補驗原始輸出分開保留。摘要見 [development-1.2.0.json](verification/development-1.2.0.json)。

先交付 1.2.0-dev01／code30 的 `.debug` 開發簽章 APK，沿用前次測試版憑證；Room schema8不變，備份輸出為格式3。沒有操作使用者手機，API26及長時間背景等缺口維持明示；這是測試版交付當時的紀錄；2026-10-27 已取得此版發布同意並納入上方正式驗證。

## 1.1.0 正式發布驗證

維護者於 2026-10-26 明確同意 1.1.0-dev02 正式簽署及合併 main；測試來源 commit 與 APK SHA-256 見 [確認紀錄](verification/approval-1.1.0.json)。App／建置來源與確認版相同，正式與開發變體的識別碼、旗標及簽章不同；來源核對見 [source-parity](verification/source-parity-1.1.0.json)。

本發布輪重新執行 91 項單元測試，核對新 XML 時間、案例數、類別與零 failure／error／skipped；release 建置與 Release Lint 通過（0 errors／10 warnings）。正式包為 1.1.0／code29，非 debuggable，沿用 1.0.5 的正式憑證，不含 QA provider 或網路權限。

在新建的專用背景模擬器完成 API37 的正式 1.0.5 → 1.1.0 覆蓋更新、啟動與虛構任務保留，安裝 UID／首次安裝時間不變；API26 完成正式新安裝與啟動，未宣稱該環境亦完成升級遷移。下方 dev01／dev02 裝置回歸是前輪紀錄，沒有當成本輪重跑。未操作使用者手機，長時間背景、隔夜、無障礙與大量資料缺口仍保留。

結果見 [正式包](verification/release-1.1.0.json)、[本輪測試](verification/tests-1.1.0.json)、[API37 更新](verification/install-api37-1.1.0.json)及 [API26 安裝](verification/install-api26-1.1.0.json)。

## 1.1.0-dev02 欄位位置修正（歷史測試）

學習開始日期／時間移到「分類與狀態」的分類按鈕下方。Debug／QA／QA 測試 APK 建置成功，Debug Lint 通過（0 errors／10 warnings，Gradle 判定報告為最新並沿用）。API37 專用背景模擬器重新執行 2 項新增表單測試，核對本輪 XML 時間、類別、案例數及零 failure／error／skipped；包含開始欄位在學習分類按鈕下方的位置斷言，以及日期／時間停用、清除與保存行為。另保留虛構測試畫面供位置檢查。

本輪局部驗證不是全套回歸；以下 dev01 的 91 項單元與 38 項裝置測試是前輪紀錄。沒有操作使用者手機。交付版本為 1.1.0-dev02／code29，沿用 dev01 的 `.debug` 識別碼與開發憑證，可覆蓋安裝。當時依指示暫緩合併與正式簽署；2026-10-26 已取得此測試版的發布同意，納入上方正式發布紀錄。摘要見 [development-1.1.0-dev02.json](verification/development-1.1.0-dev02.json)。

## 1.1.0-dev01 既有驗證（歷史測試）

本輪 91 項單元測試通過，核對新 XML 時間、類別與案例數；Debug／QA／未簽署 Release 建置及 Debug／Release Lint 通過（各 0 errors／10 warnings）。API37 隔離模擬器分兩組執行 28 項資料／遷移／新增表單及 10 項草稿／備份介面測試，合計 38 個不同案例，最後均無 failure／error／skipped；不是一次完整裝置測試全套。

初輪 27 項有 1 項失敗：舊 schema 測試建置未處理無 indices 的資料表，修正測試後重跑 28 項通過。另一組初輪 10 項有 1 項失敗：預覽錯誤提示被新增資訊擠出可見捲動範圍，移至固定控制區後重跑 10 項通過。原始初輪與補驗輸出分別保留於本機；公開去識別摘要見 [development-1.1.0.json](verification/development-1.1.0.json)。

Gradle 裝置測試在獨立連線環境未產生有效案例，未當作成功；改用明確指定隔離連線與模擬器的 AndroidJUnitRunner，保留原始狀態輸出並轉成 JUnit XML，核對兩組實際案例、類別及零失敗。沒有操作使用者手機。本輪尚未重跑 API26 裝置矩陣；真機、背景／隔夜、TalkBack、大量資料限制仍明示。

交付 .debug 開發簽章 APK，schema8／格式2；可匯入格式1／schema7。以上是 dev01 交付當時的工程紀錄；本次正式發布的確認對象是後續 dev02，不把此紀錄推定為完整手機驗收。


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

## 1.0.5 歷史公開版本

1.0.5 的功能程式沿用 1.0.4，變更版本與公開發布設定。公開前實際完成 86 項純規則測試、7 項備份還原 UI 與 1 項展示 UI（分開選取）、release 建置與 Debug／Release Lint（各 0 errors／10 warnings）。在專用空白 API26／37 模擬器確認正式 APK 新安裝及啟動成功、相同 APK 重裝保留安裝識別；相同版本重裝不是未來版本升級測試。完整安裝驗證事實保留於 verification 原始摘要。

先前 1.0.4 開發驗證涵蓋 97 個不同案例：84 UI／輸入、8 批次資料、5 分頁資料；完整 UI 初輪 84 有 2 項失敗，修正測試與補驗 24 項後各案例最後通過，最低 API 26 重點 24 項通過。這些是歷史摘要，不是宣稱本次重新跑過全部案例；原內部輸出留在私人歷史，不公開整批診斷檔。

1.0.5 的歷史結果另見 [verification](verification/tests-1.0.5.json)；具體缺口見 KNOWN-ISSUES。手機端的資料還原、通知與背景行為仍由使用者自行確認。

## 展示截圖

PublicScreenshotsTest 僅允許在 .qa、API 37 的自有模擬器執行，建立帶固定前綴的虛構任務，使用後清理自己建立的資料。截圖不來自私人手機或日用資料。
