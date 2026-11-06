# Official release signing

<p align="center">語言 / 语言 / Languages：<a href="../SIGNING.md">繁體中文</a> · <a href="../zh-CN/SIGNING.md">简体中文</a> · <strong>English</strong></p>

1.4.1 retains the RSA 4096 official certificate used since 1.0.5, package `com.chimafok.todo`, versionCode 37. The same package/certificate and a higher versionCode permit updating an existing official installation. See [installation and updates](INSTALL.md).

## Certificate and file checksums

Official certificate SHA-256:

`a4b166d522cf41ce213923ef054da8e9b4644abf1888580f502002e2aadfd197`

This identifies the signing certificate; it is **not the APK file checksum**. Each APK's SHA-256 is in its release's `SHA256SUMS.txt`. See the [1.4.1 verification summary](../verification/release-1.4.1.json) and [source approval record](../verification/approval-1.4.1.json).

A public certificate/fingerprint contains no private key, cannot sign updates and cannot replace a private-key/password backup. Official private keys, passwords and private keystores are absent from the repository, release assets and GitHub Actions. The maintainer stores and backs them up securely outside the repository.

## Test and official releases

App changes first get a development-signed `.debug` test APK. After chimafok explicitly approves the matching version, source commit and APK SHA-256, the existing official key may sign it, the source may enter main via PR, and an official release may be published. See [release approval](CONTRIBUTING.md#test-apks-and-release-approval). CI's release build is unsigned by default and has no official identity.

Self-building/signing does not confer the official signature. Do not silently replace keys or lower versionCode to work around update conflicts. The 1.4.1 payload was unchanged by signing; see [verification scope](TESTING.md).
