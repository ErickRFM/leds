# V4 — Configurar Wi-Fi sin Monitor Serie (modo físico y credenciales individuales)

**Estado:** flujo personalizado en firmware, compilación automatizada; Android interno aún sin integrar. **No es una declaración de emparejamiento RGB seguro.**

## Arquitectura de un solo firmware sin duplicar controladores BLE

El firmware habitual conserva el servicio RGB BLE, el LED y SoftAP de laboratorio.
**Solo si el archivo privado** `firmware/esp32_rgb/config/FactoryProvisioning.h`
existe al compilar, habilita el botón físico de configuración:

1. Una vez que la placa haya arrancado, mantener **BOOT/GPIO0** presionado 5 segundos (no mantenerlo durante el encendido: podría entrar al bootloader ROM).
2. El ESP32 guarda una señal de reinicio de un solo uso y se reinicia.
3. Antes de iniciar el BLE RGB, activa **WiFiProv BLE Security 1** con un **PoP de 128 bits único por placa**.
4. Desde un cliente Espressif Provisioning compatible, escanear el QR generado offline y configurar red **2.4 GHz**.
5. Tras confirmar credenciales y conexión STA, vuelve a reiniciar en modo RGB normal. Si no se completa, caduca a los 5 minutos y regresa al modo normal.
6. Nunca se inicia simultáneamente WiFiProv BLE y `BLEDevice::init` RGB en un mismo arranque.

Solo la acción física permite reconfigurar Wi-Fi; al hacerlo se restablece el estado de provisionamiento STA. En placas ya utilizadas, el gesto físico **rota exclusivamente** las credenciales `rgb-lab-v2` a las de la etiqueta privada: los clientes antiguos deben reconectarse. No se cambian silenciosamente al flashear. En placas nuevas, se inicializan a partir de la etiqueta privada. Las preferencias `cc-v4-rgb` y `cc-v4-id` no se borran. Se requiere comprobarlo físicamente.

## Preparar una placa única — una sola vez, desde tu computadora de desarrollo

```powershell
git switch main
git pull --ff-only origin main

python .\scripts\create-v4-provisioning-kit.py --out .\local-validation\factory-kits\mi-esp32
New-Item -ItemType Directory -Force .\firmware\esp32_rgb\config
Copy-Item .\local-validation\factory-kits\mi-esp32\FactoryProvisioning.h .\firmware\esp32_rgb\config\FactoryProvisioning.h

powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1 -SkipAndroid
```

Esto genera **tres archivos privados por placa**: `FactoryProvisioning.h` para compilar, `provisioning-qr.json` para WiFiProv y `lab-access-private.json` para conectar al SoftAP del laboratorio y proporcionar la clave de control desde la app, sin Monitor Serie. El segundo JSON es un secreto distinto y NO debe mostrarse como QR de WiFiProv. No los subas a GitHub, chats, servicios de QR por Internet ni capturas públicas. El generador no imprime la clave, rechaza sobrescribir un kit y no toca el ESP32.

Luego, con respaldo, cableado correcto, puerto USB verificado y revisión de partición, la grabación solo ocurre usando explícitamente `-UploadFirmware -Port COMx`. El verificador por defecto **solo compila**.

Una misma imagen compilada personalizada no debe distribuirse a más de una placa porque compartiría la misma prueba de posesión. El binario normal de CI **NO** contiene secretos personales ni habilita este modo; el CI genera una imagen personalizada con datos de prueba, la valida y **NO la publica**.

## Cliente Android

Este primer incremento utiliza temporalmente el cliente Espressif compatible con Security 1 por QR, y permite conocer la contraseña del SoftAP y clave del LAB desde la **etiqueta privada** sin Monitor Serie. **La app Kotlin integrada seguirá mostrando que la vinculación segura V4 del controlador RGB está pendiente**; la configuración segura de Wi-Fi *no* autoriza GATT WRITE, no entrega credenciales del SoftAP del laboratorio y no habilita LAN HTTP. El siguiente PR debe conectar la biblioteca oficial con el flujo Compose sin modificar el motor RGB.

## Pruebas reales obligatorias antes de declarar completado

- Kit privado por ESP32, QR y PoP coinciden (sin compartir entre dispositivos).
- Primero sin personalización: BLE RGB original, SoftAP V3 y LED sin diferencias.
- Personalizado: botón BOOT 5 s **después de iniciar**, reinicio; ESP32 emite `PROV_<ID>`; servicio RGB no está activo durante onboarding.
- Un PoP erróneo **no** entrega credenciales; PoP correcto configura una red WPA2 2.4 GHz. Confirmar que nunca aparecen SSID password/PoP en logs estándar.
- Volver a arrancar: RGB BLE funciona, NVS de ID/estado persistente, Wi-Fi STA configurado y SoftAP V3 como antes.
- Tiempo de espera 5 min sin completar retorna a RGB; modo físico se puede volver a invocar.
- Wi-Fi incorrecto, corte de energía, botón GPIO0, resistencia/cableado, recuperación USB y binario 4 MB/Arduino core 3.0.7 en placa real.
- Riesgo conocido: Security 1 PoP único no sustituye una auditoría de extracción de flash ni la autorización del control RGB. No habilitar versión pública.

## Referencias

- Espressif WiFiProv: https://github.com/espressif/arduino-esp32/tree/3.0.7/libraries/WiFiProv
- Android Unified Provisioning: https://github.com/espressif/esp-idf-provisioning-android
- Contratos existentes: `docs/V4_FEASIBILITY_AND_IDENTITY.md`, `docs/V4_BLE_PUBLIC_ID.md`

**Gate real:** compilación CI del firmware personalizado y normal, pruebas de contrato y verificación en ESP32 + teléfono. Este flujo no puede darse por aceptado sin ensayos físicos.

## Distinguir redes y secretos

- `provisioning-qr.json`: prueba de posesión para WiFiProv BLE Security 1, **no** es la contraseña del SoftAP.
- `lab-access-private.json`: contraseña del Wi-Fi `ESP32-Control-Lab` y clave `deviceKey` de 16 caracteres que se introduce en `Ajustes → Centro ESP32` de la app. No requiere Monitor Serie en una placa personalizada tras la primera configuración o después del gesto físico que sincroniza las credenciales.
- La conexión al router doméstico creada por WiFiProv se guarda en STA NVS; el servicio HTTP del laboratorio permanece aislado del interfaz LAN y **no** adquiere TLS ni permisos por el simple hecho de provisionar.
- Si personalizas una placa que ya tenía contraseña V3, puedes seguir usando esa contraseña **hasta** que pulses BOOT 5 segundos (después del arranque), lo que inicia explícitamente la migración a las claves de la etiqueta privada. Este cambio exige reconectar el móvil con la nueva contraseña.
