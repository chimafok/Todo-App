# Todo product and acceptance specification

<p align="center">語言 / 语言 / Languages：<a href="../PRD.md">繁體中文</a> · <a href="../zh-CN/PRD.md">简体中文</a> · <strong>English</strong></p>

This page consolidates **current 1.4.1 rules** without adding or removing requirements. The [complete original baseline and AC01–AC136](../history/PRODUCT-BASELINE.zh-Hant.md) preserve detailed contracts and historical increments for auditing. Where an older version/format statement conflicts with a later revision, apply the current rules below and subsequent explicit maintainer requirements. Translation does not change that precedence. Acceptance criteria are contracts, not claims that tests ran this round; see [verification](TESTING.md).

## 1.5.0 development increment

Recurring end dates offer both typed input and the existing calendar picker when creating, converting or editing this and future occurrences. Resolve the initial selection using the field's saved reference date, falling back to that reference for empty or invalid text. Cancellation preserves text; confirmation writes YYYY-MM-DD. Inclusive end-date rules and validation remain unchanged.

Tap blank space outside the painted image to close an enlarged preview, accounting for current zoom and translation. Image-content taps, pinch and pan keep it open; retain the Close button and previous/next controls. Target official 1.5.0 after maintainer approval of the current 1.5.0-dev05 test build.

At normal scale, swipe left for the next image and right for the previous image, without wrapping at either end. When zoomed, dragging pans the image instead. Keep arrow navigation; changing pages resets zoom and translation, while rotation preserves the settled image. Use an X for closing and chevrons for previous/next.

Use icons for add images, choose due/study-start/reminder time, keyboard/clock modes, filter/select/finish-selection. Keep text for reminder Remove, card Edit/Move to Trash/Restore, and Select all in tasks, categories/tags, Trash and recurring history. Keep counts, at least 48 dp tap targets, localized accessible names and icon long-press tooltips. Pending searches keep stable selection styling while stale actions remain blocked. Use the official filter glyph size of 18 dp for home filter, multiselect and finish-selection icons, with at least 48 dp tap targets; the finish-selection check has a transparent background. Create/save/add-next, permanent deletion and series-scope actions retain text and confirmations.

Image arrows use a short transition and accept consecutive or reversed presses without waiting for the previous animation to finish, while staying within the image list. Task details fade and change height on both expansion and collapse; keep children until the exit completes and allow reopening during collapse.

Expand and collapse task details from the top, including spacing, without layering list-placement springs over resizing; allow continuous reversal. Completion frames update only their card rather than rebuilding the list. Read zoom and category-drag transforms during drawing. Align selection summaries and action text by baseline on each wrapped row, with three languages, larger type and 48 dp tap targets.

## 1.4.1 time input

The shared 24-hour picker advances to minutes after two valid hour digits, including leading zeroes, midnight and re-entering the initial hour. Single digits, deletion and invalid input do not advance; blank fields cannot confirm. Preserve manual selection, shared clock/input values and cancellation without committing. The maintainer approved 1.4.1-dev02 and official 1.4.1 on 2026-11-06; see the [source and checksum approval](../verification/approval-1.4.1.json). Keep numeric text, selection and carets centered without clipping, fitting the digits to the actual font and scale. An unset time initially displays 10:00; existing times retain their initial values rather than appearing blank.

## Product scope

A native personal Android task manager, minimum API 26, with a fixed dark theme. Traditional Chinese, Simplified Chinese, English and System default are supported; unsupported system preferences fall back to English. Offline/local storage, no accounts, ads, tracking or app-managed cloud sync. GitHub distributes source and APKs. Subtasks, batch completion, general attachments, widgets, priority, collaboration, AI parsing, calendar sync and Play Store distribution are outside confirmed scope.

## Navigation and Agenda (F01)

The drawer contains Agenda, Unscheduled, Completed, Categories and tags, Trash and Settings. Tap Todo's icon/name to return to Agenda. Ordinary background re-entry returns to Agenda; rotation retains the current operation, and external system pickers resume safely.

