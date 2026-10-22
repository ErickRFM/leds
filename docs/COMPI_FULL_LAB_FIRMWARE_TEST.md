# Firmware de prueba integral Compi — ESP32-D0WD-V3

## Descarga del firmware completo

Repositorio `ErickRFM/leds`, carpeta `firmware/esp32_rgb`. **No extraer solo `esp32_rgb.ino`: incluir `core/` y `modules/` dentro del sketch.** Abrir `firmware/esp32_rgb/esp32_rgb.ino` en Arduino IDE; usar placa ESP32 clásica, particiones `min_spiffs` (1.9MB OTA), Arduino ESP32 core 3.0.7, monitor a 115200. Instalar bibliotecas `Adafruit SSD1306` y `Adafruit GFX`; si están ausentes no habrá funcionalidad OLED.

## Perfil de prueba física

Cambiar **solo** en el encabezado del `.ino`:

```cpp
#define COMPI_FULL_LAB_PROFILE 1
```

Este opt-in habilita al arranque LDR (GPIO32) y DFPlayer (UART2 RX18/TX17) sobre la configuración segura por defecto. OLED GPIO21/22 y DHT11 GPIO16 ya están habilitados por defecto. La bocina Samsung 3W/4Ω va a SPK1/SPK2 del DFPlayer, sin conectar ningún terminal a GND. DFPlayer necesita 5V estable y GND común, UART TX del ESP32 GPIO17 hacia RX de DFPlayer mediante resistencia serie de 1k; TX DFPlayer a GPIO18 con nivel ≤3.3V. **Nunca alimentar sensores analógicos de 5V a la entrada ADC del ESP32.** Verificar ausencia de PSRAM y conflictos GPIO.

El DFPlayer ejecuta comandos a volumen inicial 1/30 y pistas 2-5 cuando las lecturas físicas frescas activan los umbrales; **no puede recibir audio Bluetooth A2DP**. La app Android reproduce audio local en el móvil pero no lo envía a la bocina Samsung por A2DP. Para MP3 del DFPlayer se requiere tarjeta microSD FAT32 y comprobar sus índices de pista. No hay interfaz de selección musical DFPlayer todavía.

## OLED

Hardware Lab de Android ahora permite seleccionar cara o texto. La **vista previa solo presenta el último mensaje/cara que el ESP32 confirmó con un ACK HTTP**, y nunca pretende leer píxeles físicos, captar un video o transmitir frames OLED por BLE. El comando exige SoftAP Wi-Fi autorizado. Pantalla física puede estar desconectada aunque se reciba el ACK; verificar visualmente.

## Prueba inicial

1. Con corriente desconectada, verificar tensión, cableado, GND y salida speaker antes de encender.
2. Arrancar con monitor serie a 115200 y confirmar SoftAP y BLE.
3. Confirmar lecturas DHT11 y que GPIO32 entrega cambios LDR cubriendo/iluminando AO.
4. Verificar OLED en GPIO21/22, texto/caras desde Android por Wi-Fi autorizado.
5. Colocar MP3s numerados en microSD y disparar un evento ambiental; verificar pista audible a volumen bajo y ausencia de distorsión.
6. Repetir uso simultáneo de Wi-Fi, BLE, pantalla, sensor y comandos RGB; observar posibles reinicios o bloqueos.

**Esta guía documenta una configuración para pruebas, no garantiza validación física ni audio Bluetooth.**
