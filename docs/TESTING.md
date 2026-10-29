# 驗證與回歸

## 1.3.1 正式發布驗證

維護者於2026-10-29確認1.3.1-dev01並授權正式簽署、合併main及發布。測試來源與APK校驗值見[確認紀錄](verification/approval-1.3.1.json)；正式App及建置來源與已確認版完全一致，簽署前後APK內容一致，見[source-parity](verification/source-parity-1.3.1.json)。發布分支以main為起點套用相同的已批准dev來源，避免squash歷史衝突，沒有改App行為。

本發布輪重新執行100項單元測試，核對新XML時間、類別、實際案例數及零failure／error／skipped；Release建置與Lint通過（0 errors／14 warnings）。正式包為com.chimafok.todo／1.3.1／code34，沿用既有RSA4096憑證，不可偵錯，不含QA provider、網路或整個相簿權限。

API37／26專用背景模擬器完成正式1.3.0→1.3.1覆蓋更新與啟動，UID／首次安裝時間保留。API37另核對虛構任務、學習進度及開始日期保留，學習可改名、改名後沒有刪除選項、批量勾選停用及全選排除，重新啟動後仍保留。只檢視並一次允許專用模擬器自身授權視窗，未永久授權、未重啟共用偵錯服務、未操作手機；完成後關閉模擬器。

開發階段21項API37案例保留為下方歷史，不當作本發布輪重跑；本輪沒有正式新安裝、完整歷史回歸或真機驗收，背景／隔夜／無障礙及大量資料缺口仍保留。另更正開發PR及測試Release說明中誤寫的Debug Lint警告數，依原verification紀錄為14；原測試APK與來源未更動。

結果見[正式包](verification/release-1.3.1.json)、[本輪測試](verification/tests-1.3.1.json)、[API37更新](verification/install-api37-1.3.1.json)、[API26更新](verification/install-api26-1.3.1.json)。以下測試版段落保留交付當時的狀態；該版已取得本節日期的正式發布同意。

## 1.3.1-dev01 學習分類刪除保護

本輪正式目標1.3.1，先交付開發簽章測試APK（code34）。Debug／QA測試包／未簽署Release建置、100項單元測試、Debug Lint通過（0 errors／14 warnings）。API37逐類別及方法執行21項：新學習保護3項、分類批次6項、分類資料4項、學習狀態6項及介面2項。核對每輪XML時間、類別／方法、實際非零案例數與零failure／error／skipped；沒有沿用歷史結果。

覆蓋改名後單項刪除拒絕、混合批次與舊預覽整批拒絕、原進度／開始日期／垃圾桶／系列歷史保留、同名一般分類仍可刪除、備份還原仍保留學習身分及保護；介面確認改名入口、刪除選項隱藏、批量停用與全選排除、旋轉重建後名稱保留。

專用模擬器初次因主機認證未被接受而無法進入測試，亦修正啟動空回應的檢查。保留初期啟動日誌；最後只透過模擬器主控台檢視並一次允許其自身虛構AVD授權視窗，核對身分與就緒後才執行案例。未重啟共用偵錯服務，未操作手機；完成後關閉專用背景模擬器。

Room schema9／備份格式5及資料識別不變；這輪未重跑API26或完整歷史回歸，既有實機、背景／隔夜與無障礙限制保留。結果及APK校驗值見[本輪紀錄](verification/development-1.3.1.json)。待維護者確認此測試包後才正式簽署及合併main；本次修補版號不延續為下次版本安排。

## 1.3.0 正式發布驗證

維護者於2026-10-28確認1.3.0-dev03，授權既有正式金鑰簽署、合併main與正式發布；對應來源與測試包校驗值見[確認紀錄](verification/approval-1.3.0.json)。App與建置來源保持一致，簽署前後APK內容一致，見[source-parity](verification/source-parity-1.3.0.json)。

本發布輪重新執行100項單元測試，核對當輪新XML、實際案例數、類別與零failure／error／skipped；Release建置及Lint通過（0 errors／13 warnings）。正式包為com.chimafok.todo／1.3.0／code33，沿用既有RSA4096憑證，不可偵錯，不含QA provider、網路或整個相簿權限。

API37及API26專用背景模擬器完成正式1.2.0→1.3.0覆蓋更新與啟動，UID／首次安裝時間保留。API37另確認schema8→9後虛構一般／學習任務、學習進行中與開始日期仍在，重新啟動亦保留；正式包圖片入口與系統選圖取消返回表單通過。初次API37被模擬器System UI啟動無回應視窗擋住，保留失敗日誌／畫面，選Wait後補驗，App未更動亦未清資料。

本輪未測正式新安裝、真實照片供應商或使用者手機；開發階段38項API37及dev01的API37／26案例保留為下方歷史，沒有當成本輪重跑。背景／隔夜、完整無障礙及大量資料限制仍保留。結果見[正式包](verification/release-1.3.0.json)、[本輪測試](verification/tests-1.3.0.json)、[API37更新](verification/install-api37-1.3.0.json)、[API26更新](verification/install-api26-1.3.0.json)。專用模擬器完成後關閉。

