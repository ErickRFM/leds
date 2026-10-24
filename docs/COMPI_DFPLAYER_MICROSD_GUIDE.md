# Compi — microSD para DFPlayer Mini MP3-TF-16P

## Instalación segura

- Usa una microSD con FAT32 (prueba una de hasta 32 GB), insertada en el DFPlayer **con la alimentación desconectada**.
- Copia las pistas en una carpeta con nombre exacto `MP3` en la raíz.
- Nombra los archivos `0001.mp3`, `0002.mp3`, `0003.mp3`, `0004.mp3`, `0005.mp3`.
- El firmware utiliza el comando UART `0x12` para reproducir pistas por número en `/MP3`, no el índice dependiente del orden de copia de la raíz.
- `0001.mp3`: introducción, `0002.mp3`: hace frío, `0003.mp3`: hace calor, `0004.mp3`: luz baja, `0005.mp3`: luz recuperada.
- DFPlayer VCC: fuente de 5 V regulados, GND: masa común ESP32; ESP32 TX14 -> DFPlayer RX mediante 1 kΩ; DFPlayer TX -> ESP32 RX13 solo con tensión compatible 3.3 V; altavoz 4 Ω / 3 W entre SPK1 y SPK2, **no a GND**.
- Arranca con volumen 1/30, no aumentes sin verificar alimentación y distorsión.
- Los avisos dependen de lecturas ambientales válidas; el umbral LDR requiere calibración.
- El firmware consulta presencia de SD, total de archivos, índice, volumen y reproducción mediante respuestas UART validadas. No enumera nombres de archivos ni mide sonido por BUSY o micrófono; no transmite Bluetooth A2DP. Un comando aceptado no confirma reproducción. Ver [contrato consolidado](COMPI_CONSOLIDATED_PROTOCOL.md).
- Si no hay microSD, sensores DHT11/LDR, RGB, Wi-Fi/BLE y OLED continúan funcionando.

## Prueba física requerida

Tras insertar microSD, reinicia Compi, comprueba un evento ambiental con pista presente y registra la respuesta UART/BUSY del DFPlayer. La compilación CI valida el firmware, pero no puede sustituir esta prueba.

Referencias: https://wiki.dfrobot.com/dfr0299/docs/20905 y https://github.com/DFRobot/DFRobotDFPlayerMini.

## Diagnóstico físico confirmado (octubre 2026)

Con GPIO13 (ESP32 RX) conectado a TX del DFPlayer y GPIO14 (ESP32 TX) conectado a RX a través de 1 kΩ, el módulo respondió con una trama válida a `0x43`. Tras reinicio, `0x3F` respondió `2` (fuente SD reportada), `0x48` respondió `0` archivos y `0x12` para `/MP3/0001.mp3` produjo error `0x40` código `6`. La detección UART está confirmada, pero el contenido de la tarjeta y la reproducción audible **no**. La interfaz muestra los cero archivos sin inventar biblioteca ni afirmar reproducción. Usar solamente alimentación regulada de 3.2–5 V; nunca conectar 12 V directamente al módulo.
