# 資料模型：實作狀態與演進

## 1.1.0 開發版：學習開始時間與完整備份（schema8）

tasks 新增 nullable `startDate`／`startTime`／`startZoneId`。開始時刻須有日期與時區，日期可獨立存在；輸入使用既有日期簡寫及 HH:mm／HHmm。學習入口依內建分類的穩定 ID 識別，改名不影響此能力；移分類不破壞已保存的開始資料，其他分類不以該資料排序。

series_rules 新增 nullable `startDayOffset`／`startTime`／`startZoneId`。日偏移由表單開始日減本次截止日計算，未來各次依偏移生成；僅本次修改不覆寫模板，系列修改不回寫過去／已完成／已刪紀錄。新增作業僅允許三種未完成狀態，既有作業仍可完成；備份可保存已完成作業。

MIGRATION_7_8 只 ALTER 新增六個 nullable 欄位，不清庫或重建原表；schema1–7 繼續保留。日程在原截止排序後以開始日期／時間破同值；未安排以開始日期／時間排序，未填放最後，時間依捕捉時區換算。分頁索引與畫面使用同一排序。

備份格式升至2，加入垃圾桶刪除時間及系列復原關聯、分類／標籤 sortOrder、settings 一般設定，並包含開始欄位；settings 是匯出視圖，對應既有 retention_policy，沒有新增設定資料表。格式1可匯入，未知偏好保留本機，完整契約見 [BACKUP-FORMAT](BACKUP-FORMAT.md)。

## 1.0.4 選取與完整分組統計（schema7不變）

TaskPageWindow新增記憶體groupCounts（LocalDate?→Int），與同交易的完整匹配／首頁投影分組一起計算，之後才載入前綴完整內容；不新建表或寫進備份。HistorySearchRow只讀投影加入revision／excludedByRule，歷史全選以512列批次掃描匹配並只保留ID／版本，排除唯讀項，不把首50筆當全結果。

SelectionRepository提供TrashSelectionPreview（明確任務、系列版本、全部子項版本、復原集合與PurgePreview）及SeriesSelectionPreview（系列actionPreview及各時區預覽日）。均留ViewModel；復原集合保留更早單刪，永久刪系列集合涵蓋所有剩餘。確認在外層交易完整檢查後才重用既有Task／Recurring／History Repository，任何失敗回滾。跨系列預覽先固定一個Clock，避免讀取途中跨午夜產生不一致數量。沒有新Entity、schema遷移或JSON格式。

## 1.0.1 增量：新任務草稿（Room schema7 不變）

一份 App 私有 `filesDir/new-task-draft-v1.json`，AtomicFile、formatVersion1、1 MiB 限制。NewTaskDraft 保存 id、原始名稱／備註／日期／時間、zone／deadlineReference、category／status／tagIds、頻率／間隔／星期／結束條件／參考時刻、兩種提醒輸入 JSON、沿用提醒 stamp／templateIds。未建 Task，不執行提醒；不在手動備份的格式1內。讀寫在 IO 執行，未知版本／壞檔不自動刪；失敗寫回退舊檔。

提交 Task／系列成功才清稿；冷載入會檢查同 id 已在資料庫，避免崩潰遺留重複建立。失效分類回未分類、失效標籤排除並提示。這是新任務草稿，不是所有頁面狀態持久化。新提醒的 baseTime 工程預設改為 10:00；原資料每筆明確保存的 baseTime／offsetMinutes 不遷移，JSON 備份與提醒 Instant 契約不變。


## 目前 schema v7

0.5.3 的轉換在同一交易保存原 task／reminders、建立系列／規則／sequence=1／模板並寫 reminder_overrides，再校正未來。第一筆保留原提醒 ID／固定 Instant／時區及未改動的 DELIVERED／MISSED；原相對提醒仍跟隨明確改動的截止。固定模板按系列時區換算為曆日偏移與時分，未來用既有確定性 ID。新增草稿以草稿 taskId 採為第 1 次，重送同內容不重複建立。來源 revision 過期或任一步故障完整回滾；需要明確沿用，TaskDraft.inheritSingleReminders 僅是交易輸入，不是新增資料欄位。

