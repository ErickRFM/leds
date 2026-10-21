# Firmware ESP32 RGB — Arduino ESP32 Core 3.x

Placa recomendada de laboratorio: ESP32 Dev Module (ESP32 clásico compatible con BLE). No soporta ESP32-S2, que carece de BLE.

GPIO 27/R, 25/G, 26/B, cada uno con resistencia 220–330 Ω en serie, COM a 3V3 para ánodo común (predeterminado). Para cátodo común, COM a GND y cambiar `COMMON_ANODE=false`. Comprobar con ficha técnica/multímetro.

Abrir `firmware/esp32_rgb/esp32_rgb.ino` con Arduino IDE, instalar esp32 by Espressif Systems 3.x, seleccionar ESP32 Dev Module y el puerto, subir y abrir monitor serie 115200.

O compilar:
```sh
arduino-cli core update-index --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core install esp32:esp32@3.0.7 --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs firmware/esp32_rgb
```
Luego probar con nRF Connect Android: servicio BLE y UUIDs en `docs/PROTOCOLO_BLE.md`, activar notificaciones en STATE, enviar ASCII `SET,255,0,0,255`, `OFF`, `ON`, `FX,RAINBOW,120`, `FX,STOP`.

Nota: además de BLE V1, V2 incluye Wi-Fi **SoftAP solo para laboratorio** con clave independiente por Serial. No expone API HTTP a la red del router; no hay TLS ni emparejamiento BLE seguro. Firmware de laboratorio: no utilizar fuera de pruebas supervisadas hasta añadir autorización.

## Fundacion modular V2
El sketch existente mantiene el protocolo BLE V1. El nucleo en `core/` gestiona las reservas de recursos y el ciclo de vida, y `modules/LedRgbModule.h` controla PWM y el tick. Restricciones ESP32 clasico; no usar este mapa GPIO en otra placa sin validacion. Ver `docs/V2_FOUNDATION.md`.

## Perfil firmware Wi-Fi + BLE V2
El firmware combinado ocupa alrededor de 1.76 MB; **no cabe en la partición OTA predeterminada** de 1.25 MB. Para una placa ESP32 clásica con **flash confirmada de 4 MB o más**, selecciona **Partition Scheme: Minimal SPIFFS (1.9MB APP with OTA)** en Arduino IDE. En Arduino CLI:

```sh
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs firmware/esp32_rgb
```

**No flashees** este perfil en un ESP32 cuya flash real no hayas identificado. El perfil mantiene dos slots OTA en placas compatibles, pero **no implementa todavía actualizaciones OTA ni rollback de aplicación**. Si no dispones de 4 MB, hay que optimizar firmware o elegir otra variante de placa; no cambiar a No OTA sin evaluar la recuperación.

## Placa real verificada (esptool)
- ESP32-D0WD-V3, revisión 3.1, cristal 40 MHz.
- Flash identificada: **4 MB, 3.3 V**.
- Interfaz de programación: CH9102, el puerto COM cambia según PC.
- Consulta de solo lectura: `esptool --chip esp32 --port <PUERTO> flash-id`.
- El esquema V2 `PartitionScheme=min_spiffs` cabe en esta capacidad **según el tamaño de flash**, pero la compatibilidad de circuito, particiones instaladas y rendimiento solo se confirman cargando y probando la placa. **Antes de cambiar el esquema de particiones, respaldar firmware/datos si hacen falta**.
- No registrar la MAC ni credenciales de SoftAP/clave API en logs públicos, Git ni screenshots.

Instrucciones de ensayo físico y recuperación: [Hardware confirmado](../docs/HARDWARE_ESP32.md).


## Perfil OLED SSD1306 + DHT11 (Compi)

El firmware completo actual tiene **COMPI_ENABLE_OLED=1** (OLED SSD1306 128×64 I²C)
para el prototipo de Compi. El display es un módulo **opcional en ejecución**:
si el panel no responde a la dirección `0x3C`, el Wi-Fi/BLE/RGB/DHT11
continúan encendiendo y el fallo aparece en el monitor serie.

Cableado: OLED VCC → **3V3** (si el módulo es compatible), GND → GND,
SDA → **GPIO21**, SCL → **GPIO22**. DHT11 DATA → **GPIO16**,
RGB → GPIO27/25/26. Confirmar que tu módulo no sea SH1106,
SSD1306 128×32, ni de dirección 0x3D antes de usar este perfil.

Instala estas bibliotecas desde Arduino IDE > Library Manager:
`Adafruit SSD1306`, `Adafruit GFX Library` y `Adafruit BusIO`.
La compilación detiene con error claro si el OLED está habilitado y falta
SSD1306. En Arduino CLI:

```sh
arduino-cli lib update-index
arduino-cli lib install "Adafruit BusIO" "Adafruit GFX Library" "Adafruit SSD1306"
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs firmware/esp32_rgb
```

En el arranque, primero aparece «HOLA, SOY COMPI!» en la pantalla y,
tras unos 8 s, un resumen de temperatura/humedad DHT11 cada 3 s.
Si faltan lecturas, no se inventan valores: muestra «Esperando lectura».
Para validarlo, buscar en USB serial el registro `BOOT: OLED SSD1306 ACK OK`.
Eso confirma la respuesta I²C y el intento de refresco, **no**
verifica visualmente los píxeles sin una prueba física del panel.

El BLE usa emparejamiento cifrado Secure Connections «Just Works»:
**no existe un PIN fijo Bluetooth**. El emparejamiento no da permisos de
control; hay un alta de propietario adicional. Para abrir ventana de alta/
recuperación, mantener BOOT (GPIO0) durante 3 segundos con la placa
ya encendida y leer el código temporal en monitor serie USB. No publicar
ese código. La integración app↔propietario requiere prueba física.
