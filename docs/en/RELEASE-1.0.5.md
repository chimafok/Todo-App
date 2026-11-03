# Todo 1.0.5 — first public release

<p align="center">語言 / 语言 / Languages：<a href="../RELEASE-1.0.5.md">繁體中文</a> · <a href="../zh-CN/RELEASE-1.0.5.md">简体中文</a> · <strong>English</strong></p>

> Historical version: this page describes only 1.0.5. See [installation](INSTALL.md) and [known limitations](KNOWN-ISSUES.md) for the current version.

Source and documentation use MIT. Main features retain 1.0.4 behavior: Agenda, Assignment progress, recurring tasks, multiple reminders, categories/tags, search/long-press selection, Trash and JSON backup.

This release adds a dedicated official key, public user/developer guides, synthetic screenshots, pinned GitHub CI and third-party notices. Minimum Android 8.0 / API26; com.chimafok.todo / versionCode27.

## Downloads and installation

`Todo-1.0.5.apk` is the installer; `SHA256SUMS.txt` contains its checksum. GitHub's automatic Source code.zip/tar.gz files are source archives, not installers. Download the APK and configure notification/exact-alarm access as needed. Subsequent official versions can update the installation while retaining data. Export important content first; see [installation and updates](INSTALL.md).

## Historical verification and limitations

86 unit-rule tests, 7 backup-restore UI tests and 1 demonstration UI test passed in separate runs. Debug/Release Lint each reported 0 errors / 10 warnings. Dedicated empty API26/37 emulators checked signing boundaries, clean installation, launch and same-version reinstall. No user phone was operated. Feature Kotlin/resources and Room schemas were unchanged.

The full phone, overnight/background, TalkBack and large-data performance matrix remained incomplete. Same-version reinstall was not a future-version upgrade test. See [known limitations](KNOWN-ISSUES.md) and verify reminders on your own phone with important data backed up.

Official certificate SHA-256: `a4b166d522cf41ce213923ef054da8e9b4644abf1888580f502002e2aadfd197`.
