#!/usr/bin/env bash
set -euo pipefail

mkdir -p .github/workflows

cat > .github/workflows/android-apk-release.yml <<'YAML'
name: Build APK Release

on:
  push:
    tags:
      # Intentionally exact: this workflow only releases v0.1, not v0.1.x.
      - "v0.1"

jobs:
  build-debug-apk:
    name: Build debug APK
    runs-on: ubuntu-latest

    permissions:
      contents: write

    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: 17

      - name: Make Gradle executable
        run: chmod +x ./gradlew

      - name: Run unit tests
        run: ./gradlew :app:testDebugUnitTest

      - name: Build debug APK
        run: ./gradlew :app:assembleDebug

      - name: Rename APK
        run: |
          mkdir -p release
          cp app/build/outputs/apk/debug/app-debug.apk release/ReceiptMux-v0.1-debug.apk

      - name: Publish GitHub release
        uses: softprops/action-gh-release@v2
        with:
          tag_name: v0.1
          name: ReceiptMux v0.1
          files: release/ReceiptMux-v0.1-debug.apk
YAML

echo "Installed .github/workflows/android-apk-release.yml"
