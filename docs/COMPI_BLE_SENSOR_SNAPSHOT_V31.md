# V31 — Estado y lecturas DHT11/LDR por BLE autenticado

## Alcance real
- P0: lectura de `/v2/components/status` y `/v2/components/telemetry` por BLE, sin crear otro motor de sensores. El ESP32 toma instantáneas JSON desde **los mismos recolectores de Wi-Fi**.
- El nuevo transporte usa la característica `STATE` y `WRITE` GATT existentes. Solicitudes `SNP,S,<nonce>` y `SNP,T,<nonce>`; el nonce 1–65535 es elegido por Android y el ESP32 lo refleja.
- Respuestas: `B,<S|T>,<nonce>,<parts>`, `D,<S|T>,<nonce>,<index>,<json38>`, `E,<S|T>,<nonce>`. Fragmentos ASCII máx 38 caracteres; cada notificación máx 61 bytes (MTU >=64), documento máx 1536 B, máx 41 partes.
- No hay modificación de GPIO en callback BLE: la comprobación owner se hace en el loop mediante `CompiCommandArbiter`, después de demostrar `ownerSession.authenticated()`.
- Transmisión no bloqueante: un mensaje cada >=45 ms. Reconexión/revocación cancela el envío; el cliente exige documento íntegro, ordenado y del nonce solicitado.
- El ViewModel existente de componentes reutiliza `ComponentStatusRepository`, `ComponentStatusCodec`, `ComponentTelemetryCodec`, `TelemetryChart`, `EnvironmentalSpeechFormatter`. La UI no interpreta fragmentos como lecturas.
- El estado del propietario se verifica antes de solicitar/aceptar respuesta BLE; una conexión GATT cifrada sin AUTH no basta.
- Wi-Fi mantiene intactos sus endpoints y autorización previa. El modo de RGB ambiental V30 sigue disponible.
- Audio físico sigue bloqueado por no tener módulo reproductor/amplificador seguro para bocina 8 Ω / 0.5 W.
- Calibración LDR y reasignación GPIO **no forman parte del PR**: son escrituras sensibles que exigen el mismo CAS/idempotencia que por Wi-Fi.

## Riesgos y pruebas pendientes
- Pruebas automatizadas C++: framing exacto, límites, JSON reconstruido, nonce y política owner.
- JVM Kotlin: fragmentos ausentes/reordenados, respuestas antiguas, timeout, longitud y ACK completo.
- GitHub Actions: firmware ESP32 4MB/min_spiffs, JVM Android, CI de hardware simulado y emuladores API 30/35.
- QA físico **PENDING_HARDWARE_VALIDATION**: ESP32 y APK del mismo SHA, BOOT propietario, DHT11 GPIO16, LDR GPIO36, Wi-Fi SoftAP simultáneo, desconexión BLE entre fragmentos, reconexión y gráficas.
- Revisar la tasa de consultas sobre la placa (7.5 s) y la coexistencia con DHT11; si se observan GATT 133 o pérdidas, ajustar intervalos sin saltarse seguridad.
- Si un sensor no está configurado o su medición es inválida, el firmware reporta `catalog`/`invalid`/`not_calibrated`; la app no asigna un porcentaje o una temperatura inventada.

## Siguiente bloque
- V32: calibración LDR con comprobaciones físicas e idempotencia BLE.
- V33: GPIO remoto con CAS y readback firma/propietario; considerar ambos transportes.
- V34: reproductor Android con cola, audio físico opcional bajo límites eléctricos y QA de bocina.
