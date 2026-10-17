# SEG.1a · Protocolo de enrolamiento del propietario (BLE)

Define el **núcleo de protocolo puro** del enrolamiento del propietario y la
autorización por sesión. Es la base de SEG.1 (#78). No sustituye todavía el
transporte GATT: describe el contrato que firmware y Android implementarán.

Implementación pura y testeable: `firmware/esp32_rgb/core/OwnerEnrollment.h`
(sin asignaciones dinámicas, sin GPIO/BLE/HTTP, sin criptografía propia).

## Principios de seguridad

- Conectar (o emparejar/bondear) **no autoriza** nada.
- El propietario se enrola solo dentro de una **ventana temporal** abierta por un
  gesto físico (pulsación larga de BOOT) y con el **código de un solo uso**
  emitido para esa ventana.
- Cada comando privilegiado se autoriza **por sesión**, tras un proof ligado al
  desafío del dispositivo y un **contador estrictamente creciente** (anti-replay).
- Una sesión revocada no vuelve a funcionar; re-enrolar requiere recuperación.
- Todo camino incompleto/desconocido es **fail-closed**.

## Estados

`EnrollmentState`: `UNPAIRED`, `PAIRING_WINDOW`, `ENROLLED`, `AUTHENTICATED`,
`REVOKED`, `RECOVERY`.

`commandAllowed(state)` es `true` **solo** para `AUTHENTICATED`.

## Ciclo de vida

1. **Ventana física**: `openPairingWindow(now, windowMs, oneTimeCode)` — solo
   desde `UNPAIRED` o `REVOKED`; exige código hex de 8 caracteres y duración > 0.
   `tick(now)` cierra la ventana caducada.
2. **Enrolamiento**: `enroll(ownerId, ownerPubKeyHex, presentedCode, now)` —
   requiere ventana abierta y no caducada, código correcto (comparación en
   tiempo ~constante), `ownerId` acotado (1..32, `[A-Za-z0-9_-]`) y clave
   pública X25519 de 64 hex. Pasa a `ENROLLED`.
3. **Desafío**: `beginSession(deviceNonceHex, now)` — desde `ENROLLED`, emite un
   nonce de dispositivo (1..32 hex). Desde `REVOKED` devuelve `REVOKED`.
4. **Completar sesión**: `completeSession(deviceNonceHex, clientNonceHex,
   counter, proofValid)` — exige desafío pendiente, nonce de dispositivo
   coincidente, `counter` **estrictamente mayor** que el último aceptado y
   `proofValid` verificado por la capa criptográfica. Pasa a `AUTHENTICATED`.
5. **Desconexión**: `onDisconnect()` revoca la sesión y vuelve a `ENROLLED`.
5. **Revocación**: `revoke()` → `REVOKED`; ninguna sesión nueva es posible.
6. **Recuperación**: `openRecovery(now, windowMs, oneTimeCode)` (gesto físico) y
   `factoryReset(code, now)` (exige el código de recuperación) → `UNPAIRED`.

## Resultados

`EnrollmentResult`: `OK`, `NOT_IN_WINDOW`, `WINDOW_EXPIRED`, `BAD_CODE`,
`ALREADY_ENROLLED`, `NOT_ENROLLED`, `NO_SESSION`, `BAD_PROOF`, `REPLAY`,
`INVALID_ARGUMENT`, `REVOKED`.
`SessionChallengeResult`: `OK`, `NOT_ENROLLED`, `REVOKED`.

## Criptografía (SEG.1b, fuera de este núcleo)

El core **no** implementa primitivas. La capa de firmware calculará
`proofValid` con mbedTLS sobre:

- par de claves X25519 por dispositivo (generado en NVS en el primer arranque),
- ECDH para derivar el secreto de sesión,
- proof = HMAC-SHA256(secretoDerivado, deviceNonce ‖ clientNonce ‖ counter),
- cifrado de comandos AES-GCM con nonce/contador de sesión.

Android guarda el material del propietario en **Keystore** y nunca lo expone en
logs ni en exportaciones. La implementación Kotlin reutiliza estas primitivas vía
BouncyCastle (`core/security/BouncyCastleSessionCrypto.kt`), porque X25519 de JCA
solo existe desde Android 13 (API 33) y la app soporta API 26+.

## Puertas físicas (PENDING_HARDWARE_VALIDATION)

Dos teléfonos, replay, MITM, reinicios, pérdida de celular, cancelación y
factory reset requieren validación física en banco (SEG.3).

`tests/owner_enrollment_test.cpp` (host, `g++ -std=c++17 -Wall -Wextra -Werror
-pedantic`) ejecutado por el job `firmware-core-tests`.

## Códec de tramas (`OwnerEnrollmentFrames.h`)

`firmware/esp32_rgb/core/OwnerEnrollmentFrames.h` define el framing ASCII del
canal de enrolamiento, separado de la criptografía y de la máquina de estados.
Cada frame es una línea de tokens separados por un espacio; el parser es
estricto y fail-closed (verbo desconocido, aridad incorrecta, tokens vacíos,
espacios sobrantes, tokens demasiado largos y contadores desbordados se
rechazan).

```
client -> board   PAIR <code> <ownerId> <pubKeyHex>
client -> board   BEGIN <clientNonce>
board  -> client  CHALLENGE <deviceNonce> <devicePubKeyHex>
client -> board   AUTH <clientNonce> <counter> <proofHex>
board  -> client  OK
board  -> client  ERR <reason>
client -> board   REVOKE
client -> board   RECOVER <code>
client -> board   RESET <code>
```

El `CHALLENGE` incluye la **clave pública X25519 del tablero**: sin ella un
cliente no puede derivar el secreto ECDH (`ECDH(ownerPriv, devicePub)`) ni
calcular el proof. Se transporta en la propia respuesta del reto (sin UUIDs
nuevos) y queda ligada al nonce del desafío y al proof resultante.

`OwnerEnrollmentFrames::parse` acepta y clasifica todas ellas (incluidas las
del tablero, para round-trip); los encoders (`encodeChallenge`, `encodeOk`,
`encodeErr`) escriben respuestas acotadas y nunca desbordan el buffer.

Pruebas: `tests/owner_enrollment_frames_test.cpp` (host) vía el job
`firmware-core-tests`.

## Criptografía (`SessionCrypto` + `SessionKeySchedule`)

La criptografía queda detrás de una interfaz pura para poder probarla en host y
para que el núcleo portátil nunca incluya mbedTLS.

- `firmware/esp32_rgb/core/SessionCrypto.h`: interfaz `SessionCrypto` con
  X25519 (`generateKeyPair`, `sharedSecret`), HMAC-SHA256 (`hmacSha256`) y
  AES-256-GCM (`aeadSeal`/`aeadOpen`). Constantes: clave pública/privada/
  secreto/ clave de sesión = 32 B, nonce 12 B, tag 16 B, MAC 32 B.
- `firmware/esp32_rgb/core/SessionKeySchedule.h`: deriva la clave de sesión y
  el **proof** desde el secreto ECDH con HMAC-SHA256 usado como KDF. La clave de
  sesión ata ambos nonces; el proof además fija el contador en big-endian, de
  modo que un proof repetido o reordenado nunca valida. Incluye comparación en
  tiempo constante y limpieza de buffers temporales.
- `firmware/esp32_rgb/core/MbedTlsSessionCrypto.h`: implementación concreta con
  mbedTLS (Curve25519 + AES-256-GCM + HMAC-SHA256), compilada **solo** para el
  objetivo ESP32 (`ARDUINO`/`ESP_PLATFORM`) y validada con el compilador cruzado
  contra mbedTLS 3.6.1.

Pruebas host: `tests/session_key_schedule_test.cpp` (doble determinista no
criptográfico `tests/support/TestSessionCrypto.h`) verifica que tablero y
cliente derivan la misma clave/proof, el binding de nonce y contador
(anti-replay), la propagación de fallos (fail-closed) y la comparación en tiempo
constante.

Pendiente físico: cableado GATT (sin UUIDs nuevos, se extiende el canal de
comandos existente), almacenamiento del keypair en NVS y ventana por botón
BOOT. `PENDING_HARDWARE_VALIDATION`.