以下dev01–dev03段落保留交付當時的狀態；dev03已在本節日期取得正式發布同意。

## 1.3.0-dev03 圖片叉號與取消張數限制

Debug／QA／QA測試包及未簽署Release建置通過；100項單元測試及Debug Lint通過（0 errors／13 warnings）。API37逐類別／方法執行38項：圖片資料9項、備份19項、學習狀態6項及圖片介面4項。核對每輪新XML、選取類別／方法、非零案例與零failure／error／skipped。包含12張分批加入、去重、草稿、系列歷史、格式5完整還原、格式1–4相容、讀取失敗原子性，以及叉號幾何位置／點按、選圖取消與文字保留；另檢視虛構介面畫面。

初次誤用本專案不存在的testDebugUnitTest，改用testQaUnitTest補驗。12張介面新測試初次未在縮圖出現後把「加入圖片」捲到可視範圍而逾時，修正測試捲動並重跑成功。系統選圖取消測試單次過早返回亦曾逾時，改為等待選圖器、僅在QA外部最多返回三次後重跑成功；不在任務表單內繼續返回。初次失敗日誌／XML在本機保留，沒有算入38項通過結果。

版本1.3.0-dev03／code33、com.chimafok.todo.debug，沿用開發憑證，可更新dev02；schema9不變，備份輸出格式5、讀1–4。沒有重跑API26或先前整套回歸；系統相簿／雲端供應商、HEIC、大量圖片及完整無障礙仍待驗收。只操作並關閉專用背景模擬器，未操作手機。結果及APK校驗值見[本輪紀錄](verification/development-1.3.0-dev03.json)。等待維護者確認後才正式簽署及合併main。

## 1.3.0-dev02 備註位置調整

文字備註移到圖片區上方。Debug／QA／QA測試包建置與Debug Lint通過（0 errors／13 warnings）；API37分別重跑2項既有介面案例，核對當輪新XML、類別／方法、非零案例與零failure／error／skipped。涵蓋圖片草稿／預覽／旋轉／移除、保留文字及選圖後儲存；另以虛構畫面檢查文字在上、圖片在下。

沿用開發憑證，版本1.3.0-dev02／code32、com.chimafok.todo.debug；schema9及備份格式4不變。此輪沒有本地重跑先前99項單元測試或整套裝置回歸，舊紀錄保持為下方歷史；API26、手機、圖片供應商及其他限制仍保留。只使用並關閉專用背景模擬器，未操作手機。結果與APK校驗值見[本輪紀錄](verification/development-1.3.0-dev02.json)。等待維護者測試確認後才正式簽署及合併main。

## 1.3.0-dev01 分類管理與備註圖片

本次先交付開發簽章測試APK（com.chimafok.todo.debug／code31），等待維護者確認後才正式簽署及合併main。APK來源以不可移動的v1.3.0-dev01測試標籤為準，SHA-256與原始碼指紋見[當輪紀錄](verification/development-1.3.0.json)；沿用既有開發憑證，可更新先前測試版，與正式版資料隔離。

核對本輪XML時間、正確類別、實際案例數及零failure／error／skipped：99項單元測試、API37共90個不同裝置案例、API26共14個裝置案例通過。裝置驗證分組及補驗執行，沒有宣稱一次完整全套通過；API26重跑的14項與API37中的案例重複，分開列示。Debug／QA／QA測試包／未簽署Release建置成功，兩種Lint各0 errors／13 warnings。

新增7項介面案例覆蓋分類及標籤長按排序、邊緣自動捲動並移至畫面外、順序持久保存、批量選取與系統保護、取消與指定刪除、圖片縮圖／放大／旋轉／移除與文字保留、選圖結果加入副本及實際系統選圖取消返回。資料測試覆蓋批量交易回滾、過期範圍拒絕、垃圾桶及系列搬移、圖片讀取失敗不部分保存、重複圖片去重、草稿、歷史快照、格式4圖片往返、格式1–3匯入及schema8→9保留任務／系列；另回歸既有分類、系列、學習、草稿和備份。

初期測試程式座標型別、連線方式及多類選取不完整的輪次未當成完整通過。新UI首輪5項有4項失敗，後續曾有2項、1項未過；修正測試定位／等待及實際列表捲動中斷拖拽問題後，移除暫時診斷碼，完整7項新UI在API37與API26均通過。保留初始失敗及補驗來源，未沿用過期XML。發包前另核對既有開發憑證及無QA provider／網路或整個相簿權限。

全程使用本工作專用的無視窗模擬器與虛構資料，結束後關閉，未操作使用者手機。實際手機相簿／雲端供應商、HEIC變體、大量圖片／極大備份、背景隔夜及完整無障礙仍未完成驗收。圖片壓縮與備份容量上限見USER-GUIDE；正式1.2.0不能讀新的格式4備份。

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
