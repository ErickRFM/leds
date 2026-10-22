# DHT11 en Compi — captura compartida con el probe (V22)

## Evidencia física
El mismo DHT11 en GPIO16 produjo 3/3 lecturas completas en el probe:
`3B 00 15 02 52`, 59.0 % y 21.2 °C; 82 flancos, sync 80/77–80µs.
Esto valida el sensor/cableado/GPIO16 **en modo aislado**. No demuestra
por sí solo que el firmware completo pueda mantener esas lecturas.

## Cambios de firmware
- El firmware principal usa exactamente el mismo `DhtProbeCore.h` que el
  sketch aislado, incluido el decodificador de los cinco bytes.
- La captura crítica (aprox. 4ms cada ≥2s) usa `noInterrupts()` solo en
  el núcleo que ejecuta la lectura, **después** del pulso de inicio de 20ms.
  Un guard de alcance restablece interrupciones ante errores y timeouts.
- El firmware sigue procesando Wi-Fi, BLE, comandos y GPIO normalmente
  fuera de la transacción. Las interrupciones enmascaradas brevemente
  siguen siendo un compromiso: **hay que comprobar BLE y SoftAP con la
  placa real**. No se afirma que una prueba de CI lo garantice.
- El DHT22 mantiene su lector existente; no se modifica su protocolo.
- No se toca el pinout RGB (27/25/26), ni se restablecen claves/identidades.
- Diagnóstico serie: `DHT11 DATA GPIO16: ok, edges=82/82,
  consecutiveFailures=0, valid=1`.
- CI comprueba que ambos cores DHT11 sean byte por byte idénticos y
  prueba valores reales `59.0/21.2`, sensor ausente, checksum erróneo,
  trama rota e interrupción simulada de 200µs con/sin protección.

## Prueba física (OBLIGATORIA antes de declarar funcional)
1. Arduino IDE: ESP32 Dev Module, core 3.0.7, min_spiffs, USB 115200.
2. **Con el USB desconectado**, verificar DHT11: VCC 3V3, GND GND,
   DATA GPIO16. No cambiar si el probe ya pasó y está bien cableado.
3. Cargar firmware normal V22. Esperar 15s y verificar en el monitor
   `ok`, `edges=82/82`, `valid=1`.
4. Conectar Wi-Fi autorizado en Compi, entrar DHT11 y actualizar.
   Valores reales alimentarán las gráficas 5/15/30 min.
5. Verificar que BLE pueda conectarse/controlar RGB y que SoftAP no
   presente desconexiones. En caso de regresión, volver a V21 y
   registrar la causa. Un CI verde NO reemplaza estas pruebas.
