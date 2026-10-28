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
3. Pulsación **corta** del GPIO23 (al soltar): solicita un reporte en cuanto
   el audio esté libre y existan lecturas frescas, incluso con los avisos
   automáticos desactivados.
4. Pulsación **larga** de 3 segundos: activa/desactiva los avisos automáticos
   y guarda la preferencia en NVS. El pulsador utiliza 60 ms de antirrebote.
   Una pulsación larga NO solicita también un reporte.
5. Cada segmento se solicita por la cola UART, espera BUSY bajo para
   confirmar que empezó y BUSY alto para confirmar fin, dejando una
   pausa breve entre segmentos. El loop de ESP32 nunca usa delay para
   la voz.
6. Si no está el cable BUSY o falta un MP3, se cancela después de 5 s
   sin dar la reproducción por comprobada. Un audio atascado se cancela
   después de 20 s.
7. La voz automática respeta la preferencia almacenada, espera
   reproducción manual y conserva las alertas ambientales anteriores.
8. Si DHT11 o LDR no tienen una lectura fresca, se omite sólo esa
   medición, sin valores ficticios.

## Cómo probar
- Confirma primero reproducción manual de 2000 y 2123 desde Compi.
- Verifica calibración del LDR al tapar/iluminar: su porcentaje debe cambiar.
- Abre Serial 115200 y reinicia el ESP32.
- Busca VOICE REPORT start: N clips y VOICE REPORT result=1 al terminar.
- Pulsa y suelta GPIO23 para pedir las mediciones incluso con avisos automáticos apagados.
- Mantén pulsado GPIO23 al menos 3 segundos: comprueba en Serial el cambio ON/OFF; reinicia y comprueba que se conservó.
- Verifica que pulsación larga no reproduzca una frase accidentalmente.
- Quita intencionalmente la señal BUSY con el equipo APAGADO, vuelve a
  encender y verifica result=2; vuelve a colocar el cable apagado.
- Prueba que OLED, RGB, BLE, Wi-Fi y las pistas anteriores no se bloqueen.
- Corre las pruebas host: python scripts/run-host-tests.py
- Compila el sketch Arduino con la partición de aplicación del perfil
  documentado, comprobando tamaño; nunca asumas que compilarlo prueba sonido.

## Alcance de validación
Los tests de host validan la construcción de la frase, la máquina de
estados, no el resultado acústico. La certificación final exige tarjeta
microSD, altavoz y GPIO BUSY reales; no afirmar que se oyó el audio
hasta completar esa prueba física.

## Correcciones de auditoría (27-10-2026)
- La captura de sensores se hace **en cada ciclo del loop**, no únicamente
  durante la evaluación de avisos cada 250 ms. De esta manera la pulsación
  física y el reporte periódico obtienen las lecturas recientes disponibles.
- Si un aviso ambiental acaba de encolarse, un reporte numérico no podrá
  encolarse en ese mismo ciclo. Se reserva una ventana de 15 segundos para
  permitir que termine el aviso previo.
- El botón físico y las preferencias automáticas son independientes: una
  pulsación corta solicita el reporte aun con automático OFF.
- Los tests C++ del botón y de la voz son ahora pasos obligatorios en CI.
- El contenedor de audios y la compilación **no prueban sonido físico**; el
  pin BUSY, microSD, bocina, LDR calibrado y alimentación se verifican en mesa.

## Auditoría del botón y prioridad de audio (27-10-2026)

El firmware actual usa **GPIO23 a GND** para el pulsador. **GPIO25 es RGB
verde y no debe utilizarse**. GPIO35 carece de pull-up interno; nunca
cambiar a GPIO35 sin resistencia externa y ajustar el firmware primero.
El DFPlayer requiere además **BUSY a GPIO33**, separado del pulsador.

- Pulsación corta (al soltar): solicitud prioritaria sobre un aviso
  ambiental **aún no enviado**. Funciona aunque el automático esté OFF.
- Pulsación larga (3 segundos): alterna y guarda automático ON/OFF;
  **no corta un reporte que ya esté hablando**.
- Si ya existe un aviso en la cola UART se espera a que termine; el
  ESP32 conserva un resguardo de 15 segundos después de encolarlo.
- La reproducción manual, el estado BUSY bajo o la SD ausente bloquean
  el inicio de un informe; nunca se fuerza la salida del DFPlayer.
- El monitor Serie a **115200** muestra
  `VOICE BUTTON: short release on GPIO23; report requested` al soltar.
  Cuando consigue arrancar la locución muestra `VOICE REPORT start`;
  al acabar muestra `VOICE REPORT result=1` si BUSY confirmó la
  secuencia completa, `2` si no empezó algún fragmento y `3` si
  algún fragmento quedó ocupado hasta agotar el límite.
- Si aparece el mensaje del botón pero no `VOICE REPORT start`, comprobar
  `DFPLAYER UART ... present=1 sd=1`, la reproducción manual, GPIO33
  y al menos un sensor con lectura fresca. Si faltan los archivos 2000+
  la cola termina con error y no puede pronunciar números.
- Las pruebas portables `autonomous_voice_arbitration_test.cpp`,
  `autonomous_report_button_test.cpp` y
  `autonomous_spoken_report_test.cpp` se ejecutan en CI.

**Certificación pendiente:** el CI valida código, no puede confirmar la
pulsación eléctrica de tu botón, ni la voz audible de tu DFPlayer.
