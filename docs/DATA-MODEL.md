# 資料模型與非破壞式遷移

<p align="center">語言 / 语言 / Languages：<strong>繁體中文</strong> · <a href="zh-CN/DATA-MODEL.md">简体中文</a> · <a href="en/DATA-MODEL.md">English</a></p>

目前為 **Room schema 10**，資料庫 `todo.db`。實際欄位、主外鍵及索引以匯出的 [10.json](../app/schemas/com.chimafok.todo.data.TodoDatabase/10.json) 與 [TodoDatabase.kt](../app/src/main/java/com/chimafok/todo/data/TodoDatabase.kt) 核對；下表欄位直接取自該 schema，不使用產品概念名稱代替 SQL。

## 現行資料表

| 表 | 欄位 |
|---|---|
| `tasks` | `id`、`title`、`notes`、`categoryId`、`dueDate`、`dueTime`、`zoneId`、`status`、`completedAt`、`deletedAt`、`createdAt`、`updatedAt`、`revision`、`startDate`、`startTime`、`startZoneId`、`imageIds` |
| `categories` | `id`、`name`、`systemKind`、`sortOrder`、`createdAt`、`updatedAt`、`revision` |
| `tags` | `id`、`name`、`nameKey`、`sortOrder`、`createdAt`、`updatedAt`、`revision` |
| `task_tags` | `taskId`、`tagId` |
| `recurring_series` | `id`、`title`、`notes`、`categoryId`、`lifecycle`、`cursor`、`deletedAt`、`previousLifecycle`、`createdAt`、`updatedAt`、`revision`、`imageIds` |
| `series_rules` | `id`、`seriesId`、`version`、`effectiveFrom`、`anchor`、`frequency`、`intervalDays`、`weekdayMask`、`dueTime`、`zoneId`、`endDate`、`maxCount`、`createdAt`、`startDayOffset`、`startTime`、`startZoneId` |
| `series_tags` | `seriesId`、`tagId` |
| `occurrences` | `taskId`、`seriesId`、`sequence`、`ruleId`、`snapshot`、`deletedWithSeriesAt`、`excludedByRule` |
| `reminders` | `id`、`taskId`、`kind`、`zoneId`、`fixedAt`、`offsetMinutes`、`baseTime`、`enabled`、`state`、`resolvedAt`、`alarmKey`、`alarmMode`、`revision`、`createdAt`、`updatedAt` |
| `schedule_outbox` | `taskId`、`generation`、`processedAt` |
| `series_reminders` | `id`、`seriesId`、`kind`、`offsetMinutes`、`dayOffset`、`time`、`baseTime`、`enabled`、`createdAt`、`updatedAt` |
| `reminder_overrides` | `taskId` |
| `retention_policy` | `id`、`days`、`revision`、`updatedAt`、`language` |
| `retention_anchors` | `taskId`、`completedAt`、`restoredAt` |
| `purge_cancellations` | `id`、`taskId`、`reminderId`、`alarmKey`、`createdAt` |
| `note_images` | `id`、`data` |

日期／時間為 ISO LocalDate／LocalTime 字串，操作時間為 UTC epoch 毫秒；日期-only 不轉成 UTC 午夜。有時刻的開始／截止保存輸入時區，提醒解析為 Instant。任務含一個分類及多個標籤，系列與 occurrence 分表，以 seriesId＋sequence 保持唯一槽位。完整索引、外鍵及 SQL 定義見共用 schema JSON。

## 遷移

| 遷移 | 非破壞式新增 |
|---|---|
| 7 → 8 | tasks 開始日期／時間／時區；series_rules 開始日偏移／時間／時區，皆 nullable |
| 8 → 9 | tasks／recurring_series 的 imageIds（預設空）；note_images 內容定址圖片表 |
| 9 → 10 | retention_policy.language 非空 TEXT，預設 SYSTEM；保留 days、revision、updatedAt |

schema 1–10 保留供逐版升級測試；早期 migration 及原始設計詳見[歷史模型](history/DATA-MODEL-HISTORY.zh-Hant.md)。不得使用破壞式 fallback 或清庫代替升級。

## 固定分類與狀態

- 未分類、作業及學習依固定身分保護，不以顯示名稱判斷。學習 STUDY_ID 的 systemKind 仍為 null，可改名／排序但不可刪除；作業可改名不可刪，未分類不可改名或刪除。
- 一般任務 OPEN／COMPLETED；學習 OPEN／IN_PROGRESS／COMPLETED；作業 NOT_STARTED／IN_PROGRESS／DONE_UNSUBMITTED／COMPLETED。新學習／作業不可直接建立為 COMPLETED。
- COMPLETED 才有 completedAt；完成未繳交仍未完成。重新開啟清目前完成時間，保留 ID／原截止。
- 學習開始時刻須有開始日期與時區；移分類保留已存開始資料，但只有學習使用其排序能力。
- 內建名稱僅在固定 ID 且仍為原始名稱時翻譯顯示；不改寫 DB 名稱，不翻譯自訂內容。

## 交易、排程與保留

所有業務修改在 Room 交易核對 ID、revision 與完整成員範圍；任一過期或失敗全回滾。重送相同新增 ID 不重建，系列游標不因重開、刪除或還原倒退。歷史 snapshot 保存當次內容，不隨模板改寫；首頁至多投影每系列一個相關項，不代表其他提醒取消。

schedule_outbox 保存待校正 generation，平台工作成功後才確認 captured generation；並發新版本留待下輪。永久刪除前將舊鬧鐘取消資訊放入無任務外鍵的 purge_cancellations，刪除後仍可重試；其中不保存任務文字。

retention_policy 保存一般完成保留期限及語言；retention_anchors 提供復原／匯入寬限，不改原 completedAt。垃圾桶按原 deletedAt＋30 天到期；重複歷史不受一般完成保留期限影響。

## 圖片、草稿與查詢

note_images 以 SHA-256 識別不可變 Base64 JPEG；imageIds 為按順序的引用，圖片不放 Bundle 或搜尋索引，縮圖／大圖按需載入。表單關閉時按任務、系列、歷史快照及已存草稿引用清理孤立圖片。

新增草稿是 App 私有 AtomicFile JSON，不是 Room 任務，不排提醒，也不在備份內。保存成功才清稿；重載檢查相同 ID 是否已建立，避免重複。表單與大量確認狀態留在 ViewModel，程序重建不自動提交舊確認。

一般列表與歷史採精簡投影及累進視窗（首批／增量 50）。完整結果 ID、統計及部分批次操作仍可能 O(N)；分頁不代表全庫效能已驗收。備份使用獨立格式 6 契約，見 [BACKUP-FORMAT](BACKUP-FORMAT.md)；不能直接複製所有 Room 表作可攜備份。
