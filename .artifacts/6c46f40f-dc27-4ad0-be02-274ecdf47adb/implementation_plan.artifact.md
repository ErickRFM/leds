# Implementation Plan - Compi V16.0 / SEG1 Sync, Audit & APK Build

This implementation plan outlines the verification of repository synchronization, integration audit for recent PRs #107–#111 (SEG1 Owner Enrollment Protocol and F09 Cross-Board Compatibility Engine), execution of Android unit tests and lint checks, debug APK assembly, and distribution packaging.

## User Review Required

> [!IMPORTANT]
> - **HEAD Commit Baseline:** `61c34d3` (`feat(seg1b): session crypto adapter + key schedule + host tests (#111)`).
> - **Scope of Synchronized Changes:**
>   - `SEG1` BLE Owner Enrollment Protocol & Session Crypto (`OwnerEnrollment.kt`, `OwnerEnrollmentTest.kt`)
>   - `F09` Cross-Board Compatibility Engine & Portable Format (`BoardCompatibility.kt`, `ProjectPortableFormat.kt`)
>   - `F08` Hardware Lab Editors & Component Dashboards (`HardwareLabScreen.kt`, `ComponentDashboardViewModel.kt`)
> - **Build Strategy:** Execute Gradle unit tests (`testDebugUnitTest`), Android lint (`lintDebug`), and debug APK assembly (`assembleDebug`) using Java 17 / Android SDK 34 via `android/build-debug.ps1` / `gradlew.bat`.
> - **Distribution Artifact:** Copy compiled `app-debug.apk` to `dist/compi-seg1-debug.apk` and record its SHA-256 checksum and size.

## Proposed Changes

### 1. Repository & Integration Audit
- Verify `main` branch HEAD `61c34d3` is in sync with `origin/main`.
- Confirm newly added SEG1 Owner Enrollment tests (`OwnerEnrollmentTest.kt`), Cross-Board Compatibility tests (`BoardCompatibilityTest.kt`), and Component Dashboard tests (`ComponentDashboardViewModelTest.kt`) are present.

### 2. Android Build & Unit Testing
- Execute local Gradle unit tests (`testDebugUnitTest`).
- Execute Android lint static analysis (`lintDebug`).
- Assemble debug APK (`assembleDebug`).

### 3. Distribution Package & Verification
- Verify generated APK (`android/app/build/outputs/apk/debug/app-debug.apk`).
- Create `dist/` directory if missing and copy APK to `dist/compi-seg1-debug.apk`.
- Compute SHA-256 checksum, file size, and build metadata.

## Verification Plan

### Automated Tests
- Run `./gradlew.bat --no-daemon testDebugUnitTest` / `android/build-debug.ps1`.
- Run `./gradlew.bat --no-daemon lintDebug`.
- Confirm 100% pass rate across all Android unit tests.

### Artifact Verification
- Verify existence and size of `dist/compi-seg1-debug.apk`.
- Compute SHA-256 hash using `Get-FileHash -Algorithm SHA256`.
