# 翻譯與語言支援

<p align="center">語言 / 语言 / Languages：<strong>繁體中文</strong> · <a href="zh-CN/TRANSLATING.md">简体中文</a> · <a href="en/TRANSLATING.md">English</a></p>

目前支援 English、繁體中文與简体中文，預設英文資源在 `app/src/main/res/values/strings.xml`，英文對照在 values-en，中文在 values-b+zh+Hant／values-b+zh+Hans。新增字串時四份資源須同時更新；保留 `%1$s` 等位置參數，可調整顯示順序但不能改變數量。`tools/check-translations.py` 會檢查完整鍵集合、重複鍵及參數。

UI 透過 stringResource 或 tr 取得文字；背景通知與領域驗證也使用同一資源。不可翻譯／改寫任務、備註、使用者名稱、時區 ID、資料庫固定分類 ID、備份鍵或枚舉值。LanguageChoice 負責系統清單與英文 fallback；I18n 只使用 App Context，不修改系統語言、Locale.setDefault 或儲存的瞬間。

變更翻譯須驗證三種語言的畫面、長字串換行、日期選擇器、通知、切換後草稿與設定保存。宿主單元測試在 Android Application 尚未初始化時使用 TextFallback 的繁中錯誤文字，該檔案必須與繁中資源同步。新增語言須同時擴充 LanguageChoice、備份值契約與測試，不只新增 XML。

## 文件翻譯

根目錄 `README.md` 是繁中首頁；`README.zh-CN.md` 與 `README.en.md` 是另外兩種語言。其他說明保留繁中原路徑，對應翻譯位於 `docs/zh-CN/` 與 `docs/en/`，根目錄政策說明亦可在這兩個目錄找到同名檔案。每頁使用同一列「繁體中文 · 简体中文 · English」切換到對應頁，而非一律回首頁。

修改行為說明時同步三語，保留版本、數字、指紋、程式識別碼與共同的 ASCII 錨點；檢查相對連結、圖片及範例命令。翻譯頁中的文件連結應優先留在相同語言。翻譯不改 MIT 或第三方授權條款；授權原文、schema／驗證 JSON 與歷史繁中工程紀錄共用，於文件索引明示範圍。歷史紀錄的過去狀態不能當成目前版本限制。