資料庫 todo.db；Room 2.8.5／KSP。匯出的 [schema 7](../app/schemas/com.chimafok.todo.data.TodoDatabase/7.json) 是本版本依據；schema 1–6 保留作升級測試。0.6.0-dev 只新增 retention_policy／retention_anchors／purge_cancellations 三表，原十二表不重建。schema 7 identityHash：96eb7ccc6e9fb605d945981ea26464f2。

| 表 | 欄位與約束 |
|---|---|
| categories | id 主鍵、name、systemKind 可空且唯一、sortOrder、createdAt／updatedAt UTC 毫秒、revision。一般分類 systemKind=null；作業 ASSIGNMENTS、未分類 UNCATEGORIZED；系統 ID 固定 |
| tasks | id 主鍵 UUID；title、notes、categoryId（RESTRICT 外鍵）；dueDate ISO 日期、dueTime ISO 時間、zoneId 各可空；status enum；completedAt、deletedAt 可空；createdAt／updatedAt 毫秒、revision |
| tags | id 主鍵、name、nameKey（trim＋Locale.ROOT 小寫）、sortOrder、createdAt／updatedAt、revision；nameKey 為一般索引，新增／改名的防重複由交易驗證，不以唯一名稱索引阻斷未來匯入同名異 ID 的契約 |
| task_tags | taskId／tagId 複合主鍵；兩者分別有 CASCADE 外鍵；tagId 索引。刪連結／標籤不刪任務，永久刪任務才自動清其連結 |
| recurring_series | id 主鍵、title、notes、categoryId（RESTRICT）、lifecycle、cursor、deletedAt、previousLifecycle、createdAt／updatedAt、revision。模板不含可繼承的完成狀態 |
| series_rules | id 主鍵、seriesId（CASCADE）、version、effectiveFrom、anchor、frequency、intervalDays、weekdayMask、dueTime、zoneId、endDate、maxCount、createdAt；唯一 (seriesId, version)。規則版本插入後不覆寫，maxCount 為全系列總數 |
| series_tags | seriesId／tagId 複合主鍵，分別 CASCADE 外鍵；tagId 索引。之後新生成的實例複製此模板連結 |
| occurrences | taskId 主鍵（tasks CASCADE）、seriesId（RESTRICT）、sequence、ruleId（RESTRICT）、snapshot JSON 可空、deletedWithSeriesAt 可空、excludedByRule 非空 boolean 預設 false；唯一 (seriesId, sequence)，ruleId 索引 |
| reminders | 單次提醒 id、taskId CASCADE、FIXED／RELATIVE_DUE、zoneId、fixedAt／offsetMinutes、baseTime、enabled、state、resolvedAt、alarmKey、alarmMode、revision、createdAt／updatedAt；taskId 與 (state,resolvedAt) 索引 |
| schedule_outbox | taskId 主鍵、generation、processedAt；不設 task FK，以保留需取消的 dirty marker；永久刪除前仍須獨立保存 alarmKey |
| series_reminders | id 主鍵、seriesId CASCADE、kind RELATIVE_DUE／OCCURRENCE_TIME、offsetMinutes／dayOffset／time、baseTime、enabled、createdAt／updatedAt；seriesId 索引，停用模板保留 ID |
| reminder_overrides | taskId 主鍵／tasks CASCADE；存在代表該次提醒明確自訂（含清空），不被系列模板覆寫；明確重設刪此標記 |


首次建立資料庫時種入學習、生活、作業、未分類。Repository 驗證名稱、日期時間依存、作業／一般狀態；完成才有 completedAt。相同新增請求 ID 不重複插入，編輯以 expectedRevision 避免覆蓋較新資料。所有修改用 Room transaction；不以 UI 列表當唯一資料來源。

status：一般只 OPEN／COMPLETED；作業只 NOT_STARTED／IN_PROGRESS／DONE_UNSUBMITTED／COMPLETED。作業特殊行為依固定 ID 與 systemKind，將來改名仍有效。完成未繳交保留在未完成列表。軟刪只設 deletedAt，復原保留 ID、狀態與原日期；到期拒絕復原。0.6.0 已接 30 天到期永久清理及明確確認的手動刪除；背景實際執行受系統限制。

