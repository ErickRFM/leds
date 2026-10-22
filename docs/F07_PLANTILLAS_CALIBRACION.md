# F07 · Bloque 5: plantillas deshabilitadas y calibración persistente

Quinto bloque de F07. Cubre los puntos 7 y 8 del plan: no presentar módulos no
detectados como hardware conectado, y conservar/completar la calibración de
suelo ligada a dispositivo + módulo + versión, invalidando lo incompatible.

## Plantillas deshabilitadas (punto 7)

Ya soportado desde F03 y reforzado en la auditoría F06; este bloque lo fija
como garantía:

- El catálogo (`ComponentCatalog`) describe módulos posibles, pero el dashboard
  sólo marca como presentes los ids **confirmados por el firmware**
  (`confirmedCatalogIds`, que traduce ids de firmware como `sensor.soil_moisture`
  a `soil_moisture`).
- Todo módulo del catálogo no confirmado se muestra como
  `DisabledTemplateCard` (`testTag` `template_<id>`): es un `Card` **sin acción
  táctil**, rotulado "Plantilla · no detectado por el firmware". Nunca aparece
  como hardware conectado ni activo.
- La configuración local deseada se lista aparte (`DesiredComponentCard`) e
  indica "coincide con el firmware" o "sin confirmar por el firmware"; nunca se
  fusiona con el estado confirmado.

## Calibración de suelo persistente (punto 8)

- `core/components/calibration/SoilCalibrationStore.kt`:
  - `StoredSoilCalibration(deviceId, componentId="soil_moisture", version=1,
    dryRaw, wetRaw)`. Valida el par seco/húmedo reutilizando `SoilCalibration`
    (rango ADC 0..4095 y separación mínima de 100), de modo que un par inválido
    no puede persistirse ni decodificarse.
  - `appliesTo(deviceId, componentId)`: cierto sólo para **el mismo dispositivo
    + el mismo módulo + una versión soportada**.
  - `SoilCalibrationCodec` v1 (nunca guarda credenciales).
- Persistencia `component_soil_calibration_v1` (`ComponentStatusStorage`
  `loadSoilCalibration`/`saveSoilCalibration`, `DataStoreComponentStatusStorage`,
  `null` borra la clave).
- Repositorio: `setSoilCalibration(dryRaw, wetRaw)` sólo con dispositivo actual
  (si no hay dispositivo o el par es inválido, devuelve `false` y no guarda),
  `clearSoilCalibration()`, y `loadPersisted` restaura lo almacenado.
- Estado: `soilCalibration` (crudo persistido), `activeSoilCalibration` (sólo si
  `appliesTo` el dispositivo actual) y `soilCalibrationIncompatible`. Al cambiar
  de dispositivo, la calibración guardada **no se borra** pero deja de aplicarse
  (se ignora), evitando convertir un ADC crudo en porcentaje para otra sonda.
- UI: `SoilCalibrationCard` (`testTag` `component_soil_calibration`) muestra la
  calibración activa, avisa cuando la guardada es de otro dispositivo y permite
  descartarla (`component_soil_calibration_clear`).

## Pruebas

`SoilCalibrationPersistenceTest` (9): round-trip del codec, rechazo de versión
desconocida/dispositivo vacío/JSON inválido, `appliesTo` por dispositivo y
módulo, ligadura al dispositivo actual, invalidación al cambiar de dispositivo
(sin borrado), rechazo de par inválido y sin dispositivo, restauración en
`loadPersisted`, y borrado explícito. Se mantiene `SoilCalibrationTest`.

## Pendiente

- Editor de calibración en UI (capturar seco/húmedo desde lecturas reales):
  se propone para F09 (UX/UI) con `setSoilCalibration` ya disponible.
- Validación física sobre una sonda real (`PENDING_HARDWARE_VALIDATION`).
