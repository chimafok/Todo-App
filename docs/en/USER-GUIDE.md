# User guide

<p align="center">語言 / 语言 / Languages：<a href="../USER-GUIDE.md">繁體中文</a> · <a href="../zh-CN/USER-GUIDE.md">简体中文</a> · <strong>English</strong></p>

## Interface language

Open the navigation drawer → **Settings → App language**, then choose System default, 繁體中文, 简体中文 or English. The choice applies immediately and persists. System default uses the first supported system language, or English if none is supported. Language choices are shown in their own languages so you can switch back.

Only app text and displayed dates/weekdays are localized. Task text, notes, renamed categories and tags stay as entered. Date input, 24-hour time and stored deadline/reminder time zones keep their existing semantics. Format 6 backups include language; older backups preserve the local language. In-app date/time pickers follow the app language. Android file/photo pickers and keyboards follow device settings. Android's system per-app-language page is not integrated. Existing delivered notifications are not rewritten; later notifications use the current language.

## Navigation and creation

The drawer contains Agenda, Unscheduled, Completed, Categories and tags, Trash, and Settings. Tap the Todo icon/name to return to Agenda. Ordinary re-entry from the background also returns to Agenda; external document/photo picker continuations retain the operation.

Use **Add task**, or Add beside a date to prefill that date. Only the title is required. Deadlines and notes are optional. Tasks without deadlines appear in Unscheduled. The form directly shows dates, category/status, tags, recurrence and reminders; scroll to reach all sections.

**Add another** saves and continues with the entry date context and other fields reset. Leaving or cancelling a new task offers Keep draft, Discard or Continue editing. A draft is not a created task, schedules no reminders and is not included in JSON backup.

## Dates, times and time zones

Use pickers or type `YYYY-MM-DD`, `YYYYMMDD`, `YYMMDD` (20YY), or `MMDD`. Time accepts `HH:mm` or `HHmm` in 24-hour notation; legacy `HH-mm` is also accepted. Previews show a full date and `HH:mm`. From 1.4.1, entering two valid hour digits in the time picker automatically focuses minutes. You may also select minutes manually or use the keyboard Next action. The time fields keep digits centered and fit them to the font and font scale to avoid clipping. An unset time still opens as `10:00`; an existing time opens with that value. Cancelling does not change the form.

For `MMDD`, an earlier month/day—or today's date with an already-past specified time—resolves to next year. An explicit past year/date instead warns and can be saved after confirmation. Invalid dates, times and manually entered nonexistent daylight-saving times require correction. A date-only task for today does not roll forward a year.

Choosing Study shows Study status, optional start date and optional start time below the category button, in that order. Start time requires a start date; clearing the date clears the time. A start date may stand alone or both may be blank. Start fields do not create a deadline, accept the same shorthand/24-hour input and capture a separate input time zone.

Tasks with deadlines sort by deadline, then Study start date/time for ties. Tasks without deadlines stay in Unscheduled and sort by start date/time, with missing starts last. Within the same start date, a specified time precedes a date-only start. Recurring Study tasks retain the start-day offset relative to each occurrence's deadline and the start time.

Input captures the current time zone. Timed deadlines retain their absolute instant across zone changes; date-only tasks retain their calendar date without becoming midnight UTC.

## Tasks, categories and progress

Tap a card to expand it. The round completion button completes an ordinary task; completed tasks can be reopened. Assignments have four states: Not started, In progress, Done, not submitted, and Completed. Done, not submitted stays pending and retains reminders. Final completion cancels pending reminders. New assignments can use only the first three states; existing ones can be completed normally.

Study has Not started → In progress → Completed. New Study tasks cannot start completed. After creation, use the left status icon, the expanded progress action or the edit form. Reopen completed Study tasks as Not started or In progress. Existing open Study tasks display Not started. Renaming Study preserves its special behavior.

When moving Assignments into Study, In progress and Done, not submitted map to In progress; Not started maps to Not started, and Completed stays completed. Moving Study to an ordinary category maps its two unfinished states to Open and retains final completion. Moving a batch to the same Study category preserves In progress.

Each task has one category and multiple optional tags. In Categories and tags, long-press a row and drag it up/down; releasing saves the order. Drag near the list edge to scroll. Row menus still offer Move up/Move down. The order is used in forms and navigation and is backed up.

Use the separate **Bulk delete** button to select rows or Select all, then confirm Delete selected. Study, Assignments and Uncategorized cannot be selected for deletion. Study and Assignments can be renamed; Uncategorized cannot. Before deleting a category, choose a surviving destination. Active, completed and trashed tasks and recurring series are moved, not deleted. Moving into Assignments requires per-task status choices. Deleting tags removes their links, not tasks. Cancellation writes nothing; changed data invalidates a preview and the whole batch must be reviewed again.

## Action icons (1.5.0-dev02 test build)

Use the image-plus icon to add images, the clock to choose a time, and keyboard/clock icons to switch time-entry modes. Card pencil, trash and restore-arrow icons edit, move to Trash and restore; a reminder's trash icon removes only that reminder. The funnel opens filters, the selection icon enters multiselect, the select-all icon checks all matching results, and a check finishes selection. Categories/tags, Trash and recurring history use corresponding icons. Result, selection and filter counts remain visible. Long-press for a text tooltip; assistive tools read the action in the current app language. Permanent deletion and series-scope actions retain text and existing confirmations.

## Note images

The Notes and images section shows text first, then images. The official version uses **Add images**; 1.5.0-dev02 uses an image-plus icon. Both open Android's system picker. There is no per-note image count limit; add another batch if the picker limits one selection. Todo reads only selected images and saves private local copies without modifying originals. Each input image is limited to 25 MiB. Copies are resized to a longest edge of 1536 pixels and compressed to JPEG of at most 768 KiB, without original location/EXIF metadata. Transparency becomes white; animations become a static image.

