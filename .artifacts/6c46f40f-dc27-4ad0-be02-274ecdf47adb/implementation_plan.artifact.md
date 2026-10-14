# Implementation Plan - Compi V15 Master Integration, Post-Merge Audit & Release

This implementation plan details the final engineering, security hardening, C++ host test additions, and release verification for **Compi V15**.

## User Review Required

> [!IMPORTANT]
> - **Explicit BLE Channel Authorization (Ciclo 1 / Fase 03 & 04):** Update `esp32_rgb.ino` and `CompiCommandArbiter.h` so BLE GATT write commands evaluate channel authorization state (`BLE_UNAUTHENTICATED` vs `BLE_AUTHENTICATED`) dynamically rather than hardcoding static permissions.
> - **CI Test Execution (Ciclo 1 / Fase 11):** Add `compi_arbiter_test.cpp` explicitly to `.github/workflows/android-ci.yml` under `firmware-core-tests`.

## Proposed Changes

### Core Firmware & Arbitration
#### [MODIFY] [CompiCommandArbiter.h](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/firmware/esp32_rgb/core/CompiCommandArbiter.h)
- Refine `CompiCommandArbiter.h` to evaluate `CommandSource::BLE_UNAUTHENTICATED` vs `BLE_AUTHENTICATED` and reject unauthenticated GATT writes.
#### [MODIFY] [esp32_rgb.ino](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/firmware/esp32_rgb/esp32_rgb.ino)
- Pass dynamic channel authentication status to `commandArbiter.evaluate(...)`.

### CI/CD Pipeline
#### [MODIFY] [android-ci.yml](file:///.github/workflows/android-ci.yml)
- Add `compi_arbiter_test` host test step to `firmware-core-tests` in `.github/workflows/android-ci.yml`.

## Verification Plan

### Automated Tests & Local Build
- Run Android Gradle build and unit tests:
  `Remove-Item Env:\ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue; powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1`
- Verify 75/75 Android unit tests pass and calculate SHA-256 hash of `app-debug.apk`.

### GitHub CI Verification
- Commit, push feature branch, merge into `main`, and verify all 4 GitHub Actions jobs (`android`, `firmware`, `firmware-core-tests`, `windows-board-profile`) report `SUCCESS`.
