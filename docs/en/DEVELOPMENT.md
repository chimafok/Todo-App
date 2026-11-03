# Development and builds

<p align="center">語言 / 语言 / Languages：<a href="../DEVELOPMENT.md">繁體中文</a> · <a href="../zh-CN/DEVELOPMENT.md">简体中文</a> · <strong>English</strong></p>

## Environment

- Android Studio, or a command-line environment with JDK 17+. CI uses JDK 17; the local release has also been verified with Android Studio's JBR 25.
- Gradle wrapper 9.6.0 with download SHA-256; Android Gradle Plugin 9.4.1.
- SDK Platform 37, Build Tools 36.0.0 and Platform Tools. Minimum runtime API 26.
- Compose BOM 2026.09.00, Room 2.8.5, KSP 2.3.12. See [version catalog](../../gradle/libs.versions.toml) and [wrapper configuration](../../gradle/wrapper/gradle-wrapper.properties).

Open the project in Android Studio and configure the SDK, or set `JAVA_HOME` / `ANDROID_HOME`. Do not commit `local.properties`. The first build downloads SDK, Gradle and Maven dependencies; the running app has no cloud sync.

## Branches and checks

App work branches from the latest `dev`, integrates through a PR, and is delivered as a development-signed test APK before explicit approval for formal signing, a matching `main` PR and publication. See [contributing](CONTRIBUTING.md#branches-and-merges) for permissions and documentation-only changes.

CI checks public content and translation resources, builds debug/unsigned release/QA test APKs, runs unit tests and Lint, and uploads reports. It does not run device tests, complete phone acceptance, sign officially, publish APKs or automatically upload a downloadable test APK.

## Commands

Windows PowerShell:

```powershell
.\tools\build.ps1 -GradleTask @('assembleDebug', 'testQaUnitTest', 'lintDebug')
```

macOS / Linux:

```sh
chmod +x gradlew
./gradlew assembleDebug testQaUnitTest lintDebug
```

For isolated device tests, start your own emulator, explicitly select its serial, then run:

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.\tools\build.ps1 -GradleTask @('connectedQaAndroidTest')
```

`build.ps1` accepts `-SdkPath` / `-JavaPath` for non-default locations. Never use an everyday installation as an automation target.

Before committing, also run `python tools/check-public-content.py` and `python tools/check-translations.py`. The public-content checker reads staged Git content, so stage intended new documents first, excluding local settings and private data.

## Install variants

| Variant | applicationId | Purpose |
|---|---|---|
| debug | com.chimafok.todo.debug | Android Studio development |
| qa | com.chimafok.todo.qa | Isolated automation and synthetic demonstrations |
| release | com.chimafok.todo | Official distribution; unsigned by default |

CI uses no official private key. A self-signed release has no official update identity.

## Test APK delivery

After appropriate checks, build `assembleDebug` from the `dev` source to be tested and deliver the `.debug` APK with source commit, version, SHA-256, changes, verified scope and limitations. `.qa` is for automation. Test APKs may be delivered locally or in a clearly marked pre-release, never Git history.

Authorized development, checks and synchronization may continue while waiting for test-version approval, but related app changes cannot be formally signed, merged into `main` or officially published. See [release approval](CONTRIBUTING.md#test-apks-and-release-approval).

## Official APKs

`assembleRelease` may produce an unsigned APK for inspection before approval. Formal signing requires explicit approval of the corresponding test APK. In a secure environment, the maintainer sets `TODO_KEYSTORE`, `TODO_KEY_ALIAS`, `TODO_STORE_PASSWORD`, and `TODO_KEY_PASSWORD`, then runs `tools/sign-release.ps1`. Passwords pass to apksigner through environment variables, not project files or logs. Check zip alignment, sign, then verify. Any payload change requires signing again.

Use the existing official key and a higher versionCode. Check package, non-debuggable status, schema, permissions, fresh test XML, SHA-256 and appropriate installation/update behavior. Deliver APKs, checksums and release notes through GitHub Releases.

## Structure

- `app/src/main`: domain rules, Room repositories, reminder scheduling and Compose UI.
- `app/src/test`: unit tests.
- `app/src/androidTest`: database, backup, reminder, UI and large-data tests.
- `app/src/qa`: QA-only provider, absent from the official APK.
- `app/schemas`: historical Room schemas for non-destructive migration tests.

See the [data model](DATA-MODEL.md), [product specification](PRD.md) and [translation guide](TRANSLATING.md).
