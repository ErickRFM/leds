# Compi — DFPlayer Mini MP3-TF-16P V3.0 (prototipo V34)

## Cableado (ESP32-D0WD sin PSRAM, verificar serigrafía y disponibilidad)

| Módulo | Conectar a |
|---|---|
| DFPlayer VCC | 5V regulados y estables, no GPIO |
| DFPlayer GND | GND común con ESP32 |
| DFPlayer RX | ESP32 GPIO17 (UART2 TX), resistor serie 1 kΩ recomendado |
| DFPlayer TX | ESP32 GPIO18 (UART2 RX), comprobar niveles de salida ≤ 3.3 V; divisor/adaptador si fuera necesario |
| OLED VCC/GND | 3V3 / GND |
| OLED SDA/SCL | GPIO21 / GPIO22 |
| DHT11 DATA | GPIO16 (EXCLUSIVO, no usar para DFPlayer) |
| LDR ADC1 | GPIO36 (divisor 3.3 V, no entrada a 5 V) |
| RGB | GPIO27 / GPIO25 / GPIO26 |

**Bocina pasiva 8 Ω / 0.5 W:** NO conectar a GPIO ESP32. DFPlayer SPK1/SPK2 tienen potencia que puede superar 0.5 W. **NO conectarla directamente a SPK1/SPK2 hasta medir y limitar la potencia mediante una etapa de salida de potencia adecuada**; el volumen de software no es protección suficiente. Nunca conectar SPK1/SPK2 a GND (salida puente).

## Preparación microSD FAT32

Archivos MP3 con número de pista reproducible; conviene cargar en microSD recién formateada y comprobar el orden real de índices con un audio de prueba.

| Pista | Audio sugerido |
|---|---|
| 0001.mp3 | Hola, soy Compi |
| 0002.mp3 | Hace frío |
| 0003.mp3 | Hace calor |
| 0004.mp3 | La luz está baja |
| 0005.mp3 | La luz volvió a subir |

El firmware prototipo envía comando DFPlayer 0x03 con el índice 1–5. El orden de índices depende de la copia a microSD; confirmar físicamente. El aviso «Se apagó» requiere conectar la transición real del RGB y una política de eventos adicional. **No está implementado en esta entrega.**

## Activación y límites

Activar **solo tras verificar el circuito** mediante `#define COMPI_ENABLE_DFPLAYER 1` al comienzo de `esp32_rgb.ino` (o bandera de compilación equivalente). Por defecto queda a **0** para evitar arranques accidentales. No se habilita automáticamente por estar conectado en la protoboard.

- Temperatura: frío ≤16 °C (rearme ≥18 °C), calor ≥31 °C (rearme ≤29 °C).
- Luz calibrada: aviso bajo ≤20 %, rearme ≥30 %, siempre que el LDR publique lectura válida/fresca. Si la calibración no existe, no se inventan porcentajes.
- Mínimo 60 segundos entre solicitudes de audio.
- Sin lecturas válidas, no hay anuncio; los cambios de temperatura/humedad se obtienen del sensor real.
- Serial UART2 a 9600 baudios; volumen de arranque 1/30 **no garantiza potencia eléctrica segura**.
- La aplicación Android continúa reproduciendo música en el teléfono. Este firmware produce solicitudes locales a DFPlayer cuando se habilita; la app aún no tiene controles específicos ni telemetría de estado DFPlayer.
- No se confirman audio audible, microSD, BUSY/ACK de hardware ni pines físicamente libres sin pruebas en placa.

## Pruebas obligatorias

1. Confirmar fuente de 5V, masa común y niveles UART.
2. Proteger etapa de audio para 0.5W y medir con carga de prueba antes de conectar la bocina.
3. Cargar cinco MP3, comprobar por separado el índice reproducido y el sonido.
4. Forzar muestras reales a ambos lados de umbrales y verificar histéresis/cooldown.
5. Desconectar DHT11/LDR: no pronunciar anuncios inventados.
6. Verificar coexistencia del OLED, SoftAP, BLE y GPIO existentes, incluido reinicio.
