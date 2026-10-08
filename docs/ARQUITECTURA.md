# Arquitectura de referencia

## Capas
```text
App Android
 ├── feature/devices     Scan/connect/forget
 ├── feature/control     HSV wheel, sliders, power
 ├── feature/scenes      Presets/effects (posterior)
 ├── core/model          DeviceId, RGB, Effect, ConnectionState
 ├── core/data           Preferencias y favoritos (DataStore)
 └── core/ble            Android BLE GATT + cola de operaciones
                 ⇅ BLE GATT
ESP32
 ├── ble/                Advertising, servicio, características, callbacks
 ├── protocol/           Parser y validaciones
 ├── lighting/           Color state, LEDC PWM, conversión por tipo de LED
 ├── effects/            Bucle no bloqueante (fase posterior)
 └── main                Setup/loop y ciclo de vida
                 ↓ GPIO 27/25/26
                 LED RGB
```
La primera versión del firmware concentra lógica en `esp32_rgb.ino` para facilitar flasheo. Extraer módulos cuando parser/efectos crezcan y existan tests.

## BLE Android
- `BluetoothLeScanner` con filtro del UUID anunciado, timeout de búsqueda y Bluetooth activado.
- `BluetoothGattCallback`: máquina de estados explícita, inicialización tras servicios descubiertos.
- API 31+ permisos en runtime: `BLUETOOTH_SCAN` y `BLUETOOTH_CONNECT`; ≤30 requiere tratamiento de ubicación para scan según versión; revisar manifest maxSdkVersion.
- Llamadas GATT secuenciales: read, write y descriptor CCCD; no operaciones simultáneas.
- Suscripción a notificaciones de estado y lectura al reconectar para convergencia real.
- Escritura con respuesta. Para Android API 33+ usar overload de `writeCharacteristic` con `byte[]` y `writeType` y el método antiguo encapsulado para versiones previas.
- `StateFlow` expone estados; UI no contiene llamadas Bluetooth.
- Throttle/coalescing de arrastre a 20 Hz máximo, aplicar último comando y no acumular backlog infinito.
- Sin servicio foreground persistente en MVP: conectividad activa principalmente mientras la app está en primer plano. Cuando se sale, los efectos corren solos en el ESP32.

## Estados BLE
`Idle -> Scanning -> Connecting -> Discovering -> Subscribing -> Ready`
Fallos o Bluetooth apagado -> `Disconnected/Error`, cerrar GATT y esperar nuevo intento (backoff acotado).
No pintar la UI como conectado antes de confirmar servicios y CCCD.

## Seguridad
Un servicio BLE abierto permite a teléfonos cercanos escribir. La fase fundacional es solo laboratorio; la versión entregable requiere vínculo/autorización, bloqueo de comandos sin autorizar y flujo seguro de restablecimiento/pairing. No considerar la mera proximidad una autenticación. Evitar logs con identificadores personales.

## Control RGB
- Definir color lógico `red/green/blue` 0..255 y `brightness` 0..255.
- Potencia lógica por canal `round(color * brightness / 255)`.
- Si ánodo común: `duty = 255 - potencia`; cátodo común: `duty = potencia`.
- Futuro: curva gamma documentada y calibración perceptual, sin romper estado lógico.
- PWM 5 kHz/8 bits como punto de partida, sujeto a validación visual/física.

## Fuera de alcance inicial
Sin servidor HTTP, usuarios, contraseñas cloud, Expo, base de datos remota, sincronización vía Internet ni OTA hasta demostrar necesidad.