截止輸入可用四種日期及三種時間格式（HH:mm／HHmm，兼容 HH-mm），時間提示與預覽用 HH:mm，保存正規化成 LocalDate／LocalTime。MMDD 月日較早，或同日且指定時刻的 Instant 早於輸入參考時刻，均採下一年；只有日期今天不跨年。參考 Instant 與時區只屬草稿狀態，解析後持久化完整日期，旋轉／非截止編輯不重解。目標年無該日期則拒絕，完整／6 碼明確年不變。過去截止確認只在表單處理，不繞過 Repository 驗證。輸入或選定日期／時間才記錄當前 IANA zoneId；日期-only 的 zoneId 僅記錄背景，不改浮動日曆日，舊版未知則保持 null。帶時刻依原日期／時間／時區換算 Instant，日程用所在地日期分組並附原值。重試同 ID 新增也比較時區與標籤集合。

任務與全部標籤連結同交易保存。刪標籤時更新受影響任務的 revision／updatedAt；刪一般分類時同交易搬移全部有效／完成／垃圾桶內任務，移入作業須齊備各項狀態且版本相符才提交。系統分類保護、名稱及排序過期檢查都在 Repository 執行，並非只隱藏 UI 按鈕。

0.3.0 的查詢／選取只屬可恢復畫面狀態，不升 schema。批量三種修改同交易先重查全部 ID／revision／未刪狀態，任一過期全拒絕，中途 DB 錯誤全回滾；加標籤聯集而非取代，沒有新增連結不改 revision。移分類不改截止／時區／標籤，完成狀態的 completedAt 保留，改回未完成清空；批量軟刪使用同一時間戳，保留其他資料。範圍隔離及標籤 AND／OR 由可測試的純查詢函式處理，原本列表分組排序規則保留。

## 已實作 migration 1→2

只 CREATE tags／task_tags 與索引，不重建／刪除／回填 tasks 或 categories。既有 ID、截止、時區、狀態、revision 原樣保留；不使用 destructive fallback。裝置測試使用匯出 schema 1 JSON 建立真實 v1 檔案，插入舊任務後由 Room 開啟升級，再驗證其資料與新標籤關聯能保存。歷史測試不等於本次重跑；公開版本的驗證範圍見 [TESTING](TESTING.md)。

## 已實作 migration 2→3 與系列契約

只 CREATE recurring_series／series_rules／series_tags／occurrences 及索引；不改動原四表。PRD 的 Task.seriesId／sequence／ruleVersion 等邏輯欄位以 occurrences 一對一關聯實作，避免重建舊 tasks。schema 3 identityHash 為 1c2a3cab6b5e7b1aa024d595f444269e。舊庫驗證用實際匯出 schema 2 建非空資料庫，再由 Room 開啟升級；另保留 1→3 串接驗證。

系列 lifecycle 為 ACTIVE／STOPPED／ENDED／TRASHED；cursor 為已消耗的全系列最大序號。實例 ID 由 seriesId＋sequence 穩定產生，刪除、復原及重新完成都不倒退游標。建立與明確完成／刪單次事件最多預備一筆未來，日曆補齊則按系列保存時區生成到今天，分批交易直到完成。快照、任務變更、實例生成與游標更新同一交易；失敗回滾。

規則 effectiveFrom 將局部規則的第一筆對應全系列序號。編輯相同月曆規則時保留原月底錨點；總次數包含先前產生及已刪次數。0.4.1 已將本次之後的有效未來待辦重排，保留各次 ID／狀態；過去、完成與已刪除紀錄不改。

snapshot 保存退休／已完成時的名稱、備註、分類名稱、標籤名稱、狀態及逾期完成結果，原日期／時間／zoneId 由 Task 保存。跨日後未及背景校正就改分類／標籤名稱時，先擷取舊快照；管理操作不改已有歷史。日期-only 完成使用當下裝置日期；保存後不因旅行或移分類而重算原逾期結果。長存 Repository 的預設 Clock 動態取得裝置時區，測試可注入固定 Clock。

