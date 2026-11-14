# Changelog

<p align="center">語言 / 语言 / Languages：<a href="../../CHANGELOG.md">繁體中文</a> · <a href="../zh-CN/CHANGELOG.md">简体中文</a> · <strong>English</strong></p>

This page lists official versions and the current development test build. [Original development/test-version records](../history/CHANGELOG-HISTORY.zh-Hant.md) preserve delivery-time states, not the current version. [Releases](https://github.com/chimafok/Todo-App/releases/latest) is authoritative for official APK downloads.

## 1.5.0 — 2026-11-14

- Add a calendar picker for recurrence end dates, retaining typed input, confirmation/cancellation and inclusive end rules.
- Add image-background dismissal, horizontal swipes, quick consecutive/reversed arrows and an X close control; retain zoom/pan.
- Use accessible three-language action icons while keeping reminder removal, task edit/trash/restore and Select all as text. Home glyphs remain 18 dp with 48 dp targets and a transparent selection check background.
- Coordinate detail expansion/collapse with list motion, reduce repeated animation work and align multi-selection text baselines.
- Official release after approval of 1.5.0-dev06-perf, using its tested R8/resource-shrinking settings. Package com.chimafok.todo / code43 retains the official certificate and updates 1.4.1. Schema10 / backup format6 unchanged.

## 1.5.0-dev06 — 2026-11-13

- Add a non-debuggable R8/resource-shrunk comparison APK, sharing source, package and development signing identity with the regular test build for replacement in either direction; retain dev05 features and motion.

## 1.5.0-dev05 — 2026-11-13

- Coordinate task-detail expansion/collapse with adjacent cards and reduce repeated work in completion feedback, image zoom and category dragging.
- Align selection summaries and action text in tasks, Trash, recurring history and category/tag management while retaining wrapping and larger type.

## 1.5.0-dev04 — 2026-11-13

- Restore home filter, multiselect and finish-selection glyphs to the official filter icon size of 18 dp; retain icon actions, count text, 48 dp tap targets and the transparent check background.

## 1.5.0-dev03 — 2026-11-13

- Restore text buttons for reminder Remove, card edit/trash/restore and every Select all action; keep other icons.
- Shorten arrow navigation and accept consecutive/reversed presses, retaining swipes, zoomed pan, bounded endpoints and blank-space dismissal.
- Animate task-detail collapse while retaining children until completion, including reopening mid-transition. Size home filter/selection glyphs to adjacent text, keep 48 dp tap targets, and remove the selection check background.
- Development-signed com.chimafok.todo.debug / code40 updates dev02. Official target remains 1.5.0 pending testing and approval; no official signing or main merge. Room schema10 / backup format6 are unchanged.

## 1.5.0-dev02 — 2026-11-13

- Swipe through enlarged images without wrapping at the ends. Dragging while zoomed pans the image; changing pages resets zoom, and rotation preserves the settled image. Blank-space dismissal remains available.
- Use icons for viewer close/navigation, adding images, time selection/input modes, removing reminders, card edit/trash/restore, and filter/multiselect/select-all toolbars. Keep counts, at least 48 dp tap targets, localized action names and long-press tooltips. Permanent deletion and series-scope actions keep text.
- Development-signed com.chimafok.todo.debug / code39 can update dev01. Official target remains 1.5.0, pending maintainer testing and approval; no official signing or main merge this round. Room schema10 / backup format6 are unchanged.

## 1.5.0-dev01 — 2026-11-13

- Add a calendar button to recurring end dates for new tasks and this-and-future edits, retaining typed date shortcuts and inclusive end-date rules.
- Tap blank space around an enlarged image to close the viewer. Tapping image content keeps it open; pinch, pan and image navigation remain available.
- Development-signed com.chimafok.todo.debug / code38 targets the next regular release, 1.5.0, pending test approval. Room schema10 / backup format6 are unchanged.

## 1.4.1 — 2026-11-06

- Two valid hour digits automatically focus minutes; manual selection, clock mode and cancellation remain available.
- Fix shifted digits/carets and clipped leading zeroes with inner spacing and font-aware sizing.
- Unset times initially display 10:00; existing times retain their values.
- Official release after approval of 1.4.1-dev02. Package com.chimafok.todo / code37 retains the official certificate and can update 1.4.0. Room schema10 / backup format6 are unchanged.

## 1.4.1-dev02 — 2026-11-06

Fix shifted time digits/carets and clipped leading zeroes with explicit inner spacing and font-aware numeric sizing. Keep the initial 10:00 for an unset time and the existing value when present. Two-digit hour advance and clock mode remain available. The official target is still 1.4.1; deliver a development-signed test APK for approval before official signing/main merge. Room schema10 / backup format6 are unchanged.

## 1.4.1-dev01 — 2026-11-06

Advance from hours to minutes after two valid digits, including leading zeroes and an hour matching the initial value. The shared picker covers deadlines, Study starts and reminders, retaining clock mode, manual field selection and cancellation. This round targets official 1.4.1; deliver a development-signed test APK before approval for formal signing/main merge. Room schema10 / backup format6 are unchanged.

## 1.4.0 — 2026-11-03

- Traditional Chinese, Simplified Chinese, English and System default, with English fallback.
- Immediate/persistent language choice for UI, validation, dates/weekdays, pickers and future notifications. User content, custom names and time semantics stay unchanged.
- Room schema 10 / backup format 6 saves language and reads formats 1–5. Legacy files preserve local language; language-only restore does not reset completion retention.
- Published with the existing official key after approval of 1.4.0-dev01. com.chimafok.todo / code35 updates official 1.3.1.

## 1.3.1 — 2026-10-29

- Study can be renamed but not deleted. Individual deletion, bulk deletion and Select all share fixed-identity protection.
- Preserves progress, start dates/times, order, tasks and series. schema9 / format5 / code34.
- The maintainer specified this patch number for this round only; normal iteration resumes unless otherwise specified.

## 1.3.0 — 2026-10-28

- Category/tag long-press drag, edge auto-scroll and separate bulk deletion. Move tasks before deleting categories; tag deletion keeps tasks.
- Local images below text notes, thumbnails/full view and top-right X removal, without a per-note count limit. Compress/remove original EXIF without changing originals.
- Images follow drafts, recurrence, snapshots and Trash. Non-destructive schema9 migration; format5 embeds images and reads formats1–4; code33.

## 1.2.0 — 2026-10-27

- Recurrence field renamed First deadline date (blank means today).
- Study's three states and category → status → start date → start time ordering. New tasks cannot start completed; cards, editing, reopening and series agree.
- schema8 / format3 retains Study In progress, reads formats1–2; code30.

## 1.1.0 — 2026-10-26

- Study start date/time below category; time requires date. Start breaks deadline ties; no-deadline tasks stay Unscheduled.
- New Assignments cannot start completed. schema8 / format2 adds Trash, order and general settings and reads format1; code29.

## 1.0.5 — 2026-10-25

First public source/APK release. Keeps 1.0.4 behavior and adds MIT, official signing, public guides, CI and third-party notices. code27 / schema7 / format1.

## 1.0.4

Compact empty hints, full group counts, long-press selection in Agenda/Unscheduled/Completed/Trash/recurring history, batch restore/permanent deletion and series stop/delete actions.

## 1.0.1–1.0.3

Draft confirmation, 1-day/10:00 reminder defaults, stable search/re-entry, aligned date labels/filter controls and the fixed everyday application ID.

Earlier private development was not imported into public Git history. [Known limitations](KNOWN-ISSUES.md) retain incomplete quality gates.
