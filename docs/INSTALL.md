# 安裝與更新

## 首次安裝

1. 到本專案 GitHub Releases 下載 APK。請確認下載來自 `chimafok/Todo-App`。
2. 依需要比對同版本 `SHA256SUMS.txt`。Windows 可用 `Get-FileHash -Algorithm SHA256`，macOS／Linux 可用 `shasum -a 256`。
3. 開啟 APK，依 Android 指示允許你使用的瀏覽器或檔案管理器安裝，完成後可關閉該來源的額外安裝能力。
4. 開啟 Todo，按需要授予通知與精確提醒能力；先用虛構任務確認提醒。

最低 Android 8.0／API 26。App 識別碼為 `com.chimafok.todo`。

## 後續正式版更新

同一正式金鑰簽署的新版本提高 versionCode 後，可在原安裝上更新；安裝前仍建議匯出重要資料。App 目前沒有自動下載更新，請從 Releases 取得新 APK，不需要卸載。

## 備份格式相容性

1.2.0 匯出格式3，可匯入格式1及2。新格式保存學習進度、垃圾桶、分類／標籤排序與一般設定；1.1.0不能讀格式3，1.0.5也不能讀格式2，請保留更新前的重要備份。詳見[備份格式](BACKUP-FORMAT.md)。

## 自行建置與官方 APK

Android Studio 開發版使用 `.debug`，自動測試使用 `.qa`，與官方 App 分開。自己產生的簽章不等於官方簽章，不能保證與官方 APK 互相覆蓋更新。官方金鑰不在原始碼或下載資產中。
