# Compi V12 — Automatización RGB acotada (laboratorio)

**Integración nueva sobre V10**, basada en el motor host `RgbAutomationClock` y la idea original del PR #71. No incorpora ejecuciones ilimitadas de ese PR. **El PR original permanece intacto** para preservar trabajo; cualquier cierre/reemplazo debe explicitarse.

## Contrato HTTP en ESP32-Control-Lab

Solo habilitado dentro del SoftAP con `X-Device-Key` autorizado, sin acceso por STA/celular, sin firma OTA y sin control GPIO BLE V1.

| Endpoint | Uso | Parámetros |
|---|---|---|
| GET `/v2/automation/status` | Lectura | JSON `version=1,active,intervalMs,remainingTransitions` |
| POST `/v2/automation/start` | Comienza temporalmente | `intervalSeconds` 1–60; `cycles` 1–10; `startOn` 0/1 |
| POST `/v2/automation/stop` | Detiene | Sin otros parámetros |

La duración está acotada a **20 minutos**, 20 cambios de estado y no persiste al reiniciar. Al llegar al último cambio, envía OFF. La primera transición ocurre después del intervalo configurado; no hay efecto instantáneo al pulsar Iniciar.

Un comando manual **válido** de LED RGB (BLE o HTTP) detiene script y automatización. GET y comandos RGB malformados no deben detener nada. Un RGB Script o experimento GPIO activo impide iniciar automatización; mientras el reloj está activo, se rechaza iniciar un script. Las escrituras de GPIO manual pueden coexistir sin alterar el LED RGB reservado; comenzar una nueva automatización se rechaza si hay salidas manuales activas para evitar ambigüedad de banco.

## Riesgos y seguridad

- El receptor BLE V1 **no está autenticado por propietario**. NO habilitar métodos de automatización por GATT ni asumir que una MAC prueba identidad.
- El uso de PWM desde el ESP32 no demuestra circuitos o resistencias correctos.
- Un comando de automatización es un estado lógico; verificar físicamente el LED.
- El esquema actual de persistencia RGB puede escribir una instantánea NVS después de cambios prolongados. Los límites por ciclos y duración reducen exposición, pero conviene medir desgaste y tasa de escritura en QA.
- BLE, Wi-Fi y loop principal comparten el motor RGB existente; no agregar otro controlador PWM.

## Pruebas requeridas antes de integración sin bandera de laboratorio

1. Pruebas C++ host del reloj, límites Android y contrato estático Python.
2. Compilación Arduino CLI ESP32 4 MB con `min_spiffs` y presupuesto de flash.
3. SoftAP con clave errónea → HTTP 403; en Wi-Fi de router debe negar acceso.
4. `intervalSeconds=2, cycles=3, startOn=1` → 6 transiciones, termina en OFF.
5. Un comando inválido BLE o Wi-Fi durante la automatización → continúa.
6. `GET` BLE durante la automatización → continúa.
7. Comando manual válido BLE `SET` o `FX,STOP` → se detiene inmediatamente.
8. Script RGB activo → empezar automatización responde 409, sin interferir en script.
9. Automatización activa → ejecutar RGB Script responde 409.
10. Detener desde Android → se detiene sin desconectar BLE, el estado restante queda visible.
11. Reboot ESP32 → automatización ya no está activa.
12. Prueba física con LED común ánodo/cátodo correcto, resistencias individuales y monitor 115200; revisar NVS.
13. Duración máxima: 60 segundos x 10 ciclos (20 minutos) y abortos por desconexión Wi-Fi (el ESP32 puede seguir activo hasta completar límites aun sin el teléfono).

**Gate:** compilar y probar en CI habilita solo un APK/firmware de laboratorio. No publicar firmware para equipos de terceros hasta completar enrolamiento BLE, rollback seguro, estrés y seguridad física.

## Integración Git

Esta rama parte del commit V10 integrado en `main` y reemplaza el borrador antiguo del PR #71 solo a nivel de propuesta técnica. Mantener el PR nuevo separado hasta CI y QA física. Requiere revisión de conflictos contra V10 y documentación de release bloqueada.
