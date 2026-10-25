# Todo 1.0.5 — 首次公開發布

原始碼與文件採 MIT。主要功能沿用 1.0.4：日程、作業進度、重複任務、多個提醒、分類標籤、搜尋／長按多選、垃圾桶及 JSON 備份。

本版加入獨立正式簽章、公開使用與開發文件、虛構資料展示、固定版本的 GitHub CI，以及第三方授權聲明。Android 8.0／API26 以上；識別碼 com.chimafok.todo，versionCode27。

## 下載

下載 Todo-1.0.5.apk；SHA256SUMS.txt 是檔案校驗值。GitHub 自動產生的 Source code.zip／tar.gz 是原始碼，不是手機安裝包。

## 安裝與更新

首次使用請下載並安裝 APK，再按需要設定通知與精確提醒能力。後續正式版可直接安裝更新，保留既有資料；更新前建議匯出重要內容。完整步驟見 [安裝與更新](https://github.com/chimafok/Todo-App/blob/main/docs/INSTALL.md)。

## 驗證與限制

86 項純規則、7 項備份還原 UI、1 項展示 UI 通過（分開執行）；Debug／Release Lint 各 0 errors／10 warnings。API26／37 專用空白模擬器完成簽章界線、新安裝、啟動及同版本重裝檢查；沒有操作使用者手機。原功能 Kotlin／資源與 Room schemas 保持一致。

仍在持續開發中，尚未完成全部真機、背景／隔夜提醒、TalkBack 或大量資料效能驗收；請閱讀 [已知限制](https://github.com/chimafok/Todo-App/blob/main/docs/KNOWN-ISSUES.md)，先備份重要內容並在自己的手機確認提醒。

正式憑證 SHA-256：a4b166d522cf41ce213923ef054da8e9b4644abf1888580f502002e2aadfd197。
