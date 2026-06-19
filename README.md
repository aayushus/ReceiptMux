# ReceiptMux

ReceiptMux is an Android receipt scanner that captures receipts, cleans up the image, extracts basic OCR metadata, and queues uploads to an FTP, FTPS, or SMB destination.

## Features

- Camera-based receipt scanning with automatic capture.
- Live capture coaching for lighting, focus, stillness, and framing.
- Receipt processing with crop, deskew, cleanup, and preview.
- Manual crop adjustment for low-confidence scans.
- OCR-assisted receipt naming.
- Upload queue with retry, delete, and completed-item cleanup.
- FTP, FTPS, and SMB upload configuration.
- SMB folder browsing and connection testing.

## Tech Stack

- Kotlin
- Jetpack Compose
- CameraX
- Hilt
- Room
- DataStore
- WorkManager
- ML Kit Text Recognition
- OpenCV

## Getting Started

### Requirements

- Android Studio or the Android Gradle plugin toolchain
- JDK 17
- Android SDK 35

### Build

```sh
./gradlew :app:assembleDebug
```

### Run Unit Tests

```sh
./gradlew :app:testDebugUnitTest
```

### Install On A Connected Device

```sh
./gradlew :app:installDebug
```

## Configuration

ReceiptMux stores upload settings locally on device. Configure the destination in the app's Settings screen before uploading receipts.

Supported upload targets:

- FTP
- FTPS
- SMB/NAS shares

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
