# Implementation Plan - Compi V9 Reference UI & V12 Automation Integration

This plan outlines the complete execution of **Compi V9 Reference UI** integration across all four core screens (`DashboardScreen`, `LedModuleScreen`, `ProgramEditorScreen`, `LabHubScreen`) matching the 4 reference UI screenshots, while fully unifying V12 automation capabilities, Bluetooth, Wi-Fi, GPIO, ADC telemetry, and Compi Studio scripting.

## User Review Required

> [!IMPORTANT]
> - **Pixel-Perfect Reference UI:** All four views (Inicio, LED RGB, Studio, Hardware Lab) will be meticulously styled using Jetpack Compose and `RgbTheme` (`core/ui/RgbTheme.kt`, `core/ui/CompiComponents.kt`) to match the 4 reference screenshots.
> - **V12 Automations Integration:** Hardware Lab (`LabHubScreen.kt`) and Compi Studio (`ProgramEditorScreen.kt`) will preserve and integrate all V12 automation logic, GPIO controls, ADC sensors, script compilers, and local Wi-Fi client policies.

## Proposed Changes

### Design System & Compi Components
#### [MODIFY] [CompiComponents.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/core/ui/CompiComponents.kt)
- Enhance or refine dark mate card containers, purple accent buttons, bottom navigation bar with V9 icons (Inicio, LED, Studio, Hardware), status indicators, and headers.

### Screen 1: Dashboard / Inicio
#### [MODIFY] [DashboardScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/home/DashboardScreen.kt)
- Implement exact V9 Home layout: Compi header, ESP32 status card with illustration, quick access buttons (LED RGB, Compi Studio, Hardware Lab, Dispositivos), main tools cards, and recent activity feed.

### Screen 2: LED RGB Module
#### [MODIFY] [LedModuleScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/led/LedModuleScreen.kt)
- Implement exact V9 LED RGB layout: Bluetooth/Wi-Fi toggle header, connected device card, Color control panel with interactive color wheel and hex preview, R/G/B sliders with numeric text fields, Brightness slider, Power toggle with active status, Favorites color pills, and Effects selector (Rainbow, Breathe, Fade) with speed slider.

### Screen 3: Compi Studio
#### [MODIFY] [ProgramEditorScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/programs/ProgramEditorScreen.kt)
- Implement exact V9 Compi Studio layout: Header with subtitle, Local Editor card with toolbar (Import, Paste, Export, Copy), Project type selector (Laboratorio vs Arduino C++), code editor with line numbers and syntax highlighting, size/line counters, Run RGB / Stop controls, and "Mis proyectos" list with open/delete actions.

### Screen 4: Hardware Lab
#### [MODIFY] [LabHubScreen.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/feature/hub/LabHubScreen.kt)
- Implement exact V9 Hardware Lab layout: Top tab selector (Conexión, GPIO, Sensores, Diagnóstico), Laboratory Wi-Fi connection card with secure key and status, Device state card (Chip, Flash, RAM, RSSI), GPIO control card with pin selector and HIGH/LOW/Read actions, Sensors ADC1 card with real-time waveform chart, and Autodiagnostic execution card.

### Navigation & ViewModel Unification
#### [MODIFY] [MainActivity.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/MainActivity.kt)
- Unify bottom navigation bar to display exactly 4 tabs (Inicio, LED, Studio, Hardware) corresponding to the reference design without duplication.
#### [MODIFY] [RgbControllerViewModel.kt](file:///C:/Users/erik5/OneDrive/Escritorio/ESP32/leds/android/app/src/main/java/com/rgbcontroller/RgbControllerViewModel.kt)
- Ensure all states (BLE connection, RGB colors, effects, GPIO, ADC telemetry, script execution, Wi-Fi lab session) are robustly managed and synchronized.

## Verification Plan

### Automated Tests
- Run unit tests and instrumentation tests:
  `cd android && ./gradlew testDebugUnitTest lintDebug assembleDebug`
- Calculate SHA-256 of `app-debug.apk`.

### Manual / Visual Verification
- Compare composable screens against the 4 reference UI screenshots (Dashboard, LED RGB, Hardware Lab, Compi Studio).
