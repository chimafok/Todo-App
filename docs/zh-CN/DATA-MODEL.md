# 数据模型与非破坏式迁移

<p align="center">語言 / 语言 / Languages：<a href="../DATA-MODEL.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/DATA-MODEL.md">English</a></p>

目前为 **Room schema 10**，数据库 `todo.db`。实际字段、主外键及索引以导出的 [10.json](../../app/schemas/com.chimafok.todo.data.TodoDatabase/10.json) 与 [TodoDatabase.kt](../../app/src/main/java/com/chimafok/todo/data/TodoDatabase.kt) 核对；下表字段直接取自该 schema，不使用产品概念名称代替 SQL。

## 现行数据表

| 表 | 字段 |
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

日期／时间为 ISO LocalDate／LocalTime 字符串，操作时间为 UTC epoch 毫秒；日期-only 不转成 UTC 午夜。有时刻的开始／截止保存输入时区，提醒解析为 Instant。任务含一个分类及多个标签，系列与 occurrence 分表，以 seriesId＋sequence 保持唯一槽位。完整索引、外键及 SQL 定义见共用 schema JSON。

## 迁移

| 迁移 | 非破坏式新增 |
|---|---|
| 7 → 8 | tasks 开始日期／时间／时区；series_rules 开始日偏移／时间／时区，皆 nullable |
| 8 → 9 | tasks／recurring_series 的 imageIds（默认空）；note_images 内容定址图片表 |
| 9 → 10 | retention_policy.language 非空 TEXT，默认 SYSTEM；保留 days、revision、updatedAt |

schema 1–10 保留供逐版升级测试；早期 migration 及原始设计详见[历史模型](../history/DATA-MODEL-HISTORY.zh-Hant.md)。不得使用破坏式 fallback 或清库代替升级。

## 固定分类与状态

- 未分类、作业及学习依固定身分保护，不以显示名称判断。学习 STUDY_ID 的 systemKind 仍为 null，可改名／排序但不可删除；作业可改名不可删，未分类不可改名或删除。
- 一般任务 OPEN／COMPLETED；学习 OPEN／IN_PROGRESS／COMPLETED；作业 NOT_STARTED／IN_PROGRESS／DONE_UNSUBMITTED／COMPLETED。新学习／作业不可直接建立为 COMPLETED。
- COMPLETED 才有 completedAt；完成未缴交仍未完成。重新开启清目前完成时间，保留 ID／原截止。
- 学习开始时刻须有开始日期与时区；移分类保留已存开始数据，但只有学习使用其排序能力。
- 内建名称仅在固定 ID 且仍为原始名称时翻译显示；不改写 DB 名称，不翻译自定义内容。

## 交易、排程与保留

所有业务修改在 Room 交易核对 ID、revision 与完整成员范围；任一过期或失败全回滚。重送相同新增 ID 不重建，系列游标不因重开、删除或还原倒退。历史 snapshot 保存当次内容，不随模板改写；首页至多投影每系列一个相关项，不代表其他提醒取消。

schedule_outbox 保存待校正 generation，平台工作成功后才确认 captured generation；并发新版本留待下轮。永久删除前将旧闹钟取消信息放入无任务外键的 purge_cancellations，删除后仍可重试；其中不保存任务文字。

retention_policy 保存一般完成保留期限及语言；retention_anchors 提供恢复／导入宽限，不改原 completedAt。垃圾桶按原 deletedAt＋30 天到期；重复历史不受一般完成保留期限影响。

## 图片、草稿与查询

note_images 以 SHA-256 识别不可变 Base64 JPEG；imageIds 为按顺序的引用，图片不放 Bundle 或搜索索引，缩图／大图按需载入。表单关闭时按任务、系列、历史快照及已存草稿引用清理孤立图片。

新增草稿是 App 私有 AtomicFile JSON，不是 Room 任务，不排提醒，也不在备份内。保存成功才清稿；重载检查相同 ID 是否已建立，避免重复。表单与大量确认状态留在 ViewModel，程序重建不自动提交旧确认。

一般列表与历史采精简投影及累进视窗（首批／增量 50）。完整结果 ID、统计及部分批次操作仍可能 O(N)；分页不代表全库效能已验收。备份使用独立格式 6 契约，见 [BACKUP-FORMAT](BACKUP-FORMAT.md)；不能直接复制所有 Room 表作可携备份。
