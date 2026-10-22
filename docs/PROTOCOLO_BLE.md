# Contrato Bluetooth Low Energy GATT — Compi (estado V18, laboratorio)

Nombre anunciado: `RGB-ESP32` (ESP32 clásico con Bluetooth LE).

| Entidad | UUID | Propiedades |
|---|---|---|
| Servicio | `4b9d0001-7a5e-4d4c-ae88-c8c70d3ab211` | Discover |
| Comando | `4b9d0002-7a5e-4d4c-ae88-c8c70d3ab211` | WRITE with response |
| Estado | `4b9d0003-7a5e-4d4c-ae88-c8c70d3ab211` | READ + NOTIFY, CCCD 0x2902 |
| Identidad pública V4 (opcional) | `4b9d0004-7a5e-4d4c-ae88-c8c70d3ab211` | READ únicamente; 16 caracteres ASCII hexadecimales, NO es secreto |
| GPIO V9 (opcional) | `4b9d0005-7a5e-4d4c-ae88-c8c70d3ab211` | READ únicamente. Mapa de reservas en firmware: no escribe ni autoriza GPIO |

Los comandos RGB clásicos se escriben como ASCII/UTF-8, sin nueva línea, hasta 48 bytes. Las tramas de enrolamiento V16 son más largas y se procesan por separado en el firmware.

## Comandos
- `SET,255,120,0,200` asigna R,G,B,brillo (0..255), enciende y detiene efecto.
- `OFF`: apaga temporalmente.
- `ON`: enciende; conserva mezcla y modo previo.
- `GET`: vuelve a publicar el estado.
- `FX,RAINBOW,120`: arcoíris; velocidad 10..1000.
- `FX,BREATHE,120`: respiración; velocidad 10..1000.
- `FX,FADE,120`: fundido de colores; velocidad 10..1000.
- `FX,STOP`: detiene cualquier efecto y vuelve a color estático.
- `PAIR <codigo> <ownerId> <ownerPubKeyHex>`: enrolamiento dentro de una ventana física abierta con BOOT; el código temporal se entrega exclusivamente por consola serie local.
- `BEGIN <clientNonceHex>`: inicia reto de sesión.
- `CHALLENGE <deviceNonceHex> <devicePubKeyHex>`: respuesta del ESP32 (notificación STATE).
- `AUTH <clientNonceHex> <counter> <proofHex>`: demuestra posesión de clave con contador monotónico. **No equivale al antiguo `AUTH <clave>`.**

## Respuesta READ / NOTIFY
`STATE,r,g,b,brillo,power,mode,speed`, donde power es 0 ó 1 y mode es NONE,RAINBOW,BREATHE,FADE.
Ejemplo: `STATE,255,120,0,200,1,RAINBOW,120`.

Los clientes pueden admitir el estado anterior de 6 campos, pero el firmware nuevo publica 8.
Para un comando inválido se notifica `ERR,INVALID_COMMAND`; no modifica el estado del LED.
El resultado de `AUTH` se notifica transitoriamente como `AUTH,OK` o `AUTH,ERR` sobre la misma característica STATE; el valor se restaura de inmediato al snapshot `STATE,...`.

## Sincronización
Android habilita notificaciones mediante CCCD y espera confirmación; después lee STATE. En V4 lee opcionalmente la característica de identidad pública antes de READY. En firmware anterior, que no la anuncia, mantiene compatibilidad RGB; nunca interpreta el ID como prueba de propiedad. Solo al finalizar lectura y validación del estado declara GATT Ready. Todas las operaciones GATT deben serializarse y el estado de la UI debe distinguir cambios locales de valores confirmados.

## Seguridad y limitaciones
Firmware **experimental de laboratorio**. El firmware implementa PAIR/BEGIN/CHALLENGE/AUTH y deniega comandos privilegiados sin sesión de propietario. **Integración pendiente P0:** `BleManager.kt` aún usa `RgbDeviceCommand.auth(ownerKey)` y no integra el cliente criptográfico `OwnerEnrollmentClient`; por tanto, la APK actual no completa el enrolamiento V16. Un enlace *Just Works* no proporciona protección MITM y no equivale a autorización de propietario. No se ha validado físicamente la autorización (PENDING_HARDWARE_VALIDATION).

Antes de distribución fuera de laboratorio: integrar el cliente V16 en Android, proteger cada comando con integridad y contador de sesión, verificar recuperación y revocación, y mejorar seguridad Wi-Fi/HTTP; no asumir que la cercanía protege el dispositivo.

El ESP32 ejecuta efectos autónomamente, aunque Android salga de primer plano. No hay nube; **sí existe un servidor HTTP local** del SoftAP de laboratorio (`LocalWifiControl.h`, puerto 80).

## Estado GPIO BLE V9 (informativo y opcional)

`PINS,18:F,19:H,21:F,22:F,23:F,25:R,26:R,27:R` en texto ASCII de menos de 96 bytes. Ocho pines con orden fijo; estados: F=libre en firmware, H/L=salida manual HIGH/LOW, E=experimento, R=RGB reservado, X=otro módulo. El firmware calcula el valor en su loop, evitando leer PinManager desde tareas BLE; Android solo realiza una lectura a la vez y da prioridad a escrituras RGB. No hay credenciales ni identificadores privados.

Un estado **libre** no comprueba que el pin esté correctamente cableado. Sin característica V9, el RGB BLE anterior debe seguir funcionando. **Quedan expresamente prohibidas** escrituras GATT GPIO/ADC/Wi-Fi/OTA por BLE V1 no autenticado; exigir enrolamiento criptográfico de propietario, revocación y anti-replay antes de agregar control privilegiado.