In the 1.5.0-dev02 test build, tap a thumbnail to view it. At normal scale, swipe left for the next image or right for the previous one; navigation stops at the ends. Bottom arrows also switch images. After pinching to zoom, dragging pans the image without switching pages. Changing pages resets zoom; rotation preserves the settled image. Tap blank space outside the painted image or the top-right X to close. Image-content taps, pinch and pan keep the viewer open. In the form, the X at the thumbnail's top right removes that image while keeping text notes. Scroll the thumbnail strip for more images. Expand a saved task to view images. Cancelling the system picker returns to the form. Images can be retained with a draft, but drafts are not in manual backups.

Images follow recurring templates, edit scopes, historical snapshots and Trash. Unreferenced images are cleaned up when a form closes, checking tasks, series, snapshots and saved drafts. Cleanup never deletes the original phone-library image.

## Recurring tasks

Choose daily, weekly, selected weekdays, monthly or every N days. Weekday options run Monday through Sunday. **First deadline date (blank means today)** anchors the recurrence; it is separate from Study's start date. You may set an end date or occurrence count. From 1.5.0-dev01, a calendar button beside the end date is available for new tasks and this-and-future rule edits. Typed input remains available; calendar confirmation writes YYYY-MM-DD, while cancellation keeps the original text.

An initial In progress Study state applies only to the first occurrence; future occurrences start Not started. Series edits preserve existing progress.

Recurrence advances by rule dates without requiring completion of the previous occurrence. Agenda projects one relevant task per series; all occurrences have separate records in **Completed → Recurring records**.

- **Edit one occurrence:** change only that occurrence. Use the explicit series entry and preview for a series edit.
- **Stop future generation:** keep generated tasks, reminders and history while preventing new occurrences.
- **Delete future tasks/series:** by default, stop generation and trash unfinished occurrences after today in each series' saved time zone. Keep today, past and completed records unless history is explicitly included.
- **Delete a record:** trash only the selected occurrence without silently stopping the series.

Records cancelled by shortened rules remain read-only and cannot be long-press selected.

<a id="reminders"></a>
## Reminders

Each task or series can have up to 32 reminders.

- **Before deadline:** enter an amount and select minutes, hours or days. New reminders default to 1 day. Date-only deadlines use an adjustable reference time, initially 10:00, without adding a deadline time.
- **Fixed date/time:** time is optional. If blank, use the deadline time, or the date-only reference time when no deadline time exists. Saving resolves an independent instant; later deadline changes do not silently move it.
- **Series reminders:** set a day offset before/on/after each occurrence or use before-deadline templates. Per-occurrence custom reminders are separate from templates.

Saved reminders are ordered backwards from the deadline by actual trigger instant. Notification permission and exact-alarm access are separate. Saving tasks remains possible without them, but reminders may be unavailable or delayed. Completion/deletion cancels relevant reminders; missed past reminders are not sent in a burst.

Check notification permission, alarm access and background restrictions. Todo does not bypass Do Not Disturb or guarantee delivery after force-stop. Reopening reconciles scheduling. Verify your phone with a short-lived test task first.

## Search, filters and long-press selection

Search titles, notes, categories and tags within the current page. Select multiple categories, and choose whether tags must match all or any. Full result counts include unloaded pages.

Long-press a task to enter selection and select it. Tap to toggle selection; long-press another to add it. End selection or Back exits. Ordinary pages support moving categories, adding tags and moving to Trash; batch completion is not provided.

Trash selection can restore or permanently delete tasks and deleted series. Recurring overview selection can stop/delete series; within a series, selected occurrences can go to Trash. Review scope/count before confirming. Data changes may require a fresh selection or preview.

## Trash and retention

Deleted tasks remain in Trash for 30 days and may be restored or explicitly permanently deleted. Restoring a series brings back remaining records deleted with it, not earlier individually deleted records unless separately selected. Permanently deleting a series includes all its remaining records; inspect the preview.

Ordinary completed records are kept forever by default. Settings offers 30, 90, 365 or custom days, with a preview before changes. Expired ordinary completed items enter Trash for another 30 days. Recurring history is exempt from ordinary completion retention. System delays may postpone background cleanup but do not extend restore eligibility past expiry.

<a id="backup-and-restore"></a>
## Backup and restore

Export JSON in Settings using the system document picker. It is unencrypted plaintext containing private task content; store it appropriately.

**Format 6 includes language, note images, Study progress, active/completed tasks, Trash (including deleted series and restore relationships), category/tag order and general settings.** Current general settings are completed-record retention and app language. Drafts, Android permissions and device alarm identities are excluded. Original deletion dates remain; importing does not restart Trash's 30-day period.

Restore first shows a preview. Merge is the default; resolve conflicts explicitly. Full replacement uses the file's data, Trash, order and general settings instead of local equivalents. Merge conflicts involving language or retention require choosing the whole settings group. Applying a different retention period gives ordinary completed items a new grace period rather than immediately cleaning them. Export local data first. Cancellation/invalid files leave tasks unchanged. Recheck notification/alarm access and future reminders after importing.

1.4.0 imports format 5/4 (schema 9), 3/2 (schema 8) and 1 (schema 7). Missing preferences in older files retain local values; missing language does so even for full replacement. New exports use format 6 / schema 10, with Base64 images in the same JSON and a 64 MiB total limit. Oversized backups are rejected instead of silently omitting images. 1.3.1 and earlier cannot read format 6; keep pre-update backups.

Todo has no cloud synchronization. A cloud provider selected in the system picker accesses files according to that provider's behavior.
