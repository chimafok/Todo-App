# 备份格式与还原契约

<p align="center">語言 / 语言 / Languages：<a href="../BACKUP-FORMAT.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/BACKUP-FORMAT.md">English</a></p>

本页是 1.4.0 的现行契约；[旧格式原始说明](../history/BACKUP-HISTORY.zh-Hant.md)保留作相容性查核。一般操作见[使用说明](USER-GUIDE.md#backup-and-restore)。

## 版本与范围

输出为未加密 UTF-8 JSON。根物件为 `format="Todo-App"`、`formatVersion=6`、`schemaVersion=10`、`exportedAt`（UTC epoch 毫秒）、`appVersion`、`data`，不能添加未知字段。

| 可读格式 | schemaVersion | 契约差异 |
|---|---|---|
| 1 | 7 | 十一阵列；不含垃圾桶、排序、一般设置、学习开始字段或图片 |
| 2 | 8 | 加入垃圾桶、排序、一般设置及学习开始数据 |
| 3 | 8 | 允许学习 IN_PROGRESS |
| 4 | 9 | 加入图片；每项至多 8 张的历史契约 |
| 5 | 9 | 取消每项图片张数上限，保留容量限制 |
| 6 | 10 | 一般设置加入 language |

只接受上述格式／schema 配对。新版能读旧档，不代表旧版能读新档；1.3.1 及更旧版本不能读格式 6。

## 十三个 data 阵列

| 阵列 | 内容 |
|---|---|
| tasks | 标题、备注、分类、截止及开始日期／时间／时区、状态、完成／删除／建立／修改时间、imageIds |
| categories、tags | ID、名称、系统分类种类（适用时）、建立／修改时间及 sortOrder；不输出可重算的 nameKey |
| task_tags、series_tags | 任务／系列的标签关联 |
| recurring_series | 系列内容、分类、生命周期、游标、删除及删除前生命周期、imageIds |
| series_rules | 规则版本、频率、锚点、截止时刻／时区、结束条件及学习开始偏移 |
| occurrences | 任务／系列／规则关联、序号、历史快照、规则取消及同次系列删除标记 |
| reminders、series_reminders | 固定／相对／系列规则、时区、基准、启用状态及可携送达记录 |
| reminder_overrides | 本次明确自定义提醒标记 |
| settings | 最多一列，id 为 general；completedRetentionDays（null 为永久）、updatedAt、language |
| note_images | id 为小写 SHA-256，data 为标准含 padding 的 Base64 JPEG |

`language` 限 `SYSTEM`、`TRADITIONAL`、`SIMPLIFIED`、`ENGLISH`；格式 6 设置列不可缺少或使用 null。字段型别、必填性及主键以 [BackupModel.kt](../../app/src/main/java/com/chimafok/todo/data/BackupModel.kt) 与 [BackupFormat.kt](../../app/src/main/java/com/chimafok/todo/data/BackupFormat.kt) 核对，不能以 Room 字段直接代替备份契约。

包含有效、已完成及垃圾桶数据。保留原删除时间、删除前生命周期与同次删除关联；导入不延长 30 天垃圾桶资格。旧备份可能带回已永久删除且不再有 tombstone 的 ID，预览须说明此限制。

不包含草稿、原始数据库、缓存、revision、保留宽限锚点、outbox、平台取消伫列、alarmKey／alarmMode、Android 权限或省电设置。可携提醒历史与平台闹钟身分不同，未来提醒重新排程，过去点不大量补发。

## 图片与容量

imageIds 是按显示顺序、逗号分隔且不重复的小写 SHA-256，空字符串代表无图片；快照可保留相同引用。格式 5／6 不限制每项张数。只输出有效任务、垃圾桶、系列及历史快照引用的图片，不输出未建立草稿图片。

导入验证 Base64、SHA-256、JPEG 型别／尺寸及完整引用图；单张至多 768 KiB、宽高 1–1536。按内容去重，依选定任务／整系列裁剪图片集合；图片与数据同交易还原。整份仍限 **64 MiB、200,000 列、12 层、5,000,000 个值**，过大会拒绝，不略过图片。文件限制不代表已通过同规模效能验收。

## 合并、取代与旧档

默认合并，以 ID、内容与已知修改时间比较，同名不同 ID 不自动合并。单笔任务与关联／提醒一组，整系列与规则／记录／提醒一组；矛盾不能拆混。任一侧涉及不同垃圾桶状态、未知或相同修改时间但内容不同时须明确选择。排序随选定分类／标签保存，同顺位依 ID 稳定排序。

语言或保留期限不同时，须明确选择整组一般设置。完整取代以文件任务、垃圾桶、排序及设置为准；**旧格式缺少的偏好保留本机值，缺少 language 时即使完整取代也保留本机语言**。语言切换不改任务时区／提醒时刻，仅语言还原不重设完成保留锚点。

格式 1／2 不接受学习 IN_PROGRESS；旧格式没有的开始字段及图片补空，不能偷带新字段。格式 1 的未知排序保留本机既有顺序，新项附后；缺少设置不清本机。完整取代旧档仍会移除文件未含的本机任务及垃圾桶。

## 验证与交易

先严格解析并验证完整关联图，再预览，再确认。拒绝无效 UTF-8、重复键、尾随内容、未知字段、错型别、主外键矛盾及无效日期／时区／状态。开始时间须附开始日期与时区，排序非负，保留天数为正整数或 null。

预览冻结本机及文件指纹；确认时同一 Room 交易重读、重算与重验，过期或任一步失败全部回滚。取消零写入。导入的一般完成项或不同保留政策按契约建立恢复宽限，不改原 completedAt；未变合并保留原锚点。先保存旧平台提醒的取消工作，再安排未来提醒。文件选择器与程序重建不会自行提交还原。
