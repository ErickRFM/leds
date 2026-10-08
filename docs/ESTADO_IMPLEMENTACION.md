# Auditoría de implementación RGB V1

Se añadió una rama de integración con los siguientes cambios:
- Android: conexión BLE secuencial, CCCD confirmada, lectura de estado inicial y reconexión acotada.
- App: rueda HSV, sliders RGB/brightness, ON/OFF, presets y favoritos DataStore, efectos, ajustes, navegación y estado real.
- ESP32: código Arduino-ESP32 3.x, comandos SET/GET/ON/OFF y FX (RAINBOW/BREATHE/FADE/STOP) no bloqueantes.
- CI: Gradle 8.7 con setup-gradle y compilación del ESP32 en job independiente.
- Script Windows: `android/build-debug.ps1` para descargar Gradle y compilar APK.

**Pendiente de comprobación externa**:
1. Las ejecuciones de GitHub Actions no han iniciado pasos en runners (jobs terminan en segundos con `steps=[]` y `runner_id=0`). Investigar en GitHub Actions la causa de infraestructura/billing/runner antes de afirmar que CI pasa.
2. No existe APK generado/verificado desde este entorno hasta completar una compilación real con Android SDK.
3. La placa ESP32/LED físico y teléfono Android no están conectados a este entorno; BLE y polaridad de hardware siguen sin probar físicamente.
4. El firmware aún no autentica ni autoriza clientes BLE. Prototipo solo para laboratorio.

**Checklist para laboratorio**
- [ ] Ejecutar `python scripts/check-contracts.py`.
- [ ] Compilar `arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32_rgb`.
- [ ] Compilar Android con `android/build-debug.ps1` y verificar SHA256 del APK.
- [ ] Flashear firmware, monitor serie 115200 y confirmar el advert `RGB-ESP32`.
- [ ] Con nRF Connect activar notify y escribir `SET,255,0,0,255`.
- [ ] Probar OFF/ON/GET; FX RAINBOW/BREATHE/FADE/STOP y desconexión/reconexión.
- [ ] Instalar APK en teléfono, confirmar permisos y reproducir las mismas pruebas.
- [ ] Añadir evidencias a PR y cerrar solo con CI verde.

No confundir presencia del código con pruebas de aceptación superadas.
