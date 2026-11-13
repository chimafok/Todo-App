# Verification and regression

<p align="center">語言 / 语言 / Languages：<a href="../TESTING.md">繁體中文</a> · <a href="../zh-CN/TESTING.md">简体中文</a> · <strong>English</strong></p>

This page separates official-release checks, development checks and historical results. Separate rounds are not presented as one full regression. Original failures/retests remain in shared verification JSON and the [history archive](../history/TESTING-HISTORY.zh-Hant.md). Translating documentation does not mean these app tests were rerun.

## 1.5.0-dev06 — 2026-11-13

Debug, perf, qa and qaPerf built successfully. Perf Lint has 0 errors / 13 warnings; 104 fresh unit cases across the expected 18 classes passed with new XML checked. perf/qaPerf are non-debuggable with verified R8/resource shrinking and mappings. Both user-facing APKs use com.chimafok.todo.debug / code43 and the same development certificate, contain no QA providers, Internet or broad-photo permissions, and bundle licenses. The ordinary Debug APK is a source-matched control; main feature sources are unchanged from dev05.

The owned API26 headless emulator passed five black-box smoke checks: seed 20 synthetic tasks, retain installation identity/data across qa-to-qaPerf replacement, use details/vertical scrolling, retain an edit after process restart, and remove only synthetic fixtures. tools/smoke-perf-qa.py generated fresh JUnit XML with zero failures/errors/skips. This is not connected instrumentation or FPS benchmarking. Initial shell Bundle/cold-start and selector assertion failures are preserved. Optimized QA initially lacked its custom Kotlin source classes; correcting the source directory and rebuilding resolved it without blanket keep rules.

All owned emulators were closed with userdata retained. See [development-1.5.0-dev06.json](../verification/development-1.5.0-dev06.json). No phones, phone-performance comparison or full historical regression this round. Existing official release settings, main and the official key remain unchanged, pending maintainer testing.

## 1.5.0-dev05 — 2026-11-13

Debug build passed, Lint has 0 errors / 13 warnings, and 104 fresh unit cases passed. The owned API26 headless emulator ran nine separate groups: detail motion 4, toolbar alignment 1 (18 language/width/font scenarios), icon/text actions 4, gallery gestures 5, reorder/edge scroll 1 each, successful/failed completion 1 each, and long-selection/Trash/history 7: 25 cases total. Fresh XML class/count/time and zero failures/errors/skips were verified.

The largest adjacent-card gap error was 63.81 dp before the fix and under 0.20 dp afterward. This measures deterministic frame-by-frame position coordination, not FPS or phone performance. Initial alignment-coordinate/font-metric assertion attempts are preserved; final comparisons use painted root text coordinates plus actual font baselines. Owned API37 emulator transports stayed unauthorized after restart and a fresh isolated AVD, so no API37 runtime cases ran this round. No host private keys or phones were accessed. All owned emulators were closed with userdata retained.

Package com.chimafok.todo.debug / code42 updates dev04. No official signing or main merge; Room schema10 / backup format6 unchanged. See [development-1.5.0-dev05.json](../verification/development-1.5.0-dev05.json). This is not a full historical, phone, every-screen translation/system-font/accessibility, large-data or overnight matrix.

## 1.5.0-dev04（2026-11-13）

Restore home filter, multiselect and finish-selection glyphs to the official filter icon size of 18 dp. Keep count text, icon actions, at least 48 dp tap targets and the transparent check background. Debug build passed, Lint has 0 errors / 13 warnings, and 104 fresh unit cases passed. API37/26 each ran four icon/text-action cases, eight overall, covering all three actual glyph sizes, tap bounds, transparent background, long-press help in three languages, card Edit/Delete/Restore and reminder actions. Fresh XML class/count/time and zero failures/errors/skips were verified. Dedicated headless emulators were closed with userdata retained.

Package com.chimafok.todo.debug / code41 uses the existing development certificate to update dev03. No official signing or main merge; Room schema10 / backup format6 unchanged. See [development-1.5.0-dev04.json](../verification/development-1.5.0-dev04.json). This is targeted validation, not phone acceptance, a full historical/language/accessibility matrix, large-data or overnight testing. Gallery and collapse code/previous-round evidence are unchanged and not counted again.

## 1.5.0-dev03 (2026-11-13)

Debug builds passed with 0 Lint errors / 13 warnings; 104 fresh unit tests across 18 classes passed. API37 and API26 each ran six separate groups: gallery/swipes/rapid or reversed arrows 5, icon/text actions 4, collapse/reopening details 2, search/foreground refresh 3, long selection/Trash/recurring history 7, and protected category Select all 1. This is 22 cases per API, 44 overall, with fresh XML timestamps/classes/counts and zero failures/errors/skips checked each round. Coverage includes actual restored text-button actions, compact glyph bounds/48 dp tap targets, a pixel comparison of the transparent selection-check background, rapid arrow presses and reopening before transitions finish.

