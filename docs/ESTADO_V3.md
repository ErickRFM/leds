# ESP32 Control Center — Estado V3 incremental

**Corte técnico:** 10 de octubre de 2026 (hora de México).  
**Estado real:** `INTEGRATED_V3_LAB_INCREMENTS` — **`NOT_PRODUCTION_READY`**.  
**Seguimiento:** [issue #28](https://github.com/ErickRFM/leds/issues/28) · [criterios de release](RELEASE_GATE_V3.md).

## Entregado a `main` con CI de PR aprobado

| PR | Incremento comprobado por CI | Evidencia hardware |
|---|---|---|
| [#21](https://github.com/ErickRFM/leds/pull/21) | FQBN/Arduino core unificados para ESP32 4 MB; verificador Windows y Actions | Pendiente |
| [#22](https://github.com/ErickRFM/leds/pull/22) | Parser único de secuencias, plantillas, pasos visuales, borrador local | Pendiente |
| [#23](https://github.com/ErickRFM/leds/pull/23) | Parser C++ de comandos RGB BLE/Wi-Fi compartido con tests host | Pendiente |
| [#24](https://github.com/ErickRFM/leds/pull/24) | Catálogo GPIO seguro y disposición Android compacta | Pendiente |
| [#25](https://github.com/ErickRFM/leds/pull/25) | Control CI de tamaño de binario y reserva de 64 KiB en slot OTA | Pendiente |
| [#26](https://github.com/ErickRFM/leds/pull/26) | RGB completo y efectos por Wi-Fi de laboratorio, con readback | Pendiente |
| [#27](https://github.com/ErickRFM/leds/pull/27) | Release Android deshabilitado hasta autorización BLE y QA física | No aplica como aprobación de hardware |
| [#29](https://github.com/ErickRFM/leds/pull/29) | Gráfica ADC1, muestreo foreground, límites y CSV | Pendiente |
| [#30](https://github.com/ErickRFM/leds/pull/30) | Diagnóstico read-only autenticado: RAM/flash/reinicio/pines | Pendiente |
| [#31](https://github.com/ErickRFM/leds/pull/31) | `lintDebug` ahora falla el build ante errores de Lint | No aplica |
| [#32](https://github.com/ErickRFM/leds/pull/32) | Ocho proyectos locales de laboratorio con DataStore y pruebas | Pendiente |

Los checks CI de cada PR comprobaron pruebas disponibles y compilación, pero **no equivalen a una instalación en un teléfono/placa físicos**.

## Matriz F00–F16

| Fase | Estado al corte | Bloqueo restante |
|---|---|---|
| F00 | Parcial | Auditoría eléctrica/cableado y recuperación USB en banco |
| F01 | Software implementado | Firmware grabado con partición real y APK instalado |
| F02 | **Pendiente crítica** | Bonding BLE, PoP y autorización de escrituras verificadas |
| F03 | Parcial | `CommandRouter`, `DeviceStateStore` y permisos por recurso |
| F04 | Laboratorio SoftAP | Transporte LAN protegido, descubrimiento y WebSocket |
| F05 | Parcial | PWM genérico, políticas dinámicas y periféricos físicos |
| F06 | Implementación de software | Paridad RGB en dispositivo real BLE/Wi-Fi |
| F07 | ADC1 experimental | Sensores reales I²C y telemetría calibrada |
| F08 | Editor visual de pasos | Blockly/flujo avanzado seguro sin código nativo |
| F09 | Biblioteca local | Importación/exportación y compatibilidad entre placas |
| F10 | Diagnóstico HTTP | USB-OTG, monitor serial y simulación diferenciada |
| F11 | Pendiente | Automatizaciones autónomas seguras en firmware |
| F12 | Sólo preflight | Firma, subida OTA, rollback y recuperación real |
| F13 | Pendiente | Sesiones de múltiples ESP32 y dashboards asociados |
| F14 | Pendiente | MQTT, ESP-NOW y extensiones con permisos |
| F15 | Parcial | Capturas reales, accesibilidad, navegación y pruebas responsive |
| F16 | Gates software mejorados | QA física, security review, firma APK y release final |

## Verificar en Windows sin flashear

Con JDK 17, SDK 34, Arduino CLI y el repositorio actualizado:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
```

El script usa `config/esp32-board.env`: `esp32:esp32:esp32:PartitionScheme=min_spiffs`, Arduino-ESP32 3.0.7. **No flashea la placa** salvo petición explícita con `-UploadFirmware -Port COMx`. Antes de grabar comprobar alimentación, polaridad del LED, tres resistencias, copia del firmware funcional y recuperación USB; un cambio de particiones puede alterar los datos guardados.

## Reglas de producción

- No publicar como producto: BLE GATT V1 todavía admite escritura sin asociación autorizada.
- La API HTTP es de laboratorio sobre SoftAP WPA2 + clave, no TLS ni Internet. En Android sólo está habilitada en debug.
- Actualización OTA aún desactivada; el check de tamaño de binario no demuestra rollback.
- Los experimentos siguen acotados a acciones declarativas autorizadas; prohibido C++ arbitrario o reclamación de pines RGB/flash.
- No cerrar [#10](https://github.com/ErickRFM/leds/issues/10), [#12](https://github.com/ErickRFM/leds/issues/12), [#13](https://github.com/ErickRFM/leds/issues/13), [#14](https://github.com/ErickRFM/leds/issues/14) ni [#15](https://github.com/ErickRFM/leds/issues/15) sin aceptación comprobada.

**Siguiente puerta técnica:** pruebas físicas en placa y teléfono, luego onboarding BLE protegido; el resto de funcionalidades sensibles depende de ella. Este documento es un registro de estado y no cambia las capacidades de hardware.
