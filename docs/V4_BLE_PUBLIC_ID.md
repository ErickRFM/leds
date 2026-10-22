# V4 · Identidad por BLE sin Monitor Serie

Se incorpora una característica BLE **solo lectura** al servicio RGB V1: `4b9d0004-7a5e-4d4c-ae88-c8c70d3ab211`.

Contiene exactamente los 16 caracteres hexadecimales públicos guardados en NVS, los mismos que se consultan por `GET /v2/identity`. No contiene password del SoftAP, clave de control, PoP ni información de propietario.

En Android la lectura GATT es opcional y serializada: primero se habilitan las notificaciones de `STATE`, se lee el estado RGB real y, cuando existe la característica nueva, se lee el ID **antes de pasar a Ready**. El firmware anterior, que no ofrece esta característica, continúa funcionando como laboratorio BLE normal, mostrando la identidad como desconocida. No se guarda una clave ni se abre permisos por recibir un ID.

### Pruebas reales

- Primer arranque con firmware V4: conectar en Equipos por BLE, comparar ID anunciado con el de `GET /v2/identity` en laboratorio.
- Apagar/encender ESP32 manteniendo NVS: ambos IDs deben permanecer iguales.
- Conectar un firmware anterior sin característica nueva: continuar READY BLE RGB pero ID no disponible.
- Desconectar y elegir otra placa: nunca mostrar el ID anterior durante el nuevo handshake.
- Recibir valor corto/no hexadecimal: mostrar identidad desconocida y no elevar privilegios.
- Intentar escribir a la característica de identidad: debe rechazar porque no tiene propiedad WRITE; comprobar en físico.

**Pendiente para independencia total:** WiFiProv seguro, credenciales únicas por placa, QR PoP y GATT RGB autenticado. La identidad es pública y no sustituye la autorización.