All five cases in the initial API37 gallery round failed while Espresso waited for editor keyboard window focus, before gallery assertions. A wake/keyguard retry still failed. A single-case diagnostic confirmed a System UI not-responding prompt owned window focus. The owned emulator System UI was restarted, focus recovery checked, and the full group rerun. The initial icon/action round had three passes and one pixel failure from sampling normal transient touch feedback. The test now lets the ripple fade before comparing the persistent background; all four cases were rerun, without production changes. Both new motion tests initially looked for a nested detail tag in the merged semantics tree; they now inspect the unmerged tree and were rerun without production changes. Initial XML/logs remain in the evidence and are not counted as passes. See [development-1.5.0-dev03.json](../verification/development-1.5.0-dev03.json). These are separate targeted rounds, not one full historical suite, phone acceptance or a complete translation/accessibility matrix. Only isolated synthetic QA data was used; headless emulators were closed with userdata retained.

Development-signed com.chimafok.todo.debug / code40 updates dev02 with the existing certificate. Official target 1.5.0 awaits approval; no official signing or main merge. Room schema10 / backup format6 are unchanged. Earlier verification remains preserved and is not counted again.

## 1.5.0-dev02 (2026-11-13)

Debug builds passed with 0 Lint errors / 13 warnings; 104 fresh unit tests across 18 classes passed. API37 and API26 each ran eight separate groups: gallery paging 4, icon actions/three-language tooltips 4, search/foreground refresh 3, image hit testing 3, time focus/forms 7, recurring end-date calendars 5, long selection/Trash/recurring history 7, and the existing image-draft workflow 1. That is 34 cases per API, 68 total. Fresh XML timestamps, classes, counts and zero failures/errors/skips were checked for each round. Coverage includes bounded swipes, zoomed pan, zoom reset, rotation, icon callbacks/48 dp targets/tooltips, reminder removal, card edit/trash/restore, stable search selection appearance, calendars and drafts.

New test fixtures initially failed compilation due to a missing type import and unavailable DpRect accessors. The initial record was retained; old XML was not claimed as a passing result. The corrected build and subsequent separate device groups passed. The initial historical long-selection round had five language-dependent failures and two passes because it assumed Traditional Chinese in an English UI. Selectors were localized and the full seven-case group rerun; initial failures and XML remain separate and are not counted as successes. The initial API26 calendar round had four passes and one query-presentation race; the test now waits for the expected presented page before scrolling, and the full group was rerun without production changes. See [development-1.5.0-dev02.json](../verification/development-1.5.0-dev02.json). These are targeted rounds, not one full historical regression, phone acceptance or a complete three-language/TalkBack/large-font matrix. Only owned headless emulators and isolated synthetic QA data were used; emulators were closed afterward with userdata retained. No personal phone was operated.

Development-signed com.chimafok.todo.debug / code39 can update dev01 with the existing development certificate. Official target is 1.5.0 after maintainer testing and approval. No official signing or main merge this round; Room schema10 / backup format6 remain unchanged. The dev01 results and initial issues below and in their original evidence file are preserved, not counted again.

## 1.5.0-dev01 (2026-11-13)

The Debug build and Lint passed (0 errors / 13 warnings), with 104 fresh unit cases and the expected 18-class set verified from new XML. Each dedicated API37/26 headless emulator passed separate groups of 5 calendar cases, 3 image-gesture cases and 1 real gallery/draft flow (18 total), checking fresh XML, counts, classes and timestamps with zero failure/error/skipped. Coverage includes new/this-and-future persistence, typed shortcuts, confirmation/cancellation, disabled controls, top/bottom/side blank dismissal, image-content retention, zoom/pan hit testing, retained draft/text, rotation, closing and removal.

Initial class selection ran only calendars; all groups were then run separately. Calendar day selection and asynchronous editor/text synchronization needed test corrections. The gallery flow caught an empty draft-reference parse in the new calendar initialization; it was corrected before rebuilding and rerunning the scoped matrix. An existing hard-coded Traditional Chinese removal assertion now uses the selected language. Initial attempts and separate-run evidence are in [development-1.5.0.json](../verification/development-1.5.0.json). This is not full historical regression, phone acceptance or a complete language/accessibility matrix. Only dedicated QA installations and synthetic data were used; emulators were closed afterward and the user's phone was not operated.

