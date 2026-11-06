# 安装与更新

<p align="center">語言 / 语言 / Languages：<a href="../INSTALL.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/INSTALL.md">English</a></p>

## 1.4.1 更新

可直接覆盖更新正式 1.4.0，保留数据和语言设置，无需卸载。本版修复时间栏居中／裁切、两位小时自动换栏，并保留默认 10:00。正式包名 com.chimafok.todo／code37；Room schema10／备份格式6不变。

## 1.4.0 更新

正式识别码与凭证保持一致，可直接覆盖更新1.3.1，无须卸载。首次升级的语言偏好为跟随系统，可在「设定 → 介面语言」修改；不支持的系统语言使用英文。备份输出为格式6，可导入格式1–5；旧版不能读取格式6，请保留更新前的备份。


## 首次安装

1. 到本专案 [GitHub Releases](https://github.com/chimafok/Todo-App/releases/latest) 下载最新正式版 APK。请确认下载来自 `chimafok/Todo-App`。
2. 依需要比对同版本 `SHA256SUMS.txt`。Windows 可用 `Get-FileHash -Algorithm SHA256`，macOS／Linux 可用 `shasum -a 256`。
3. 开启 APK，依 Android 指示允许你使用的浏览器或文件管理器安装，完成后可关闭该来源的额外安装能力。
4. 开启 Todo，按需要授予通知与精确提醒能力；先用虚构任务确认提醒。

最低 Android 8.0／API 26。App 识别码为 `com.chimafok.todo`。`Source code.zip`／`tar.gz` 是源代码压缩档，不是 APK 安装档。

## 后续正式版更新

同一正式金钥签署的新版本提高 versionCode 后，可在原安装上更新；安装前仍建议导出重要数据。App 目前没有自动下载更新，请从 Releases 取得新 APK，不需要卸载。

## 备份格式相容性

1.4.0 导出格式 6／schema 10，可导入格式1–5，包含语言偏好、备注图片、学习进度、垃圾桶、分类／标签排序与一般设置。1.3.1及以前的版本不能读格式6，请保留更新前的重要备份。详见[备份格式](BACKUP-FORMAT.md)。

## 自行建置与官方 APK

Android Studio 开发版使用 `.debug`，自动测试使用 `.qa`，与官方 App 分开。自己产生的签章不等于官方签章，不能保证与官方 APK 互相覆盖更新。官方金钥不在源代码或下载资产中。

开发测试包与正式版的安装及数据分开；更新既有正式版请使用正式APK。