Today is always shown; empty future dates are hidden. Jumping to an empty date creates a temporary anchor and Add entry. Overdue tasks project into Today without changing original deadlines or duplicating tasks. Date-only deadlines become overdue after that calendar day; timed deadlines use Instant. Unscheduled has a reachable section and a separate page. Timed tasks precede date-only tasks, sorted by deadline, then Study start, creation time and ID. Study without a deadline stays in Unscheduled, ordered by start date/time with missing starts last.

## Tasks, drafts and images (F02)

Trimmed titles contain 1–200 characters; notes allow 20,000. Show fields directly in grouped sections without a second-level details menu. Global Add has no deadline; date Add prefills that date. Create closes after saving; Add another retains only entry-date context. Failures preserve input, and retrying the same ID never creates duplicates. Leaving a new task offers Keep draft/Discard/Continue. Drafts are not tasks, schedule no reminders and are excluded from backup.

Tap cards to expand in place. HTTP/HTTPS note links can open. Ordinary completion controls are round; Assignments/Study use left status icons with aligned titles. Completion feedback/fade/reposition starts only after transaction success, follows system animation settings and does not delay persistence. Empty hints differ visibly from tasks. Search loading appears after a delay, without per-keystroke returns to Agenda or selection-button flashes.

Notes show text before images. Use the system picker, thumbnails, zoom/pan/navigation and a top-right X for removal. No per-note count limit. Input images are at most 25 MiB; output static JPEGs have a longest edge of 1536 pixels and at most 768 KiB, without original EXIF/location or modification of originals. Images follow drafts, templates, snapshots and Trash and are cleaned up only when unreferenced.

## Categories, tags and status (F03, F04)

One category and multiple tags per task. Names are 1–50 characters, trimmed and checked case-insensitively for duplicates within each type; a category and tag may share a name. Management supports long-press drag, edge auto-scroll, order saved on release and Move up/down menus. A separate bulk-delete mode previews and commits atomically. Category deletion first moves active/completed/trashed tasks and series; tag deletion removes links only.

Uncategorized cannot be renamed/deleted. Assignments and Study may be renamed but not deleted, including bulk selection/Select all. Stable identity enables behavior, not display names. Ordinary tasks are OPEN/COMPLETED. Assignments progress through Not started → In progress → Done, not submitted → Completed; unsubmitted work remains pending. Study is Not started → In progress → Completed. New Assignments/Study cannot start completed; later completion/reopening is allowed. Moving into Assignments requires per-task state choices; cancellation writes nothing. Assignment In progress/Done, not submitted maps to Study In progress; final completion is preserved.

Study form order is category → status → optional start date → optional start time. Start time requires a date; clearing the date clears time. Capture its time zone separately and preserve stored fields through category moves.

## Dates and time zones

Date hint YYYY-MM-DD accepts YYYY-MM-DD/YYYYMMDD/YYMMDD (20YY)/MMDD. Time hint HH:mm accepts HH:mm/HHmm and legacy HH-mm. MMDD resolves to next year if its month/day or today's specified time is earlier than the captured input reference. Today without a time stays this year; explicit years never auto-adjust. Invalid target-year dates and manual DST gaps are rejected. Past deadlines warn but may be confirmed. Rotation/non-date edits do not re-resolve a previewed year.

Capture the current IANA zone when dates/times are entered or reselected; text-only edits retain zones. Timed deadlines preserve absolute meaning; cross-zone Agenda uses local-date projections while retaining original values. Date-only tasks remain calendar dates. Fixed reminders store Instant; recurrence follows saved-zone calendar rules. Automatic occurrences shift forward across DST gaps and choose the earlier offset for repeated times.

## Recurrence (F05)

Daily, weekly, selected weekdays (Monday–Sunday), monthly or every N days (1–3650), with unlimited/inclusive end date/total count (including first and individually deleted occurrences). Blank first deadline means today and is separate from Study start. Monthly rules retain the original day and temporarily clamp to month-end. Generate by dates without waiting for completion. Sequences/cursors never rewind; slots are unique and retry-safe.

