# Compi: catálogo DFPlayer ambiental (V35)
El DFPlayer reproduce con comando 0x12, ruta FAT32 `/MP3/NNNN.mp3` (cuatro dígitos). Los índices son identificadores de evento, no el orden de copia en la microSD.

| Nombre | Mensaje | Regla |
|--|--|--|
| 0001.mp3 | Hola, soy Compi | Inicio con avisos habilitados |
| 0002.mp3 | Hace frío | temperatura <20°C |
| 0003.mp3 | Qué agradable está el clima | 20–<26°C |
| 0004.mp3 | Ya está haciendo calor | 26–<30°C |
| 0005.mp3 | Hace bastante calor | 30–<35°C |
| 0006.mp3 | Qué calor tan extremo | >=35°C |
| 0007.mp3 | Está muy húmedo el ambiente | humedad >=95%; rearme <=90% |
| 0008.mp3 | ¿Quién apagó la luz? | iluminación calibrada <=20%; rearme >=30% |
| 0009.mp3 | Ya volvió la luz | iluminación calibrada >=30% después de oscuridad |

Mínimo de 3 muestras frescas consecutivas para temperatura y 15 segundos entre peticiones automáticas. Se consideran solamente muestras válidas. No se interrumpe una pista manual: la evaluación de voz del firmware ocurre al estar STOPPED. El conteo de pistas no certifica que existan los nueve nombres exactos; cargarlos todos y comprobar cada frase por número.

La humedad es relativa y se obtiene del DHT11 GPIO16. LDR GPIO32 se interpreta con porcentaje *calibrado*, jamás con 0/300/1500/3000 ADC sin conocer la polaridad del módulo. Los cambios muy rápidos podrían requerir ajustes de umbral/calibración en el hardware real.

La interfaz conserva visualmente el último estado confirmado durante los sondeos UART; no prueba físicamente pausa o continuidad. Validar en hardware la pausa, los 9 nombres, el RGB/OLED y BLE/Wi-Fi antes de declarar cierre.
