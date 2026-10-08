# ESP32 — Backend embebido y firmware

## Alcance del código inicial
- ESP32 clásico + Arduino-ESP32 **3.x**.
- LED RGB (tres PWM independientes: GPIO 27,25,26).
- Control local BLE GATT: SET, ON, OFF, GET.
- Color y brillo 0..255, notificaciones STATE y errores básicos.
- Estado apagado al arranque; mantiene último color tras desconexión.
- **No** incluye seguridad de pairing ni efectos; firmware de laboratorio.

## Dependencias
Instala el paquete de placas **esp32 by Espressif Systems** versión 3.x en Arduino IDE (o Arduino CLI equivalente). Selecciona el modelo exacto de la placa, por ejemplo `ESP32 Dev Module` para DevKit/WROOM clásico.

## Flasheo
1. Confirma cableado en `docs/CABLEADO.md`.
2. Abre `firmware/esp32_rgb/esp32_rgb.ino` en Arduino IDE.
3. Selecciona placa y puerto; compila y sube.
4. Abre monitor serie a 115200. Nombre BLE anunciado: `RGB-ESP32`.
5. Prueba con nRF Connect los UUID indicados en `docs/PROTOCOLO_BLE.md`.
6. En Android activa NOTIFY en la característica State, escribe ASCII `SET,255,0,0,255` en la característica Command y verifica rojo.
7. Documenta el resultado antes de afirmar hardware validado.

Nota: la simulación de Wokwi no sustituye pruebas BLE físicas; no permite validar el enlace Bluetooth.

## Seguridad
El prototipo admite escrituras BLE sin autenticación. **No se debe tratar como firmware seguro para uso público**; agregar emparejamiento autorizado/bonding y política de accesos antes del lanzamiento.
