# ESP32 Control Center V2 — Laboratorio Wi-Fi local (F2–F7 parcial)

Esta entrega es funcional **solo en laboratorio**; conserva BLE RGB V1.
El ESP32 publica un SoftAP WPA2 llamado ESP32-Control-Lab (192.168.4.1).
En el monitor serie 115200 aparecen dos valores generados en el dispositivo y
almacenados en NVS: contraseña WPA2 de 16 hex y clave API de 16 hex.
**No compartir, capturar en pantallas públicas ni subir estos valores a Git.**

## Uso desde Android debug
1. Flashea esp32_rgb.ino, abre Serial 115200 y copia ambas claves privadas.
2. Instala APK **debug** V1.2-lab, abre Ajustes > Centro ESP32.
3. Abre los ajustes Wi-Fi, conecta el móvil a ESP32-Control-Lab usando su contraseña.
4. Vuelve a Centro ESP32 e introduce la clave API que aparece en Serial.
5. Consulta Estado; Wi-Fi scan devuelve redes detectadas por el ESP32.
6. Selecciona SSID y escribe contraseña del router. El ESP32 comienza conexión STA.
7. Consulta Estado para comprobar wifiConnected, SSID e IP estación.
8. Prueba HIGH/LOW GPIO18, lectura GPIO, ADC GPIO34, laboratorio y RGB Wi-Fi.
9. Salidas manuales caducan tras 30s; laboratorio solo alterna pin autorizado con límites.

## Modelo de seguridad (no apto para producción)
- HTTP se escucha por el servidor local pero **rechaza peticiones que no lleguen a la IP SoftAP**.
- WPA2 SoftAP aleatoria e independiente de clave HTTP; ambas se leen físicamente por USB Serial.
- Android solo activa cleartext en build debug. La versión release no puede operar la API de laboratorio.
- No hay TLS ni autenticación mutua. No exponer el dispositivo ni credenciales en redes compartidas.
- El RGB BLE V1 sigue sin autenticar; limitar uso a pruebas locales.
- No se envían contraseñas Wi-Fi por BLE sin autenticar. Se envían dentro del SoftAP protegido WPA2.
- No hay GPIO remoto arbitrario: se respetan reservas, pines seguros, caducidad y LOW al liberar.
- Los módulos C++ no están aislados. No se permite cargar firmware remoto ni C++ arbitrario.
- No se implementó WebSocket/TLS, onboarding BLE Security1 ni OTA firmado: pendientes reales.

## Endpoints bajo el SoftAP y clave X-Device-Key
| Método | Ruta | Función |
| --- | --- | --- |
| GET | /v2/status | RGB real, RAM, uptime, red, recursos y experimento |
| GET | /v2/wifi/scan | escanea hasta 24 redes desde el ESP32 |
| POST | /v2/wifi/connect | conecta a SSID mediante credenciales de formulario |
| POST | /v2/gpio/write | salida HIGH/LOW con lease y caducidad |
| GET | /v2/gpio/read?pin=18 | lectura digital |
| GET | /v2/adc/read?pin=34 | ADC1 bruto |
| POST | /v2/gpio/reset | LOW y libera salidas |
| POST | /v2/lab/blink | blink declarativo, 100–2000ms, 1–20 ciclos |
| POST | /v2/lab/stop | cancela experimento y libera recursos |
| POST | /v2/led/command | reutiliza SET/GET/ON/OFF/FX BLE V1 |

## Regresión / bloqueos de release
- CI: Android tests, lint, APK; Arduino esp32:esp32 3.0.7; pruebas host del núcleo.
- Validar firmware y red reales, wifi scan, fallos WPA2, retardo BLE+Wi-Fi y recursos en placa.
- Confirmar modelo/flash y plan particiones OTA antes de implementar OTA A/B.
- Implementar vinculación BLE con prueba de posesión + TLS antes de exponer LAN de producción.
- Añadir WebSocket, autorización por recurso, versión de contrato, auditoría de flujo y pruebas hardware.
- El estado V2 no será PRODUCTION_READY hasta completar todos estos puntos.

**Esta fase NO equivale a entrega completa de F2–F9.**

## Requisito de memoria y particiones
El perfil Wi-Fi+BLE necesita >1.3 MB de app. El CI usa `esp32:esp32:esp32:PartitionScheme=min_spiffs` (4 MB de flash, dos slots de ~1.9 MB), sujeto a verificación física. Una compilación válida para esta partición no certifica que la placa real tenga flash suficiente ni que OTA funcione.

## Auditoría de capacidad OTA
`GET /v2/firmware` consulta las particiones activa y candidata que reporta ESP-IDF (tamaño y etiqueta) y muestra el estado en la app. La inspección no permite flashear. La ruta de subida OTA queda **deshabilitada** hasta tener autenticación de firmware, rollback y ensayos de recuperación probados en la placa física.

## Reconexión tras reiniciar
Wi-Fi STA almacena la configuración de red en NVS y al arrancar intenta restaurarla de forma no bloqueante con `WiFi.begin()`. El SoftAP protegido permanece accesible para recuperación. La persistencia y coexistencia BLE/Wi-Fi siguen pendientes de QA físico en la placa.

## Editor declarativo de experimentos V2
La pantalla Centro ESP32 permite introducir secuencias `1:500,0:500,1:300`, donde 1/0 representan HIGH/LOW y el número es duración en milisegundos. Se admiten entre 1 y 16 pasos, cada uno entre 100 y 2000 ms, y máximo 20 segundos totales. Solo GPIO19 está expuesto inicialmente desde la UI. El firmware valida cada valor antes de reservar el GPIO, permite cancelar, libera la reserva y fuerza LOW/INPUT al terminar. No admite Python, C++ ni scripts arbitrarios por red.

### Read-only hardware diagnostics (new)

`GET /v2/diagnostics` is available **only** through the authenticated laboratory SoftAP session with `X-Device-Key` and the existing station-interface rejection. Fields: chip model/revision, flash bytes, current/minimum free heap, uptime, numeric reset reason, station RSSI or zero when disconnected, and reserved pin count. It intentionally omits MAC, SSID, passwords and the API key. This is not a serial boot log, production telemetry service, physical memory proof or calibration record.
