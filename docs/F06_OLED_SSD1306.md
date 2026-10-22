# F06 — OLED SSD1306 integrado al dashboard (solo software)

Fase V15.1 posterior a F05 (PR #92, `a0c36291ae955375121d4b318f995696a5017a8b`).

## Criterios de aceptación (inferidos)

Los criterios verbatim del plan V15.1 no estaban en el repositorio; se infirieron del
issue #85 (bloque "OLED"), del módulo `OledSsd1306Module.h` y del patrón F02–F05.

- **Reutilización sin duplicar**: se auditan y reutilizan `OledSsd1306Module.h`,
  `ComponentCatalog`, `ComponentRenderer`, `OledDisplayWidget`, `HardwareLabScreen` y
  los contratos de telemetría/estado existentes.
- **Detección por identificador del firmware** `display.ssd1306`, con los tres estados
  separados: **detectado** (el firmware lista el componente), **configurado** (el módulo
  tomó SDA/SCL y ejecutó el constructor, `active`) y **operativa** (driver I²C
  inicializado y `state == "active"`).
- **Tarjeta reutilizable** en Hardware Lab con estado de comunicación I²C y mensajes
  claros para módulo deshabilitado/ausente/no inicializado. Sin datos inventados.
- **I²C**: SDA GPIO21, SCL GPIO22, dirección 0x3C por defecto, sujetos al `PinManager`
  y a la validación de conflictos GPIO del firmware.
- **Dependencia Adafruit SSD1306 opcional** (`__has_include`); si falta, el módulo no se
  instancia y se informa la limitación.
- **`COMPI_ENABLE_OLED=0`** por defecto: no se flashea ni sobrescribe la placa.

## Diseño

- `OledRuntime` / `OledRuntimeState` (nuevo): mapea `ComponentRuntimeStatus` de
  `display.ssd1306` a `detected`/`configured`/`operative` + pines I²C. El contrato de
  estado no incluye texto ni confirmación de `present`, así que no se inventa contenido
  ni un estado "mostrando".
- `OledDisplayWidget` (extendido, retrocompatible con `ComponentRenderer`): muestra los
  tres estados, pines I²C, dirección 0x3C declarada, `live`/última consulta y una
  **vista previa** que solo pinta texto publicado por el firmware; sin texto muestra
  "sin contenido publicado (no se simula)".
- `HardwareLabScreen`: sección "Pantalla OLED (display)" con `OledStatusCard`
  (testTag `component_oled_status`), separada del estado de sensores.
- El mapeo de ids `display.ssd1306` → `ssd1306_oled` en `ComponentCatalog` ya se
  incorporó en F05 y evita que un módulo habilitado aparezca como plantilla no detectada.

## Limitaciones reportadas

- El endpoint `/v2/components/status` no publica el texto de la pantalla ni un flag de
  confirmación de `present`, por lo que F06 no puede afirmar "contenido visible": solo
  "driver I²C inicializado". Publicar contenido requeriría una extensión de contrato y
  validación física.
- Sin la librería Adafruit SSD1306 o con `COMPI_ENABLE_OLED=0`, el firmware no reporta
  `display.ssd1306`; la tarjeta lo muestra como "no detectado". La inclusión en el
  catálogo no implica hardware funcional.

## Firma de verificación

- C++ (`firmware-core-tests`): `component_modules_test.cpp` (OLED + nuevo caso de
  conflicto GPIO: SDA21 ocupado por `led.rgb` bloquea el OLED sin robar el pin).
- Kotlin: `OledRuntimeTest` (ausente/activo/error/lease-con-error, dirección y mapeo);
  regresiones de catálogo/telemetría DHT22 y suelo ya cubiertas.
- `check.ps1`, `lintDebug`, `assembleDebug`, `assembleDebugAndroidTest`, `testDebugUnitTest`.
- Firmware sin cambios en esta fase: presupuesto de flash intacto.
