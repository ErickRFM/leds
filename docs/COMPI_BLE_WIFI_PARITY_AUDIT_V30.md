# Compi V30 — Auditoría y primera paridad Bluetooth/Wi-Fi

Base auditada: `main@a61b878cd4ba753c4fbdf864d37e79aac0d9c0cc`.

## Capacidades auditadas

| Función | Bluetooth previo | Wi-Fi | Estado tras V30 |
|---|---|---|---|
| RGB manual / FX / estado | Autorizado vía OWNER + GATT | HTTP autorizado | Sin cambios |
| GPIO | Snapshot parcial read-only GATT | GPIO completo autorizado | Sin cambios |
| DHT11/DHT22/LDR | No (no existe característica o protocolo) | `/v2/components/telemetry` | **Pendiente**: fragmentación, timestamps, confirmación y auth |
| Estado y catálogo de componentes | No | `/v2/components/status` y `/catalog` | Pendiente |
| LDR calibración | No | `/v2/components/ldr/calibrate` | Pendiente; necesita ACK inequívoco |
| Aplicar GPIO | No | `/v2/components/apply` con CAS/receipt | Pendiente; evitar duplicar algoritmo CAS en GATT |
| RGB ambiental GET/START/STOP | No | `/v2/environment/rgb/*` | **V30 implementa paridad de control BLE** |
| Voz ambiental Android | Solo sobre mediciones autorizadas y vigentes | Funciona si existe telemetría HTTP | BLE sigue pendiente de telemetría |
| Audio físico DFPlayer/8Ω 0.5W | No | No | No existe driver ni etapa eléctrica validada |

### Hallazgos

1. `CommandCallbacks::onWrite` validaba únicamente RGB o frames de enrolamiento. Cualquier `ENV,*` válido era rechazado antes de llegar al loop.
2. `BleManager.writeCommand` rechazaba los comandos ambientales vía `RgbDeviceCommand.allowed`; no existía estado BLE ambiental ni respuesta de confirmación.
3. El drawer de módulos decía correctamente "sensores por Wi-Fi". No debe cambiar ese mensaje hasta desplegar telemetría BLE real.
4. `LocalLabClient.request` exige `FLAG_DEBUGGABLE` para HTTP; esto limita el uso del laboratorio Wi-Fi en APK release. Auditar política de producto antes de levantar esa restricción, **no** quitarla sin seguridad y tests.
5. El cifrado/bonding BLE **no** autoriza comandos. La sesión `ownerSession.authenticated()` es la única autoridad y cada mensaje debe validarse sobre la misma conexión.
6. Respuesta `ENV,ON` o `ENV,OFF` sólo informa estado aceptado por firmware, no demuestra cambios PWM físicos; validación física sigue pendiente.

## V30 implementado

- Usa la **misma** característica GATT WRITE y notificación STATE, sin UUIDs adicionales.
- Frames de control exactos: `ENV,GET`, `ENV,START`, `ENV,STOP`. Respuestas exactas: `ENV,ON`, `ENV,OFF`, `ENV,BUSY`.
- La callback BLE encola, **nunca escribe GPIO** ni decide autorización.
- El loop exige dueño autorizado mediante `CompiCommandArbiter` y llama al mismo `LocalWifiControl::startEnvironmentalRgb()` que HTTP.
- Compi Android envía mensajes por el mismo canal GATT serializado y refleja únicamente respuestas reales de firmware. Al desconectar borra el readback.
- Comportamiento de firmware antiguo: no se confirma la automatización BLE; la UI no debe marcarla como encendida. Instalar firmware+APK del mismo commit.
- No guarda el modo temporal en NVS y no altera el RGB ni la política ambiental existentes.

## Bloques de trabajo siguientes para paridad real

**BLE-S1 — Telemetría autorizada:** crear codec binario o enmarcado compacto versionado para DHT11/LDR con secuencia, longitud y comprobación de integridad; fragmentar por MTU y reensamblar con límites estrictos, expiración y validación de propietario; no transmitir solo lecturas del sensor como éxito de comandos. Implementar tests de transporte, fallos de MTU, desconexión, pérdida/reordenación y sensor stale. Adaptar `ComponentDashboardViewModel` como fuente de datos real para BLE autorizado.

**BLE-S2 — Catálogo y estados:** sincronización confiable de IDs de módulos activos y asignaciones GPIO, no sólo snapshot genérico; UI expresa capacidades por transporte y versión de firmware. No afirmar presencia física por catálogo.

**BLE-S3 — Escrituras y LDR:** reutilizar control de conflictos y CAS de `/v2/components/apply` y confirmación de calibración; request IDs, idempotencia y ACK seguro. No habilitar GPIO remotos sin el mismo nivel de autorización.

**BLE-S4 — Audio/reproductor:** motor Android para biblioteca/pausa/cola e interfaces hacia DFPlayer sólo cuando exista circuito alimentado, amplificado y limitado para bocina pasiva 8Ω/0.5W; comandos firmes sin bypass propietario.

**BLE-S5 — QA físico:** comprobar SoftAP simultáneo, reconexión, prueba de propietario, ESP32 4MB/min_spiffs, DHT11 `bit_timeout`, LDR GPIO36 y RGB GPIO27/25/26, BLE MTU 64/128, Android API30/35, wifi/ble handoff y pruebas de ataque.

## Validación V30

- CI: C++ host + Android JVM + firmware ESP32 + Android emulador.
- Sólo con CI completo verde, merge de PR y despliegue de APK/firmware del mismo SHA.
- Test real: pairing por BOOT USB, seleccionar Bluetooth, activar RGB ambiental y observar `ENV,ON`, verificar LED sólo con temperatura válida; STOP `ENV,OFF`; GET; manual override; sesión revocada, código incorrecto, enlace caído.
- Sin prueba física: `PENDING_HARDWARE_VALIDATION`.
