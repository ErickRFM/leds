# F07 · Bloque 4: automatizaciones (avisos y acciones)

Cuarto bloque de F07. Se apoya en el historial (bloque 1) y en el motor de
alertas (bloque 3): no introduce transporte, codec de telemetría ni comandos
nuevos. Una automatización es una regla declarativa *condición → acción*.

## Qué se añadió

- `core/components/automation/AutomationEngine.kt`:
  - `AutomationAction`: `Notify` (aviso), `SetRgb` (acción de hardware) y
    `UpdateOled` (acción de hardware).
  - `AutomationRule`: reutiliza la condición del motor de alertas
    (`AlertEngine`), por lo que hereda histéresis, intervalo mínimo,
    no-repetición y la regla de *nunca disparar con una lectura inválida*.
    `isHardware` distingue avisos de acciones que tocan hardware.
  - `AutomationEngine.evaluate(...)`: un `Notify` se registra en línea
    (`NOTIFIED`); una acción de hardware queda `PENDING` sin ejecutarse.
  - `DefaultAutomationRules`: dos avisos habilitados por defecto (temperatura
    crítica, suelo seco). Ninguna acción de hardware se habilita sola.
  - `AutomationRulesCodec` v1 (nunca guarda credenciales).
  - `AutomationCoordinator.dispatch(...)`: ejecuta los eventos pendientes
    delegando en un `AutomationActionSink`; **nunca habla con el hardware** y
    deja la acción `BLOCKED` si el enlace no está autorizado o no hay sink.
- Persistencia `component_automation_rules_v1` (`ComponentStatusStorage`
  `loadAutomationRules`/`saveAutomationRules`, `DataStoreComponentStatusStorage`).
- Repositorio: `automationRules`/`automationStates`/`automationEvents`,
  `setAutomationRules`, siembra única de reglas por defecto en `loadPersisted`,
  evaluación en `ingestTelemetry` y `runAutomations(sink, authorized)`.
- ViewModel: `automationSink` enlazado al **mismo** canal ya autorizado
  (`LocalLabClient.setRgbConfirmed` con lectura de verificación). No se crea un
  segundo transporte ni se simula hardware.
- UI: `AutomationCard` (`testTag` `component_automations`) lista reglas
  (habilitada/deshabilitada, acción) y eventos recientes con su estado.

## Garantías

- Las acciones de hardware pasan por la autorización y verificación existentes;
  si no hay enlace autorizado quedan `BLOCKED`, nunca `DISPATCHED` en falso.
- Una lectura inválida o ausente no genera ni aviso ni acción.
- No hay repetición mientras la condición sigue activa.
- Los avisos y acciones son locales; no se presentan como aceptados por el
  firmware (`gpioAuthority = "firmware"`).

## Pruebas

`AutomationEngineTest` (12): aviso en línea, acción de hardware `PENDING`,
sin disparo con lectura inválida/ausente, no-repetición, regla deshabilitada,
codec round-trip, coordinador bloquea sin autorización, despacha vía sink,
marca fallo si el sink falla, y flujo de repositorio (ingesta + `runAutomations`
bloqueado/despachado) y siembra única de reglas por defecto.

## Pendiente

- Editor de automatizaciones/umbrales en UI (se propone para F09, UX/UI).
- Reintento de acciones `BLOCKED` al autorizarse el enlace (hoy no se reintenta).
- Acción BLE como sink (hoy el sink usa el cliente Wi-Fi autorizado).
- Persistencia del estado de automatización entre reinicios.
- Validación física de una acción real sobre hardware
  (`PENDING_HARDWARE_VALIDATION`).
