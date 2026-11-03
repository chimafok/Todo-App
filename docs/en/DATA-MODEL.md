# Data model and non-destructive migrations

<p align="center">語言 / 语言 / Languages：<a href="../DATA-MODEL.md">繁體中文</a> · <a href="../zh-CN/DATA-MODEL.md">简体中文</a> · <strong>English</strong></p>

The current database is **Room schema 10**, `todo.db`. Check exact fields, primary/foreign keys and indexes against exported [10.json](../../app/schemas/com.chimafok.todo.data.TodoDatabase/10.json) and [TodoDatabase.kt](../../app/src/main/java/com/chimafok/todo/data/TodoDatabase.kt). The field list below is generated from that schema; product concepts are not SQL column names.

## Current tables

| Table | Fields |
|---|---|
| `tasks` | `id`, `title`, `notes`, `categoryId`, `dueDate`, `dueTime`, `zoneId`, `status`, `completedAt`, `deletedAt`, `createdAt`, `updatedAt`, `revision`, `startDate`, `startTime`, `startZoneId`, `imageIds` |
| `categories` | `id`, `name`, `systemKind`, `sortOrder`, `createdAt`, `updatedAt`, `revision` |
| `tags` | `id`, `name`, `nameKey`, `sortOrder`, `createdAt`, `updatedAt`, `revision` |
| `task_tags` | `taskId`, `tagId` |
| `recurring_series` | `id`, `title`, `notes`, `categoryId`, `lifecycle`, `cursor`, `deletedAt`, `previousLifecycle`, `createdAt`, `updatedAt`, `revision`, `imageIds` |
| `series_rules` | `id`, `seriesId`, `version`, `effectiveFrom`, `anchor`, `frequency`, `intervalDays`, `weekdayMask`, `dueTime`, `zoneId`, `endDate`, `maxCount`, `createdAt`, `startDayOffset`, `startTime`, `startZoneId` |
| `series_tags` | `seriesId`, `tagId` |
| `occurrences` | `taskId`, `seriesId`, `sequence`, `ruleId`, `snapshot`, `deletedWithSeriesAt`, `excludedByRule` |
| `reminders` | `id`, `taskId`, `kind`, `zoneId`, `fixedAt`, `offsetMinutes`, `baseTime`, `enabled`, `state`, `resolvedAt`, `alarmKey`, `alarmMode`, `revision`, `createdAt`, `updatedAt` |
| `schedule_outbox` | `taskId`, `generation`, `processedAt` |
| `series_reminders` | `id`, `seriesId`, `kind`, `offsetMinutes`, `dayOffset`, `time`, `baseTime`, `enabled`, `createdAt`, `updatedAt` |
| `reminder_overrides` | `taskId` |
| `retention_policy` | `id`, `days`, `revision`, `updatedAt`, `language` |
| `retention_anchors` | `taskId`, `completedAt`, `restoredAt` |
| `purge_cancellations` | `id`, `taskId`, `reminderId`, `alarmKey`, `createdAt` |
| `note_images` | `id`, `data` |

Dates/times use ISO LocalDate/LocalTime strings; operation timestamps use UTC epoch milliseconds. Date-only values do not become midnight UTC. Timed starts/deadlines capture input zones; reminders resolve to Instant. A task has one category and multiple tags. Series and occurrences are separate, with a unique seriesId + sequence slot. See the shared schema JSON for full indexes, references and SQL definitions.

## Migrations

| Migration | Non-destructive additions |
|---|---|
| 7 → 8 | Nullable start date/time/zone on tasks, start-day offset/time/zone on series_rules |
| 8 → 9 | imageIds on tasks/recurring_series (default empty), content-addressed note_images table |
| 9 → 10 | Non-null TEXT retention_policy.language, default SYSTEM; preserves days, revision and updatedAt |

Schemas 1–10 remain for incremental migration tests. See [historical model notes](../history/DATA-MODEL-HISTORY.zh-Hant.md) for earlier migrations and original design. Never replace upgrades with destructive fallback or database clearing.

## Fixed categories and status

- Uncategorized, Assignments and Study are protected by stable identity, not display name. Study's STUDY_ID still has null systemKind: it may be renamed/reordered but not deleted. Assignments may be renamed, not deleted; Uncategorized allows neither.
- Ordinary tasks use OPEN/COMPLETED; Study uses OPEN/IN_PROGRESS/COMPLETED; Assignments use NOT_STARTED/IN_PROGRESS/DONE_UNSUBMITTED/COMPLETED. New Study/Assignment tasks cannot start COMPLETED.
- Only COMPLETED has completedAt; DONE_UNSUBMITTED remains pending. Reopening clears current completion time while preserving ID/deadline.
- A Study start time requires a start date/zone. Category moves preserve stored start fields; only Study uses their special sorting behavior.
- Built-in names translate for display only when the fixed ID and original canonical name still match. Do not rewrite database names or translate custom content.

## Transactions, scheduling and retention

Room transactions recheck IDs, revisions and complete member scopes for business changes. Any stale input/failure rolls back the whole operation. Repeated creation with the same ID is idempotent. Reopening/deleting/restoring never rewinds a series cursor. Historical snapshots retain occurrence content independently of template edits. Agenda projects at most one relevant task per series without cancelling other occurrences' reminders.

schedule_outbox records reconciliation generations. Acknowledge only the captured generation after successful platform work; newer concurrent generations remain. Before permanent deletion, persist old alarm cancellation data in purge_cancellations without task foreign keys so it can be retried after deletion. This queue contains no task text.

retention_policy stores ordinary-completion retention and language. retention_anchors provide restore/import grace without changing completedAt. Trash expires at original deletedAt + 30 days. Ordinary-completion retention does not apply to recurring history.

## Images, drafts and queries

note_images contains immutable Base64 JPEGs addressed by SHA-256; ordered imageIds reference them. Images are absent from Bundle/search indexes, and thumbnails/full images load on demand. When a form closes, cleanup checks tasks, series, snapshots and saved drafts before deleting unreferenced images.

A new-task draft is private AtomicFile JSON, not a Room task, schedules no reminders and is excluded from backups. Clear it only after successful creation. Reload checks the same task ID to avoid duplicates. Forms/large confirmation state remain in ViewModel; process recreation does not submit an old confirmation.

Ordinary and history lists use compact projections and progressive windows (initial/incremental 50). Complete result IDs, counts and some batches can still be O(N). Paging does not certify whole-database performance. Portable backup is the separate [format 6 contract](BACKUP-FORMAT.md), not a raw copy of every Room table.
