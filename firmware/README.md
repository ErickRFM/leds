# Firmware ESP32 RGB — Arduino ESP32 Core 3.x

Placa recomendada de laboratorio: ESP32 Dev Module (ESP32 clásico compatible con BLE). No soporta ESP32-S2, que carece de BLE.

GPIO 27/R, 25/G, 26/B, cada uno con resistencia 220–330 Ω en serie, COM a 3V3 para ánodo común (predeterminado). Para cátodo común, COM a GND y cambiar `COMMON_ANODE=false`. Comprobar con ficha técnica/multímetro.

Abrir `firmware/esp32_rgb/esp32_rgb.ino` con Arduino IDE, instalar esp32 by Espressif Systems 3.x, seleccionar ESP32 Dev Module y el puerto, subir y abrir monitor serie 115200.

O compilar:
```sh
arduino-cli core update-index --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli core install esp32:esp32@3.0.7 --additional-urls https://espressif.github.io/arduino-esp32/package_esp32_index.json
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32_rgb
```
Luego probar con nRF Connect Android: servicio BLE y UUIDs en `docs/PROTOCOLO_BLE.md`, activar notificaciones en STATE, enviar ASCII `SET,255,0,0,255`, `OFF`, `ON`, `FX,RAINBOW,120`, `FX,STOP`.

Nota: el control se realiza por Bluetooth Low Energy, no por Wi-Fi ni potenciómetros. Firmware de laboratorio sin emparejamiento seguro; no utilizar fuera de pruebas hasta añadir autorización.
