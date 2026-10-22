# Diagnóstico BLE — entrega de comandos y reconexión

**Ámbito:** app Android DEBUG / ESP32 de laboratorio. El protocolo BLE V1 del firmware continúa sin autenticación y NO debe publicarse.

## Semántica

- `Disconnected`, `Connecting`, `Discovering` y `Ready` son estados del transporte, no una confirmación de LED físico.
- `Ready` solo aparece después de CCCD habilitado y una lectura inicial válida de `STATE`.
- Una escritura GATT enviada **no** es una confirmación de estado ni prueba de voltaje en pines.
- Solo una trama `STATE` válida actualiza el valor autoritativo que se muestra en Control RGB.
- El último `SET` pendiente sustituye a `SET` anteriores aún no enviados. La conexión gestiona una cola de máximo 16 mensajes.
- Si un `WRITE` no recibe su callback en 5 s, el cliente descarta su cola, cierra GATT y ejecuta reconexión acotada (1 s, 2 s, 4 s), **releyendo** el estado del dispositivo.
- Con firmware actual, sigue pendiente autorización/BLE bonding. No confundir el timeout con seguridad de conexión.

## Casos a verificar con Android real

1. Conecta en Equipos: esperar `Ready`, texto inicial y RGB coherente.
2. Arrastra rápidamente sliders 10 s; la app debe seguir respondiendo, sin backlog infinito.
3. Apaga el ESP32 durante un arrastre; el UI debe abandonar `Ready` y no fingir un color confirmado.
4. Enciende el ESP32; comprobar reintentos y reconciliación de `STATE` con color físico.
5. Apaga Bluetooth o revoca permisos: informar error recuperable, nunca mostrar `Ready` falso.
6. Registra capturas, resultado, modelo Android, Android API, versión de firmware, timestamp y última excepción. NO compartas claves del AP ni GATT keys.

**Verificación física del LED:** revisar resistencia 220–330 Ω por canal y polaridad `COMMON_ANODE` del firmware; comprobar con voltímetro si no enciende. Un `STATE` lógico correcto sin luz no confirma el cableado.