停止不刪既有實例；整組刪除另外記 previousLifecycle 與每筆 deletedWithSeriesAt。復原只處理屬於同次群組刪除的紀錄，因此此前單獨刪除的任務不會誤復原；保留 cursor，重新校正漏過日期。單獨復原會拒絕仍在垃圾桶的父系列，UI 引導明確確認整組復原。0.6.0 到期清理先清已到期子任務，空且到期的父系列才移除規則／模板。單次永久刪除保留父系列與 cursor。

畫面透過 Room invalidation flow 監聽八表，每次以同一讀取交易取得完整快照，避免新實例短暫被誤當普通任務。首頁單筆是查詢投影；所有實例都持久保存，已完成主頁排除 occurrence 關聯，歷史頁另行查閱。既有單次編輯不能清除重複紀錄的日期。

## 既有任務轉系列（0.4.2，schema 不變）

未完成且未刪除的非循環任務可在單一交易轉為系列。原 taskId 同時作為系列 ID，原任務以 occurrences 關聯至 sequence=1，保留 createdAt 與目前狀態；系列本身的 createdAt 是轉換時間。第一筆不改成 occurrenceId(seriesId, 1)，後續 sequence≥2 才採既有確定性 ID。任何使用實例的流程都從 occurrences 取得 taskId，不自行假設第一筆 ID。游標從 1 起，總次數包含被接納的原任務。

先驗證 expectedRevision、存續及未完成狀態，再保存表單、插入系列／規則／模板標籤／第一筆關聯與第一批日期校正。中途失敗連原任務與標籤修改一併回滾；重送的舊轉換不建立第二個系列。已完成紀錄須先重開，垃圾桶需先復原，不重分類舊歷史。之後的新作業從未開始起，原本進行中或完成未繳交的第一筆不被重設。

## 已實作 migration 3→4 與重排

只執行 `ALTER TABLE occurrences ADD COLUMN excludedByRule INTEGER NOT NULL DEFAULT 0`；八表其餘欄位不變。schema 4 identityHash 為 1cdc68c83af40a93638e3df4202ca0b2。非空 v3 測試包含真正的系列／規則／任務／實例關聯，不以空表升級取代。

`planSeriesEdit` 是表單與交易共用的純規劃。來源必須未完成、未刪除、未規則取消，且其保存時區的日期仍未退休。其他序號較高且有效的未來未完成項目採新內容／分類／標籤／截止，保留狀態、ID 及 createdAt。作業類型轉換依既有映射；已完成或已刪除、日期已退休的項目不改。

新規則排除既有未來槽時，先保存原內容快照，再設 excludedByRule=true；Task 的原截止／內容／狀態留存，不設 deletedAt。首頁投影及未來提醒必須排除；歷史顯示規則取消且只讀。之後規則再次涵蓋該槽，可改新截止／內容並將標記清除，保留原 ID／狀態；使用者單獨刪除的項目不會復原。已取消且仍不符合的槽不重複寫入，不清理／重用游標。整組刪除及復原仍保留這個取消標記。

開表單時保存來源 revision、系列 revision 及所有相關任務 revision；確認交易重查全部，避免單次編輯不改系列 revision 時被覆蓋。新規則版本、模板及標籤、每筆重排／取消／啟用、本次保存、快照和游標校正同一交易；中途失敗全部回滾。大型歷史的讀取及草稿版本集合壓縮仍隨正式性能驗收處理，不能宣稱已達 50,000 歷史門檻。

首次新增 schema 檔曾遇兩個 KSP 版本同時讀寫的競爭；Gradle 明確令 kspQaKotlin 在同一建置有 kspDebugKotlin 時排後，兩個 schema 寫入者不並行。不新增破壞式重建或清庫作為修復。

## 已實作 migration 4→5 與單筆提醒

新增 reminders 與 schedule_outbox，以及提醒 taskId、(state,resolvedAt) 索引；原八表不改。schema 5 identityHash 為 65712165b5ecf953846b23fa9367ebdb。資料庫建立／4→5 遷移時安裝 IF NOT EXISTS 的 task／提醒定義觸發器；它們與業務交易共用原子性，包含分類搬移、批量和舊 Repository 呼叫。

