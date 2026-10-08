# Implementation Plan - RGB Controller V1 (PR #7 Integration & Local Verification)

This plan details the steps to sync branch `fix/android-ci-ble-rgb-integration`, verify local environments (JDK 17, Android SDK 34, Arduino CLI), run the verification script `scripts/verify-local.ps1`, build the debug APK locally, calculate its SHA-256 hash, and merge PR #7 into `main`.

## User Review Required

> [!IMPORTANT]
> - **Local Build & Verification:** Since GitHub Actions is blocked by billing, builds will be executed locally via `scripts/verify-local.ps1` using the Gradle wrapper and local Android SDK 34.
> - **Hardware Testing:** Physical BLE tests (`READY_FOR_PHYSICAL_TEST`) will be noted as pending or executed depending on whether an ESP32 and phone are physically connected.

## Proposed Changes

### Branch Synchronization & Environment Setup
#### [MODIFY] [git checkout](file:///)
- Checkout and pull `fix/android-ci-ble-rgb-integration` from origin.
- Ensure Gradle wrapper scripts (`gradlew`, `gradlew.bat`) and `scripts/verify-local.ps1` are active in the local workspace.

### Local Verification & APK Generation
#### [NEW] [verify-local.ps1 execution](file:///scripts/verify-local.ps1)
- Run `powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1` to execute unit tests, lint checks, and APK compilation (`assembleDebug`).
- Verify output APK at `android/app/build/outputs/apk/debug/app-debug.apk`.
- Calculate SHA-256 hash of the generated APK.

### Merge PR #7 & Main Synchronization
#### [MODIFY] [main branch](file:///)
- Merge `fix/android-ci-ble-rgb-integration` into `main` (closing PR #7).
- Push `main` to origin.

## Verification Plan

### Automated Tests & Local Build
- Run `powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1`
- Verify unit tests and APK generation.
- Check SHA-256 hash of `app-debug.apk`.

### Manual Verification
- Physical BLE verification with ESP32 & Android phone (if connected).
