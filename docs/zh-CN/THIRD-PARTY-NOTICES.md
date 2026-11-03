# 第三方声明

<p align="center">語言 / 语言 / Languages：<a href="../../THIRD-PARTY-NOTICES.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/THIRD-PARTY-NOTICES.md">English</a></p>

本专案自有代码与文件采 MIT；以下第三方元件保留其原有授权。MIT 不取代依赖、工具或 Android 平台的授权。

| 元件 | 来源与授权 |
|---|---|
| AndroidX Activity、Compose、Material 3、Lifecycle、Room 及其 AndroidX 依赖 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/) — Apache License 2.0 |
| Kotlin 标准程序库 | [JetBrains Kotlin](https://github.com/JetBrains/kotlin) — Apache License 2.0 |
| Kotlinx Coroutines 与 Serialization | [Kotlinx](https://github.com/Kotlin) — Apache License 2.0 |
| JetBrains Annotations | [JetBrains](https://github.com/JetBrains/java-annotations) — Apache License 2.0 |
| JSpecify | [JSpecify](https://github.com/jspecify/jspecify) — Apache License 2.0 |
| Guava listenablefuture | [Guava](https://github.com/google/guava) — Apache License 2.0 |
| Gradle Wrapper 与 Gradle | [Gradle](https://github.com/gradle/gradle) — Apache License 2.0 |
| Android Gradle Plugin、Android 建置工具 | [Android Tools](https://android.googlesource.com/platform/tools/base/) — 依工具原授权；未随源代码提供 SDK |

Apache 2.0 完整文字见 [LICENSES/Apache-2.0.txt](../../LICENSES/Apache-2.0.txt)。68 个实际 runtime artifacts 与 POM 授权字段列于 [runtime-dependencies.json](../runtime-dependencies.json)。Guava listenablefuture 1.0 的 POM 未直接宣告授权，按 Guava 上游 Apache 2.0 处理。实际版本固定于 Gradle catalog 与 wrapper，传递依赖由 Gradle 解析；发布前核对 runtime 依赖及其授权，不把本专案 MIT 应用到第三方二进位。

测试使用 JUnit 4（Eclipse Public License 1.0）、AndroidX Test／Espresso（Apache 2.0）；它们只在开发或隔离 QA 使用，不包含在官方 release APK。测试与建置依赖由各自上游供应。

Android 系统界面、系统字体、商标及作业系统不包含在本专案 MIT 授权范围。展示画面由本专案隔离 QA 的虚构数据产生。
