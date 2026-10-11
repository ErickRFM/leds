# Estado verificable — ESP32 Control Center V2

Base de referencia: laboratorio V2 integrado a main (PR #11, #16–#19).
**Verificación hardware aportada por el usuario (esptool 5.3.1, consulta de solo lectura):** microcontrolador ESP32-D0WD-V3 revisión 3.1, cristal 40 MHz, flash detectada de **4 MB** y voltaje de flash 3.3 V. Puerto USB serial CH9102; el puerto COM asignado por Windows puede cambiar. La dirección MAC no se almacena en el repositorio.

Esta verificación confirma **modelo y tamaño de flash**, NO confirma que el firmware V2 se haya cargado, que la partición OTA se haya grabado o que BLE/Wi-Fi/GPIO funcionen físicamente. El esquema compilado es `PartitionScheme=min_spiffs`; antes de flashear hay que seleccionar ese mismo perfil. Ver `docs/HARDWARE_ESP32.md`.

## CI probado en PRs
- Android: tests unitarios, lint y APK debug.
- Arduino ESP32 core 3.0.7: build firmware con PartitionScheme=min_spiffs.
- Host C++: registro de módulos, recursos, revocación, experimento y seguridad de límites.
- Auditoría estática de compatibilidad BLE UUID/contratos RGB V1.
- La placa física ESP32 y el celular real no se probaron en este entorno.

## Entregado y fusionado
- F0/F1: PinManager, registro modular, LedRgbModule y baseline V1.1.
- F2 (laboratorio): SoftAP WPA2 con clave de red aleatoria, secreto HTTP distinto y Wi-Fi scan/STA join.
- F3 (laboratorio): HTTP solo sobre interfaz SoftAP, estado compartido con RGB BLE.
- F4/F5: GPIO digital reservado y tiempo limitado, RGB BLE y Wi-Fi con mismo parser.
- F6: lectura ADC1 puntual, GPIO read, RAM, uptime e IP estación.
- F7: secuencias HIGH/LOW de máximo 16 pasos, 20 segundos y retorno seguro al cancelar.
- F8 preflight: tamaño real de particiones OTA mostrado en la app, sin permitir subida.
- F9 parcial: Centro ESP32 Kotlin Compose accesible desde Ajustes, solo debug.

## Bloqueos críticos
1. BLE RGB V1 continúa sin pairing/autorizar escritura: solo entorno supervisado.
2. Wi-Fi laboratorio usa HTTP sobre WPA2 SoftAP, no TLS ni mTLS; no usar en red pública.
3. Para vinculación completamente desde móvil hace falta aprovisionamiento BLE seguro con prueba de posesión.
4. No existe transporte LAN autenticado mediante TLS ni WebSocket multi-módulo.
5. GPIO PWM/I2C/sensores adicionales aún necesitan mapeo físico por placa.
6. OTA firmado, verificación de salud y rollback en placa física **NO implementados**.
7. Flash **4MB detectada**; aún falta validar polaridad, circuitos, energía, BLE/Wi-Fi concurrentes, pinout y UX en dispositivo.
8. No se acepta código nativo remoto; laboratorio solo ejecuta acciones declarativas permitidas.

## Próximo trabajo concreto
Primero validar placa/flasheo y comunicaciones reales, después vinculación segura oficial/PoP,
TLS LAN/autorización, módulos de sensores, OTA firmado con rollback y QA de release.
Gestión: issues #10 y #12-#15. Cada cierre requiere evidencia real, no simulaciones.

**Estado:** INTEGRATED_LAB_V2 · NOT_PRODUCTION_READY
