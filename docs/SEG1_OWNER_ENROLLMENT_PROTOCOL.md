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
logs ni en exportaciones.

## Puertas físicas (PENDING_HARDWARE_VALIDATION)

Dos teléfonos, replay, MITM, reinicios, pérdida de celular, cancelación y
factory reset requieren validación física en banco (SEG.3).

## Pruebas

`tests/owner_enrollment_test.cpp` (host, `g++ -std=c++17 -Wall -Wextra -Werror
-pedantic`) ejecutado por el job `firmware-core-tests`.
