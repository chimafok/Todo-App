# 翻譯與語言支援

目前支援 English、繁體中文與简体中文，預設英文資源在 `app/src/main/res/values/strings.xml`，英文對照在 values-en，中文在 values-b+zh+Hant／values-b+zh+Hans。新增字串時四份資源須同時更新；保留 `%1$s` 等位置參數，可調整顯示順序但不能改變數量。`tools/check-translations.py` 會檢查完整鍵集合、重複鍵及參數。

UI 透過 stringResource 或 tr 取得文字；背景通知與領域驗證也使用同一資源。不可翻譯／改寫任務、備註、使用者名稱、時區 ID、資料庫固定分類 ID、備份鍵或枚舉值。LanguageChoice 負責系統清單與英文 fallback；I18n 只使用 App Context，不修改系統語言、Locale.setDefault 或儲存的瞬間。

變更翻譯須驗證三種語言的畫面、長字串換行、日期選擇器、通知、切換後草稿與設定保存。宿主單元測試在 Android Application 尚未初始化時使用 TextFallback 的繁中錯誤文字，該檔案必須與繁中資源同步。新增語言須同時擴充 LanguageChoice、備份值契約與測試，不只新增 XML。
