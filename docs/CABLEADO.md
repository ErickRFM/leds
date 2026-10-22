# Cableado ESP32 + LED RGB de 4 terminales

## Circuito inicial
| ESP32 | Destino | Protección |
| --- | --- | --- |
| GPIO 27 | canal R del LED | resistencia 220–330 Ω en serie |
| GPIO 25 | canal G del LED | resistencia 220–330 Ω en serie |
| GPIO 26 | canal B del LED | resistencia 220–330 Ω en serie |
| 3V3 | COM del LED **solo si es ánodo común** | — |
| GND | COM del LED **solo si es cátodo común** | — |

Cada color necesita su propia resistencia. No conectar un canal directo al GPIO.
Para Wokwi: LED RGB por defecto de ánodo común, configurar `ANODO_COMUN = true` en firmware. En LED físico se debe identificar el tipo con hoja de datos/multímetro; **no adivinar por longitud de patitas**.

## Potenciómetros
Para control BLE puro NO son necesarios. Si se desea un modo manual futuro: pot 10 kΩ con extremos a 3V3 y GND y cursor a GPIO ADC 34/35/32, con cambio de modo explícito para que teléfono y perillas no compitan.

## Precauciones eléctricas
- La corriente total y de cada GPIO debe mantenerse bajo los límites de la placa; validar LED y resistencias con hoja de datos.
- Jamás alimentar tiras LED directamente desde GPIO; utilizar drivers/MOSFET y fuente correcta con masa común.
- No usar 5V sobre entradas analógicas del ESP32.
- Las fotografías anteriores mostraban protoboard/simulación; **no hay verificación eléctrica física en este repo**.

## Prueba básica
1. Desenergizar antes de reconectar.
2. Verificar resistencias y pin común con multímetro.
3. Flashear firmware (Arduino-ESP32 3.x) y abrir monitor serie a 115200.
4. Abrir nRF Connect desde Android: conectar a `RGB-ESP32`.
5. Escribir `SET,255,0,0,255` en Command y confirmar LED rojo.
6. Repetir verde y azul, luego `OFF`, `ON` y `GET`.
7. Desconectar, volver a conectar y leer State.
