# Compi V17 — módulos dinámicos y conexiones GPIO reales

## Estado técnico
- **UI Android:** Inicio, biblioteca, módulos independientes/composición ambiental, Studio, Hardware, diagrama Compose por señal y editor local.
- **GPIO de firmware:** DHT22 y humedad de suelo ADC1 admiten reconfiguración transaccional cuando están compilados y activos. No es una simulación; `GET /v2/components/status` confirma el GPIO que posee cada módulo.
- **Canal permitido:** HTTP **solo** sobre SoftAP protegido `ESP32-Control-Lab`, con clave `X-Device-Key` de 16 dígitos hexadecimales, en **APK debug**. No habilitar HTTP sobre WAN ni red STA.
- **Sin persistencia de cableado remoto:** el cambio vuelve al valor compilado tras reiniciar, hasta que exista validación física y un diseño de migración NVS con recuperación.
- **RGB GPIO 27/25/26:** reservados por el firmware; no hay reasignación PWM en caliente. Cambiar controles RGB sí utiliza el motor existente y el canal autorizado.
- **OLED I²C:** render de vista previa y estado. Reasignar SDA/SCL o confirmar contenido físico no está permitido por esta API; validar el dispositivo y el driver primero.
- **Genéricos/sensor digital/relé:** requieren firmware habilitado; el catálogo es un borrador y NO prueba detección.

## Contrato REST de reconfiguración (v1)

`GET /v2/components/wiring` → `{"version":1,"persistence":"until_reboot","revision":0,"supported":["dht22","soil_moisture"]}` cuando ambos módulos están compilados, inicializados y activos. La lista puede estar vacía.

`POST /v2/components/wiring/apply` (form-urlencoded; no JSON arbitrario):

| Campo | Valor |
|---|---|
| `componentId` | `dht22` o `soil_moisture` (y debe figurar en `supported`) |
| `expectedPin` | GPIO actual confirmado por el firmware, 0–39 |
| `pin` | Nuevo GPIO permitido por `PinManager`, 0–39 |
| `requestId` | 16 caracteres hexadecimales aleatorios |

La respuesta 200 devuelve `version`, `outcome`, `componentId`, `requestId`, `pin`, `revision` y `persistence=until_reboot`. Los resultados `applied` y `already_applied` se consideran solo **acuse lógico inicial**. Android hace obligatoriamente una segunda lectura `/v2/components/status`: requiere `gpioAuthority=firmware`, módulo correcto, `state=active` y el único pin esperado. Sin ello el UI enseña **No confirmado**.

## Política de seguridad y recuperación

1. Nunca mover pines de flash, arranque, reservados RGB ni GPIO de solo entrada a DHT22.
2. No asignar un pin ya reclamado por otro propietario.
3. Comprobar el pin anterior antes de aplicar; una petición obsoleta falla con HTTP 409 y no modifica conexiones.
4. Detener **solo** el módulo afectado, no RGB, BLE ni todo el laboratorio; intentar adquirir el nuevo GPIO; al fallar se libera y se restaura el anterior.
5. Si la restauración también falla, responder explícitamente `recovery_failed`; no simular continuidad. Releer diagnóstico y reiniciar bajo supervisión.
6. Reintentos del último `requestId` con los mismos parámetros devuelven el mismo acuse; reutilizarlo con otros parámetros produce HTTP 409. Cada nueva operación debe emplear un identificador nuevo.
7. Conexión Wi-Fi fuera de SoftAP o clave inválida: rechazada. Un BLE emparejado **no** habilita la API GPIO.

## Pruebas y límites

CI ejecuta C++ host para validación de tipos de pines, colisiones, precondiciones, cambio DHT22/ADC1 y rollback por error; ensambla el firmware normal y una variante con `COMPI_ENABLE_DHT22=1 COMPI_ENABLE_SOIL=1`. Android ejecuta pruebas JVM y Compose API 30/35.

Validación física **todavía obligatoria** en la placa ESP32-D0WD-V3: verificar voltajes correctos (3.3 V para señales), tierra común, resistencia pull-up apropiada en DHT22, sensor capacitivo ADC1 y cableado con alimentación desconectada; confirmar lectura real y reconexión Wi-Fi/BT tras aplicar y revertir. Un acuse GPIO no demuestra temperatura, humedad, nivel eléctrico ni el cableado real.
