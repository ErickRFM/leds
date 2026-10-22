# V4 · Identidad y factibilidad de vinculación sin PC

## Estado y alcance de esta primera entrega

Se introduce una **identidad pública, no secreta, persistida en NVS** con esquema V4 separado de las credenciales del laboratorio V3. No se elimina, reescribe ni migra el secreto SoftAP V3. La API `GET /v2/identity` está restringida al SoftAP con `X-Device-Key`, igual que los diagnósticos ya existentes.

Respuesta ejemplo sin secretos:
```json
{"schemaVersion":4,"deviceId":"1234ABCDDEADBEEF","secureProvisioning":false,"legacyBleWriteUnauthenticated":true,"ownerBound":false}
```

Los campos explícitos `false/true` impiden que la app confunda una identidad con un dispositivo autenticado. El identificador NO reemplaza un QR PoP, NO es contraseña ni autorización.

## Revisión de factibilidad WiFiProv oficial

- Arduino-ESP32 incluye `WiFiProv` y Espressif mantiene `esp-idf-provisioning-android`, compatible con transportes BLE/SoftAP.
- Security 2 requiere SRP6a/AES-GCM y credenciales únicas preparadas por dispositivo; **no** se debe reutilizar el `X-Device-Key` V3 ni un PoP fijo.
- El ejemplo WiFiProv puede reclamar BLE y liberar recursos BLE al finalizar. El firmware actual usa `BLEDevice` para RGB; no se debe llamar `WiFiProv.beginProvision` hasta demostrar en Arduino core **3.0.7** una secuencia compatible sin doble inicialización del controlador.
- Se debe medir tamaño binario real, heap libre, esquema 4 MB `min_spiffs`, conexión Android y recuperación USB. Security 0 o 1 sin PoP único no pasa el gate de producción.
- Se mantiene release Android deshabilitado. El firmware actual conserva escritura GATT no autenticada hasta un PR separado y verificado; este cambio NO es onboarding funcional.

Fuentes técnicas: https://github.com/espressif/arduino-esp32/tree/master/libraries/WiFiProv y https://github.com/espressif/esp-idf-provisioning-android .

## Contratos para siguientes PR V4

1. **Provisioning:** credencial por equipo con proceso físico de bootstrap; ventana limitada, reintentos acotados, secret nunca en serial normal, fingerprint de identidad comprobado.
2. **Ownership:** estados `UNPROVISIONED`, `PAIRING`, `OWNED`, `RECOVERY`; ninguna escritura RGB/GPIO antes de autorización.
3. **Android:** flujo Compose de búsqueda → prueba de posesión → configuración BLE/SoftAP/STA → verificación física → `READY`. Mostrar explícitamente versión legacy V3 como `SIN VINCULACIÓN SEGURA`.
4. **Red:** SoftAP seguro, modo STA protegido y sesión LAN autenticada; Wi-Fi sin Internet permitido; credenciales del router no salen de la sesión cifrada.
5. **Recuperación:** cambios de red sin perder propietario, reset físico diferenciado, sin activar automatizaciones inseguras por error.
6. **QA:** host, Kotlin, compilación, pruebas en placa y teléfono; cierre condicionado a evidencia real.

## Pruebas de la identidad

- Dos arranques sucesivos con NVS intacta → mismo `deviceId`.
- NVS no disponible/lectura o escritura fallida → no iniciar servicios de control.
- Formato exactamente 16 caracteres hexadecimales públicos; no derivado de MAC.
- Ninguna respuesta enumera secretos de vinculación, Wi-Fi o llaves V3.
- `GET /v2/identity` sin `X-Device-Key` o desde interfaz STA → 403.
- Pruebas de corrupción/recovery sin borrar namespace V3.
