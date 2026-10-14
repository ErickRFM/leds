# Implementation Plan - Compi V14.2 → V15 Continuous Engineering & Stabilization

This implementation plan details the step-by-step engineering, stabilization, error typing, and runtime safety enhancements across Android, ESP32 firmware, and CI/CD pipelines.

## User Review Required

> [!IMPORTANT]
> - **Typed HTTP Exceptions (Phase 02):** Replacing string-matching HTTP error handling with typed `LabHttpException` sealed hierarchy in `LocalLabClient.kt` and `CompiLabSession.kt` to prevent non-auth HTTP status codes (e.g. 409 Conflict) from prematurely invalidating authorized SoftAP sessions.
> - **Script Runtime Safety (Phase 01):** Refining `RgbScriptRuntime.h` to distinguish execution duration bounds from loop tick jitter, verified with C++ host tests.

## Proposed Changes

### Core Firmware & Runtime
#### [MODIFY] [RgbScriptRuntime.h](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/firmware/esp32_rgb/core/RgbScriptRuntime.h)
- Refine deadline safety check and tick jitter tolerance in `RgbScriptRuntime.h`.
#### [MODIFY] [rgb_script_runtime_test.cpp](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/tests/rgb_script_runtime_test.cpp)
- Add host C++ regression tests for tick delays (1ms, 5ms, 20ms, 100ms) and max duration bounds.

### Android Core & Network API
#### [NEW] [LabHttpException.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/wifi/LabHttpException.kt)
- Create typed HTTP exception hierarchy (`Unauthorized`, `Forbidden`, `Conflict`, `BadRequest`, `NetworkUnavailable`, `ServerError`).
#### [MODIFY] [LocalLabClient.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/wifi/LocalLabClient.kt)
- Throw typed `LabHttpException` based on HTTP response code instead of generic string exceptions.
#### [MODIFY] [CompiLabSession.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/wifi/CompiLabSession.kt)
- Handle `LabHttpException` in `failed()` to preserve `AUTHORIZED` connection state when non-auth HTTP errors occur.

### Testing & CI Workflow
#### [MODIFY] [CompiLabSessionTest.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/test/java/com/rgbcontroller/CompiLabSessionTest.kt)
- Add unit tests for typed HTTP exceptions and session preservation on HTTP 409 Conflict.
#### [MODIFY] [android-ci.yml](file:///.github/workflows/android-ci.yml)
- Verify and include any missing C++ host test executions in the GitHub Actions pipeline.

## Verification Plan

### Automated Tests
- Execute C++ host tests:
  `g++ -std=c++17 -Wall -Wextra -Werror -pedantic tests/rgb_script_runtime_test.cpp -o /tmp/rgb_script_runtime_test && /tmp/rgb_script_runtime_test`
- Execute Android build & JVM unit tests:
  `Remove-Item Env:\ANDROID_PREFS_ROOT -ErrorAction SilentlyContinue; powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1`
- Calculate SHA-256 hash of output `app-debug.apk`.

### Manual / CI Verification
- Push commit to GitHub, verify all CI jobs (`android`, `windows-board-profile`, `firmware-core-tests`, `firmware`) return `SUCCESS`.
