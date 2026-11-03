# Verification and regression

<p align="center">語言 / 语言 / Languages：<a href="../TESTING.md">繁體中文</a> · <a href="../zh-CN/TESTING.md">简体中文</a> · <strong>English</strong></p>

This page separates official-release checks, development checks and historical results. Separate rounds are not presented as one full regression. Original failures/retests remain in shared verification JSON and the [history archive](../history/TESTING-HISTORY.zh-Hant.md). Translating documentation does not mean these app tests were rerun.

## Official 1.4.0 release (2026-11-03)

| Check | Actual result |
|---|---|
| Fresh unit tests | 104; XML classes, counts and timestamps checked; zero failure/error/skipped |
| Release build/Lint | Build passed; 0 errors / 13 warnings |
| Signing/payload | Existing RSA 4096 certificate; signing left the payload unchanged |
| Official package | com.chimafok.todo / code35, non-debuggable, no QA provider/Internet/broad photo permission; original license text included |
| API 37/26 update | Official 1.3.1→1.4.0, schema9→10, successful launch; UID and first-install time preserved |
| API 37 data/language | Synthetic tasks, Study progress/start date and renamed category preserved; three-language switching and preference persistence after restart |

APK SHA-256: `2e8532687e01d5a16d1b25ee8f715a5fabf97582ef6287ad6364f25ac29e88ce`. See [source/approval](../verification/approval-1.4.0.json) and the [release summary](../verification/release-1.4.0.json). These are dedicated-emulator official-update smoke checks, not a fresh full historical regression or phone acceptance. Official clean installation was not tested in this round.

## 1.4.0-dev01 development checks (historical)

- 104 unit tests, builds and Debug Lint (0 errors / 13 warnings). Four complete catalogs with 796 resource keys passed key/placeholder checks.
- Separate API 37 rounds: language UI 3, language backup/migration 2, backup regression 19, Study 6, images/legacy formats 9: 39 cases.
- Separate API 26 rounds: language UI 3 and language backup/migration 2: 5 cases. All 44 had zero failure/error/skipped; this was not one full historical suite run.
- Covered switching/recreation/drafts, unchanged custom content/zones, notification text, format 6 round-trip/settings conflicts, legacy language preservation, invalid-language rejection and language-only restore preserving retention anchors. API 26 also had manual inspection of all three settings screens.

The maintainer approved that test APK and official 1.4.0 was published on 2026-11-03. It is no longer awaiting approval or superseded by an alleged current 1.3.1. See [development-1.4.0.json](../verification/development-1.4.0.json) for original failures and retests.

## Incomplete validation

Independent native-speaker review, phones, overnight/extended background, TalkBack/200% text/RTL, a full historical suite and large-data performance remain incomplete. Earlier poor performance measurements remain disclosed in [known limitations](KNOWN-ISSUES.md). Never assume unreported phone cases passed.

## Running and recording checks

See the [development guide](DEVELOPMENT.md): testQaUnitTest, relevant variant builds/Lint, and connectedQaAndroidTest only for `.qa` on owned/authorized devices. Record each start time. Check fresh XML classes, positive case counts, zero failure/error/skipped and timestamps at or after the start. No fresh result means no reuse of an old result. Preserve initial failures and retests separately. Selected tests are not a full suite; compiling a device-test APK in CI does not execute it.

Screenshots use synthetic data in isolated QA, not private phones/everyday installations. App changes require an identifiable development-signed test APK and explicit approval before formal signing/main merge/publication; see [contributing](CONTRIBUTING.md#test-apks-and-release-approval). Documentation-only changes need no APK.
