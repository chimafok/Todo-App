# 備份格式與還原契約

<p align="center">語言 / 语言 / Languages：<strong>繁體中文</strong> · <a href="zh-CN/BACKUP-FORMAT.md">简体中文</a> · <a href="en/BACKUP-FORMAT.md">English</a></p>

本頁是 1.4.0 的現行契約；[舊格式原始說明](history/BACKUP-HISTORY.zh-Hant.md)保留作相容性查核。一般操作見[使用說明](USER-GUIDE.md#backup-and-restore)。

## 版本與範圍

輸出為未加密 UTF-8 JSON。根物件為 `format="Todo-App"`、`formatVersion=6`、`schemaVersion=10`、`exportedAt`（UTC epoch 毫秒）、`appVersion`、`data`，不能添加未知欄位。

| 可讀格式 | schemaVersion | 契約差異 |
|---|---|---|
| 1 | 7 | 十一陣列；不含垃圾桶、排序、一般設定、學習開始欄位或圖片 |
| 2 | 8 | 加入垃圾桶、排序、一般設定及學習開始資料 |
| 3 | 8 | 允許學習 IN_PROGRESS |
| 4 | 9 | 加入圖片；每項至多 8 張的歷史契約 |
| 5 | 9 | 取消每項圖片張數上限，保留容量限制 |
| 6 | 10 | 一般設定加入 language |

只接受上述格式／schema 配對。新版能讀舊檔，不代表舊版能讀新檔；1.3.1 及更舊版本不能讀格式 6。

## 十三個 data 陣列

| 陣列 | 內容 |
|---|---|
| tasks | 標題、備註、分類、截止及開始日期／時間／時區、狀態、完成／刪除／建立／修改時間、imageIds |
| categories、tags | ID、名稱、系統分類種類（適用時）、建立／修改時間及 sortOrder；不輸出可重算的 nameKey |
| task_tags、series_tags | 任務／系列的標籤關聯 |
| recurring_series | 系列內容、分類、生命週期、游標、刪除及刪除前生命週期、imageIds |
| series_rules | 規則版本、頻率、錨點、截止時刻／時區、結束條件及學習開始偏移 |
| occurrences | 任務／系列／規則關聯、序號、歷史快照、規則取消及同次系列刪除標記 |
| reminders、series_reminders | 固定／相對／系列規則、時區、基準、啟用狀態及可攜送達紀錄 |
| reminder_overrides | 本次明確自訂提醒標記 |
| settings | 最多一列，id 為 general；completedRetentionDays（null 為永久）、updatedAt、language |
| note_images | id 為小寫 SHA-256，data 為標準含 padding 的 Base64 JPEG |

`language` 限 `SYSTEM`、`TRADITIONAL`、`SIMPLIFIED`、`ENGLISH`；格式 6 設定列不可缺少或使用 null。欄位型別、必填性及主鍵以 [BackupModel.kt](../app/src/main/java/com/chimafok/todo/data/BackupModel.kt) 與 [BackupFormat.kt](../app/src/main/java/com/chimafok/todo/data/BackupFormat.kt) 核對，不能以 Room 欄位直接代替備份契約。

包含有效、已完成及垃圾桶資料。保留原刪除時間、刪除前生命週期與同次刪除關聯；匯入不延長 30 天垃圾桶資格。舊備份可能帶回已永久刪除且不再有 tombstone 的 ID，預覽須說明此限制。

不包含草稿、原始資料庫、快取、revision、保留寬限錨點、outbox、平台取消佇列、alarmKey／alarmMode、Android 權限或省電設定。可攜提醒歷史與平台鬧鐘身分不同，未來提醒重新排程，過去點不大量補發。

## 圖片與容量

imageIds 是按顯示順序、逗號分隔且不重複的小寫 SHA-256，空字串代表無圖片；快照可保留相同引用。格式 5／6 不限制每項張數。只輸出有效任務、垃圾桶、系列及歷史快照引用的圖片，不輸出未建立草稿圖片。

匯入驗證 Base64、SHA-256、JPEG 型別／尺寸及完整引用圖；單張至多 768 KiB、寬高 1–1536。按內容去重，依選定任務／整系列裁剪圖片集合；圖片與資料同交易還原。整份仍限 **64 MiB、200,000 列、12 層、5,000,000 個值**，過大會拒絕，不略過圖片。檔案限制不代表已通過同規模效能驗收。

## 合併、取代與舊檔

預設合併，以 ID、內容與已知修改時間比較，同名不同 ID 不自動合併。單筆任務與關聯／提醒一組，整系列與規則／紀錄／提醒一組；矛盾不能拆混。任一側涉及不同垃圾桶狀態、未知或相同修改時間但內容不同時須明確選擇。排序隨選定分類／標籤保存，同順位依 ID 穩定排序。

語言或保留期限不同時，須明確選擇整組一般設定。完整取代以檔案任務、垃圾桶、排序及設定為準；**舊格式缺少的偏好保留本機值，缺少 language 時即使完整取代也保留本機語言**。語言切換不改任務時區／提醒時刻，僅語言還原不重設完成保留錨點。

格式 1／2 不接受學習 IN_PROGRESS；舊格式沒有的開始欄位及圖片補空，不能偷帶新欄位。格式 1 的未知排序保留本機既有順序，新項附後；缺少設定不清本機。完整取代舊檔仍會移除檔案未含的本機任務及垃圾桶。

## 驗證與交易

先嚴格解析並驗證完整關聯圖，再預覽，再確認。拒絕無效 UTF-8、重複鍵、尾隨內容、未知欄位、錯型別、主外鍵矛盾及無效日期／時區／狀態。開始時間須附開始日期與時區，排序非負，保留天數為正整數或 null。

預覽凍結本機及檔案指紋；確認時同一 Room 交易重讀、重算與重驗，過期或任一步失敗全部回滾。取消零寫入。匯入的一般完成項或不同保留政策按契約建立復原寬限，不改原 completedAt；未變合併保留原錨點。先保存舊平台提醒的取消工作，再安排未來提醒。檔案選擇器與程序重建不會自行提交還原。
