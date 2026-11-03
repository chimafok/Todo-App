# Privacy and permissions

<p align="center">語言 / 语言 / Languages：<a href="../../PRIVACY.md">繁體中文</a> · <a href="../zh-CN/PRIVACY.md">简体中文</a> · <strong>English</strong></p>

Updated: 2026-11-03 (1.4.0).

Todo stores task data in the app's private storage on your device. It has no accounts, ads, analytics or app-managed cloud synchronization. The normal app declares no Internet permission. Uninstalling or clearing app data removes local content; export your own backups.

## Data and files

Tasks, text notes and images, categories, tags, deadline time zones, recurring rules and reminders are processed locally. Task content may appear in lock-screen notifications, subject to Android and your notification settings.

Backups use the system document picker without broad storage permission. Exported JSON is unencrypted plaintext. A cloud file provider you choose may process the file. Todo excludes its task data from Android automatic backup/data transfer and provides user-selected JSON export and previewed restore.

The app's private cache may temporarily hold import/export data to resume an operation. Temporary files are cleaned up and are not reliable backups. Format 6 includes language, note images, Trash, category/tag order and general settings, so a backup can contain deleted task content. Drafts and system permissions are excluded; see the [user guide](USER-GUIDE.md#backup-and-restore).

## Permissions and capabilities

| Permission or capability | Purpose |
|---|---|
| Notifications | Show task reminders when allowed |
| Exact alarms | Schedule more precise reminders when the system permits; otherwise show a warning and use ordinary scheduling |
| Boot completed | Reconcile eligible future reminders after reboot |
| System image picker | Read only chosen images, copy and compress them locally; no whole-library access, original-image modification or retained original location/EXIF metadata |
| System document picker | Read or write only the backup document you explicitly choose |

Todo requests neither whole-library photo access nor broad storage access. It does not read contacts, personal messages or other apps' private content. It reads images only after you select them. A cloud photo provider may need to download selected images; Todo itself does not upload them.

## GitHub and reports

Downloads, issues and contributions are hosted by GitHub and subject to GitHub's policies. Do not publish real task backups, device identifiers, passwords or keys. Redact personal content from screenshots. See [security reporting](SECURITY.md).
