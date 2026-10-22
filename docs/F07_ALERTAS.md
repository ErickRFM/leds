# F07 · Bloque 3: alertas configurables

Tercer bloque de F07. Se apoya en el historial (bloque 1) y no introduce
transporte ni codec de telemetría nuevo.

## Qué se añadió

- `core/components/alerts/AlertEngine.kt` — máquina de estados pura:
  - `AlertRule` (umbral, comparación `ABOVE`/`BELOW`, histéresis, intervalo
    mínimo, severidad, habilitada).
  - `AlertState` (activa, `lastRaisedAtMs`, último valor) y `AlertEvent`
    (activación/`clear`).
  - `evaluate(...)`: nunca alerta con una lectura inválida, obsoleta o
    inexistente; usa **histéresis** para no oscilar en el umbral; **no repite**
    la activación mientras la regla está activa y respeta un **intervalo
    mínimo** entre activaciones.
  - `latestBySeries`: última muestra graficable por serie.
  - `DefaultAlertRules`: umbrales de partida (temp alta/crítica, humedad baja,
    suelo bajo) editables como cualquier `AlertRule`.
  - `AlertRulesCodec`: persistencia versionada `v1`.

- `ComponentStatusRepository`: evalúa las alertas en cada ingesta y expone
  `alertRules`, `alertStates` y `alertEvents`; `loadPersisted` siembra los
  umbrales por defecto una vez; nuevo `setAlertRules(...)` para configurarlos
  (persistidos en `component_alert_rules_v1`).

- `HardwareLabScreen`: tarjeta `component_alerts` con alertas activas
  (severidad, umbral, último valor) e historial reciente de activaciones.

## Invariantes

- Sin lectura válida no hay alerta: nunca se fabrica un disparo.
- No hay alertas repetidas mientras una regla permanece activa.
- La histéresis evita el parpadeo en el umbral; el intervalo mínimo limita la
  frecuencia tras una normalización.
- Los umbrales son locales y nunca se presentan como aceptados por el firmware.

## Pruebas

`AlertEngineTest` (8): activación, no-repetición, histéresis (permanece/cruza),
límite de frecuencia tras normalizar, sin alerta con datos inválidos/ausentes,
regla deshabilitada, evaluación en la ingesta del repositorio y siembra única de
umbrales por defecto.

## Pendiente

- Editor de umbrales en UI (se propone para F09, UX/UI).
- Motor de automatizaciones y acciones sobre hardware (bloque 4).
- Persistencia del estado de alerta entre reinicios (hoy se re-evalúa por sesión).
- Validación física (`PENDING_HARDWARE_VALIDATION`).
