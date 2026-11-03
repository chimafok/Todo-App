# Backup format and restore contract

<p align="center">語言 / 语言 / Languages：<a href="../BACKUP-FORMAT.md">繁體中文</a> · <a href="../zh-CN/BACKUP-FORMAT.md">简体中文</a> · <strong>English</strong></p>

This is the current 1.4.0 contract. [Original legacy-format notes](../history/BACKUP-HISTORY.zh-Hant.md) remain available for compatibility auditing. See the [user guide](USER-GUIDE.md#backup-and-restore) for ordinary operations.

## Versions and scope

Exports are unencrypted UTF-8 JSON. The root contains `format="Todo-App"`, `formatVersion=6`, `schemaVersion=10`, `exportedAt` (UTC epoch milliseconds), `appVersion` and `data`. Unknown fields are rejected.

| Readable format | schemaVersion | Contract changes |
|---|---|---|
| 1 | 7 | Eleven arrays; no Trash, order, general settings, Study start fields or images |
| 2 | 8 | Adds Trash, order, general settings and Study start fields |
| 3 | 8 | Allows Study IN_PROGRESS |
| 4 | 9 | Adds images with the historical limit of 8 per note |
| 5 | 9 | Removes the image count limit while retaining size limits |
| 6 | 10 | Adds language to general settings |

Only these format/schema pairs are accepted. New readers accepting old files does not imply backward readability. 1.3.1 and earlier cannot read format 6.

## Thirteen data arrays

| Arrays | Contents |
|---|---|
| tasks | Title, notes, category, due/start date/time/zone, status, completion/deletion/creation/update timestamps, imageIds |
| categories, tags | ID, name, system kind where applicable, creation/update times and sortOrder; recalculable nameKey is excluded |
| task_tags, series_tags | Task/series tag links |
| recurring_series | Content, category, lifecycle, cursor, deletion/previous lifecycle and imageIds |
| series_rules | Version, frequency, anchor, deadline time/zone, end conditions and Study start offset |
| occurrences | Task/series/rule links, sequence, snapshot, rule-exclusion flag and same-series-deletion marker |
| reminders, series_reminders | Fixed/relative/series definitions, zones, reference times, enabled flags and portable delivery history |
| reminder_overrides | Explicit per-occurrence customization markers |
| settings | At most one row: id general, completedRetentionDays (null means forever), updatedAt and language |
| note_images | Lowercase SHA-256 id and standard padded Base64 JPEG data |

`language` is one of `SYSTEM`, `TRADITIONAL`, `SIMPLIFIED`, `ENGLISH`. A format 6 settings row cannot omit it or use null. Check exact types, required fields and keys in [BackupModel.kt](../../app/src/main/java/com/chimafok/todo/data/BackupModel.kt) and [BackupFormat.kt](../../app/src/main/java/com/chimafok/todo/data/BackupFormat.kt); Room columns are not a substitute for the portable contract.

Active, completed and trashed data are included. Preserve deletion dates, previous lifecycle and same-deletion relationships; import does not extend Trash's 30-day eligibility. An old backup may reintroduce permanently deleted IDs without surviving tombstones; the preview must explain this.

Drafts, raw databases, cache, revision, retention grace anchors, outbox, platform cancellation queues, alarmKey/alarmMode, Android permissions and battery settings are excluded. Portable reminder history is separate from native alarm identity. Reschedule future reminders without a burst of past notifications.

## Images and limits

imageIds is an ordered comma-separated list of unique lowercase SHA-256 values; empty means no images. Snapshots may retain the same references. Formats 5/6 have no per-note count limit. Export only images referenced by active/trashed tasks, series or historical snapshots, not uncreated drafts.

Import validates Base64, SHA-256, JPEG type/dimensions and the complete reference graph. Each JPEG is at most 768 KiB, with width/height 1–1536. Deduplicate by content and retain images referenced by selected task/series aggregates. Restore images and data in the same transaction. Overall limits remain **64 MiB, 200,000 rows, depth 12 and 5,000,000 values**. Reject oversized files rather than dropping images. Parser limits do not certify performance at those sizes.

## Merge, replacement and legacy files

Merge is the default. Compare IDs, content and known modification times; identical names with different IDs are not automatically combined. Treat a task with links/reminders as one aggregate and a series with rules/records/reminders as another. Do not mix inconsistent fragments. Conflicting Trash state, unknown timestamps or equal timestamps with different content require explicit choices. Keep order with selected categories/tags and use ID as a stable tie-breaker.

Different language or retention values require choosing the entire general-settings group. Full replacement uses the file's tasks, Trash, order and settings. **Preferences absent from older formats retain local values; missing language preserves the local language even in full replacement.** Language changes do not alter task zones/reminder instants, and language-only restore does not reset completion-retention anchors.

Formats 1/2 reject Study IN_PROGRESS. Missing start/image fields become empty; old files cannot smuggle in new fields. Unknown order in format 1 keeps existing local order and appends new items. Missing settings preserve local settings. Replacing from an old file still removes local tasks and Trash absent from the file.

## Validation and transactions

Strictly parse/validate the entire graph before preview and confirmation. Reject invalid UTF-8, duplicate keys, trailing content, unknown fields, wrong types, inconsistent keys/references and invalid dates/zones/states. Start time requires a start date/zone; order is non-negative; retention is a positive integer or null.

Preview freezes local/file fingerprints. Confirmation re-reads, replans and revalidates in one Room transaction; stale data or any failure rolls everything back. Cancellation writes nothing. Imported ordinary completed items or changed retention policies receive the specified grace anchors without changing completedAt; unchanged merges preserve anchors. Persist cancellation work for old native reminders before scheduling future ones. Document pickers and process recreation never submit a restore automatically.
