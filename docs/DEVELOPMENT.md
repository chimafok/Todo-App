# 開發與建置

## 環境

- Android Studio，或具備 JDK 17 以上的命令列環境；本機亦以 Android Studio 的 JBR 25 建置驗證。
- Gradle 9.6.0 wrapper（含下載 SHA-256）、Android Gradle Plugin 9.4.1。
- Android SDK Platform 37、Build Tools 36.0.0、Platform Tools；最低執行 API 26。
- Compose BOM 2026.09.00、Room 2.8.5、KSP 2.3.12；完整版本見 gradle/libs.versions.toml。

下載並開啟專案，讓 Android Studio 設定本機 SDK，或自行設定 JAVA_HOME／ANDROID_HOME。local.properties 不入 Git。首次需要連線下載 SDK、Gradle 與 Maven 依賴；App 執行本身不做雲端同步。

## 命令

Windows PowerShell：

```powershell
.	oolsuild.ps1 -GradleTask @('assembleDebug', 'testQaUnitTest', 'lintDebug')
```

macOS／Linux：

```sh
chmod +x gradlew
./gradlew assembleDebug testQaUnitTest lintDebug
```

隔離裝置測試：先啟動你自己的模擬器，明確指定其 serial，然後執行：

```powershell
$env:ANDROID_SERIAL = 'emulator-5554'
.	oolsuild.ps1 -GradleTask @('connectedQaAndroidTest')
```

SDK／JDK 不在預設位置時可使用 build.ps1 的 -SdkPath／-JavaPath。不要用使用者的日用安裝作自動測試。

## 安裝變體

| 變體 | applicationId | 用途 |
|---|---|---|
| debug | com.chimafok.todo.debug | Android Studio 日常開發 |
| qa | com.chimafok.todo.qa | 自動化與虛構資料展示 |
| release | com.chimafok.todo | 官方發布，建置結果預設未簽署 |

CI 建置與測試不使用正式私鑰。自行簽署的 release 不會取得官方更新身分。

## 正式 APK

執行 `assembleRelease` 產生未簽署 APK。維護者在安全環境設定 TODO_KEYSTORE、TODO_KEY_ALIAS、TODO_STORE_PASSWORD 與 TODO_KEY_PASSWORD，然後執行 tools/sign-release.ps1；密碼只經環境變數交給 apksigner，不寫入專案或日誌。先完成 zipalign 檢查，再簽署並驗證；任何 APK 內容變更都必須重新簽署。

正式發布需使用既有官方金鑰、遞增版本碼、核對 package／不可偵錯／schema／權限、核對 XML 及 SHA-256，並執行適當的安裝或更新驗證。APK、雜湊檔與版本說明以 GitHub Releases 提供。

## 結構

- app/src/main：domain 純規則、data Room 交易、reminders 系統排程、ui Compose。
- app/src/test：純規則單元測試。
- app/src/androidTest：資料庫、備份、提醒與 UI／大型資料測試。
- app/src/qa：僅 QA 包含的測試 provider，不進入正式 APK。
- app/schemas：歷史 Room schemas，供非破壞式升級測試。

資料模型見 DATA-MODEL.md，產品規則與驗收條件見 PRD.md。
