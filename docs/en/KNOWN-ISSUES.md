# Known limitations and validation status

<p align="center">語言 / 语言 / Languages：<a href="../KNOWN-ISSUES.md">繁體中文</a> · <a href="../zh-CN/KNOWN-ISSUES.md">简体中文</a> · <strong>English</strong></p>

This page describes 1.4.1. Official signing/publication does not mean the gaps below are resolved. See [verification](TESTING.md) for actual evidence.

- **Reminders:** the full phone, extended-background, overnight, lock-screen and vendor battery-policy matrix remains incomplete. Force-stop, Do Not Disturb, notification/exact-alarm access and background restrictions may delay or prevent reminders. Emulator success is not a guarantee for every phone.
- **Language and accessibility:** Traditional Chinese, Simplified Chinese and English are supported with a fixed dark theme. Language selection is in the app, not Android's system per-app-language page. Independent native-speaker review, full TalkBack, 200% text size, all long-string layouts and RTL validation are incomplete. Delivered notifications and historical snapshots are not rewritten. User content and original licenses are not translated.
- **Images:** no per-note count limit, but Android/providers may limit one picker selection. Input images are limited to 25 MiB and become static JPEGs with a longest edge of 1536 pixels and at most 768 KiB each. Todo is not an original-image archive. HEIC, cloud providers and vendor pickers need real-device checks.
- **Backups:** format 6 / schema 10 includes images, language, Trash, order and general settings, and imports formats 1–5. JSON is unencrypted with a 64 MiB limit; oversized files are rejected rather than dropping images. 1.3.1 and earlier cannot read format 6. Importing does not extend Trash expiry. Inspect previews and retain pre-update backups.
- **Large-data performance:** compact indexes, complete result IDs, selection and series previews can still grow with the dataset; some organization/Trash operations need the full scope. An older software-rendered debug emulator with 60,000 records measured a median launch of 7.867 seconds and 79% jank in its scrolling sample, missing reference-phone targets of 2 seconds / 500 ms. This historical measurement neither represents every current release installation nor proves the issue fixed.
- **Not provided:** accounts, app-managed cloud sync, automatic update downloads, light mode, subtasks, batch completion, general file attachments, home-screen widgets or Play Store distribution.

Report the app/Android versions, reproduction steps and expected outcome. Do not upload real backups or unredacted private content.
