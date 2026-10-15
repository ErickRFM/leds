# F03 — Dashboard persistente de componentes (criterios inferidos)

Fecha: tras el merge de F02 (PR #89, `0cf286d84c5605d51c7593a566e000679d8b176f`).

## Objetivo

Persistir y presentar de forma reactiva el estado real de los componentes del
ESP32, manteniendo separados tres conceptos que nunca deben confundirse:

1. **`LocalDesiredConfig`** — lo que el usuario configuró localmente. Es un deseo.
2. `FirmwareReportedStatus`: lo que el firmware confirma ahora mismo.
3. `LastKnownStatus`: el último estado confirmado, persistido entre sesiones.

**Regla fundamental:** una configuración local nunca equivale a una configuración
aceptada por el ESP32. `gpioAuthority = firmware` sigue siendo obligatorio.

## Arquitectura

- `core/components/status/ComponentDashboardModels.kt`: modelo de dominio.
- `core/components/status/ComponentStatusPersistence.kt`: codecs versionados
  (`LastKnownStatusCodec`, `LocalDesiredConfigCodec`), reutilizan
  `ComponentStatusCodec.parseComponents/encodeComponents`.
- `core/components/status/ComponentStatusStorage.kt`: puerto de persistencia.
- `core/components/status/ComponentStatusRepository.kt`: única fuente de verdad
  (StateFlow + transiciones, Kotlin puro, testeable sin Android).
- `core/data/DataStoreComponentStatusStorage.kt`: adaptador DataStore.
- `feature/hardwarelab/ComponentDashboardViewModel.kt`: orquesta transporte
  (autorización, identidad de dispositivo, fetch), sin reglas propias que puedan
  divergir del repositorio.
- `feature/hardwarelab/HardwareLabScreen.kt`: dashboard persistente; reutiliza el
  sistema visual existente, sin pantallas nuevas.

### Room vs DataStore

Room **no** se usa: el modelo es una instantánea pequeña y acotada (por
dispositivo), sin consultas relacionales ni agregados. DataStore ya está presente
en el proyecto y cubre la necesidad sin añadir un motor de base de datos ni
migraciones complejas. Los esquemas llevan `version`; un esquema desconocido se
decodifica a un valor vacío seguro en lugar de fallar.

## Invariantes (probadas)

1. Un payload inválido, truncado o sin `gpioAuthority=firmware` **nunca**
   sobrescribe datos confirmados.
2. Perder el enlace **revoca** el estado autoritativo: nada se muestra como "en
   vivo" mientras está desconectado.
3. Un cambio de dispositivo **no** arrastra el estado autoritativo ni el
   last-known de otro dispositivo.
4. El estado persistido se etiqueta explícitamente como *último confirmado*, no
   como telemetría en vivo.
5. La configuración local se muestra separada y marcada como pendiente.
6. No se almacenan credenciales, claves de propietario ni UUID BLE en DataStore.

## Flujo

- `LaunchedEffect(Unit)` → `loadPersisted()` (restaura last-known + deseo local).
- Cambio de conexión → `onConnectionChanged()` → si autorizado, `refresh()`.
- `refresh()` obtiene `GET /v2/components/status` vía `LocalLabClient` y llama a
  `repository.ingest(raw)`.
- `ingest` decodifica con el decodificador estricto de F02; en éxito actualiza
  *live* + persiste *last-known*; en fallo registra error y conserva lo previo.

## Criterios de aceptación (inferidos)

- La app conserva el último estado válido entre reinicios.
- El dashboard reacciona a nuevas lecturas (StateFlow).
- Configuración local y confirmada están claramente diferenciadas.
- Los GPIO mostrados como actuales provienen del firmware.
- El modo desconectado nunca simula conexión ni aceptación.
- Sin regresiones en LED RGB, OLED, BLE, Wi-Fi o Hardware Lab.
- Pruebas automatizadas verdes.

## Estado

Diseño e implementación **INTEGRADOS** a nivel de código y pruebas. La lectura
real de GPIO/OLED/BLE sigue **PENDING_HARDWARE_VALIDATION** (no hay hardware
físico disponible en este entorno).
