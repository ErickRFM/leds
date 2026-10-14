# Implementation Plan - Compi V15 Master Integration (Fases 03–13)

This plan outlines the design and step-by-step implementation for integrating Phases 03 through 13 of the Compi V15 Master Plan, spanning firmware command arbitration, async Wi-Fi scanning, Android GATT concurrency & state management, and CI/CD pipeline completion.

## User Review Required

> [!IMPORTANT]
> - **Central Command Arbiter (Fase 03):** Implement thread-safe command priority and source arbitration in firmware (`esp32_rgb.ino`) so unauthenticated BLE writes cannot disrupt authenticated SoftAP Wi-Fi scripts or automations.
> - **Async Wi-Fi Scanning (Fase 05):** Transition `/v2/wifi/scan` to an asynchronous non-blocking state machine in `LocalWifiControl.h` so network scanning never blocks loop execution or LED timing.
> - **GATT Concurrency & Cleanup (Fase 06):** Harden `BleManager.kt` against callback races, stale GATT references, and Binder thread concurrency issues.

## Proposed Changes

### Firmware Core & Arbitration
#### [MODIFY] [esp32_rgb.ino](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/firmware/esp32_rgb/esp32_rgb.ino)
- Integrate source arbitration between BLE command inbox, Wi-Fi SoftAP endpoints, active RGB scripts, and background automations.
#### [MODIFY] [LocalWifiControl.h](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/firmware/esp32_rgb/core/LocalWifiControl.h)
- Refactor `/v2/wifi/scan` to use non-blocking async scan state machine (`IDLE`, `SCANNING`, `RESULTS_READY`, `FAILED`, `TIMED_OUT`).

### Android BLE Transport & Network Concurrency
#### [MODIFY] [BleManager.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/ble/BleManager.kt)
- Protect state transitions across Binder callbacks, guard null characteristics, and serialize pending write/read queues with strict timeout boundaries.

### CI/CD Workflow
#### [MODIFY] [android-ci.yml](file:///.github/workflows/android-ci.yml)
- Ensure all host unit tests (`compi_capabilities_test.cpp`, `compi_ota_preflight_test.cpp`, `rgb_automation_clock_test.cpp`) run cleanly in CI.

## Verification Plan

### Automated Tests & Local Build
- Run C++ host unit tests:
  `g++ -std=c++17 -Wall -Wextra -Werror -pedantic tests/rgb_script_runtime_test.cpp -o /tmp/rgb_script_runtime_test && /tmp/rgb_script_runtime_test`
- Run local Android build & unit tests:
  `Remove-Item Env:\ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue; powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1`
- Calculate SHA-256 hash of generated `app-debug.apk`.

### GitHub CI Verification
- Push commit to GitHub branch, create/update PR into `main`, and verify all 4 GitHub Actions jobs (`android`, `firmware`, `firmware-core-tests`, `windows-board-profile`) report `SUCCESS`.