reminders：id 主鍵、taskId CASCADE、kind FIXED／RELATIVE_DUE、zoneId、fixedAt 或 offsetMinutes、baseTime、enabled、state、resolvedAt、alarmKey、alarmMode、revision、createdAt、updatedAt。固定／相對欄位互斥；10:00 為新提醒基準（既有保存值不變），是提醒欄位，不寫 Task.dueTime。enabled=false 保留取消 tombstone；state 為 PENDING／BLOCKED／DELIVERED／MISSED／CANCELED。alarmKey 包含 taskRevision、reminderRevision 及 triggerAt，平台 URI 再包含完整 taskId／reminderId。

schedule_outbox：taskId 主鍵、generation、processedAt；任務或提醒定義修改以 INSERT SELECT WHERE NOT EXISTS 後 UPDATE 增 generation 並清 processedAt（避免要求新版 SQLite UPSERT，也不依賴會被外層 Room OR ABORT 蓋過的 OR IGNORE），不含任務文本。僅在平台操作成功後以相同 generation 標記完成；失敗可重試。觸發器對完成／刪除同步取消未發提醒；其他時間解析與平台鬧鐘由 reconcile 驗證／校正。Reminder 交付狀態等暫態更新不重新產生 dirty marker，避免自我循環。

任務保存與提醒全量定義、標籤、dirty marker 同交易，非法相對日期或第二筆提醒寫入失敗全部回滾；revision 擋過期草稿。畫面讀取加入 reminders 表，完整資料仍以 Room 同交易快照呈現。單筆提醒的編輯草稿保存 ID／未完成輸入／IANA zone／參考 Instant；固定提醒未改動時保留毫秒精度，不因 UI 只顯示分鐘而漂移。

