# 翻译与语言支持

<p align="center">語言 / 语言 / Languages：<a href="../TRANSLATING.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/TRANSLATING.md">English</a></p>

目前支持 English、繁体中文与简体中文，默认英文资源在 `app/src/main/res/values/strings.xml`，英文对照在 values-en，中文在 values-b+zh+Hant／values-b+zh+Hans。新增字串时四份资源须同时更新；保留 `%1$s` 等位置参数，可调整显示顺序但不能改变数量。`tools/check-translations.py` 会检查完整键集合、重复键及参数。

UI 透过 stringResource 或 tr 取得文字；背景通知与领域验证也使用同一资源。不可翻译／改写任务、备注、使用者名称、时区 ID、数据库固定分类 ID、备份键或枚举值。LanguageChoice 负责系统清单与英文 fallback；I18n 只使用 App Context，不修改系统语言、Locale.setDefault 或储存的瞬间。

变更翻译须验证三种语言的画面、长字串换行、日期选择器、通知、切换后草稿与设置保存。宿主单元测试在 Android Application 尚未初始化时使用 TextFallback 的繁中错误文字，该文件必须与繁中资源同步。新增语言须同时扩充 LanguageChoice、备份值契约与测试，不只新增 XML。

## 文件翻译

根目录 `README.md` 是繁中首页；`README.zh-CN.md` 与 `README.en.md` 是另外两种语言。其他说明保留繁中原路径，对应翻译位于 `docs/zh-CN/` 与 `docs/en/`，根目录政策说明亦可在这两个目录找到同名文件。每页使用同一列「繁体中文 · 简体中文 · English」切换到对应页，而非一律回首页。

修改行为说明时同步三语，保留版本、数字、指纹、程序识别码与共同的 ASCII 锚点；检查相对连结、图片及范例命令。翻译页中的文件连结应优先留在相同语言。翻译不改 MIT 或第三方授权条款；授权原文、schema／验证 JSON 与历史繁中工程纪录共用，于文件索引明示范围。历史纪录的过去状态不能当成目前版本限制。
