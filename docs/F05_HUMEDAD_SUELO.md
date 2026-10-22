# F05 — Humedad de suelo (ADC1) real en el dashboard persistente

Fase V15.1 posterior a F04 (PR #91, `18a3f11dbca78864de9a781b5ba9f19c44ff9dae`).

## Criterios de aceptación (inferidos)

Los criterios verbatim del plan V15.1 no estaban disponibles en el repositorio; se
infirieron de `docs/ESTADO_V3.md`, del issue #85 ("persistencia, DHT22, OLED y
combinaciones", que lista la humedad de suelo como bloque separado) y del patrón
de F02–F04. El usuario aprobó el alcance F05 "solamente humedad de suelo (ADC1)".

- **Driver real y no bloqueante en tick**: lectura ADC1 en GPIO configurable (por
  defecto 34) bajo `COMPI_ENABLE_SOIL`, con muestreo por intervalo y filtro EMA.
  Ya entregado en el piloto #84 (`modules/SoilMoistureModule.h`, `esp32_rgb.ino`)
  y cubierto por `tests/component_modules_test.cpp` en CI.
- **Lecturas reales, nunca fabricadas**: el firmware publica `adc_raw` (crudo) y
  `soil_moisture` (porcentaje) vía `GET /v2/components/telemetry`
  (`SensorTelemetryCodec`). Si el sensor no está calibrado, `soil_moisture`
  viaja como `not_calibrated` (`valid=false`) y el cliente **muestra solo el crudo**,
  sin inventar un porcentaje.
- **Integración en el dashboard persistente**: la telemetría de suelo se muestra
  con su estado (fresco/sin calibrar/error), timestamp y marca de "en vivo" vs
  "último confirmado, no en vivo", separada del estado de GPIO del firmware.
- **Widget reutilizable**: se reutiliza `SoilMoistureWidget`, extendido con
  `hasData`/`lastUpdate`/`live`/`status` (mismo patrón que el widget DHT22).
- **Plantillas veraces**: los módulos del catálogo no confirmados por el firmware
  se muestran como plantillas deshabilitadas. Se corrige el mapeo de ids
  firmware→catálogo (`sensor.soil_moisture` → `soil_moisture`, `led.rgb` →
  `led_rgb`, …) para que un módulo habilitado deje de aparecer como "no detectado".
- **Sin dependencias/pines sin perfil**: `COMPI_ENABLE_SOIL` sigue en 0; habilitar
  el sensor en un tablero real es un paso de hardware (PENDING_HARDWARE_VALIDATION).

## Diseño

- No se duplica telemetría: se reutilizan `ComponentMeasurement`,
  `FirmwareReportedTelemetry`/`LastKnownTelemetry` y `telemetryFreshness` de F04.
  La única adición de modelo es la función de mapeo de ids en `ComponentCatalog`.
- `SoilTelemetryCard` (en `HardwareLabScreen`) lee `soil_moisture` + `adc_raw` del
  stream de telemetría (`authoritativeTelemetry ?: persistedTelemetry`) y:
  - solo muestra porcentaje cuando el firmware reportó `status=fresh` y `valid=true`;
  - si no está calibrado, muestra el ADC crudo y la advertencia de no calibrado;
  - sin lecturas, muestra un estado "sin datos" explícito.
- `SoilMoistureWidget` conserva su firma previa (compatibilidad con
  `ComponentRenderer`) y añade parámetros opcionales.

## No objetivos (diferidos)

- OLED SSD1306: fase posterior.
- Calibración en teléfono (escribir `dryRaw`/`wetRaw` al firmware): la fase es de
  lectura; `SoilCalibration` permanece como utilidad local de cálculo sin cablear
  al transporte de escritura.
- Habilitar pines por defecto o instalar dependencias de sensores: requiere perfil
  de placa y pruebas físicas.

## Firma de verificación

- `tests/component_modules_test.cpp` (driver de suelo + codec) en `firmware-core-tests`.
- Tests Android: `ComponentTelemetryCodecTest` (mapeo de ids, crudo activo sin
  calibrar), `ComponentDashboardViewModelTest` (ingesta de suelo calibrado y
  no-calibrado: crudo sí, porcentaje no).
- `check.ps1` (contratos), `lintDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`.
- Firmware sin cambios en esta fase: presupuesto de flash intacto.
