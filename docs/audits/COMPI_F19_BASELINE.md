# COMPI — FASE F19 — RECONCILIACIÓN Y AUDITORÍA COMPLETA DEL REPOSITORIO

## F19.1. Inspección Git y Estado del Repositorio

- **Repositorio Oficial:** https://github.com/ErickRFM/leds
- **Rama de Integración:** `main`
- **Referencia Auditada Inicial:** `c0cc954c7bc433e06e8daab62c1eec57afc100d8`
- **HEAD Actual de `main`:** `c13c84a` (`docs(v19): finalize task tracking and walkthrough for APK build`)
- **Estado de Trabajo Git Local:** Limpio.
- **PRs Pendientes:** 0 (Sin PRs abiertos en el repositorio).
- **PRs Recientes Fusionados (#145 - #157):**
  - #157: `fix(wifi): stabilize ESP32 laboratory SoftAP initialization`
  - #156: `fix(v17): firmware sin GPIO falsamente reservados en módulos inactivos`
  - #155: `feat(v17): reasignación real y confirmada de tres GPIO RGB`
  - #154: `style(v19): menos texto y jerarquía limpia en Studio, Módulos y Hardware`
  - #153: `feat(v17): escaneo Wi-Fi real desde Conectar dispositivo`
  - #152: `fix(v19): BLE discovery after NVS boot fix + compact connections UX`
  - #151: `feat(v17): Inicio compacto reconciliado con UX actual`
  - #150: `fix(v18): constant-work lab HTTP credential check (reconciled)`
  - #149: `fix(ESP32): optional modules cannot block SoftAP/BLE boot`
  - #145: `fix(v17): activar módulos sin GPIO previamente reservados`

---

## F19.2. Inventario Arquitectónico

### Android App (`com.rgbcontroller`)
- **BLE Core:** `BleManager.kt`, `BleConnectionState.kt`, `OwnerEnrollmentClient.kt`, `OwnerEnrollment.kt`, `EnrollmentReply.kt`, `SessionCrypto.kt`, `SessionKeySchedule.kt`, `BouncyCastleSessionCrypto.kt`.
- **Wi-Fi / Local Lab Client:** `LocalLabClient.kt`, `CompiLabSession.kt`, `LabNetworkPolicy.kt`, `LabAuthPolicy.kt`, `ComponentApplyCasPolicy.kt`.
- **ViewModels:** `RgbControllerViewModel.kt`, `ComponentDashboardViewModel.kt`.
- **Hardware & Components:** `ComponentCatalog.kt`, `ComponentStatusRepository.kt`, `ComponentStatusPersistence.kt`, `Esp32BoardPinout.kt`, `PinAssignmentPolicy.kt`.
- **UI Screens & Features:** `DashboardScreen.kt` (Home), `DevicesScreen.kt` / `ConnectionCenterScreen.kt`, `ModuleWorkspaceScreen.kt`, `HardwareLabScreen.kt`, `ProgramEditorScreen.kt` (Studio), `SettingsScreen.kt`.

### Firmware ESP32 (`firmware/esp32_rgb`)
- **Core Sketch:** `esp32_rgb.ino`.
- **Security & Authorization:** `OwnerEnrollmentSession.h`, `OwnerEnrollmentFrames.h`, `OwnerEnrollmentStore.h`, `NvsOwnerEnrollmentStore.h`, `CommandAuthorization.h`, `CompiCommandArbiter.h`, `BleSecurityPolicy.h`, `MbedTlsSessionCrypto.h`, `SessionKeySchedule.h`, `ComponentApplyReplayGuard.h`.
- **Network & HTTP:** `LocalWifiControl.h`.
- **Hardware / Pin Management:** `PinManager.h`, `ModuleRegistry.h`, `ComponentCatalog.h`, `ComponentStatus.h`, `CompiCapabilities.h`.
- **Modules / Drivers:** `LedRgbModule.h` (GPIO 27/25/26 default), `Dht22Module.h`, `SoilMoistureModule.h` (ADC1), `OledSsd1306Module.h` (I²C).
- **Runtimes & Storage:** `RgbScriptRuntime.h`, `ExperimentRuntime.h`, `RgbAutomationClock.h`, `RgbSettingsStore.h`.

### Calidad & Automatización (CI/CD)
- Workflows: `.github/workflows/android-ci.yml`.
- Pruebas C++ Host (24 suites de prueba en `tests/`).
- Pruebas Unitarias Kotlin Android en `android/app/src/test/`.
- Verificaciones de contratos y presupuestos mediante scripts Python.

---

## F19.3. Matriz de Trazabilidad y Estado de Funcionalidades

| Función / Componente | Código | Runtime | UI | Unit Tests | Integración | Validación Física | Estado | Archivo Principal |
|---|---|---|---|---|---|---|---|---|
| **RGB LED Control (27/25/26)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `LedRgbModule.h` / `RgbControllerViewModel.kt` |
| **Reasignación GPIO RGB Dinámica** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `PinManager.h` / `HardwareLabEditors.kt` |
| **BLE GATT Transport** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `BleManager.kt` / `RgbCommandInbox.h` |
| **BLE Owner Enrollment (SEG1)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `OwnerEnrollmentClient.kt` / `OwnerEnrollmentSession.h` |
| **Wi-Fi SoftAP (`ESP32-Control-Lab`)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `LocalWifiControl.h` / `LocalLabClient.kt` |
| **Seguridad HTTP (`X-Device-Key`)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `LocalLabClient.kt` / `LocalWifiControl.h` |
| **Árbitro Único (`CompiCommandArbiter`)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `CompiCommandArbiter.h` / `CommandAuthorization.h` |
| **Replay Guard (`ComponentApplyReplayGuard`)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `ComponentApplyReplayGuard.h` |
| **Catálogo Modular & Leases GPIO** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `ModuleRegistry.h` / `ComponentCatalog.kt` |
| **Sensor DHT22 (Temp/Hum)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `Dht22Module.h` / `HumidityTemperatureWidget.kt` |
| **Humedad del Suelo (ADC1)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `SoilMoistureModule.h` / `SoilMoistureWidget.kt` |
| **OLED SSD1306 (I²C)** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `OledSsd1306Module.h` / `OledDisplayWidget.kt` |
| **Studio & Script Runtime** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `RgbScriptRuntime.h` / `ProgramEditorScreen.kt` |
| **Pre-flight OTA Check & Memory Budget** | Sí | Sí | Sí | Sí | Sí | Requiere banco | **INTEGRATED** | `CompiOtaPreflight.h` / `check-firmware-budget.py` |

---

## F19.4. Resumen y Plan de Trabajo Inmediato (F20–F26)

1. **F20 — BLE Seguro:** Auditar y verificar que no existan carreras en `BleManager` y garantizar la completa interoperabilidad entre Android `OwnerEnrollmentClient` y ESP32 `OwnerEnrollmentSession`.
2. **F21 — Conectividad Wi-Fi & Autoridad Unificada:** Asegurar que las operaciones HTTP pasen de forma consistente por `CompiCommandArbiter` y que los fallos de red no afecten las radios de respaldo BLE/SoftAP.
3. **F22 — Sistema de Módulos & GPIO Dinámicos:** Verificar los leases de GPIO y prevenir conflictos entre buses I²C, PWM y ADC1.
4. **F23 — Funciones de Laboratorio:** Validar lecturas crudas/porcentuales y evitar falsas lecturas del sensor si este no está presente.
5. **F24 — UX/UI Definitiva:** Asegurar una navegación fluida, diseño responsive glassmorphism mate y buena accesibilidad.
6. **F25 — Firmware, Memoria & OTA:** Confirmar el presupuesto de 1.9 MB y verificar que el pre-flight de OTA devuelva diagnósticos claros.
7. **F26 — Certificación:** Ejecutar la suite completa de pruebas automatizadas y generar la documentación de entrega.
