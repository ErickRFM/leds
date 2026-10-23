# Auditoría de rutas activas y limpieza Compi — 2026-10-22

Base auditada: `main@eb49ee58`. Evidencia visual del hardware/app: DHT11 22.7 °C, humedad 63.8 %, LDR ADC crudo 1049, RGB físicamente azul/cian, pantalla OLED de la app Offline.

## Política de limpieza
- Mantener **un solo propietario físico por GPIO y por función**. No eliminar módulos útiles ni sustituir el diseño.
- Un estado de comando **enviado** no equivale a estado físico **confirmado**.
- La estación muestra controles ambientales y accesos a sus módulos; rueda, presets y brillo manual quedan en RGB.
- Nunca cambiar conexiones a ciegas con alimentación activa.

## Mapa de cables del sketch vigente (no deducido de la fotografía)

| Elemento | ESP32 | Destino | Notas |
|---|---|---|---|
| RGB rojo | GPIO27 | R vía resistencia propia | La polaridad común ánodo/cátodo debe coincidir con COMMON_ANODE |
| RGB verde | GPIO25 | G vía resistencia propia | Verificar con prueba de canal aislado |
| RGB azul | GPIO26 | B vía resistencia propia | Verificar con prueba de canal aislado |
| DHT11 | GPIO16 | DATA | Compartir GND; no confundir DHT11 con DHT22 |
| LDR | GPIO32 | señal ADC1 | ADC 0–4095 no son lux; calibración necesaria |
| OLED SSD1306 | GPIO21 | SDA | 128×64, dirección esperada 0x3C |
| OLED SSD1306 | GPIO22 | SCL | Verificar 0x3D y controlador real antes de afirmar fallo |
| DFPlayer | GPIO17 TX | RX del DFPlayer | UART2 9600 baudios; alimentación compatible |
| DFPlayer | GPIO18 RX | TX del DFPlayer | Tierra común; verificar respuesta y tarjeta |

## Hallazgos probados leyendo el código
1. `esp32_rgb.ino` controla el MP3 con `HardwareSerial(2)` y `dfplayerCommand`; la clase `DfPlayerMiniModule.h` **no está instanciada** por el sketch principal. Se retiró únicamente su include sin uso. No se borró el archivo porque otros consumidores/pruebas podrían necesitarlo.
2. `audioControlCommand` confirma aceptación/enviado sin validar reproducción física. **Pendiente P0:** unificar UART, parser con trama/checksum y estados (detectado, SD lista, reproducción confirmada, error, sin respuesta), con tests y datos verificables.
3. `EnvironmentalRgbPolicy` entrega `Comfort=(54,186,116)` para 22.7 °C y 64 % de humedad, sin tinte azul (>70 %). El sketch sí llama a `evaluate` y luego a `applyRgbRuntimeCommand` cuando modo ambiental puede ejecutarse. El LED azul/cian observado **no demuestra** cuál de estas causas sucede: versión de firmware distinta, modo no habilitado, polaridad, canales cambiados, estado de comando o error eléctrico. No corregir por intuición.
4. `OledSsd1306Module` habilita estado `ready` después de inicialización I²C. El sketch incluye expresiones/cat dance; la app muestra Offline. **Pendiente P0:** contrastar el estado publicado para OLED, permiso/control BLE vs Wi-Fi, ACK I²C y controles deshabilitados.
5. La UI muestra un selector OLED estrecho que parte el texto, y una rueda RGB dentro de Estación Compi. **Pendiente UI:** tabs scrollables de ancho intrínseco, y sustitución de la rueda duplicada por solo interruptor de RGB ambiental y su estado, preservando el RGB manual en su módulo original.

## Secuencia de pruebas obligatoria
1. Compilar firmware con `esp32:esp32:esp32:PartitionScheme=min_spiffs`, Arduino ESP32 core 3.0.7, y dependencias OLED instaladas; ejecutar las pruebas unitarias y build Android.
2. Verificar el pinout mediante GPIO/color individual con modo ambiental desactivado: rojo puro, verde puro, azul puro; si el color no coincide, rectificar física/configuración antes de automatizar.
3. Activar ambiente a ~22.7 °C, comprobar política verde, estado RGB reportado y salida LED física; cambiar temperatura controladamente o simular en pruebas, no calentar el sensor innecesariamente.
4. Comparar BLE y SoftAP Wi-Fi con órdenes equivalentes, confirmaciones reales y reconexión.
5. Confirmar presencia OLED I²C 0x3C/0x3D, control de expresiones y presentación visible en panel físico. Si la pantalla real está apagada o es otro controlador, el firmware debe indicar por qué.
6. Comprobar DFPlayer: UART RX/TX cruzados, alimentación, SD insertada/formateada, pista válida, respuesta/errores del módulo y salida de audio. Sin respuesta no mostrar «Reproduciendo».
7. Calibrar LDR con puntos oscuros/iluminados y comprobar persistencia tras reinicio.
8. Comparar SHA del firmware **realmente flasheado** y APK **realmente instalada** con los artefactos del mismo commit.

## Alcance de esta PR
Cambio mínimo y verificable de higiene de código: quitar una inclusión sin usos del sketch. **No** se declara cerrado el hardware, la UI ni la integración; requieren cambios específicos y validación. No hacer merge basándose solo en este documento.
