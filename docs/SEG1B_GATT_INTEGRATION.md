# SEG.1b-2b · Integración real del canal de propietario (BLE/NVS)

## Qué se hizo

- **Controlador puro `OwnerEnrollmentSession`** (`firmware/esp32_rgb/core/`):
  compone framing (`OwnerEnrollmentFrames`) → máquina de estados
  (`OwnerEnrollment`) → cripto (`SessionCrypto` + `SessionKeySchedule`) →
  persistencia (`OwnerEnrollmentStore`). Sin asignaciones dinámicas y sin
  dependencias de Arduino, para poder probarlo en host.
- **Almacén NVS `NvsOwnerEnrollmentStore`**: guarda identidad del dispositivo
  (clave X25519), propietario, último contador y estado revocado como **un único
  blob** con `magic` + `version`. Un registro desconocido/corrupto **falla en
  cerrado** (no se confía en estado a medias). La protección en reposo es el
  cifrado de flash de ESP32.
- **Cableado en `esp32_rgb.ino`**:
  - Los frames de enrolamiento (`PAIR/BEGIN/AUTH/REVOKE/RECOVER/RESET`) viajan
    por la **característica WRITE existente**; **no se añade ningún UUID**.
  - Las respuestas (`CHALLENGE …`, `OK`, `ERR …`) se publican transitoriamente
    por la característica STATE existente.
  - La autoridad de privilegios BLE es **la sesión de propietario verificada por
    el firmware**; el estado de enlace (cifrado/bonded) ya **no** autoriza.
    `bleAuthState` se deriva de `ownerSession.authenticated()`.
  - **Ventana física**: mantener pulsado BOOT ~3 s abre una ventana temporizada
    (emparejamiento o, si ya hay propietario, recuperación). El código de un solo
    uso se imprime **solo en el puerto serie USB local** (canal físico); nunca
    por BLE, nunca en exportaciones y no se registra en ningún otro sitio.
  - Se **elimina el camino legacy `AUTH <clave>`** como vía de autorización: la
    autorización depende ahora de la prueba criptográfica por sesión.
  - `OwnerEnrollmentSession::begin()` que no puede cargar/guardar → el firmware
    **detiene el control privilegiado** (fail-closed).

## Decisión de protocolo corregida

`SessionKeySchedule` liga ahora los **nonces de 16 bytes (32 hex)** del
enrolamiento (`kNonceBytes = 16`), independientes del nonce AES-GCM de 12 bytes.
Esto elimina la ambigüedad previa y hace coherente el tamaño del nonce con
`OwnerEnrollment::kNonceHexMax`.

Además, la respuesta del reto pasa a ser
`CHALLENGE <deviceNonce> <devicePubKeyHex>`: sin la clave pública X25519 del
tablero el cliente no puede derivar `ECDH(ownerPriv, devicePub)` ni producir el
proof. Es un cambio mínimo que mantiene el canal existente (sin UUIDs nuevos).

## Cliente Android (SEG.1b-2c)

- `core/security/SessionCrypto.kt`, `BouncyCastleSessionCrypto.kt`,
  `SessionKeySchedule.kt`, `Hex.kt`: espejo en Kotlin de las primitivas y del
  KDF del firmware. BouncyCastle aporta X25519 en todas las API soportadas
  (API 26+), ya que el JCA `XDH` solo existe desde API 33.
- `core/ble/enrollment/EnrollmentReply.kt` + `OwnerEnrollmentClient.kt`: cliente
  de protocolo puro que construye `PAIR/BEGIN/AUTH`, parsea `OK/ERR/CHALLENGE`
  (estricto) y deriva el proof. El contador es estrictamente creciente.
- `OwnerEnrollmentClientTest.kt`: handshake completo contra un tablero simulado
  con X25519/HMAC reales, anti-replay del contador y parseo fail-closed.

## Verificación automática

- `tests/owner_enrollment_session_test.cpp`: primer arranque, enrolamiento,
  autenticación, **persistencia y anti-replay tras reinicio**, revocación +
  recuperación, y **fallo en cerrado** de un almacén que no puede leer/escribir.
  Ahora comprueba además que `CHALLENGE` lleva la clave pública del tablero.
- `tests/owner_enrollment_frames_test.cpp` y `tests/security_adversarial_test.cpp`
  actualizados al `CHALLENGE` con clave pública.
- `tests/session_key_schedule_test.cpp` actualizado al nonce de 16 bytes.
- Android: `OwnerEnrollmentClientTest` (JVM, BouncyCastle) dentro de
  `testDebugUnitTest` (243 tests, 0 fallos).
- El firmware compila para `esp32:esp32:esp32:PartitionScheme=min_spiffs`.

## PENDING_HARDWARE_VALIDATION

- Ventana física BOOT real (rebote, tiempos) y lectura del código por USB.
- Persistencia real en NVS entre reinicios eléctricos y con cifrado de flash.
- Handshake X25519/AES-GCM real contra un cliente (Android) por GATT.
- Transición BLE ↔ Wi-Fi con caída/reconexión real.

## Siguiente sub-bloque

- **Cableado en `BleManager` + persistencia Keystore** (SEG.1b-2d): enviar los
  frames por la característica WRITE existente, guardar el keypair del
  propietario con Keystore y derivar `BleSecurityState` solo de las respuestas
  del firmware. Hasta entonces, `BleManager` conserva el camino legacy que
  **falla en cerrado** (el firmware ya no reconoce `AUTH <clave>`, así que la app
  nunca se marca como autorizada).
