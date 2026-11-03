> 历史版本：以下仅描述 1.0.5。最新安装与限制请看 [INSTALL](INSTALL.md)／[KNOWN-ISSUES](KNOWN-ISSUES.md)。

# Todo 1.0.5 — 首次公开发布

<p align="center">語言 / 语言 / Languages：<a href="../RELEASE-1.0.5.md">繁體中文</a> · <strong>简体中文</strong> · <a href="../en/RELEASE-1.0.5.md">English</a></p>

源代码与文件采 MIT。主要功能沿用 1.0.4：日程、作业进度、重复任务、多个提醒、分类标签、搜索／长按多选、垃圾桶及 JSON 备份。

本版加入独立正式签章、公开使用与开发文件、虚构数据展示、固定版本的 GitHub CI，以及第三方授权声明。Android 8.0／API26 以上；识别码 com.chimafok.todo，versionCode27。

## 下载

下载 Todo-1.0.5.apk；SHA256SUMS.txt 是文件校验值。GitHub 自动产生的 Source code.zip／tar.gz 是源代码，不是手机安装包。

## 安装与更新

首次使用请下载并安装 APK，再按需要设置通知与精确提醒能力。后续正式版可直接安装更新，保留既有数据；更新前建议导出重要内容。完整步骤见 [安装与更新](INSTALL.md)。

## 验证与限制

86 项纯规则、7 项备份还原 UI、1 项展示 UI 通过（分开执行）；Debug／Release Lint 各 0 errors／10 warnings。API26／37 专用空白模拟器完成签章界线、新安装、启动及同版本重装检查；没有操作用户手机。原功能 Kotlin／资源与 Room schemas 保持一致。

仍在持续开发中，尚未完成全部真机、背景／隔夜提醒、TalkBack 或大量数据效能验收；请阅读 [已知限制](KNOWN-ISSUES.md)，先备份重要内容并在自己的手机确认提醒。

正式凭证 SHA-256：a4b166d522cf41ce213923ef054da8e9b4644abf1888580f502002e2aadfd197。
