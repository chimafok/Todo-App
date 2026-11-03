# Third-party notices

<p align="center">語言 / 语言 / Languages：<a href="../../THIRD-PARTY-NOTICES.md">繁體中文</a> · <a href="../zh-CN/THIRD-PARTY-NOTICES.md">简体中文</a> · <strong>English</strong></p>

Project-owned code and documentation use MIT. Third-party components retain their original licenses; MIT does not replace dependency, tool or Android platform licenses.

| Component | Source and license |
|---|---|
| AndroidX Activity, Compose, Material 3, Lifecycle, Room and their AndroidX dependencies | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) — Apache License 2.0 |
| Kotlin standard library | [JetBrains Kotlin](https://github.com/JetBrains/kotlin) — Apache License 2.0 |
| Kotlinx Coroutines and Serialization | [Kotlinx](https://github.com/Kotlin) — Apache License 2.0 |
| JetBrains Annotations | [JetBrains](https://github.com/JetBrains/java-annotations) — Apache License 2.0 |
| JSpecify | [JSpecify](https://github.com/jspecify/jspecify) — Apache License 2.0 |
| Guava listenablefuture | [Guava](https://github.com/google/guava) — Apache License 2.0 |
| Gradle Wrapper and Gradle | [Gradle](https://github.com/gradle/gradle) — Apache License 2.0 |
| Android Gradle Plugin and build tools | [Android Tools](https://android.googlesource.com/platform/tools/base/) — each tool's original license; the SDK is not bundled with this source |

The complete Apache 2.0 text is in [LICENSES/Apache-2.0.txt](../../LICENSES/Apache-2.0.txt). [runtime-dependencies.json](../runtime-dependencies.json) lists 68 actual runtime artifacts and their POM license fields. The POM for Guava listenablefuture 1.0 does not directly declare a license; it is treated under upstream Guava's Apache 2.0 license. Direct versions are pinned in the catalog/wrapper and transitive dependencies are resolved by Gradle. Check runtime dependencies/licenses before release; do not apply this project's MIT to third-party binaries.

Tests use JUnit 4 (Eclipse Public License 1.0) and AndroidX Test/Espresso (Apache 2.0). These development/isolated-QA dependencies are not in the official release APK. Testing/build dependencies come from their respective upstreams.

Android system UI, system fonts, trademarks and the operating system are outside this project's MIT scope. Demonstration screenshots use synthetic isolated-QA data. Original legal license texts are shared without translation; this explanation does not alter their terms.
