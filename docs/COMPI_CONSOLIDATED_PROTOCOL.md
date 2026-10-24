# Compi Aurora: contrato consolidado

Código Arduino activo: `firmware/esp32_rgb`. Android activo: `android/app`.
La auditoría de ramas está en `audits/COMPI_BRANCH_CONSOLIDATION_2026-10-23.md`.
El perfil compartido sigue siendo `esp32:esp32:esp32:PartitionScheme=min_spiffs`,
Arduino-ESP32 3.0.7, ESP32 clásico de 4 MB. No hay otra implementación UART activa.

## BLE y HTTP

Los siguientes frames usan las características existentes WRITE/STATE, cola
acotada y propietario autenticado. Los cambios también pasan por autorización
al ejecutarse; perder autorización vacía la cola. No se crean UUID alternativos.
`id` es 1–10 caracteres ASCII alfanuméricos, `_` o `-`. Cada respuesta coincide
con el ID pendiente; una escritura GATT no equivale a aplicación del comando.

| Familia | Comandos | Respuesta |
|---|---|---|
| `AUD,id,` | `GET`, `PLAY,n` (1–2999), `VOLUME,n` (0–30), `PAUSE`, `RESUME`, `STOP`, `VOICE,0/1` | `AUD,id,OK/ACCEPTED/REJECTED,module,sd,tracks,playback,track,volume,voice,fresh` |
| `OLD,id,` | `GET`, `FACE,nombre`, `TEXT,texto` (ASCII imprimible ≤39), `CONTRAST,n` (8–255), `INVERT,0/1`, `PAUSE,0/1` | `OLD,id,OK/REJECTED,ready,scene,contrast,inverted,paused` |
| `PIN,nonce,D/L,from,to` | CAS de un GPIO DHT11/LDR; nonce de 16 dígitos hexadecimales | `PIN,nonce,APPLIED/REPLAYED/BUSY/REJECTED` |

Caritas: `happy`, `sleepy`, `angry`, `surprised`, `pumpkin`, `cat`, `dance`.
El estado de OLED puede ser `telemetry` o `text` además de esas escenas.
Cambiar una carita no altera la vista confirmada hasta recibir respuesta.
Inversión y pausa actúan sobre la pantalla, no sobre flags locales de Compose.

HTTP autorizado usa el mismo backend que BLE:

- `GET /v2/audio/status`: `modulePresent`, `sdPresent`, `tracks`, `playback`,
  `track`, `volume`, `voiceEnabled`, `fresh`.
- `POST /v2/audio/play|volume|pause|resume|stop|voice`, conservando `track`,
  `volume`, `enabled` como argumentos de los endpoints correspondientes.
- `GET /v2/display/oled/status`: `ready`, `scene`, `contrast`, `inverted`, `paused`.
- `POST /v2/display/oled`: `mode=text|face|contrast|invert|pause`, `value`.

Los valores desconocidos son `-1` y `UNKNOWN`, no ceros inventados. La voz es
configuración del ESP32 persistida en NVS; arranca desactivada si no hay valor
guardado. Cada reasignación GPIO consulta primero el estado, usa compare-and-swap
y confirma después los pines activos. La reasignación sigue siendo temporal.

## Lo que realmente demuestra DFPlayer

El único controlador UART2 usa RX18/TX17 a 9600 baudios, valida checksum y
longitud, espacia envíos, limita la cola y consulta presencia/SD, estado, total
de archivos, índice SD y volumen. Cada campo caduca. Un envío UART aceptado
devuelve `ACCEPTED` (HTTP 202); solo las respuestas del módulo actualizan
`PLAYING/PAUSED/STOPPED`. Retirar la SD invalida la reproducción y sus índices.

`PLAY,n` direcciona `/MP3/NNNN.mp3` con el comando 0x12. El total SD (0x48) y
el índice SD (0x4c) no enumeran nombres de archivos; no se presenta una biblioteca
ficticia de 24 títulos. El audio importado del teléfono usa su reproductor local
y permisos de documentos; no se transmite al DFPlayer.

El protocolo UART DFPlayer no lleva nonce. Se rechazan consultas fuera de su
ventana pendiente, pero el hardware no permite identificar una respuesta que
llegue extremadamente tarde justo dentro de otra consulta del mismo opcode.
El estado UART tampoco comprueba acústicamente la bocina. Son límites del
hardware, no validaciones que pueda reemplazar una compilación.

## Validación física que completa la entrega

1. Instalar APK y firmware del mismo commit y comprobar hashes.
2. DHT11 en GPIO16: lecturas reales, desconexión, recuperación y carga simultánea.
3. LDR en ADC1 GPIO32: capturar oscuridad/claridad, calibrar, reiniciar y verificar
   recuperación; no confundir ADC crudo con porcentaje o lux.
4. RGB R27/G25/B26: cinco bandas 20/26/30/35 °C con histéresis, humedad y LDR;
   comprobar que control manual detiene ambiental.
5. OLED SDA21/SCL22: probar cada escena, contraste, invertir y pausar tanto por
   BLE como por Wi-Fi, y desconectar el panel sin bloquear la estación.
6. DFPlayer: SD ausente/presente, archivos reales `/MP3/0001.mp3` en adelante,
   play/pausa/seguir/parar/volumen/voz por ambos transportes y sonido audible.
7. Cortar cada conexión durante un comando; verificar que ninguna pantalla
   confirma resultados de la sesión anterior. Probar GPIO con lectura posterior.

Las pruebas automáticas y los artefactos verifican software. La comprobación
física sigue pendiente hasta registrar resultados en el circuito y teléfono.
