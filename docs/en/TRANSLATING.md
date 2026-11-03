# Translation and language support

<p align="center">語言 / 语言 / Languages：<a href="../TRANSLATING.md">繁體中文</a> · <a href="../zh-CN/TRANSLATING.md">简体中文</a> · <strong>English</strong></p>

## App resources

The app supports English, Traditional Chinese and Simplified Chinese. English fallback resources are in `app/src/main/res/values/strings.xml`, mirrored by `values-en`; Chinese catalogs are in `values-b+zh+Hant` and `values-b+zh+Hans`. Update all four catalogs when adding strings. Preserve positional placeholders such as `%1$s`; order may change, but types/counts must agree. `tools/check-translations.py` checks key parity, duplicates and placeholders.

UI, background notifications and domain validation use `stringResource` or `tr`. Do not translate task/notes/user text, time-zone IDs, fixed category IDs, backup keys or enum values. `LanguageChoice` resolves system language preferences and English fallback. `I18n` uses the app Context without changing system language, `Locale.setDefault` or stored instants.

Check all three languages, long-line wrapping, date pickers, notifications and draft/settings persistence after switching. Host unit tests before Android initialization use Traditional Chinese error text in `TextFallback`; keep it synchronized with the Traditional Chinese resources. Adding a language also requires updating `LanguageChoice`, the backup value contract and tests, not just XML.

## Documentation translations

Root `README.md` is the Traditional Chinese homepage; `README.zh-CN.md` and `README.en.md` are the other editions. Other Traditional Chinese guides retain their original paths; translations live in `docs/zh-CN/` and `docs/en/`, including same-named copies of root policy documents. Each page links to its counterpart using **繁體中文 · 简体中文 · English**, rather than always returning to a homepage.

Update all three editions for behavioral changes. Preserve versions, numbers, fingerprints, code identifiers and shared ASCII anchors. Check relative links, images and example commands. Document links should stay in the current language where possible. Translations do not change MIT or third-party license terms. Original license text, schema/verification JSON and historical Traditional Chinese engineering records are shared; the index states this scope. Past states in historical records are not current-version limitations.
