# Contributing

<p align="center">語言 / 语言 / Languages：<a href="../../CONTRIBUTING.md">繁體中文</a> · <a href="../zh-CN/CONTRIBUTING.md">简体中文</a> · <strong>English</strong></p>

Bug reports and improvements are welcome. chimafok maintains this project; there is no fixed response or release schedule.

Check existing issues first. Discuss substantial behavior changes in an issue before implementing them. Submit only changes you have the right to contribute under this project's MIT license, and preserve third-party licenses.

<a id="branches-and-merges"></a>
## Branches and merges

A public repository accepts outside pull requests but does not grant write or merge access. If you lack repository write access, fork it, create a work branch in your fork, and open a PR. `codex/*` is a naming convention; other clear branch names are welcome.

For app changes:

1. Branch from the latest `dev`.
2. Update code and documentation, run appropriate checks, and open a PR targeting `dev`.
3. A maintainer with merge permission confirms scope and checks, then merges for integration validation.
4. Deliver a development-signed test APK. Only after chimafok explicitly approves that version may the matching source be formally signed, merged into `main` through a PR by an authorized maintainer, and published.

Documentation-only text, layout and link edits may branch from the latest `main`, go through a PR and required checks, and be merged by an authorized maintainer before synchronization to `dev`. This route excludes code, configuration, CI, dependencies, license terms and runtime behavior. Fast-forward `dev` only when possible; otherwise integrate through a PR without overwriting unreleased work.

`main` requires pull requests and required checks. Do not push directly, force-push, delete the release branch or bypass protection. Submitting a PR does not guarantee acceptance. CI does not automatically merge or publish APKs. Documentation or source merges do not update installed apps.

GitHub permissions and branch protection govern technical access; this document grants none. Release approval is an additional project rule even if GitHub does not require a particular approving review. Refer to the repository's current protection settings instead of assuming a dated description remains accurate.

- `main`: checked release baseline.
- `dev`: integration branch; its content is public too.
- `codex/*` or another clearly named short-lived branch: an individual change.

<a id="test-apks-and-release-approval"></a>
## Test APKs and release approval

After engineering checks and `dev` integration, deliver a development-signed `.debug` APK for chimafok to install and test. `.qa` is reserved for isolated automation. Include the version, source commit, APK SHA-256, changes, verified scope and known limitations. Test and official builds install separately and store data separately.

Formal signing with the existing official key, the matching `main` PR and official publication require explicit approval of that test version. Approval may be expressed in a conversation or PR. Record the corresponding version and date without publishing private conversations. Silence, passing CI and requests to continue development are not release approval.

Approval covers the tested app content. Further app changes need a new test APK and approval. Check formal-build/source correspondence and release-variant signing and installation/update behavior separately; successful debug testing is not a substitute. APKs are delivery assets, never Git source files.

Already authorized routine work—branches, edits, tests, commits, pushes, PRs, `dev` integration and test APK delivery—does not need repeated step-by-step permission. Documentation-only changes follow the route above without generating an APK or obtaining app-release approval.

## Changes and verification

Keep Traditional Chinese, Simplified Chinese and English interfaces consistent. Preserve migrations and backup contracts. Do not clear user data to fix an upgrade or automate against the everyday official installation.

For documentation-only changes, check text, links and public content locally; required repository CI still applies. For code, run relevant unit tests, builds and Lint, plus appropriate UI, transaction or scheduling QA. Device tests explicitly target `.qa` on an owned emulator or an authorized test device. Tests should verify behavior, not mirror implementation.

Do not commit private keys, keystores/backups containing them, passwords, tokens, local path settings, real databases or backups. Public certificates and fingerprints identify a signer and differ from private keys; see [signing](SIGNING.md). Screenshots use synthetic data. Publish official APKs only as approved Releases; test APKs may be delivered locally or as clearly marked pre-releases. Neither belongs in Git history. CI receives no official private key.
