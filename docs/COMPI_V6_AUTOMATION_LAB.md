# V6 — Automatización RGB de intervalo (laboratorio)

Integración incremental con la V3. El motor `RgbAutomationClock` ya está probado a nivel host y ahora se conecta al tick del SoftAP existente. **No es una automatización persistente ni un motor de reglas general.** Las ejecuciones se pierden al reiniciar el ESP32.

## Endpoints (HTTP exclusivo a ESP32-Control-Lab)
Requieren conexión a la red SoftAP local y cabecera `X-Device-Key` existente. No exponer por LAN/Internet.

| Método | Ruta | Campos | Resultado |
|---|---|---|---|
| GET | `/v2/automation/status` | — | `active`, `intervalMs` |
| POST | `/v2/automation/start` | `intervalSeconds=1..3600`, `startOn=0|1` | HTTP 202 RUNNING |
| POST | `/v2/automation/stop` | — | HTTP 200 STOPPED |

La automatización alterna ON/OFF por intervalos de 1 segundo a 1 hora sin ejecutar C++ de usuario. Usa el estado RGB existente y el parser de comandos compartido, sin una segunda implementación de PWM. Un comando RGB BLE o Wi-Fi manual cancela automáticamente la automatización. Un script ya iniciado impide comenzar la automatización y viceversa. Aceptar un comando START no modifica inmediatamente el LED: la primera acción ocurre después del intervalo configurado.

## Prueba manual necesaria
1. Conectarse al SoftAP protegido y confirmar que solicitudes sin credenciales son rechazadas.
2. Ejecutar `POST /v2/automation/start` con `intervalSeconds=2` y `startOn=1`.
3. Leer estado, esperar dos segundos y comprobar físicamente la transición a ON; dos segundos más y comprobar OFF.
4. Ejecutar control RGB manual por BLE y confirmar que `/v2/automation/status` regresa `active=false`.
5. Repetir iniciando desde Wi-Fi y comprobar que otro script no pueda ejecutarse simultáneamente.
6. Probar reconexión, apagado y reinicio: la automatización no debe afirmarse persistente.
7. Verificar que el LED se encuentre conectado con resistencias y que no haya cargas alimentadas directamente por GPIO.

**Gate:** los tests host del reloj NO certifican que el firmware completo compile ni que el circuito físico sea seguro. Release público sigue bloqueado por falta de autorización BLE y QA.
