# SEG.2 · Autorización unificada por comando

`firmware/esp32_rgb/core/CommandAuthorization.h` centraliza la decisión de si
un comando **privilegiado** puede ejecutarse, combinando la fuente del comando
con la sesión de owner verificada por el firmware (SEG.1). Es puro, determinista
y host-testable: no toca GPIO, BLE ni HTTP.

## Clases de privilegio

`READ_ONLY`, `HARDWARE_WRITE`, `SCRIPT_AUTOMATION`, `NETWORK_CHANGE`,
`PROJECT_INSTALL`, `FIRMWARE_UPDATE`, `ADMIN`.

`READ_ONLY` es la única clase no privilegiada. El resto exige una fuente
autorizada.

## Reglas (fail-closed)

| Fuente | READ_ONLY | Privilegiado |
|--------|-----------|--------------|
| `BLE_UNAUTHENTICATED` | sí | **no** (`unauthenticated_ble`) |
| `BLE_AUTHENTICATED` (sesión verificada) | sí | sí (`owner_session`) |
| `BLE_AUTHENTICATED` (sin sesión) | sí | **no** (`no_owner_session`) |
| `WIFI_SOFTAP_LOCAL` | sí | sí (`local_owner`) |
| `AUTOMATION_INTERNAL` | sí | solo `HARDWARE_WRITE`/`SCRIPT_AUTOMATION` (`automation_scope`); el resto se deniega (`automation_out_of_scope`) |

Principios:

- La autoridad sobre BLE **solo** proviene de `OwnerEnrollment::authenticated()`
  (sesión verificada con proof + contador), **nunca** de que la app Android diga
  que está autorizada ni de que el enlace esté cifrado o emparejado.
- Conectar o hacer bonding no concede nada.
- Un `onDisconnect` retira la autorización de inmediato.
- Las automatizaciones internas nunca cambian red, instalan proyecto, actualizan
  firmware ni ejecutan administración.

## Integración

Dos formas de uso:

```cpp
// Fuente + booleano de sesión ya verificado:
auto decision = CommandAuthorization::decide(source, enrollment.authenticated(), privilege);

// O directamente con la máquina SEG.1:
auto decision = CommandAuthorization::decide(enrollment, source, privilege);
```

El llamador clasifica el comando en una `PrivilegeClass` y consulta la decisión
antes de tocar el hardware. Un verbo/clase desconocida no debe asumirse como
`READ_ONLY`: el cableado tratará lo desconocido como denegado.

## Pruebas

`tests/command_authorization_test.cpp` (job `firmware-core-tests`): cobertura de
todas las fuentes y clases, READ_ONLY para todos, rechazo de BLE no autenticado
y sin sesión, permitido de BLE con sesión verificada y SoftAP local, alcance
restringido de las automatizaciones internas, e integración con el flujo real de
`OwnerEnrollment` (denegado antes de autenticar, permitido después, denegado tras
disconnect).

Pendiente físico: aplicar la decisión en los puntos de despacho reales de
comandos GATT (SEG.1b-2b) — `PENDING_HARDWARE_VALIDATION`.
