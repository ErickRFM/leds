# Diagnóstico Compi — DHT11 GPIO16 y salida LED RGB

## Qué prueban los indicadores
- Driver DHT11 activo en GPIO16 = firmware adquirió el pin, NO detección física.
- `diagnostic=ok` y telemetría `fresh/valid=true` para temperatura y humedad
  = se recibió al menos una trama DHT con checksum y contenido correcto.
- `no_response`: no llega el primer pulso de respuesta.
- `line_low`: DATA permanece baja antes de comenzar la lectura (corto,
  módulo mal alimentado u otra carga; NO confirma daño interno del GPIO).
- `response_timeout`: respuesta de sincronía incompleta.
- `bit_timeout`: un pulso de datos tardó demasiado o quedó truncado.
- `bad_checksum`: 40 bits recibidos, pero checksum inválido.
- `invalid_payload`: checksum válido, modelo o valores fuera de contrato.
- `failures` son los fallos consecutivos de lectura; no es el número
  de muestras históricas inválidas.

## Corrección del protocolo
Se elimina `pulseIn(pin, LOW)` que podía omitir el inicio cuando el
sensor ya estaba manteniendo DATA baja; ahora se espera cada transición
desde la liberación del pin con un plazo acotado. El ESP32 **libera**
la línea con `INPUT_PULLUP` después del pulso bajo en vez de conducir
un HIGH en conflicto con el DHT. Se captura el ancho de HIGH de 40 bits
con umbral entre un 0 (~26–28us) y un 1 (~70us). DHT11 utiliza inicio
a 20ms; DHT22 utiliza 2ms.

## Cómo comprobar si GPIO16 sirve en tu placa
1. Con USB DESCONECTADO, revisa la serigrafía real del DHT11: 3V3 → VCC,
   GND → GND, DATA → GPIO16. Si usas el sensor sin placa adaptadora, coloca
   pull-up de 4.7k–10k a **3.3V**, nunca 5V.
2. Conecta USB, abre Monitor Serie 115200. Busca aviso sobre PSRAM:
   si se detecta, GPIO16/17 quedan reservados; usa GPIO32 o GPIO33.
3. Lee `DHT11 DATA GPIO16: <diagnostic>, consecutiveFailures=N, valid=0/1`
   cada ~15s, y en Compi el diagnóstico actualizado por Wi-Fi autorizado.
4. Si sale `no_response`, confirma continuidad, alimentación y el terminal
   DATA. Un pull-up interno puede no ser suficiente para cables largos.
5. Para aislar GPIO16 de un posible problema de cableado, apaga la placa,
   mueve SOLO DATA a GPIO17 (si no hay PSRAM) y aplica GPIO17 desde Compi
   por Wi-Fi autorizado; luego repite la lectura. Si ambos fallan igual,
   no implica que ambos GPIO estén defectuosos.
6. Para una **prueba eléctrica de loopback real** hace falta instrumental
   o un puente controlado con el sensor desconectado. No hay test eléctrico
   automático que pruebe GPIO16 sin un circuito externo.
7. Si hay 2–3 lecturas `fresh`/válidas, comprueba gráficas 5/15/30 min.
8. Nunca conectes/desconectes jumpers con USB encendido.

## LED RGB
El estado `led.rgb.active` significa únicamente que 27/25/26 tienen
periféricos PWM adquiridos. No existe circuito de realimentación de carga
o corriente. Un LED físicamente desconectado sigue apareciendo como
"PWM configurado"; no debe mostrarse como un LED detectado ni con una
insignia verde de confirmación física. Para detección auténtica haría
falta sensor de corriente o realimentación de hardware.

## Compatibilidad
El JSON `GET /v2/components/status` v1 acepta los nuevos campos
opcionales `diagnostic` y `failures` solo para DHT; las apps anteriores
los ignoran. El JSON de telemetría mantiene su contrato existente.
