# 備份格式與還原契約

格式1／Room schema7。輸出為未加密JSON；一般操作見 USER-GUIDE，簽章搬移見 INSTALL。

## 檔案範圍

UTF-8 明文 JSON，最外層必須恰好有 format="Todo-App"、formatVersion=1、schemaVersion=7、exportedAt（UTC 毫秒）、appVersion、data。data 包含下列十一個陣列；陣列次序不影響識別，以穩定主鍵／複合鍵比對。不同格式／schema 版本明確拒絕，不猜測或清庫。

| 陣列 | 匯出欄位 |
|---|---|
| tasks | id, title, notes, categoryId, dueDate, dueTime, zoneId, status, completedAt, createdAt, updatedAt |
| categories | id, name, systemKind, createdAt, updatedAt |
| tags | id, name, createdAt, updatedAt |
| task_tags | taskId, tagId |
| recurring_series | id, title, notes, categoryId, lifecycle, cursor, createdAt, updatedAt |
| series_rules | id, seriesId, version, effectiveFrom, anchor, frequency, intervalDays, weekdayMask, dueTime, zoneId, endDate, maxCount, createdAt |
| series_tags | seriesId, tagId |
| occurrences | taskId, seriesId, sequence, ruleId, snapshot, excludedByRule |
| reminders | id, taskId, kind, zoneId, fixedAt, offsetMinutes, baseTime, enabled, state, resolvedAt, createdAt, updatedAt |
| series_reminders | id, seriesId, kind, offsetMinutes, dayOffset, time, baseTime, enabled, createdAt, updatedAt |
| reminder_overrides | taskId |

只匯出未刪任務及未刪系列；已完成也包含。關聯、歷史快照、規則版本、游標、停用的提醒定義及單次 override 一併保留。已刪單次留在游標中的空位，不為匯出補造紀錄；還原不能倒退游標重新生成這些 ID。整組垃圾桶不包含在檔案。分類／標籤同名不同 ID 仍是不同資料；系統分類按 systemKind 轉成本機固定 ID，轉換後重查重複及外鍵；匯入也不可繞過「未分類」不可改名的保護。

不匯出排序、nameKey、本機 revision、保留政策、復原 anchor、outbox、待取消平台佇列、alarmKey 或 alarmMode。提醒 state／resolvedAt 是可攜的送達歷史資訊，不是平台權限；只有 DELIVERED／MISSED 且與重新解析的時刻相同才可保留終態，未來未送達點重新安排、過去未送達點標 MISSED。不匯出 Android 原始資料庫檔。

## 輸入驗證與上限 [A/T]

由有位元組上限的串流與嚴格 JsonReader 讀取，不先配置整份輸入緩衝。上限 64 MiB、200,000 資料列、巢狀 12 層及 5,000,000 個值；拒絕無效 UTF-8／代理字元、重複 JSON key、尾隨內容、分數／溢位整數、未知欄位及錯誤型別。這些是第一版工程界線，不宣稱極大量記憶體／效能已達正式驗收。

任務、分類、標籤名稱／日期／狀態／時區與提醒使用現有規則；外鍵及複合鍵全部驗證。系列的版本、effectiveFrom、cursor、次序／規則引用與單次 override 必須完整一致；cursor 0–10,000,000 為匯入保護上限。不自動補無效父列或套假預設。非獨立的系列提醒必須能追溯至該系列保留的模板 ID。歷史快照也經嚴格解析。

createdAt 必須存在；updatedAt 缺少／null 則標記為 0（未知），保存時不換成匯入時間。同 ID 異內容遇未知時間必須選版本。相同內容忽略建立／修改時間差及平台排程狀態，保留本機資料，不為重匯做無效寫入。

## 合併與確認

預設合併。一般任務連同標籤及提醒定義比較，已知修改時間較新者勝；父任務與子提醒的較新方向矛盾時須明確選版本。同時／未知時間、垃圾桶同 ID 均須選擇，沒有預選檔案版；同名不同 ID 不以名稱去重。

系列以模板、規則、實例、歷史快照與提醒作一致性聚合。槽位或關聯集合有差異、各部分時間方向不一致，以及普通任務轉系列／跨任務重用提醒 ID 造成的所有權交叉，都合併為整組衝突，只能明確保留整組本機或採用整組檔案，避免混出孤兒或兩個目前指標。明細可查看任務狀態、日期、筆記、分類標籤、規則與提醒；長串明細用虛擬清單延後格式化。

預覽列出本機／檔案筆數、新增／更新／移除任務、分類／標籤／系列總數及同名異 ID 提示。完整取代明示會清本機垃圾桶，提供「先匯出目前資料」。已永久刪除且無 tombstone 的 ID 可由舊檔重新加入，畫面有說明。

預覽同時捕捉本機指紋與檔案資料指紋；本機指紋包含業務資料、revision、排序、政策及 anchor，排除純排程 bookkeeping。確認後同交易重新讀取、比對指紋、重算選擇並重驗完整圖。過期或故障全回滾；重新預覽會清除舊衝突選擇，須重新核對。取消、選檔取消、錯檔都不寫任務資料。

## 寫入、提醒與本機偏好

取代清舊業務資料及垃圾桶；合併保留未被明確替換的有效／垃圾桶資料。分類／標籤既有排序按 ID 保留，新項附加；保留政策與既有獨立取消佇列不清。未變合併是 no-op；改動任務／系列／提醒遞增本機 revision，完整取代也遞增同 ID 的本機 revision，避免舊通知或表單因重設版本而重新有效。

刪掉舊提醒前，同交易保存舊 taskId／reminderId／alarmKey 到既有獨立取消佇列；成功校正才清佇列，取消故障可重試且不阻擋其他提醒。保留相符的已發終態、不補發過去點；提交後由既有 Runtime 觀察資料並校正，不在檔案驗證前動平台鬧鐘。

一般完成任務由檔案真正加入／取代後，用現在作新的復原寬限起點，completedAt 原樣保留，避免本機有限保留政策立即把剛還原紀錄清走。合併未變者沿用原 anchor；系列歷史不受完成保留期限影響。

## 檔案選擇與接續

使用 CreateDocument(application/json) 與 OpenDocument，讀寫 ContentResolver；不要求廣泛儲存權限。使用者自行選目的地，App 不新增網路同步。App-private cache 暫存檔案，Bundle 只放 token／指紋／選擇，不放整份 JSON；新建暫存時清除逾 24 小時舊檔。暫存被系統收回時要求重新選擇，不自行套用。

檔案選擇器回來保持設定／預覽；一般背景重返仍回日程。旋轉保留未提交模式、衝突選擇及原預覽指紋。程序重建只恢復可檢查狀態，沒有自動執行還原；再次確認仍做資料版本檢查。外部供應商的讀寫失敗可顯示錯誤；匯出目的地可能留下未完整檔案，但匯入嚴格驗證不會先傷原庫。

QA-only provider 只放 src/qa，非公開且不包進正常 App；測試攔截系統選檔 Intent 後，以真正 ContentResolver／provider 完成讀寫。這不等於已操作所有系統檔案選擇器或雲端供應商，實機流程仍留整合驗收。
