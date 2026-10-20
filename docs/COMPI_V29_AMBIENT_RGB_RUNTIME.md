# Compi V29 — RGB autónomo con temperatura + luz

## Qué se integra
- Reutiliza **`LedRgbModule`**, `RgbCommandCodec` y el PWM existente: nunca escribe GPIO desde el driver LDR ni desde callbacks BLE.
- `EnvironmentalRgbPolicy` (PR #171) decide banda de temperatura con histéresis de 0.5°C, color, brillo limitado y fallback para LDR sin calibrar.
- `esp32_rgb.ino` evalúa la política cada 1000 ms en el único loop Arduino, usando solo temperatura `Fresh/valid` con edad máxima de 6000 ms, y luz `Fresh/valid` opcional. Sin lectura válida de DHT **no modifica** el RGB.
- Utiliza el comando compartido `SET,R,G,B,brightness` **sin persistir temporalmente** en NVS. La última elección manual sigue siendo persistible como antes.
- `LocalWifiControl` expone rutas locales con la autorización SoftAP existente:
  - `GET /v2/environment/rgb/status`
  - `POST /v2/environment/rgb/start`
  - `POST /v2/environment/rgb/stop`
  El modo es opt-in, RAM-only y queda OFF tras reinicio.
- Inicio rechaza scripts, automatización anterior, experimento o lease manual simultáneo. Las operaciones de scripts y rewire también rechazan el modo ambiental activo.
- Un comando RGB manual válido recibido por BLE autenticado o Wi-Fi autorizado **desactiva inmediatamente** el modo ambiental; `GET` no lo cancela. La pantalla refresca el estado real cada pocos segundos.
- Android usa los servicios Wi-Fi autorizados existentes para activar/desactivar desde LED. No confunde conexión BLE cifrada con autorización HTTP de sensores.

## Comportamiento
- Menos de 20°C: azul. 20–26°C: verde. 26–30°C: amarillo. ≥30°C: rojo.
- 0% luz relativa calibrada → brillo mayor, 100% → menor; siempre limitado.
- Sin LDR o sin calibrar → brillo de reserva limitado, **no** valor ficticio.
- DHT stale, invalid, no response o temperatura fuera de rango → suspender cambios; se conserva el estado seguro visible actual.
- Detener modo deja el último estado de LED, hasta que un control manual lo reemplace; nunca se afirma que se restauró una preferencia anterior.
- No se guarda el modo en NVS ni se asegura OTA automático.

## Validación requerida
1. Firmware + APK de mismo SHA.
2. LED RGB GPIO27/25/26 y DHT11 GPIO16 probados físicamente.
3. Wi-Fi SoftAP y propietario BLE deben conservar su seguridad.
4. Arrancar modo con temperatura fresh; comprobar color y readback lógico.
5. Cubrir/aclarar LDR calibrada: comprobar que cambia brillo y no temperatura.
6. Forzar `bit_timeout`, desconectar LDR, quitar Wi-Fi y reiniciar; sin parpadeo indebido ni bloqueo del firmware.
7. Pulsar color manual por Wi-Fi o BLE autenticado: debe terminar modo ambiental y conservar el control manual.
8. Activar script y automatización heredada: no deben disputar PWM con modo ambiental.
9. Medir consumo y tiempos de loop; el DHT y BLE no deben degradarse por escritura PWM.
10. Cierre QA físico `PENDING_HARDWARE_VALIDATION` hasta recibir trazas de placa/teléfono reales.

## Alcance / pendientes
- Los umbrales están inicialmente en valores seguros del policy; el editor completo de configuración por usuario pertenece a V31.
- Audio/TTS V27 es independiente; DFPlayer físico V28 requiere hardware adicional y protección de altavoz 8Ω/0.5W.
