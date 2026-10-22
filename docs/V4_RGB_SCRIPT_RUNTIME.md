# V4 Incremento: ejecución temporal RGB Script sin reflashear

## Contrato de laboratorio (no es Arduino C++)
`RgbScriptRuntime` interpreta exclusivamente instrucciones declarativas:
```text
RGB 255 0 0
WAIT 500
RGB 0 0 255 120
WAIT 500
OFF
STOP
```
- Comandos: `RGB r g b [brillo]` (0–255), `ON`, `OFF`, `WAIT n` (100–2000 ms) y `STOP`; comentario de línea con `#`.
- Máximo 512 bytes, 32 instrucciones y 20 segundos de esperas; una ejecución a la vez; sin archivos, flash, bucles ilimitados, hilos, GPIO sin permiso, red ni código C++ arbitrario.
- Ejecución en `loop()` mediante `tick` no bloqueante; RGB usa el comando y parser ya existente, con pines 27/25/26 reservados por `PinManager`.
- Al finalizar o detener se conserva el último color. No se apaga BLE ni Wi-Fi ni se reinicia la placa. Una orden RGB manual por Wi-Fi/BLE detiene el script (GET BLE no lo interrumpe).
- No hay instalación persistente, autonarranque, BLE provisioning ni OTA en este incremento.

## API HTTP existente en SoftAP de laboratorio debug
- `POST /v2/script/run` con `source` form-urlencoded, respuesta 202, `X-Device-Key` y comprobación de interfaz SoftAP.
- `GET /v2/script/status` campos `active`, `step`, `total`, autenticada.
- `POST /v2/script/stop`, cancela y conserva último estado.
- Rechazar concurrencia GPIO experiment/script (409), scripts inválidos (400), cualquier intento por otra interfaz (403).
- El tráfico sigue siendo HTTP sobre AP WPA2 de laboratorio, NO cifrado de aplicación, NO apto para exposición pública; no habilitar release.

## Reglas de verificación
- Test C++ host: parser estricto, límites, comandos, clock wrap, cancelación, expiración y control manual.
- Compilación ESP32 3.0.7 con perfil de 4 MB, medir binario vs partición.
- Android cliente y UI deben incorporarse en un PR posterior, sobre el módulo central y el editor de #50 (evitar sobrescrituras).
- QA físico pendiente: comandos reales, secuencia con BLE concurrente, Wi-Fi sin Internet, interrupción, RAM libre, cortes de conexión y alimentación, persistencia RGB anterior.
- El APK actual no ejecuta estos scripts hasta integrar el cliente; no confundir CI verde con la prueba real.
