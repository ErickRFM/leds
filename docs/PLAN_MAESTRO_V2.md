# ESP32 Control Center V2 — Plan de ejecución

## Base estable
- RGB V1.1: 5effcbccc8f49222120c6fb0bb8c2c51f7ddc874
- Respaldo: baseline/rgb-controller-v1.1
- GPIO RGB actuales: rojo 27, verde 25, azul 26.
- Modelo ESP32, flash y polaridad: pendientes de confirmación física.

## Fases y criterios
| Fase | Objetivo | Estado | Puerta de salida |
| --- | --- | --- | --- |
| F0 | Respaldo, mapa GPIO y auditoría | Parcial | Baseline y criterios documentados |
| F1 | Core de módulos, PinManager y LED | En desarrollo | CI firmware + Android + pruebas host |
| F2 | Vinculación segura y aprovisionamiento Wi-Fi por BLE | Pendiente | Contraseñas protegidas y prueba física |
| F3 | HTTP/WebSocket local y estado único | Pendiente | Autenticación, reconexión e idempotencia |
| F4 | GPIO + control Android | Pendiente | Exclusividad de recursos, restricciones y estado seguro |
| F5 | LED RGB integrado en ambos transportes | Pendiente | Regresiones V1 BLE y control Wi-Fi |
| F6 | Sensores, ADC y telemetría | Pendiente | Valores reales y errores recuperables |
| F7 | Laboratorio declarativo | Pendiente | Límites, cancelación y liberación de recursos |
| F8 | OTA A/B y rollback | Pendiente | Firma/versión y recuperación demostrada |
| F9 | UX, accesibilidad, QA y release | Pendiente | Pruebas físicas y CI completos |

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
- arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32_rgb
- Android: gradle testDebugUnitTest lintDebug assembleDebug