Agenda shows at most one task per series, preferring the most recent overdue occurrence, otherwise the earliest pending one. All other records remain in Completed → Recurring records, separate from ordinary completed tasks. Past/completed snapshots survive template edits. New recurring Assignments start Not started; initial Study In progress applies only to the first occurrence. Explicit one-occurrence/current-and-future previews protect past/completed/deleted records. Shortened rules retain read-only excluded records; extending rules does not reuse old slots.

Stopping prevents generation but preserves existing tasks/reminders. Agenda and history offer future deletion: stop and trash unfinished occurrences after today in each series' saved zone by default, preserving today/past/completed records unless history is explicitly included. Deleting one occurrence does not stop the series. Restoring never rewinds the cursor.

## Reminders (F06)

Up to 32 per task/series. Before-deadline reminders use an amount plus minutes/hours/days, initially 1 day. Adjustable date-only reference time defaults to 10:00 without adding a deadline time. Fixed reminders may omit time and use deadline time or the reference time; after saving their Instant stays fixed. Relative reminders follow deadline edits. Series use day-offset or before-deadline templates; per-occurrence customization/reset is explicit. Saved reminders sort backwards from the deadline by trigger instant.

Completion/deletion cancels relevant reminders. Do not burst-deliver past reminders. Notification completion affects only the original taskId. Notification permission and exact-alarm access are separate; refusal does not prevent task saving. Warn and use ordinary scheduling if exact access is absent. Reconcile after reboot/time/zone changes/re-entry, with persistent retryable outbox/cancellation work. Never guarantee delivery through force-stop/vendor battery restrictions; the real-device matrix remains incomplete.

## Search and batches (F07)

Page-scoped, case-insensitive contiguous substring search covers title/notes/category/tags. Multiple categories use OR; tags use all/any; text/category/tag groups combine with AND. Cancelling filters changes nothing. Full counts/Select all include unloaded results. Long-press enters selection. Query changes clear selection; stale responses cannot restore old scopes. Ordinary pages move categories/add tags/trash, never batch-complete. Trash restores/permanently deletes. Series overview stops/deletes series; within a series select occurrences, excluding read-only rule-cancelled records. Freeze complete revisions/membership before confirmation; any stale member rejects the whole operation, and write failures roll back everything.

## Completion, Trash and backup (F08, F09)

Ordinary completed records may be reopened. Retention is forever/30/90/365/custom days with preview before changes, excluding recurring history. Expired completed records enter Trash; restore eligibility lasts deletedAt + 30×24 hours. Delayed system cleanup never extends eligibility. Series restore returns remaining same-deletion members, not earlier individual deletions. Permanent series deletion includes all remaining members with explicit scope/count. Restore/import grace does not change original completion times.

Backup format 6 / schema 10 reads formats 1–5 and includes language, images, Trash, order and general settings, excluding drafts, permissions and native alarm identities. Replacement applies file data/preferences; missing legacy preferences retain local values. Merge compares IDs and actual update times, treats series as aggregates and asks about Trash/settings conflicts. Names do not identify duplicates. Validate → preview → revalidate/commit atomically, with no partial writes. See the full [backup contract](BACKUP-FORMAT.md).

## Quality and acceptance

Room/Repository/ViewModel provide persistent data and one-way state flow. Preserve non-destructive migrations, stable IDs, revisions and old alarm cancellation data. The fixed dark interface uses text/icons in addition to color, with 48dp targets, TalkBack and 200% text as quality requirements—not claims of completed validation. For a reference dataset of 10,000 tasks/50,000 historical records, 2-second cold start and 500 ms common search/update are targets, not accepted current results.

The [original baseline](../history/PRODUCT-BASELINE.zh-Hant.md) retains AC01–126, AC127–134 (Study start/full backup) and AC135–136 (Study states/first deadline). Later image, protected Study and language increments are covered here and by the data/backup contracts. See [known gaps](KNOWN-ISSUES.md) for phone/overnight/large-data validation. Follow [test APK approval](CONTRIBUTING.md#test-apks-and-release-approval); CI success is not maintainer approval.
