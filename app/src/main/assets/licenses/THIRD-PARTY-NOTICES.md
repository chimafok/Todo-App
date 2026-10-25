# 第三方聲明

本專案自有程式碼與文件採 MIT；以下第三方元件保留其原有授權。MIT 不取代依賴、工具或 Android 平台的授權。

| 元件 | 來源與授權 |
|---|---|
| AndroidX Activity、Compose、Material 3、Lifecycle、Room 及其 AndroidX 依賴 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) — Apache License 2.0 |
| Kotlin 標準程式庫 | [JetBrains Kotlin](https://github.com/JetBrains/kotlin) — Apache License 2.0 |
| Kotlinx Coroutines 與 Serialization | [Kotlinx](https://github.com/Kotlin) — Apache License 2.0 |
| JetBrains Annotations | [JetBrains](https://github.com/JetBrains/java-annotations) — Apache License 2.0 |
| JSpecify | [JSpecify](https://github.com/jspecify/jspecify) — Apache License 2.0 |
| Guava listenablefuture | [Guava](https://github.com/google/guava) — Apache License 2.0 |
| Gradle Wrapper 與 Gradle | [Gradle](https://github.com/gradle/gradle) — Apache License 2.0 |
| Android Gradle Plugin、Android 建置工具 | [Android Tools](https://android.googlesource.com/platform/tools/base/) — 依工具原授權；未隨原始碼提供 SDK |

Apache 2.0 完整文字見 [Apache-2.0.txt](Apache-2.0.txt)。68 個實際 runtime artifacts 與 POM 授權欄位列於 [runtime-dependencies.json](runtime-dependencies.json)。Guava listenablefuture 1.0 的 POM 未直接宣告授權，按 Guava 上游 Apache 2.0 處理。實際版本固定於 Gradle catalog 與 wrapper，傳遞依賴由 Gradle 解析；發布前核對 runtime 依賴及其授權，不把本專案 MIT 套用到第三方二進位。

測試使用 JUnit 4（Eclipse Public License 1.0）、AndroidX Test／Espresso（Apache 2.0）；它們只在開發或隔離 QA 使用，不包含在官方 release APK。測試與建置依賴由各自上游供應。

Android 系統介面、系統字型、商標及作業系統不包含在本專案 MIT 授權範圍。展示畫面由本專案隔離 QA 的虛構資料產生。
