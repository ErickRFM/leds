# ESP32 Control Center V2 — Plan de ejecución

## Base estable
- RGB V1.1: 5effcbccc8f49222120c6fb0bb8c2c51f7ddc874
- Respaldo: baseline/rgb-controller-v1.1
- GPIO RGB actuales: rojo 27, verde 25, azul 26.
- Modelo ESP32, flash y polaridad: pendientes de confirmación física.

## Fases y criterios
| Fase | Objetivo | Estado | Puerta de salida |
| --- | --- | --- | --- |
| F0 | Respaldo, mapa GPIO y auditoría | Parcial: falta placa física | Baseline y criterios documentados |
| F1 | Core de módulos, PinManager y LED | Integrada CI | CI firmware + Android + pruebas host |
| F2 | Vinculación segura y aprovisionamiento Wi-Fi por BLE | Parcial: SoftAP WPA2 laboratorio; BLE seguro pendiente | Contraseñas protegidas y prueba física |
| F3 | HTTP/WebSocket local y estado único | Parcial: HTTP SoftAP clave API; TLS/WebSocket LAN pendiente | Autenticación, reconexión e idempotencia |
| F4 | GPIO + control Android | Parcial: GPIO 18/19/21/22/23 laboratorio; PWM genérico pendiente | Exclusividad de recursos, restricciones y estado seguro |
| F5 | LED RGB integrado en ambos transportes | Parcial: BLE V1 + comandos RGB HTTP SoftAP; QA físico pendiente | Regresiones V1 BLE y control Wi-Fi |
| F6 | Sensores, ADC y telemetría | Parcial: lectura digital/ADC1 y diagnóstico; drivers/historial pendiente | Valores reales y errores recuperables |
| F7 | Laboratorio declarativo | Parcial: secuencia HIGH/LOW limitada y cancelación; editor avanzado pendiente | Límites, cancelación y liberación de recursos |
| F8 | OTA A/B y rollback | Parcial: inspección de slots; carga/firma/rollback pendiente | Firma/versión y recuperación demostrada |
| F9 | UX, accesibilidad, QA y release | Parcial: hub Compose debug, CI verde; QA físico y visual pendiente | Pruebas físicas y CI completos |

## Garantías del desarrollo
1. RGB V1 BLE se preserva (UUID, SET/GET/ON/OFF/FX/STATE).
2. El ESP32 es servidor local, sin nube ni cuentas obligatorias.
3. PinManager coordina módulos auditados, pero NO es aislamiento de memoria.
4. No se añaden comandos GPIO ni Wi-Fi sobre BLE sin autenticar.
5. Pines críticos y desconocidos se niegan por defecto; ADC2 no se usa con Wi-Fi.
6. OTA depende del tamaño flash real y requiere recuperación probada.
7. La compilación CI no equivale a control LED físico validado.

## Estrategia Git
- Primera PR: feat/esp32-control-center-v2-foundation (F0/F1).
- Próximas: secure provisioning, LAN transport, GPIO, lab runtime, OTA y QA.
- Cada PR documenta alcance, contrato, compatibilidad, pruebas, riesgos y reversión.
- No declarar V2 terminada antes de F2–F9 y hardware probado.

## QA automático F1
- python3 scripts/check-contracts.py
- g++ -std=c++17 -Wall -Wextra -Werror -pedantic tests/firmware_core_test.cpp -o /tmp/firmware_core_test
- /tmp/firmware_core_test
- arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs firmware/esp32_rgb
- Android: gradle testDebugUnitTest lintDebug assembleDebug

## Registro de PRs reales V2
- [#11](https://github.com/ErickRFM/leds/pull/11): F0/F1 y pruebas de núcleo.
- [#16](https://github.com/ErickRFM/leds/pull/16): API Wi-Fi SoftAP, GPIO, ADC y app Android debug.
- [#17](https://github.com/ErickRFM/leds/pull/17): inspección de particiones OTA y restauración Wi-Fi.
- [#18](https://github.com/ErickRFM/leds/pull/18): intérprete declarativo acotado y pruebas host.

Los bloqueos de producción siguen abiertos en [#12](https://github.com/ErickRFM/leds/issues/12), [#13](https://github.com/ErickRFM/leds/issues/13), [#14](https://github.com/ErickRFM/leds/issues/14) y [#15](https://github.com/ErickRFM/leds/issues/15). **No se completan automáticamente por CI**.
