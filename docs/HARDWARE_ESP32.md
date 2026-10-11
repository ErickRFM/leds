# Hardware confirmado — ESP32 Control Center

## Identificación aportada por el usuario
| Dato | Resultado |
| --- | --- |
| SoC | ESP32-D0WD-V3, revisión 3.1 |
| Flash SPI | **4 MB** detectados por esptool 5.3.1 |
| Cristal | 40 MHz |
| Flash voltage | 3.3 V |
| USB serial | CH9102 |
| Conectividad del chip | Wi-Fi, Bluetooth, dos núcleos |

La consulta se efectuó mediante `esptool ... flash-id` sin escribir memoria y sin borrar firmware.
**No registrar ni divulgar MAC, clave SoftAP, clave API ni secretos Wi-Fi.** El puerto COM es local y puede cambiar; no se conserva en configuración del repo.

## Consecuencia para V2
- Hardware apto en capacidad de flash para el perfil compilado `esp32:esp32:esp32:PartitionScheme=min_spiffs`.
- Confirmado 4 MB total; no se ha comprobado todavía el esquema de particiones grabado ni la salud de OTA.
- El firmware Wi-Fi+BLE utiliza aproximadamente 1.76 MB de aplicación según compilaciones previas; con el perfil de 1.9 MB se verificó en CI.
- **No OTA productivo todavía:** no hay subida segura, firma ni rollback validado.
- Se conserva el respaldo `baseline/rgb-controller-v1.1` en Git.

## Compilar firmware (PowerShell, desde carpeta leds)
Usar Arduino IDE con `esp32 by Espressif Systems` 3.0.7 para reproducir CI.
1. Seleccionar **Tools → Board → ESP32 Arduino → ESP32 Dev Module**.
2. Seleccionar **Tools → Port → puerto USB CH9102** (identificarlo en Administrador de dispositivos).
3. Seleccionar **Tools → Partition Scheme → Minimal SPIFFS (1.9MB APP with OTA)**.
4. Abrir `firmware/esp32_rgb/esp32_rgb.ino` y **verificar polaridad del LED y resistencias**.
5. Antes de subir: cerrar monitor serie y desconectar del circuito cualquier carga insegura.
6. Subir firmware y abrir monitor serie a 115200; guardar únicamente mensajes no sensibles.
7. En el primer arranque, la placa imprime **dos secretos diferentes** por serial USB físico: contraseña de SoftAP y clave API; no copiarlos a Git.
8. Para la prueba Android usar **APK debug** y **Ajustes → Centro ESP32**.

### Alternativa Arduino CLI
```powershell
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs firmware/esp32_rgb
# SOLO cuando estés listo para reemplazar el firmware de la placa:
arduino-cli upload --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs --port COM5 firmware/esp32_rgb
```
`COM5` es un ejemplo observado en el equipo actual; comprobar que el puerto sigue siendo el CH9102 antes de cada carga.

## Plan de prueba físico (no realizado en este entorno)
- [ ] Alimentación 3.3 V, resistencias, común ánodo/cátodo y pines 27/25/26 revisados.
- [ ] Firmware V2 subido con la partición seleccionada; arranca sin bucle de reinicios.
- [ ] LED RGB inicia apagado; ON/OFF/SET/FX funcionan por BLE.
- [ ] SoftAP `ESP32-Control-Lab` visible y conexión WPA2 protegida exitosa.
- [ ] `Centro ESP32` consulta estado usando clave API (red privada de laboratorio).
- [ ] El ESP32 encuentra redes y se conecta al router sin exponer el HTTP por STA.
- [ ] GPIO18/19, entrada ADC1 y secuencias limitadas responden en hardware real.
- [ ] Desconectar BLE, Wi-Fi y alimentación; se recuperan los estados seguros.
- [ ] App muestra tamaños de slots OTA; **OTA upload sigue deshabilitado**.

## Recuperación
Si V2 presenta fallos, volver a flashear la referencia estable de firmware
desde el respaldo Git `baseline/rgb-controller-v1.1` con la configuración de
placa/partición correspondiente. Cambiar el esquema de particiones puede dejar
inaccesibles los datos previos guardados en flash; respaldar antes de sobrescribir.

**Estado: flash/modelo verificados; pruebas de carga, cableado, Wi-Fi, BLE, GPIO y OTA pendientes.**

## Source of truth for automated builds

The verified FQBN and pinned Arduino-ESP32 core live in [`config/esp32-board.env`](../config/esp32-board.env). The GitHub Actions workflow and `scripts/verify-local.ps1` use exactly this profile. The local verifier now rejects unsafe FQBN overrides; changing the flash/partition scheme requires a documented migration, partition backup and tested USB recovery. The check `python3 scripts/check-board-profile.py` runs in CI. Hardware flashing remains **opt-in only** with an explicit port.
