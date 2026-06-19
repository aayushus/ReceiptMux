---
name: android-apk-release-workflow
description: Sets up a GitHub Actions workflow that builds an Android debug APK and publishes it to a GitHub Release. Use when the user asks to auto-generate APKs, create Android release workflows, save APKs in GitHub Releases, or configure the ReceiptMux v0.1 release automation.
---

# Android APK Release Workflow

## Purpose

Create or refresh the GitHub Actions workflow that builds ReceiptMux's debug APK and attaches it to a GitHub Release.

## Rules

- Trigger only on the exact Git tag `v0.1`.
- Do not use wildcard tags such as `v0.1.*`.
- Run `:app:testDebugUnitTest` before building the APK.
- Build `:app:assembleDebug`.
- Publish `release/ReceiptMux-v0.1-debug.apk` to the `ReceiptMux v0.1` GitHub Release.
- Do not configure signing unless the user explicitly asks for release signing.

## Apply The Workflow

Run this script from the repository root:

```sh
bash .cursor/skills/android-apk-release-workflow/scripts/install-workflow.sh
```

Then inspect:

```sh
git diff -- .github/workflows/android-apk-release.yml
```

## Release Command

After the workflow and Android source are committed and pushed, create the release with:

```sh
git tag v0.1
git push origin v0.1
```

The workflow intentionally will not run for `v0.1.0`, `v0.1.1`, or other patch tags.
