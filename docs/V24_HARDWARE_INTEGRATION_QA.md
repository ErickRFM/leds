# Compi V24 — validación integrada en hardware real

Esta guía registra evidencia **pendiente**. CI, emuladores y pruebas simuladas no equivalen a certificar GPIO, audio o pantalla físicos.

## Requisitos
- Firmware y APK construidos desde el **mismo SHA** de main; anotar SHA, fecha, versión de ESP32 core y partición.
- ESP32-D0WD-V3, 4 MB flash, Arduino ESP32 3.0.7 y min_spiffs.
- RGB R27/G25/B26; confirmar físicamente **ánodo común** antes de flashear perfil actual.
- DHT11 DATA GPIO16; LDR conectado al pin configurado en firmware; SSD1306 SDA21/SCL22; DFPlayer ESP32 RX13/TX14, 5V y GND común.
- microSD 16/32 GB FAT32 con pista `/MP3/0001.mp3`; comprobar UART y respuesta de conteo antes de reproducir. No conectar SPK1/SPK2 directamente a GPIO del ESP32.

## Matriz de pruebas (cada fila: PASS/FAIL/NOT_TESTED + captura/log)
| ID | Escenario | Resultado comprobable | Estado inicial |
|---|---|---|---|
| RGB-01 | DHT11 a ~23 °C, modo Ambiental | Banda Confort; ESP32 reporta RGB 54/186/116, LED físicamente verde | NOT_TESTED |
| RGB-02 | Temperaturas 19.9, 20.1, 25.9, 26.1 °C | Transiciones e histéresis 0.5 °C sin parpadeos | NOT_TESTED |
| RGB-03 | Cubrir LDR y exponerlo a luz | Cambio de brillo cuando se habilita su contribución, sin cambiar banda incorrectamente | NOT_TESTED |
| DHT-01 | Monitor 10 min con OLED/MP3/BLE/Wi-Fi activos | Temperatura/humedad no desaparecen y muestras caducadas se etiquetan como tales | NOT_TESTED |
| LDR-01 | Calibrar oscuro/claro y reiniciar pantalla | Lectura calibrada coherente, ADC crudo separado y referencias preservadas | NOT_TESTED |
| OLED-01 | Caritas feliz, dormida, sorpresa, calabaza, gato y baile | Todas se muestran físicamente, sin texto estático OLED + ambiente | NOT_TESTED |
| OLED-02 | Ajustar contraste, inversión, pausa por BLE y Wi-Fi | Lectura posterior coincide con lo enviado; pantalla sin artefactos | NOT_TESTED |
| MP3-01 | Encender DFPlayer con FAT32 | UART detecta módulo; SD presente; cantidad de pistas mayor a cero | NOT_TESTED |
| MP3-02 | PLAY/PAUSE/RESUME/VOLUME 15 con pista 0001 | Audio audible por bocina; app no presupone sonido con solo ACCEPTED | NOT_TESTED |
| LINK-01 | Cambiar BLE ↔ Wi-Fi durante operación | Estado anterior se invalida; no se envía orden a sesión caducada | NOT_TESTED |
| ALL-01 | Los cinco módulos y UI funcionando a la vez 15 min | Sin resets, cuelgues, estados falsos ni desbordes UART | NOT_TESTED |

## Evidencias mínimas
Guardar serial a 115200, respuestas de estado componente/telemetría, captura de Inicio, Estación, RGB, OLED y reproductor, fotografía del cableado y de la pantalla OLED, resultado audible de DFPlayer, y SHA de firmware/APK. No subir claves Wi-Fi, claves de propietario, direcciones MAC ni tokens.

## Regla de certificación
Una fila pasa solo con evidencia del periférico físico. Si el hardware no está disponible, conservar `NOT_TESTED` y el estado global `PENDING_HARDWARE_VALIDATION`. El merge de código no cambia esta regla.
