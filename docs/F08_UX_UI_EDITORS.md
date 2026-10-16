# F08 · Editores UX/UI de Hardware Lab

**Propósito:** aportar **editores visuales funcionales** para los elementos
de Hardware Lab, manteniendo la arquitectura modular existente, respetando la
autoridad del firmware (`gpioAuthority = "firmware"`) y **sin presentar
simulaciones como datos físicos confirmados**.

## Alcance

- **Umbrales de alerta**: creación, edición, borrado y persistencia local de
  `AlertRule` (componente/serie reales, comparación, umbral, histéresis,
  intervalo mínimo, severidad, habilitado).
- **Automatizaciones**: edición completa de reglas con acciones
  `NOTIFY` / `SET_RGB` / `UPDATE_OLED`; las acciones de hardware solo se
  ejecutan por el canal ya autorizado (quedan **BLOQUEADAS** si no hay
  autorización, nunca simuladas). La condición reutiliza el motor de alertas.
- **Calibración de suelo**: asistente de captura que solo acepta lecturas reales
  de `adc_raw` (fresco y válido) para rellenar las referencias **seco** y
  **húmedo**; valida el par mediante `SoilCalibration` (rango 0–4095, separación
  mínima 100) y lo guarda ligado al **dispositivo actual**.
- **Componentes reutilizables locales**: editor para `LocalDesiredComponent` con
  validación contra `ComponentCatalog` (id conocido, GPIO válidos 0–39,
  requeridos, sin duplicados).
- **Validación de configuración**: `LabConfigValidation.validateDesired` marca
  duplicados e ids fuera de catálogo (banner visible en UI).
- **Estados reales**: se muestra claramente conexión/autorización, «en vivo» vs
  «último estado confirmado», errores y sin conexión. Ningún dato de esta
  fase se trata como **aceptado** por el firmware.

## Arquitectura

- **Capa pura (sin Android):** `core/components/editors/HardwareLabEditors.kt`
  - `EditorIssue`, `EditorResult<T>` (un borrador inválido **nunca** devuelve
    `value != null`).
  - `AlertRuleDraft`, `AutomationRuleDraft`, `SoilCalibrationDraft`,
    `ComponentConfigDraft`, `LabConfigValidation`.
- **UI (Compose):** `feature/hardwarelab/HardwareLabEditorDialogs.kt` y
  `HardwareLabConfigEditors.kt` (diálogos, asistente de calibración, validación).
- **Wiring:** `ComponentDashboardViewModel` expone `setAlertRules`,
  `setAutomationRules`, `setSoilCalibration`; `HardwareLabScreen` añade botones
  «Editar umbrales», «Editar automatizaciones», «Calibrar», «Editar» y el banner
  de validación. La automatización reusa el `AutomationActionSink` ya existente
  (Wi-Fi autorizado; `UPDATE_OLED` queda bloqueado por diseño hasta que exista
  un endpoint autorizado).

## Compatibilidad

- Respeta **ESP32/BLE/WiFi** existentes: no se añade ningún UUID BLE nuevo,
  ni nuevo transporte. `gpioAuthority = "firmware"`.
- `DataStoreComponentStatusStorage` almacena únicamente datos de experimento/
  configuración local (alertas, automatizaciones, calibración de suelo ligada
  al dispositivo). **Nunca** claves/propietario del dispositivo.
- Ids de catálogo conservados: `dht22`, `soil_moisture`, `led_rgb`,
  `ssd1306_oled`, `analog_sensor`, `digital_sensor`, `actuator_relay`.
- Navegación y `MainActivity` sin cambios.

## Validación

- **Tests Android:** `HardwareLabEditorsTest` (10), tests de VM ampliados para
  persistencia desde editores. Total **194 pruebas / 38 clases**.
- **Lint/Build:** `lintDebug`, `assembleDebugAndroidTest`, `assembleDebug` OK.
- **Contratos:** `check-contracts.py` → **0 fallos**.
- **CI:** 4 trabajos verdes (`android`, `firmware`, `firmware-core-tests`,
  `windows-board-profile`).

## No incluido en F08

- **F09** reservado para _import/export local_ con _cross-board compatibility_.
- Items físicos (DHT22/soil real, OLED I²C, acciones RGB reales desde
  automatización) permanecen **PENDING_HARDWARE_VALIDATION**.
- Persistencia de estado de alertas/automatizaciones entre reinicios ya existía
  en parte, pero acciones bloqueadas no se reintentan (seguimiento aparte).

## Estado

F08.1–F08.3 mergeados (PRs #101–#103). Este documento corresponde a **F08.4**.