Development-signed com.chimafok.todo.debug / code38 retains the development certificate and targets official 1.5.0, pending maintainer test approval. This round has not been officially signed or merged into main. Room schema10 / backup format6 are unchanged.

## Official 1.4.1 release (2026-11-06)

After approval of 1.4.1-dev02, the official APK was rebuilt from the same App/build sources. All 104 fresh unit tests passed; Release Lint reported 0 errors / 13 warnings. Fresh XML verified count, classes, timestamps and zero failure/error/skipped. The existing RSA4096 certificate is retained; signed/unsigned payloads match. The APK is non-debuggable, contains no QA provider, Internet or broad-photo permission, and bundles license notices.

Dedicated API37/26 emulators completed official 1.4.0→1.4.1 updates and launch with UID/first-install time retained; schema10 is unchanged. API37 also checked retained synthetic tasks, Study progress/start date, renamed category and English preference. Official-form smoke checks covered default 10:00, two-digit hour advance, minute input, commit, reopening an existing value and cancellation; the synthetic form was discarded. The 22 development device cases are earlier separate runs, not a full suite rerun this round. These are update/form smoke checks, not complete historical regression or phone acceptance.

See [approval](../verification/approval-1.4.1.json), [release metadata](../verification/release-1.4.1.json) and [test records](../verification/tests-1.4.1.json). APK SHA-256: `a647bd92a6ab58cbc395a230950b43c9a853bea8118a74b4f35a5243211e0258`. Fresh official installs, full phone/keyboard/accessibility/language matrices and long-background behavior were not tested. Owned headless emulators were closed afterward; the user's phone was not operated.

## 1.4.1-dev02 development (approved, 2026-11-06)

104 fresh unit tests passed; Debug build/Lint reported 0 errors / 13 warnings. Each of API37 and API26 ran 7 focus/form cases and 4 layout cases separately (11 per API, 22 total). Fresh XML verified class, count and timestamps with zero failure/error/skipped. Coverage includes untouched 10:00, existing times, leading zeroes, selection/end-cursor placement and actual system font scales of 100%/140%/200%; owned QA screenshots were inspected. This is not a complete historical regression, phone acceptance or full accessibility/language matrix.

An initial test-only type error was corrected before successful compilation. The first multi-class selection ran only the focus class, so layout cases were run separately. Initial outer-density injection did not establish the dialog's real font scale; final tests set/restore the owned emulator's system setting and assert the dialog text-layout density. See [development-1.4.1-dev02.json](../verification/development-1.4.1-dev02.json). The .debug development-signed test APK has code37 and has been approved and published as official 1.4.1 from the same App/build sources; this section retains the development results.

## 1.4.1-dev01 historical — (2026-11-06)

104 fresh unit tests passed; Debug build/Lint reported 0 errors / 13 warnings. API37 and API26 each ran 7 focused time-input cases with zero failure/error/skipped, verified from fresh XML. Coverage includes two-digit advance, leading zero/midnight/initial-hour re-entry, invalid input/deletion, clock sharing, cancellation and deadline-form integration. This is not a full historical regression. The initial API37 run had 2 failures; deferred focus and a stable form-reopen test were followed by retests. Settled screenshots also verified digit bounds and the separator. See [development-1.4.1.json](../verification/development-1.4.1.json). The .debug development-signed test APK targets official 1.4.1 and still awaits maintainer approval; official signing/update, phone and full accessibility matrices were not validated.

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

The maintainer approved that test APK and official 1.4.0 was published on 2026-11-03. These development results remain historical records. See [development-1.4.0.json](../verification/development-1.4.0.json) for original failures and retests.

## Incomplete validation

Independent native-speaker review, phones, overnight/extended background, TalkBack/200% text/RTL, a full historical suite and large-data performance remain incomplete. Earlier poor performance measurements remain disclosed in [known limitations](KNOWN-ISSUES.md). Never assume unreported phone cases passed.

## Running and recording checks

See the [development guide](DEVELOPMENT.md): testQaUnitTest, relevant variant builds/Lint, and connectedQaAndroidTest only for `.qa` on owned/authorized devices. Record each start time. Check fresh XML classes, positive case counts, zero failure/error/skipped and timestamps at or after the start. No fresh result means no reuse of an old result. Preserve initial failures and retests separately. Selected tests are not a full suite; compiling a device-test APK in CI does not execute it.

Screenshots use synthetic data in isolated QA, not private phones/everyday installations. App changes require an identifiable development-signed test APK and explicit approval before formal signing/main merge/publication; see [contributing](CONTRIBUTING.md#test-apks-and-release-approval). Documentation-only changes need no APK.
