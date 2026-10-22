# F07 · Auditoría final (bloques 1–6)

Cierre de F07 (telemetría, alertas y automatizaciones). Resume qué quedó
integrado, cómo se probó y qué defectos se corrigieron en la auditoría.

## Bloques integrados

| Bloque | PR | Contenido |
|---|---|---|
| F07.1 | #95 | Repositorio de telemetría unificado + historial persistente acotado (`TelemetryHistory`, `TelemetryHistoryCodec`). |
| F07.2 | #96 | Gráficas con estados válido/inválido/obsoleto/desconectado (`TelemetryChart`, `TelemetryChartCard`). |
| F07.3 | #97 | Alertas configurables con histéresis y límite de frecuencia (`AlertEngine`). |
| F07.4 | #98 | Automatizaciones declarativas por la ruta de comando autorizada (`AutomationEngine`, `AutomationCoordinator`). |
| F07.5 | #99 | Plantillas deshabilitadas + calibración de suelo persistente ligada a dispositivo/módulo/versión. |
| F07.6 | (este) | Pruebas de integración, correcciones de aislamiento y esta auditoría. |

## Puntos 7–12 del plan

- **7. Componentes intercambiables / plantillas deshabilitadas:** el dashboard
  sólo muestra como presentes los ids confirmados por el firmware; el resto son
  `DisabledTemplateCard` sin acción y nunca hardware conectado.
- **8. Calibración de suelo persistente:** `StoredSoilCalibration` +
  `component_soil_calibration_v1`, invalidación por dispositivo/módulo/versión.
- **9. Pruebas C++ y Kotlin:** `firmware-core-tests` (host C++) permanece verde
  sin cambios de firmware; en Kotlin, 181 pruebas en 37 clases, incluidas
  `TelemetryHistoryTest`, `TelemetryChartTest`, `AlertEngineTest`,
  `AutomationEngineTest`, `SoilCalibrationPersistenceTest` y `F07IntegrationTest`
  (valores inválidos, timestamps, datos obsoletos, alertas, automatizaciones,
  concurrencia, persistencia y aislamiento por dispositivo).
- **9–12. Consistencia y no duplicación:** un único motor de condición
  (`AlertEngine`, reutilizado por `AutomationEngine`), un solo historial, un solo
  transporte (no se añadió transporte ni UUID BLE), un prefijo de claves por
  dominio y todos los codecs en versión 1.

## Defectos encontrados y corregidos en la auditoría

1. **Fuga de estado entre dispositivos.** `setDevice` limpiaba el estado
  autoritativo y la telemetría, pero **no** `alertStates`/`alertEvents` ni
  `automationStates`/`automationEvents`. Al cambiar de dispositivo, las alertas y
  automatizaciones del dispositivo anterior seguían visibles. Se corrigen
  limpiándolos en `setDevice`. Cubierto por
  `F07IntegrationTest.switchingDeviceClearsCrossDeviceAlertAndAutomationState`.
- **Valor de una lectura inválida:** `TelemetryHistory.samplesFromMeasurements`
  conservaba el número crudo de una lectura inválida. Se anula el valor
  (`value = null`) cuando la lectura no es válida o no es finita, para que
  ningún consumidor interprete un 0 inválido como medición real. Cubierto por
  `F07IntegrationTest.invalidReadingEntersHistoryAsUnplottableAndNeverAlerts`.

## Garantías verificadas

- Una lectura inválida/obsoleta/ausente nunca genera alerta, acción ni punto
  graficable.
- Los datos de un dispositivo nunca se muestran para otro (estado, lecturas,
  historial, alertas, automatizaciones y calibración).
- Las acciones de hardware pasan por la autorización y verificación existentes;
  sin enlace autorizado quedan `BLOCKED`.
- El historial está acotado por serie, por número de series y por retención.
- El `gpioAuthority` sigue siendo `firmware`; no se añadieron UUID BLE ni un
  transporte nuevo.

## Pruebas y comprobaciones (local)

- `testDebugUnitTest`: 181 pruebas, 37 clases, todas en verde.
- `lintDebug`, `assembleDebugAndroidTest`, `assembleDebug`: en verde.
- `check-contracts.py` (vía `check.ps1`): 0 fallos.
- `firmware-core-tests` (C++ host): en verde en CI (sin cambios de firmware).

## Pendiente (no bloquea F07)

- Editor de umbrales/automatizaciones y de calibración en UI (F09, UX/UI).
- Persistencia del estado de alerta/automatización entre reinicios (hoy se
  re-evalúa por sesión) y reintento de acciones `BLOCKED`.
- Sink BLE para automatizaciones (hoy el sink usa el cliente Wi-Fi autorizado).
- **Validación física** de DHT22, sonda de suelo, OLED I²C y una acción real de
  RGB: `PENDING_HARDWARE_VALIDATION`.
