# F04 — Telemetría DHT22 real en el dashboard persistente

Fase V15.1 posterior a F03 (PR #90, `2b329c13a840ee3273e95c9971b842c24a526175`).

## Criterios de aceptación (inferidos)

Los criterios verbatim del plan V15.1 no estaban disponibles en el repositorio;
se infirieron de `docs/ESTADO_V3.md`, del issue #85 ("persistencia, DHT22, OLED y
combinaciones") y del patrón de F02/F03. El usuario aprobó el alcance F04
"solamente DHT22".

- **Driver DHT22 real y no bloqueante en tick** (GPIO aprobado, por defecto 32,
  bajo `COMPI_ENABLE_DHT22`): temperatura y humedad con calidad/errores/timestamp.
  Ya entregado en el piloto #84 (`modules/Dht22Module.h`, `esp32_rgb.ino`) y
  cubierto por `tests/component_modules_test.cpp` en CI.
- **Lecturas reales, nunca fabricadas**: el firmware publica solo lo que leyó
  (`GET /v2/components/telemetry`, `SensorTelemetryCodec`); ausente/error se
  reporta como `Absent`/`Stale`/`SensorError` con `valid=false`. Se conserva el
  principio de que el cliente nunca "asciende" una lectura inválida a dato real.
- **Integración en el dashboard persistente**: la telemetría DHT22 se muestra
  con su estado (fresco/nostálgico/error), timestamp y marca de "en vivo" vs
  "último confirmado, no en vivo", separada del estado de GPIO del firmware.
- **Widget reutilizable**: se reutiliza `HumidityTemperatureWidget` extendido con
  estado/calidad, sin recrear UI por dispositivo.
- **Plantillas deshabilitadas**: los módulos del catálogo no confirmados por el
  firmware se muestran como plantillas deshabilitadas (no como "activos").
- **Sin dependencias/pines sin perfil**: `COMPI_ENABLE_DHT22` sigue en 0; habilitar
  el sensor en un tablero real es un paso de hardware (PENDING_HARDWARE_VALIDATION).

## Diseño

Se mantiene la separación de F03 y se añade telemetría con la misma disciplina:

- `LocalDesiredConfig`: deseo local (nunca aceptación).
- `FirmwareReportedStatus` / `FirmwareReportedTelemetry`: confirmado ahora.
- `LastKnownStatus` / `LastKnownTelemetry`: último confirmado persistido.

`ComponentDashboardState` expone getters filtrados por dispositivo
(`authoritativeTelemetry` / `persistedTelemetry`) y `telemetryFreshness`
(`LIVE` solo con telemetría del dispositivo actual; `LAST_KNOWN` si proviene de
persistencia; `NONE` si no hay nada). La telemetría en vivo se descarta al
desconectar o cambiar de dispositivo, igual que el estado autoritativo.

La persistencia usa DataStore (`component_telemetry_v1`) con un codec versionado
que reutiliza `ComponentTelemetryCodec.encode`/`parseMeasurements`. No se guardan
credenciales, claves ni UUID BLE.

## No objetivos (diferidos)

- OLED SSD1306 y humedad de suelo: fases posteriores.
- Habilitar pines por defecto o instalar dependencias de sensores: requiere perfil
  de placa y pruebas físicas.

## Firma de verificación

- `tests/component_modules_test.cpp` (drivers + codec) en `firmware-core-tests`.
- Tests Android: codec (round-trip), persistencia, repositorio y ViewModel de la
  telemetría; sin lecturas fabricadas cuando falta telemetría o está obsoleta.
- `check.ps1` (contratos), `lintDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`.
- Firmware sin cambios en esta fase: presupuesto de flash intacto.