系列模板、背景生成及永久清理取消契約見後續已實作章節；提醒操作與恢復說明見 [USER-GUIDE](USER-GUIDE.md#提醒)，驗證缺口見 [KNOWN-ISSUES](KNOWN-ISSUES.md)。不能因有 schema 5 而宣稱 F06 全部完成。

## 提醒編輯修正（0.5.1，schema 仍為 5）

截止前的 quantity／unit 僅是可旋轉恢復的表單狀態；交易只保存驗證後的 offsetMinutes，單位不新增資料欄。固定時間留空按截止有效時間（先換算提醒時區）或 baseTime 解析 fixedAt；保存後不再動態繼承，baseTime 亦保留。重新編輯以有效時間回填，未改動時保留既有秒／毫秒。

保存任務／提醒同交易寫 resolvedAt；DAO 以 resolvedAt DESC、createdAt、id 讀取。詳情／編輯入口從當前任務推導排序，以容納升級後尚未由 scheduler 計算的舊 null 值，不在資料庫 onOpen 改寫資料。後臺 reconcile 仍從定義重算，發送前再檢查版本與時刻。改截止導致已發／錯過相對提醒移動時，保存即設 PENDING 並保留舊 alarmKey 等持久取消；相同終態不重送，完成／刪除保持取消。這些 bookkeeping 不增加提醒定義版本，不觸發 dirty 自我循環。

## 已實作 migration 5→6 與系列提醒

只 CREATE series_reminders／reminder_overrides 及 seriesId 索引，原十表和原提醒觸發器不變。schema 6 identityHash 為 416c2aed8993b079a8fa9cfac35fb09c。遷移不在 onOpen 做寫入；非空 v5 的提醒送達終態、alarmKey、revision 及 outbox 直接保留。

模板存最新定義，歷史與已產生各次仍有獨立 reminders，不靠回讀最新模板來猜舊固定時刻。occurrenceReminderId 由完整 taskId＋templateId 產生確定性 UUID；新增每次任務／occurrence／標籤／提醒與游標同交易。模板修改使用系列與全部實例 revision 保護；只更新可改未來且未自訂項目，相同固定模板保留既有 fixedAt。所有單次自己的相對規則仍隨其截止重算。

單次保存的提醒定義與原資料不同時新增 override；未更動提醒不加標記。使用者重設時驗證系列版本，刪標記並按目前日期／模板重新物化；寫入錯誤全部回滾。模板停用與提醒 tombstone 保留，取消平台鬧鐘仍用既有 durable outbox；不先刪唯一 alarmKey。

有模板的 ACTIVE 系列按最大提前量＋兩天分批生成，讓即將觸發的提醒先有實體任務；無模板依原日期生成。停止不再生成但保留現有任務／提醒，規則取消在交付前檢查 occurrence。背景維護不需 Activity；個別系列故障不阻擋其他系列補齊及獨立鬧鐘恢復。大量歷史／極端十年提前量的效能仍須正式矩陣，不宣稱已達 50,000 筆驗收。

## 已實作 migration 6→7 與清理交易

只 CREATE 三張新表，不做 onOpen 寫入。retention_policy 為 id=1 的本機偏好（days nullable、revision、updatedAt），沒有列即永久；不是可備份的一般任務資料。retention_anchors 用 taskId 外鍵 CASCADE，保存 completedAt／restoredAt，以標記與本次完成相符的復原寬限，不修改 tasks.completedAt；重新開啟／完成清除標記，其他寫入用完成時間比對避免沿用不同週期。

purge_cancellations 無外鍵，保存唯一 ID、taskId、reminderId、舊 alarmKey（可空）及建立時間。任務和提醒消失後仍可取消系統舊鬧鐘／移除通知；同交易先寫取消列、刪任務（cascade 標籤關聯／occurrence／提醒／override／anchor）、清 task outbox。引擎在沒有 dirty task 時也先處理佇列；取消成功才刪列，失敗列保留，其他鬧鐘繼續校正。原始題名／備註不留在這張表。

保留預覽保存設定 revision 與受影響 ID／revision；提交重新計算筆數及期限、一次交易寫政策與 deletedAt=now。自動清理共用相同選取邏輯；僅非循環且有 completedAt 的 COMPLETED 參與保留。逐項永久刪除驗證垃圾桶狀態／revision，清空再驗證完整範圍，所有錯誤全回滾。單次 purge 移除 occurrence 但 cursor 不退，並遞增父系列 revision 使舊預覽失效。空垃圾桶系列可明確復原，過期且無子項才自動刪父系列。

## 檔案搬移（0.6.1，schema 7 不變）

備份使用十一個可攜陣列；不直接複製 SQLite 檔。範圍、每欄及合併單位見 [BACKUP-FORMAT](./BACKUP-FORMAT.md)。DB snapshot 在 Room 交易讀取，排除垃圾桶／本機偏好／native 狀態；匯入先完整驗證，再比對預覽業務指紋與檔案指紋，最後以單交易按 FK 次序置換／合併。相同內容重匯 no-op。

保留分類／標籤既有排序及本機政策，真正加入的一般完成項給新 anchor，原 completedAt 不改。原平台取消資訊在刪列前存入既有 purge_cancellations，已有列不清除；同 ID 的本機 revision 遞增，不接受檔案重設舊通知版本。舊送達終態相符則保留，其餘過去提醒標 MISSED，之後由既有 Runtime 校正。

## 後續 migration

1. Phase 4 剩餘更完整的長離線／跨區 UI 矩陣，保持穩定 ID、不可回退游標及舊歷史。
2. Phase 5 已有單筆／系列提醒、outbox 及背景生成，接著補完整冷程序／跨區與實機矩陣；日期無時間的相對提醒另存可調基準，不改寫截止時間。
3. Phase 6 的保留清理及檔案備份／匯入已接上，後續驗收外部供應商與大資料性能；備份不含垃圾桶／排序／一般偏好；同 ID 修改時間衝突明確選擇。

每次升 schema 都要提供從上一版的非破壞式 Migration，保留 schema JSON，加入舊庫升級測試；不得使用 fallbackToDestructiveMigration。歷史 P0 尚無 DB；0.2.0 起首次涵蓋已存在的 v1 資料庫升級，不能僅以新空庫建立取代升級驗證。

目前列表為基本資料規模一次觀察；正式 v1 前須按 PRD 改成分頁／查詢，驗證 10,000 任務／50,000 歷史性能。尚未宣稱大資料量達標。

## 0.7.1 查詢模型（schema7 不變，2026-10-24）

BrowserDao 使用既有表／系列索引，沒有新增欄位或破壞式遷移。首頁的 task／occurrence／task_tag／reminder／override 使用同一條範圍：普通任務、未完成重複，或明確要求的編輯系列；整理與垃圾桶可取全資料。系列／規則／模板仍完整供導航與表單使用。

HistoryRow 是 task＋occurrence 的只讀投影；HistoryWindow 含首 N 列、對應當前標籤、完整命中數、SQL 系列統計及概要命中 ID。首批 50、每次加 50；每輪交易重讀首 N 與總數。全文搜尋以 512 列暫存批次掃描、解碼快照後作 Kotlin 忽略大小寫子字串比對；已要求的 N 筆累積保留，不是 LRU 頁面快取。

SeriesActionPreview 只讀 ID／revision／截止／狀態／刪除時間，包含所有實例的版本及未刪筆數／未來未完成筆數。ViewModel 暫存 map，確認仍交既有交易重驗全圖；不把大型 map 放 Bundle。編輯系列時保留原有完整 SeriesEditRecord 上下文，未用已載入頁假裝全部歷史。

## 0.7.2 搜尋投影（schema7 不變）

HistorySearchRow 只含 taskId、seriesId、sequence、title、notes、categoryId、snapshot、dueDateKey（原始 SQLite 日期文字），不是持久表。總覽按 sequence 游標掃描，系列內按原 SQLite 截止倒序＋sequence 接續，避免修改截止後與序號順序不同造成漏列。每批最多 512；總覽首批 32，命中可停止。系列內完整計數後，僅為要求的前 N 個 ID 讀取 HistoryRow 與當前標籤；ID 讀回亦分批，無超大 IN 清單。

HistoryWindow.total 是系列內的完整命中數，總覽不提供全庫命中列數；總覽 matchingSeries 包含系列名稱或任一未刪紀錄命中，counts 仍是完整完成／未完成／取消統計。所有讀取在同一交易，原編輯及系列刪除的完整版本預覽不變。

名稱／備註／分類／每個標籤逐欄比對，不串接文字；快照必要才解析。單次搜尋僅暫存上一份相同 JSON 的解析結果，返回後不留搜尋索引或跨查詢快取。備份格式、十五張持久表及 schema identityHash 不變。

## 0.7.3 一般列表查詢模型（schema7 不變）

TaskIndexRow 僅含 ID、分類、日期時間／時區、狀態／完成時間、建立時間、revision 及可空的系列序號／規則取消旗標；不是持久表。暫時轉成無內容的排序投影以重用純規則，不顯示也不保存該投影。TaskPageWindow 含完整結果 revision map、目前前綴的實際 TaskPageRows 及日期／無日期還有更多旗標。

精簡索引仍完整載入並在背景投影／排序，文字與標籤搜尋每批最多 400 個 ID。完整內容與五類關聯只依目前前綴讀回；有日期與無日期各首批 50，跳日期另加該日 N 項。這是完整內容分批，不是全流程固定記憶體的游標；索引／結果 ID 仍 O(N)，累進視窗及大型操作仍會成長。

ViewModel 分開維護可見 IDs 與 pending recurring／編輯／批次 contextIds，離開頁面釋放可見內容；所有實際 TaskEntity 均讀自 Room，stub 不會進入 UI 或寫入。批次預覽完整 ID／revision、所選 ID 與逐項狀態放 ViewModel，版本變動後仍由原 repository 交易驗證；大型 map 不放 Bundle。整理／垃圾桶保持完整範圍。

## 0.7.4 排程確認與暫存狀態

仍為 Room schema7／備份格式1。`schedule_outbox` 維持 taskId 主鍵、generation 與 processedAt；平台工作全部成功後，在同交易用原先捕捉的 taskId／generation 條件確認，最多每批 400 列、801 參數。過期版本不能清掉新工作，已刪任務的無外鍵記號仍可處理。這是寫入批次改進，不是刪掉持久排程／取消佇列。

大型系列編輯、刪除／保留預覽與衝突選擇為 ViewModel 暫存，不寫入 Bundle 或產品資料表。旋轉保留原捕捉範圍；新程序失去預覽時重新開啟確認，不自動提交。開發、QA 與正式包維持隔離，各自管理私有資料與明確確認的備份還原。
