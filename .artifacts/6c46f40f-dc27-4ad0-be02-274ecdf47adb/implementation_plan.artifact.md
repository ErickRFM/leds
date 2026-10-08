# Implementation Plan - RGB Controller V1 (Android Kotlin + ESP32 + BLE)

This plan outlines the complete implementation of the **RGB Controller** project, integrating native Android (Kotlin, Jetpack Compose, Material 3, BLE GATT) with the ESP32 C++ firmware (`esp32_rgb.ino`), following the Master Plan (`docs/PLAN_MAESTRO.md`).

## User Review Required

> [!IMPORTANT]
> - **Bluetooth Permissions & Hardware:** Android 12+ requires `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT` runtime permissions. Physical testing requires an ESP32 connected to an RGB LED on GPIO 27 (Red), 25 (Green), and 26 (Blue).
> - **Architecture:** Clean Architecture separation: `core/ble`, `core/model`, `core/data`, `core/ui`, and feature modules (`devices`, `control`, `presets`, `effects`, `settings`).

## Open Questions

- None. The protocol (`PROTOCOLO_BLE.md`) and architecture (`ARQUITECTURA.md`) are fully established in PR #1.

## Proposed Changes

### Project Foundation & Android Module Setup
#### [NEW] [build.gradle.kts](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/build.gradle.kts)
- Project-level Gradle configuration (Kotlin version, Android Gradle Plugin, Compose compiler).
#### [NEW] [settings.gradle.kts](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/settings.gradle.kts)
- Gradle settings for `:app` module.
#### [NEW] [app/build.gradle.kts](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/build.gradle.kts)
- App-level Gradle dependencies: Jetpack Compose, Material 3, ViewModel, Coroutines, DataStore, Lifecycle, JUnit, Espresso, Compose UI Test.
#### [NEW] [AndroidManifest.xml](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/AndroidManifest.xml)
- Declaring Bluetooth permissions (`BLUETOOTH`, `BLUETOOTH_ADMIN`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `ACCESS_FINE_LOCATION`) and application entry point.

### Core BLE & Business Logic Layer
#### [NEW] [BleConnectionState.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/ble/BleConnectionState.kt)
- Sealed interface for BLE states (`Disconnected`, `Scanning`, `Connecting`, `Discovering`, `Ready`, `Error`).
#### [NEW] [BleManager.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/ble/BleManager.kt)
- BLE GATT client wrapper handling scanning, connection, service discovery, characteristic read/write, notifications, and reconnection logic.
#### [NEW] [ColorUtils.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/model/ColorUtils.kt)
- HSV to RGB conversion functions, Hex string parsing, and command string serialization (`SET,R,G,B,Brightness`, `OFF`, `ON`, `GET`).
#### [NEW] [PreferencesRepository.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/data/PreferencesRepository.kt)
- DataStore implementation for saving favorite colors, last connected device MAC, and settings.

### Jetpack Compose UI Screens & ViewModels
#### [NEW] [MainActivity.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/MainActivity.kt)
- Host activity with Material 3 theme and Navigation setup.
#### [NEW] [DevicesScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/devices/DevicesScreen.kt)
- Device scanner, permission handler, and connection list.
#### [NEW] [ControlScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/control/ControlScreen.kt)
- Interactive color picker/wheel, RGB sliders, brightness slider, power toggle, and live status preview.
#### [NEW] [PresetsScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/presets/PresetsScreen.kt)
- Predefined color palettes and custom favorite colors persistence.
#### [NEW] [EffectsScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/effects/EffectsScreen.kt)
- LED lighting effects (Rainbow, Breath, Fade, RGB Cycle) with speed adjustment.
#### [NEW] [SettingsScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/settings/SettingsScreen.kt)
- Device diagnostics, firmware version display, and connection help.

### Firmware Hardening & CI
#### [MODIFY] [esp32_rgb.ino](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/firmware/esp32_rgb/esp32_rgb.ino)
- Enhanced command parsing robustness, PWM pin configuration, advertising recovery, and non-blocking state handling.
#### [NEW] [android-ci.yml](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/.github/workflows/android-ci.yml)
- GitHub Actions workflow for building Android app and running unit tests.

## Verification Plan

### Automated Tests
- Run unit tests for color conversion (HSV/RGB) and command serialization:
  `cd android && ./gradlew testDebugUnitTest`
- Run Android lint check:
  `cd android && ./gradlew lintDebug`
- Build debug APK:
  `cd android && ./gradlew assembleDebug`

### Manual Verification
- Deploy APK to an Android device, scan for ESP32, connect via BLE, test RGB sliders, presets, and power toggles.
- Verify ESP32 compilation with Arduino-ESP32 3.x.
