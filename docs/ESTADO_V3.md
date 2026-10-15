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

## Iteración de pruebas físicas y depuración lógica (#34–#38)

Los siguientes PR ya se fusionaron a `main` tras los checks de Android, firmware y host disponibles:

| PR | Mejora funcional | Qué demuestra CI | Qué sigue requiriendo hardware |
|---|---|---|---|
| [#34](https://github.com/ErickRFM/leds/pull/34) | GATT WRITE timeout 5 s y reconexión/relectura `STATE` | Compilación y guardas de cola, contratos | Forzar pérdida real de ACK BLE |
| [#35](https://github.com/ErickRFM/leds/pull/35) | Ruta explícita Wi-Fi SoftAP y sin fallback a datos móviles | Pruebas de política de red y APK | Android con Wi-Fi sin Internet |
| [#36](https://github.com/ErickRFM/leds/pull/36) | APK y firmware DEBUG con hashes/commit vía Actions | Artefactos generados por CI | Flasheo/instalación y regresión física |
| [#37](https://github.com/ErickRFM/leds/pull/37) | Autodiagnóstico y readback exacto del RGB enviado por Wi-Fi | Validación contra `STATE` y QA API | LED físico y carga eléctrica |
| [#38](https://github.com/ErickRFM/leds/pull/38) | Secuencias GPIO ancladas a tiempo transcurrido, sin pulsos atrasados | Pruebas host de lag, fin de secuencia y `millis()` wrap | Temporización medida en osciloscopio o analizador |

**Instrucciones ejecutables:** [PRUEBAS_REALES_V3.md](PRUEBAS_REALES_V3.md) (matriz B1…F1), [QA_BLE_V3.md](QA_BLE_V3.md) y [GitHub Actions](https://github.com/ErickRFM/leds/actions/workflows/android-ci.yml). Elige una ejecución verde de `main`; los artifacts deben incluir **rgb-controller-debug** y **esp32-lab-4mb-firmware**, junto con SHA-256 y revisión exacta.

**Desde la app:** `Ajustes → Centro ESP32 → Ejecutar autodiagnóstico (solo lectura)`, luego `Compartir reporte sin claves`. El informe **no valida** autorización BLE, OTA ni pines eléctricos. Para pruebas Wi-Fi, conectarse a `ESP32-Control-Lab` y aceptar mantener el Wi-Fi sin Internet; un gateway diferente a `192.168.4.1` se rechaza intencionalmente, no se usa celular como respaldo.

**Próxima decisión:** interpretar resultados B1–F1 que entregue el usuario y priorizar problemas observados, sin exponer credenciales en logs. Seguridad BLE, OTA firmado, I²C, red multi-ESP32 y release formal siguen pendientes.

## Iteración del Plan Maestro V15.1 (F00–F07)

Incrementos fusionados a `main` con los cuatro checks de CI en verde (`android`,
`firmware`, `firmware-core-tests`, `windows-board-profile`). Igual que en las
iteraciones anteriores, la CI comprueba pruebas disponibles y compilación, pero
**no sustituye** la validación en placa/teléfono.

| Fase | PR | Incremento | Evidencia hardware |
|---|---|---|---|
| F00 | [#87](https://github.com/ErickRFM/leds/pull/87) | Base V15.1 sobre el laboratorio existente | Pendiente |
| F01 | [#88](https://github.com/ErickRFM/leds/pull/88) | Autorización BLE real (sin regresión de transporte) | Pendiente |
| F02 | [#89](https://github.com/ErickRFM/leds/pull/89) | Catálogo/disposición de componentes | Pendiente |
| F03 | [#90](https://github.com/ErickRFM/leds/pull/90) | Dashboard de componentes persistente y autoritativo por firmware | Pendiente |
| F04 | [#91](https://github.com/ErickRFM/leds/pull/91) | Telemetría real DHT22 en el dashboard | Pendiente |
| F05 | [#92](https://github.com/ErickRFM/leds/pull/92) | Humedad de suelo real (ADC1) | Pendiente |
| F06 | [#93](https://github.com/ErickRFM/leds/pull/93) | Estado OLED SSD1306 en el dashboard | Pendiente |
| F06 aud. | [#94](https://github.com/ErickRFM/leds/pull/94) | Corrección de comparación de ids en tarjetas deseadas | No aplica |
| F07.1 | [#95](https://github.com/ErickRFM/leds/pull/95) | Repositorio de telemetría unificado + historial persistente acotado | Pendiente |
| F07.2 | [#96](https://github.com/ErickRFM/leds/pull/96) | Gráficas con válido/inválido/obsoleto/desconectado | Pendiente |
| F07.3 | [#97](https://github.com/ErickRFM/leds/pull/97) | Alertas configurables con histéresis y límite de frecuencia | Pendiente |
| F07.4 | [#98](https://github.com/ErickRFM/leds/pull/98) | Automatizaciones declarativas por la ruta de comando autorizada | Pendiente |
| F07.5 | [#99](https://github.com/ErickRFM/leds/pull/99) | Plantillas deshabilitadas + calibración de suelo persistente por dispositivo | Pendiente |

**Ver F07:** [F07_TELEMETRIA_HISTORIAL.md](F07_TELEMETRIA_HISTORIAL.md),
[F07_GRAFICAS.md](F07_GRAFICAS.md), [F07_ALERTAS.md](F07_ALERTAS.md),
[F07_AUTOMATIZACIONES.md](F07_AUTOMATIZACIONES.md),
[F07_PLANTILLAS_CALIBRACION.md](F07_PLANTILLAS_CALIBRACION.md) y
[F07_AUDITORIA_FINAL.md](F07_AUDITORIA_FINAL.md).
