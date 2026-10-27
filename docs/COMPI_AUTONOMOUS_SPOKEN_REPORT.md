# Compi — reporte ambiental hablado sin teléfono

## Objetivo
El propio ESP32 obtiene las mediciones del DHT11 y del LDR, arma una
secuencia de audios de microSD y la reproduce por DFPlayer Mini + bocina.
No utiliza Android, TTS del teléfono, Wi-Fi ni BLE para hablar.

Ejemplo (solamente si los valores están medidos realmente):
"La temperatura es de 23 coma cinco grados Celsius.
La humedad relativa es del 46 por ciento.
El nivel de iluminación es del 72 por ciento."

El LDR entrega un porcentaje de luz RELATIVA sólo si está calibrado;
esto no es una medición de lux.

## Cableado ESP32 clásico
| Dispositivo | Conexión |
| --- | --- |
| DHT11 DATA | GPIO16, VCC 3V3 y GND |
| LDR AO | GPIO32, VCC 3V3 y GND; DO desconectado |
| OLED SSD1306 | SDA GPIO21, SCL GPIO22, VCC 3V3 y GND |
| RGB ánodo común | R GPIO27, G GPIO25, B GPIO26, común positivo según tensión del módulo |
| DFPlayer RX | GPIO14 TX del ESP32 por resistencia 1 kΩ |
| DFPlayer TX | GPIO13 RX del ESP32 (garantizar nivel máximo 3.3 V) |
| DFPlayer BUSY | GPIO33 ESP32 (obligatorio para la cola; máximo 3.3 V) |
| DFPlayer VCC | 5 V regulados; GND común ESP32 |
| Botón de reporte opcional | GPIO23 a GND mediante pulsador normalmente abierto |
| Bocina adecuada | SPK1 y SPK2 del DFPlayer; ninguno a GND |

GPIO0/BOOT se deja intacto porque ya participa en la seguridad de Compi.
Una bocina 0.5 W requiere LIMITACIÓN FÍSICA de potencia; sólo bajar
el volumen no protege contra sobrecarga.

## Banco de voz microSD
Usa FAT32 y carpeta MP3 en la raíz. Ejecuta offline:

    python scripts/generate-compi-speech.py --output build/compi-voice

Instala previamente espeak y ffmpeg. Copia sólo los 108 archivos MP3
generados dentro de la carpeta existente /MP3 de tu tarjeta de 16 GB.
**No formatees ni borres pistas anteriores**.

- 2000: La temperatura es de
- 2001: grados Celsius
- 2002: La humedad relativa es del
- 2003: por ciento
- 2004: El nivel de iluminación es del
- 2005: coma
- 2006: menos
- 2100..2199: números del cero al noventa y nueve
- 2200: cien

Reservar **2000..2200** para voz. No debe haber canciones con esos
números. Los avisos 0001..0009 y las demás pistas manuales continúan.

## Comportamiento
1. Al encender, el primer reporte se programa tras 45 segundos.
2. Los reportes periódicos se solicitan cada cinco minutos.
3. Al pulsar GPIO23 se solicita un reporte inmediato cuando el audio
   esté libre y existan lecturas frescas.
4. Cada segmento se solicita por la cola UART, espera BUSY bajo para
   confirmar que empezó y BUSY alto para confirmar fin, dejando una
   pausa breve entre segmentos. El loop de ESP32 nunca usa delay para
   la voz.
5. Si no está el cable BUSY o falta un MP3, se cancela después de 5 s
   sin dar la reproducción por comprobada. Un audio atascado se cancela
   después de 20 s.
6. La voz automática respeta la preferencia almacenada, espera
   reproducción manual y conserva las alertas ambientales anteriores.
7. Si DHT11 o LDR no tienen una lectura fresca, se omite sólo esa
   medición, sin valores ficticios.

## Cómo probar
- Confirma primero reproducción manual de 2000 y 2123 desde Compi.
- Verifica calibración del LDR al tapar/iluminar: su porcentaje debe cambiar.
- Abre Serial 115200 y reinicia el ESP32.
- Busca VOICE REPORT start: N clips y VOICE REPORT result=1 al terminar.
- Pulsa el botón GPIO23 y verifica las frases reales.
- Quita intencionalmente la señal BUSY con el equipo APAGADO, vuelve a
  encender y verifica result=2; vuelve a colocar el cable apagado.
- Prueba que OLED, RGB, BLE, Wi-Fi y las pistas anteriores no se bloqueen.
- Corre las pruebas host: python scripts/run-host-tests.py
- Compila el sketch Arduino con la partición de aplicación del perfil
  documentado, comprobando tamaño; nunca asumas que compilarlo prueba sonido.

## Alcance de validación
Los tests de host validan la construcción de la frase y la máquina de
estados, no el resultado acústico. La certificación final exige tarjeta
microSD, altavoz y GPIO BUSY reales; no afirmar que se oyó el audio
hasta completar esa prueba física.
