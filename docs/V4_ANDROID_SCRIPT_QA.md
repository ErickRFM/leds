# V4 Android · Ejecutar y detener RGB Script en firmware

## Flujo implementado
1. Con APK **debug**, abrir `Código` y seleccionar modo **Laboratorio**.
2. Conectarse por Wi-Fi a `ESP32-Control-Lab`, aceptar mantenerlo sin Internet e introducir clave temporal de 16 caracteres del monitor serial USB.
3. Pegar/escribir script:
   ```text
   RGB 255 0 0
   WAIT 500
   RGB 0 0 255
   WAIT 500
   OFF
   STOP
   ```
4. Revisar preflight (máximo 512 bytes, 32 instrucciones, 20 s); pulsar **Ejecutar**.
5. **Consultar estado real** (/v2/script/status), **Detener** (/v2/script/stop) o cambiar LED manualmente.
6. Confirmar en el LED físico. Sólo cambiar estado por solicitud explícita.

## Restricciones
- Requiere firmware con `RgbScriptRuntime` y endpoints `/v2/script/*` (PR #51); firmware anterior devolverá 404.
- Sólo comandos RGB/WAIT/ON/OFF/STOP; Arduino `setup/loop` en modo C++ se edita, pero NO se ejecuta como script ni se carga vía OTA.
- Clave solo variable `remember`, no DataStore, backups, logs ni guardar estado.
- Cliente usa la ruta Wi-Fi SoftAP explícita existente, sin celular; aplicación release sigue bloqueada.
- El preflight Android es UX: firmware vuelve a validar texto. Sin seguridad BLE/OTA validada no hay distribución pública.

## Gates
- `testDebugUnitTest`, `lintDebug`, `assembleDebug`; parser Kotlin y C++ deben coincidir en conjunto de casos aceptados.
- Hardware: Wi-Fi sin Internet, latencia, cancelar a mitad de WAIT, pulsar BLE ON/OFF durante script, cambio Wi-Fi directo, pérdida de señal.
- Confirmar seguridad HTTP SoftAP y claves antes de producir controles de firmware peligroso.
