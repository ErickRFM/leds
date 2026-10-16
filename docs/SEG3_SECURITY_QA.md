# SEG.3 · QA de seguridad adversarial

Matriz de ataques cubiertos por `tests/security_adversarial_test.cpp`, que
integra el flujo **framing → enrolment → crypto → authorization** con los
componentes reales y el doble criptográfico determinista.

| Escenario | Resultado esperado | Resultado |
|-----------|--------------------|-----------|
| Dispositivo no enrolado | nada privilegiado; `PAIR` fuera de ventana rechazado | automático ✔ |
| Frames malformados / inyectados (`OK`, `CHALLENGE`, aridad) | `ERR malformed`, fail-closed | automático ✔ |
| Código de un solo uso incorrecto | `ERR bad_code`, sigue sin propietario | automático ✔ |
| Ventana física caducada | no se puede enrolar; vuelve a `UNPAIRED` | automático ✔ |
| Reutilización de mensaje `AUTH` | `NO_SESSION` (sin `BEGIN` pendiente) | automático ✔ |
| Proof incorrecto | `BAD_PROOF`, sin privilegio | automático ✔ |
| Reutilización de contador | `REPLAY`, sin privilegio | automático ✔ |
| Contador estrictamente mayor | sesión autenticada | automático ✔ |
| Concurrencia / cancelación (`BEGIN` nuevo invalida el anterior) | proof del nonce obsoleto rechazado | automático ✔ |
| Transición BLE → Wi-Fi | pierde sesión BLE, conserva propietario; SoftAP local sigue autorizado | automático ✔ |
| Automatización interna intentando red/instalación/OTA/admin | denegado | automático ✔ |
| Denegación de GPIO / scripts / credenciales sin autorización | denegado en todas las clases | automático ✔ |
| Revocación de propietario | `BEGIN`/`AUTH` rechazados; re-enrolamiento directo rechazado | automático ✔ |
| Pérdida de propietario / recuperación | código erróneo falla; código correcto borra el propietario | automático ✔ |
| Re-enrolamiento tras recuperación | funciona; contador reinicia | automático ✔ |

## Pendiente de hardware (PENDING_HARDWARE_VALIDATION)

- Replay real y MITM con **dos teléfonos** sobre GATT.
- Reinicio **físico** del ESP32 (persistencia NVS del propietario y del
  contador) y expiración de sesión medida en el reloj real.
- Transición real BLE ↔ Wi-Fi (SoftAP) con caída y reconexión.
- Tiempos de espera y cancelación en el enlace real.
- Recuperación del teléfono perdido y revocación física.

Todos estos requieren hardware y quedan **PENDING_HARDWARE_VALIDATION**; no se
declaran verificados.
