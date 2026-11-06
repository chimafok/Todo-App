# Installation and updates

<p align="center">語言 / 语言 / Languages：<a href="../INSTALL.md">繁體中文</a> · <a href="../zh-CN/INSTALL.md">简体中文</a> · <strong>English</strong></p>

## Updating to 1.4.1

Install over official 1.4.0 to retain data and language preferences; no uninstall is required. This release fixes time-field alignment/clipping, advances to minutes after two hour digits, and preserves the default 10:00. Official package com.chimafok.todo / code37; Room schema10 / backup format6 are unchanged.

## Updating to 1.4.0

The official package and certificate are unchanged, so 1.4.0 can update official 1.3.1 without uninstalling. The new language preference defaults to System default; change it in **Settings → App language**. Unsupported system languages fall back to English. The app exports backup format 6 and imports formats 1–5. Older apps cannot read format 6; keep pre-update backups.

## First installation

1. Download the latest official APK from this project's [GitHub Releases](https://github.com/chimafok/Todo-App/releases/latest). Confirm the repository is `chimafok/Todo-App`.
2. If needed, compare it with `SHA256SUMS.txt` from the same release. On Windows use `Get-FileHash -Algorithm SHA256`; on macOS/Linux use `shasum -a 256`.
3. Open the APK and follow Android's instructions to allow installation from your browser or file manager. You can revoke that source's installation access afterwards.
4. Open Todo, grant notification and exact-alarm access as needed, and check reminders with a synthetic task.

Android 8.0 / API 26 is the minimum. The official application ID is `com.chimafok.todo`. `Source code.zip` and `tar.gz` are source archives, not APK installers.

## Later official updates

An APK signed with the same official key and a higher versionCode can update the existing installation. Export important data first. Todo does not automatically download updates; get new APKs from Releases. Uninstalling is unnecessary and removes local app data.

## Backup compatibility

1.4.0 exports format 6 / schema 10 and imports formats 1–5. Backups include the language preference, note images, Study progress, Trash, category/tag order and general settings. 1.3.1 and earlier cannot read format 6. Keep important pre-update backups; see the [backup contract](BACKUP-FORMAT.md).

## Local builds and official APKs

Android Studio development builds use `.debug`; automated QA uses `.qa`. Their installations and data are separate from the official app. A self-generated signing key is not the official key and cannot normally update an officially signed installation. The official private key is not in the source or release assets. Use an official release APK to update an existing official installation.
