# F07 · Telemetría, alertas y automatizaciones — Bloque 1: repositorio e historial

Primer bloque de F07. Reutiliza los contratos de F04/F05/F06 sin duplicar nada:
el mismo `ComponentTelemetryCodec` (DHT22/suelo) y el mismo
`ComponentStatusRepository`/`ComponentStatusStorage` del dashboard persistente.

## Qué se añadió

- `core/components/telemetry/TelemetryHistory.kt`
  - `TelemetrySample`: una medición con identidad completa — dispositivo,
    componente de catálogo, tipo de medición, valor, unidad, estado, validez,
    `sourceTimestampMs` (reloj del firmware), `recordedAtMs` (reloj de la app) y
    `provenance` (`FIRMWARE` / `LAST_KNOWN`).
  - `isPlottable`: solo una medición validada por el firmware con valor finito
    puede alimentar una gráfica; una lectura inválida queda con `value = null`.
  - `TelemetryHistory`: álgebra pura de historial. `append` recorta por serie
    (`maxSamplesPerSeries`), caduca por antigüedad (`retentionMs`), limita el
    número de series (`maxSeries`) y el total (`maxTotalSamples`).
  - `samplesForDevice`: aislamiento estricto por dispositivo.
  - `TelemetryHistoryCodec`: codec versionado (`v1`) para persistir el historial;
    nunca escribe credenciales, claves de propietario ni UUID BLE.

- `ComponentStatusRepository`: acumula historial al ingerir telemetría
  (`ingestTelemetry`) y lo restaura en `loadPersisted`. Si no hubiera historial
  previo pero sí un `LastKnownTelemetry`, siembra una muestra `LAST_KNOWN` por
  lectura para que una gráfica nunca aparezca vacía sin explicación.

- `ComponentDashboardState`: expone `deviceHistory` (solo el dispositivo actual)
  y `series(componentId, measurementType)`.

- `ComponentStatusStorage` + `DataStoreComponentStatusStorage`: nueva clave
  versionada `component_telemetry_history_v1`.

- `HardwareLabScreen`: tarjeta `component_telemetry_history` con el recuento de
  muestras por serie, válidas, último valor, unidad, estado y hora.

## Invariantes

- Una lectura inválida/ausente nunca se convierte en un número: `value = null`,
  `isPlottable = false`.
- El historial de otro dispositivo nunca se expone como del actual.
- El almacenamiento está acotado por conteo, series y retención temporal.

## Pruebas

`TelemetryHistoryTest` (8):
recorte por serie, caducidad, invalidez no graficable, aislamiento de
dispositivo, round-trip y rechazo de versión del codec, ingesta con procedencia
`FIRMWARE` + persistencia, siembra `LAST_KNOWN` y no-resiembra si ya hay historial.

## Pendiente (siguientes bloques de F07)

- Gráficas (bloque 2), alertas con histéresis (bloque 3), motor de
  automatizaciones con autorización/arbitraje existentes (bloque 4), plantillas
  deshabilitadas intercambiables y calibración persistente de suelo (bloque 5).
- Validación física: sin hardware conectado no se afirma ninguna lectura real.
