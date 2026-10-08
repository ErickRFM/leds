# Protocolo BLE RGB — borrador v1 (laboratorio)

## Identificación
- Nombre anunciado: `RGB-ESP32`
- Service UUID: `4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211`
- Command characteristic UUID: `4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211` (WRITE con respuesta)
- State characteristic UUID: `4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211` (READ + NOTIFY)
- Transporte: ASCII/UTF-8, mensaje máximo 48 bytes, sin terminador requerido.
- Todas las cifras decimales enteras.

## Comandos implementados en firmware inicial
| Entrada WRITE | Semántica |
| --- | --- |
| `SET,255,120,0,200` | R=255,G=120,B=0,brightness=200, encendido |
| `OFF` | Apaga salida conservando último color y brillo |
| `ON` | Recupera el último color y brillo |
| `GET` | Solicita republicar estado actual |

Validación: cuatro enteros en rango 0..255, formato exacto, rechazar texto adicional. Ejemplo: `SET,256,0,0,255` -> `ERR,INVALID_COMMAND`.

## Estado READ/NOTIFY
`STATE,r,g,b,brightness,power`, donde power es `0` o `1`.
Ejemplos:
- `STATE,255,120,0,200,1`
- `STATE,255,120,0,200,0`
Error NOTIFY: `ERR,INVALID_COMMAND`.
Cliente debe hacer READ al conectarse después de configurar notificaciones; leer el estado actual, no dar por confirmada una escritura al invocarla.

## V1.1 reservada: efectos
Propuesta no implementada todavía: `FX,RAINBOW,120`, `FX,BREATHE,150`, `STOP`.
Antes de añadirla, decidir formato final, rangos de velocidad y versión de protocolo. No enviar estos comandos al firmware v1.

## Consideraciones
- Tramas compactas de laboratorio, no un API web.
- La UI debe limitar envío continuo (~20/s) y usar cola GATT secuencial; lectura tras reconexión.
- `PROPERTY_WRITE` no implica seguridad por sí sola: se agregará bonding/autorización.
- Característica de estado mide el último comando aceptado, no corriente eléctrica real del LED.
- La app no debe descubrir por nombre solamente: preferir UUID de servicio.
