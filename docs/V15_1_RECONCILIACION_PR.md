# Compi V15.1 — Reconciliación de PRs #84 y #79

Fecha de referencia: tras el merge del PR #86 (component studio) en `main`
(`187731900d9889120b8f5acc236dc0ba7c91bbb4`).

## Objetivo

Determinar, sin fusionar ramas antiguas indiscriminadamente, qué trabajo de los
PR en borrador #84 y #79 sigue aportando valor sobre la arquitectura actual y
qué quedó obsoleto.

## Matriz de reconciliación

| Funcionalidad | En main actual | PR #84 `feat/compi-v15-modular-sensors-ui` | PR #79 `feat/compi-v9-reference-ui` | Acción |
| --- | --- | --- | --- | --- |
| Catálogo de componentes | `core/components/registry/ComponentCatalog.kt` | `core/modules/ModuleManifest.kt` (registro paralelo) | — | Descartar duplicado; usar `ComponentCatalog` |
| Pantalla de módulos | `feature/hardwarelab/HardwareLabScreen.kt` | `feature/modules/ModulesScreen.kt` (paralela) | — | Descartar duplicado |
| Telemetría de sensores | `core/components/model/ComponentTelemetry.kt` + endpoints `/v2/components/*` | `/v2/modules`, `/v2/modules/telemetry` | — | Descartar duplicado de firmware |
| Modelo de calibración de suelo | Ausente | `core/sensors/SoilCalibration.kt` | — | **Recuperar** |
| Sugerencias de Studio | Ausente | `core/modules/ProjectModuleHints.kt` | — | **Recuperar** (movido a `core/programs`) |
| Widgets reutilizables de métrica/gráfica | `ComponentRenderer` + widgets de componente | `core/ui/modules/SensorWidgets.kt` | — | Descartar (duplica widgets existentes) |
| Sistema visual V9 | `core/ui/CompiV9Surface.kt`, `RgbTheme.kt`, dashboard V9 | — | Idénticos a main | Ya integrado |
| Docs V9 | `docs/v9/*` presente | — | `docs/v9/*` | Ya en main |
| ADC raw GPIO34 | `/v2/adc/read` + `core/sensors/AdcTelemetry.kt` | Estaba en el PR | — | Ya presente en main |

## Decisión

- **PR #79** — CERRADO como *superseded*: `CompiV9Surface.kt`, `RgbTheme.kt`,
  el dashboard V9, el test de navegación y `docs/v9` ya están en `main`
  (verificado con `git diff origin/main origin/feat/compi-v9-reference-ui` sin
  diferencias en esos archivos).
- **PR #84**: sus piezas de registro/pantalla/firmware quedan superseded por el
  Component Studio de #86. Se recuperan únicamente dos utilidades no duplicadas:
  - `core/sensors/SoilCalibration.kt` (+ test)
  - `core/programs/ProjectModuleHints.kt` (+ test), conectado al editor.

No se introducen pantallas, registries ni sistemas de diseño duplicados.
