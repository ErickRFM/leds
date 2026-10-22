# Compi BLE — Corrección de `ERR bad_code` (V24)

## Causa raíz reproducible por revisión de código

`OwnerEnrollmentSession::generateOneTimeCode` genera 8 caracteres hexadecimales en minúsculas (`a–f`), mientras que la pantalla `DevicesScreen` convierte el código a MAYÚSCULAS. `OwnerEnrollment::constantTimeEquals` comparaba los caracteres de forma sensible a mayúsculas. Resultado: un código físico correcto con `a–f` se rechazaba como `ERR bad_code`. Los códigos compuestos solo de dígitos no disparaban el bug.

## Corrección coordinada

- Firmware `OwnerEnrollment.h`: compara el código físico de **exactamente 8 dígitos hex** de forma insensible a mayúsculas, manteniendo comprobación de contenido, ventana física, prueba criptográfica de sesión y comparación de todos los nibbles.
- Android `OwnerEnrollmentClient.kt`: envía `PAIR` con código hexadecimal canónico en minúsculas, aunque el usuario lo vea en mayúsculas.
- Android `BleManager.kt`: normaliza `RESET`, y presenta explicaciones accionables de `bad_code`, `not_in_window`, `window_expired` y estado del propietario sin debilitar los controles.
- `DevicesScreen.kt`: no borra el código antes de recibir confirmación; lo elimina al autorizar, resetear correctamente o desconectar, y marca el campo en error cuando el dispositivo rechaza el intento.
- Pruebas firmware de máquina de estados y protocolo; pruebas JVM Android de formateo, errores y protección ante código incorrecto.

## Prueba física que falta

1. Construir **firmware ESP32 y APK Android de este mismo SHA**; instalar la APK y flashear el firmware con las precauciones vigentes del proyecto. Un firmware anterior al cambio sigue rechazando `DEADBEEF` si la app anterior lo envía en mayúsculas.
2. Abrir USB Serial Monitor a 115200 baudios. Con el ESP32 ya encendido, mantener BOOT 3 segundos (sin reiniciar).
3. Confirmar `PHYSICAL PAIRING WINDOW open. One-time code (local USB only): XXXXXXXX` y que sea una ventana nueva. Si aparece `PHYSICAL RECOVERY WINDOW`, hay un propietario anterior; **NO hacer RESET** salvo recuperación intencional.
4. En `Conexiones → Bluetooth`, conectar, pegar el código exacto y pulsar `Autorizar propietario` antes de 120 segundos. Probar tanto códigos con letras como solo números en sucesivas ventanas físicas.
5. Observar `OK`, `CHALLENGE`, `AUTH`, y finalmente estado `Authorized` en Android. Verificar que un código incorrecto sigue fallando y no modifica el propietario.
6. Desconectar y reconectar: comprobar revalidación de sesión y conservación segura de propietario.
7. Si se desea probar `RESET`, hacerlo **solo** en un dispositivo de laboratorio y con consentimiento expreso: borra el propietario de la placa.

**Limitación:** la ejecución de tests automatizados no valida el tiempo físico del botón BOOT, NVS real, cifrado, bonding BLE Android ni alimentación del sensor. Reportar como `PENDING_HARDWARE_VALIDATION` hasta completar pruebas físicas.

## Preparación para luz, audio y RGB según temperatura

- Mantener sensores como módulos con ID, estado, lectura válida y reserva GPIO en firmware; no inferir presencia por catálogo.
- El DHT11 en GPIO16 ya registró lecturas válidas; `bit_timeout` intermitente requiere revisar cableado, pull-up y muestreo físico antes de cualquier automatización RGB.
- **Sensor de luz:** pendiente del modelo real (LDR ADC1 vs BH1750 I²C). No elegir pin/driver hasta conocerlo; `PinManager` es autoridad.
- **Audio:** pendiente de elegir DFPlayer (UART/microSD) o MAX98357A (I²S). La bocina necesita etapa amplificadora compatible.
- **RGB ambiente:** usar únicamente temperatura `fresh/valid`, histéresis, prioridades manual/automático y un fallback seguro cuando el sensor falle.
- Trabajar la integración de componentes en PRs posteriores; este parche no altera el catálogo, GPIO ni firmware de DHT.

## Archivos de referencia

- `firmware/esp32_rgb/core/OwnerEnrollment.h`
- `android/app/src/main/java/com/rgbcontroller/core/ble/enrollment/OwnerEnrollmentClient.kt`
- `android/app/src/main/java/com/rgbcontroller/core/ble/enrollment/OwnerEnrollmentErrorMessage.kt`
- `android/app/src/main/java/com/rgbcontroller/core/ble/BleManager.kt`
- `android/app/src/main/java/com/rgbcontroller/feature/devices/DevicesScreen.kt`
