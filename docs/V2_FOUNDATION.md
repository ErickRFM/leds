# F0/F1 — Auditoría de fundación modular

Implementado:
- core/PinManager.h: lista conservadora GPIO ESP32 clásico y propietario exclusivo.
- core/ModuleRegistry.h: registro acotado, inicio, tick, parada, rollback.
- modules/LedRgbModule.h: posesión GPIO27/25/26, PWM y tick.
- esp32_rgb.ino mantiene parser, UUID y formato BLE RGB V1.
- Host tests cubren GPIO prohibidos, conflictos, rollback y ciclo de vida.
- CI ejecuta tests host, Android y compilación ESP32.

Limitaciones y seguridad:
- Los módulos C++ comparten memoria y privilegios; no hay sandbox.
- BLE V1 sigue sin autenticación. No exponerlo fuera del laboratorio.
- No existe todavía interfaz Wi-Fi, control GPIO Android ni experimentos ejecutables.
- No conocemos flash/capacidad ni modelo físico exacto del ESP32.

Pruebas físicas pendientes:
- Arranque LED apagado, R27/G25/B26, resistencias y polaridad.
- SET/GET/ON/OFF/FX y lectura STATE desde Android.
- Desconexión y reconexión BLE, efectos y estado sincronizado.
- Confirmar placa, flash, memoria y comportamiento con Wi-Fi coexistente.

F2 debe asegurar identidad/vinculación antes de ofrecer Wi-Fi provisioning.
F3 compartirá estado único, autorización central y request IDs.
F4 deberá pasar por PinManager y controlar estados seguros al detenerse.
