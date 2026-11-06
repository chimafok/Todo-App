# 驗證與回歸

<p align="center">語言 / 语言 / Languages：<strong>繁體中文</strong> · <a href="zh-CN/TESTING.md">简体中文</a> · <a href="en/TESTING.md">English</a></p>

本頁區分正式版、開發階段與歷史結果；不同輪不合併成一次全套回歸。完整初始失敗／補驗來源保留於共用 verification JSON 及[歷史紀錄](history/TESTING-HISTORY.zh-Hant.md)。本次文件翻譯不代表重新執行以下 App 測試。

## 1.4.1 正式發布（2026-11-06）

維護者確認 1.4.1-dev02 後，以相同 App／建置來源重新建置正式包；104 項當輪單元測試通過，Release Lint 0 errors／13 warnings，已核對新 XML 的數量、類別、時間及零 failure／error／skipped。沿用 RSA4096 正式憑證；簽署前後 payload 一致，正式包不可偵錯、不含 QA provider 或網路／廣泛相簿權限，授權文件已附。

專用 API37／26 模擬器完成正式 1.4.0→1.4.1 覆蓋更新與啟動，UID／首次安裝時間保留，schema10 不變。API37 另核對既有虛構任務、學習進度／開始日期、改名分類及英文偏好保留；正式表單驗證預設 10:00、兩位小時自動換欄、分鐘輸入、回填、已有值重開及取消，測試表單已捨棄。開發階段 22 項裝置案例是先前分輪結果；本輪更新／表單冒煙檢查不代表全套歷史回歸或手機驗收。

來源見 [approval-1.4.1.json](verification/approval-1.4.1.json)，正式摘要見 [release-1.4.1.json](verification/release-1.4.1.json)，測試記錄見 [tests-1.4.1.json](verification/tests-1.4.1.json)。APK SHA-256：`a647bd92a6ab58cbc395a230950b43c9a853bea8118a74b4f35a5243211e0258`。未測正式新安裝、完整手機／鍵盤／無障礙／語言矩陣或長背景；專用背景模擬器完成後關閉，未操作使用者手機。

## 1.4.1-dev02 開發階段（已確認，2026-11-06）

104 項當輪單元測試通過；Debug 建置及 Lint 0 errors／13 warnings。API37／API26 各分開執行 7 項焦點與表單案例、4 項版面案例（每個版本 11 項，共 22 項），新 XML 均核對案例數、類別與時間，零 failure／error／skipped。涵蓋未修改的預設 10:00、已有時間、前導零、選取／游標位置及實際系統 100%／140%／200% 字級；已檢查專用 QA 畫面。不是全套歷史回歸、手機驗收或完整無障礙／語言矩陣。

初始測試編譯型別錯誤已修正；首輪選取只跑到焦點類別，另分輪補跑版面類別。初版字級測試的外層密度不能代表對話框內實際字級，已改為調整並還原專用模擬器系統字級、斷言對話框實際密度後補驗。各來源及限制見 [development-1.4.1-dev02.json](verification/development-1.4.1-dev02.json)。測試 APK 為 .debug 開發簽章、code37；已獲維護者確認，並以相同 App／建置來源發布正式 1.4.1；本段保留開發階段結果。

## 1.4.1-dev01 歷史 —（2026-11-06）

104 項新單元測試通過；Debug 建置及 Lint 0 errors／13 warnings。Android 17（API37）與 Android 8（API26）各執行 7 項時間輸入案例，均無失敗／錯誤／跳過，已核對當輪 XML。涵蓋兩位換欄、前導零／午夜／相同初始小時、無效輸入／刪字、時鐘共享值、取消及截止表單回填；不是全套歷史回歸。初輪 API37 有 2 項失敗，修正焦點時序與測試重開方式後補驗；數字裁切及分隔符亦經定稿畫面核對。來源與限制見 [development-1.4.1.json](verification/development-1.4.1.json)。測試 APK 為 .debug 開發簽章，目標正式版 1.4.1，仍待維護者確認；未正式簽署、未驗證正式版覆蓋更新或手機／完整無障礙矩陣。

## 1.4.0 正式發布（2026-11-03）

| 檢查 | 實際結果 |
|---|---|
| 當輪單元測試 | 104 項；新 XML 核對類別、數量、時間；零 failure／error／skipped |
| Release 建置／Lint | 建置通過；0 errors／13 warnings |
| 簽章與 payload | 沿用 RSA 4096；簽署前後 payload 一致 |
| 正式包 | com.chimafok.todo／code35，非可偵錯，無 QA provider／網路／廣泛相簿權限，包含授權原文 |
| API 37／26 覆蓋更新 | 正式 1.3.1→1.4.0、schema9→10，啟動成功，UID／首次安裝時間保留 |
| API 37 資料／語言 | 虛構任務、學習進度／開始日期、改名分類保留；三語切換及重啟後偏好保存 |

APK SHA-256：`2e8532687e01d5a16d1b25ee8f715a5fabf97582ef6287ad6364f25ac29e88ce`。來源／授權見 [approval-1.4.0.json](verification/approval-1.4.0.json)，正式摘要見 [release-1.4.0.json](verification/release-1.4.0.json)。這是專用模擬器的正式更新冒煙驗證，不是本輪全套歷史回歸或真機驗收；本輪未測正式新安裝。

## 1.4.0-dev01 開發階段（歷史）

- 104 項單元測試、建置及 Debug Lint（0 errors／13 warnings）。796 個資源鍵的四份完整目錄及位置參數檢查通過。
- API 37 分輪：語言 UI 3、語言備份／遷移 2、備份回歸 19、學習 6、圖片／舊格式 9，共 39 項。
- API 26 分輪：語言 UI 3、語言備份／遷移 2，共 5 項。44 項全部零 failure／error／skipped，不是一次全套歷史執行。
- 涵蓋切換與重建／草稿保存、自訂內容與時區不變、通知文字、格式 6 往返／設定衝突、舊檔語言保留、無效語言拒絕、僅語言還原不重設保留錨點。API 26 另人工檢查三語設定頁。

這份測試 APK 已由維護者確認，並於 2026-11-03 發布為正式 1.4.0。開發測試結果保留為歷史，完整初始失敗與補驗見 [development-1.4.0.json](verification/development-1.4.0.json)。

## 尚未完成

未全面完成獨立母語審校、真機、隔夜／長背景、TalkBack／200% 字級／RTL、全套歷史及大量資料效能。歷史效能不佳的量測仍有效，見[已知限制](KNOWN-ISSUES.md)。使用者未回報的手機案例不得推定通過。

## 執行與記錄

執行命令見[開發指南](DEVELOPMENT.md)：testQaUnitTest、適當變體的建置／Lint，及只針對自有／已授權裝置 `.qa` 的 connectedQaAndroidTest。每輪記錄起點，檢查新 XML 類別、案例數大於零、無 failure／error／skipped，且時間不早於起點。沒有新結果不能沿用舊檔；初始失敗及補驗分開保存。選測不可寫成全套，CI 編譯裝置測試 APK 不等於執行裝置案例。

展示畫面只用隔離 QA 的虛構資料；不操作私人手機或日用 App。App 改動須先交付可識別來源／雜湊的開發簽章測試 APK，明確確認後才正式簽署／合併 main／發布；流程見[貢獻指南](../CONTRIBUTING.md#test-apks-and-release-approval)。純文件修改不需產生 APK。
