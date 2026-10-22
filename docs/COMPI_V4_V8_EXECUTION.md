# COMPI — Programa de ingeniería V4–V8
Estado: PLAN APROBABLE / EJECUCIÓN POR PR · 2026-10-11
Repositorio: ErickRFM/leds · Origen: main V3 laboratorio

> Esta hoja de ruta **no significa que V4–V8 estén implementadas**. Nunca inferir pruebas físicas desde CI, ni publicar release sin gates de seguridad y hardware. Mantener el control RGB existente funcional en todo momento.

## 0. Inventario trazable, decisiones y restricciones

**Reutilizar:** BLE GATT RGB y estados, parser C++ común BLE/Wi-Fi (#23), PinManager/catálogo GPIO (#24), SoftAP laboratorio y RGB Wi-Fi con readback (#26/#37), ADC1 con CSV (#29), diagnóstico de RAM/flash/reinicio (#30), editor declarativo de pasos (#22), biblioteca local de ocho proyectos (#32), timeout GATT (#34), selección de red sin fallback celular (#35), artefactos debug con hash (#36), ejecución de pasos con tiempo monotónico (#38).

**No implementado/verificado según docs/ESTADO_V3.md:** autorización BLE F02, CommandRouter+DeviceStateStore completos F03, LAN protegida F04, hardware físico F00/F01, sensores I²C F07, import/export F09, USB/serial F10, automatización autónoma F11, OTA firmada/rollback F12, multipla­ca simultánea F13, MQTT F14, QA UX física F15 y release F16. La presencia de un esquema de partición OTA no implica que OTA funcione.

**Placa base de aceptación:** ESP32-D0WD-V3, 4 MB flash, Arduino-ESP32 3.0.7, `esp32:esp32:esp32:PartitionScheme=min_spiffs`. Pines RGB 27/25/26; 220–330 Ω por canal con LED discreto; nunca alimentar motor/tira LED desde GPIO. Android Kotlin/Compose, SDK 34, JDK 17 y Gradle 8.7 según documentación vigente. Reconfirmar versiones efectivas antes de actualizarlas.

**Invariantes:** offline-first; cero nube obligatoria; GATT v1 compatible; sin exponer secretos en logs; sin mock de conexión declarado real; estado autoritativo leído desde la placa; sin cargar código importado automáticamente; ninguna operación de flasheo sin aprobación explícita del usuario y ruta USB de recuperación comprobada.

## 1. Gates y orden obligatorio

| Gate | Dependencias | Evidencia antes de marcar PASS |
|---|---|---|
| G0 baseline | main | tests Android + firmware + parser host, checks hashes, flash budget, captura de paths y revisión git |
| G1 seguridad | G0 | teléfono autorizado puede escribir; no autorizado no puede; reset de ownership probado en banco |
| G2 núcleo | G1 | RGB BLE y SoftAP siguen idénticos; sincronización readback y desconexión testadas |
| G3 programación | G2 | import/save/export, parser seguro, compilación reproducible en entorno seleccionado; USB en equipo soportado |
| G4 hardware | G2 | PinManager bloquea conflictos; sensores y PWM probados con circuitos correctos |
| G5 autonomía | G4 | reglas persistentes siguen funcionando con Android cerrado, límites y watchdog |
| G6 OTA | G1,G2 | firma, anti-rollback según política, interrupción controlada, rollback/recovery real |
| G7 multiplaca | G2,G6 | comandos y estados aislados por deviceId, sin fuga entre placas |
| G8 producción | G0–G7 | QA Android física, seguridad, accesibilidad, APK release firmado y trazabilidad |

Cada gate admite BLOCKED con razones y evidencias. No fusionar binarios destructivos a main sin pruebas en banco.

## 2. V4: arquitectura, seguridad, dispositivos y UX

### V4-A — Modelo de dominio
Introducir progresivamente (sin reescribir UI existente): `DeviceId` (estable y NO secreto), `BoardProfile` (chip, flash, particiones, pines, capacidades), `DeviceCapabilities`, `DeviceSession`, `ConnectionState`, `CommandId`, `CommandResult`, `PinReservation` y `FirmwareManifest`. Mantener un adaptador de protocolo GATT v1. **Identidad pública no implica titularidad**.

### V4-B — Contratos de transporte
`DeviceTransport.discover/connect/disconnect/request/observeState`; implementaciones BLE GATT, SoftAP HTTP y USB futuro. Un único `CommandRouter` serializa por placa, cancela por epoch al desconectar, maneja timeout, deduplicación y límites; NO reintentar ciegamente comandos no idempotentes. `DeviceStateStore` distingue preview local, inflight, confirmado y stale. Negociación versionada de capabilities, con fallback sólo a operaciones soportadas por v1. Wi-Fi vinculada explícitamente a la red ESP32 y nunca red celular. Sin auto-fallback de BLE a Wi-Fi que relaje autenticación.

### V4-C — Seguridad BLE
Modelo de amenaza: teléfono externo, replay, atacante próximo, reset de placa, pérdida de teléfono, robo de credenciales y comandos de GPIO. Definir enrolamiento físicamente verificable (botón/ventana temporal o canal de prueba de posesión) más bonding BLE y comprobación de autorización en el firmware **por cada escritura**, no sólo en Android. Identificador de dueño y revocación. Negar todo por defecto para configuración/firmware. Pruebas negativa/positiva sobre Android real y nRF Connect. No quemar eFuses irreversibles en banco sin procedimiento. Antes de establecer READY protegido: identidad, bonding y autorización validados.

### V4-D — Navegación/UI
Conservar componentes Compose ya construidos. Destinos: Inicio, Dispositivos, Módulos, Studio, Ajustes; Monitor accesible desde cada dispositivo. Control RGB idéntico funcionalmente. Componentes reutilizables: `ConnectionBadge`, `DeviceCard`, `CapabilityTile`, `HardwareWarning`, `DangerConfirmation`, `TelemetryChart`, `EmptyState`. Diseño claro/oscuro mate, tamaños 360/390/430 dp, horizontal Studio, TalkBack, contraste, animaciones con respeto a reducción de movimiento. Aceptar mediante snapshots/capturas reales, navegación atrás y rotación.

**Pruebas:** unit ViewModel/reducer/estado, matrices de comandos y error, instrumentadas navegación, BLE propio/ajeno, pérdida de red; equipo LED con R/G/B/brillo/OFF/ON/FX y estados leídos.

## 3. V5: Compi Studio

### V5-A — Almacenamiento
ProjectId, ProjectFile, BoardTarget, SketchEntrypoint, LibraryLock, BuildConfiguration, Revision. Room para índices; archivos bajo espacio privado SAF para import/export; guardar atomicamente, validar encoding UTF-8, tamaño, extensiones .ino/.cpp/.h/.txt/.zip, rutas ZIP traversal, archivos duplicados y límites. Backup/export reproducible, migraciones Room y pruebas de corrupción.

### V5-B — Editor
Compose con texto incremental, highlighting, buscar/reemplazar, undo/redo, autosave con debounce y conflictos; no bloquear UI con archivos grandes. Cada archivo pertenece a proyecto; no confundir el .txt importado con binario ejecutable. Plantillas versionadas y ejemplos que correspondan a pines disponibles.

### V5-C — Compilación
Hito de factibilidad antes de prometer Arduino toolchain nativa completa en teléfono: POC en arm64 real, medir tamaño RAM/disco, compatibilidad toolchains, tiempos, límites térmicos. Alternativas: compilador remoto **opcional** con aislamiento, rate limiting, sandbox, salida reproducible y privacidad; o desktop companion/Arduino CLI local en PC. No afirmar compilación offline Android de C++ como entregada sin binario y prueba. Registrar core, FQBN, bibliotecas y SHA256 del artefacto.

### V5-D — Ejecución/carga
Tres rutas: 1) instrucciones/reglas declarativas sobre Runtime Compi; 2) extensión compilada junto a runtime con presupuestos y perfil de capacidades; 3) firmware completo que sustituye Compi. No hay sandbox robusta para C++ arbitrario sobre ESP32 clásico. USB OTG requiere Android USB Host, consentimiento, chip USB-UART compatible, protocolo de flasheo probado, verificación posflash, desconexiones y procedimiento manual de BOOT/EN si aplica. OTA sólo después de gate G6. Antes de firmware completo advertir pérdida de BLE/Wi-Fi Compi y exigir recovery documentado. Consola serie con flujo backpressure y secretos redactados.

**Aceptación:** crear/importar/exportar proyecto, editar tras reinicio, detectar conflicto de dependencias, compilar en al menos un backend verificado, flashear y leer versión de placa física compatible, restaurar control Compi después de recuperación.

## 4. V6: Hardware Lab + Automation Engine

**Módulos:** LED RGB/RGBW y tiras con driver externo; GPIO digital, PWM/LEDC; ADC1 calibrado; I2C con address scan limitado; SPI sólo con perfil y reserva; sensores temperatura/humedad/distancia; servo, motor y relé sólo con driver y fuente apropiada. Para cada driver: capabilities, pins, voltajes, frecuencia, consumo, sampling, permisos, estrategia de desconexión y tests.

**PinManager** debe registrar propietario/propósito y bloquear pines prohibidos o reservados, conflictos de boot/flash/RGB, ADC2 durante Wi-Fi, recursos PWM agotados y mezcla de tensiones. Interfaz presenta esquema de conexión e instrucciones de prueba. No reinterpretar lecturas sintéticas como mediciones.

**Automation Engine**: DSL JSON/CBOR declarativo versionado con AST validado; `Trigger` (sensor/temporizador/estado), `Condition` (comparador con histéresis), `Action` (SET/FX/GPIO autorizado), `RuleId`, `SafetyPolicy`. Límites duros por número de reglas, profundidad, frecuencia, CPU/RAM, pines y tiempo de ejecución; persistencia atómica y restauración de estado seguro; watchdog, cancelación, logs y conflicto entre control manual y automático (política explícita: override manual temporal, prioridad configurable). Reglas permanecen activas sin teléfono; mecanismo disable local físico si se requiere.

**Pruebas:** hardware por componente con diagramas, cortes de energía, jitter, millis wrap, 24 h soak para reglas críticas, CSV/telemetría, no interferencia con RGB.

## 5. V7: Firmware Manager, diagnóstico y flota local

**OTA:** manifiesto firmado (device profile, version, SHA, longitud, protocolo, partition layout); validar compatibilidad y espacio libre antes de transferir, A/B y rollback sólo si el bootloader/partition scheme lo permiten. Confirmación healthcheck y rollback en caso de fallo; no depender de que la app siga activa. Seguridad por capa y pruebas de imagen corrupta/interrupción/alimentación insuficiente. Mantener backup/recovery USB. Never flash de forma silenciosa.

**Multi-device:** repositorios particionados por deviceId, sesiones y colas independientes, límites BLE/Wi-Fi, grupos y escenas con resultados parciales visibles; prohibido prometer atomicidad real en varios dispositivos. QR de onboarding con token de corta duración únicamente después de implementar prueba de posesión; no incluir claves en QR público.

**Compi Assist:** primero motor local determinista de pinouts, errores de protocolo y diagnósticos; IA opcional con consentimiento para datos que salgan del teléfono, redacción de secretos, recomendaciones nunca autoejecutadas. Mostrar confianza, fuentes e instrucciones reversibles. Modo offline sin perder RGB ni herramientas.

## 6. V8: ecosistema

BoardProfile por variantes (ESP32 clásico, C3, S3) con discovery/capabilities propios; jamás asumir BLE clásico/periféricos idénticos. Extension SDK versionado con interfaces de sensores/controladores, permisos declarados y firma de módulos distribuidos; no cargar código nativo de terceros en ESP32 sin vetting explícito. Biblioteca local de ejemplos, import/export seguro, guías interactivas. MQTT/Home Assistant sólo opt-in con TLS/credenciales y aislamiento por tenant/dispositivo si en el futuro hay cuentas. Desktop Companion opcional, nunca requisito para RGB. Releases reproducibles Android/firmware, changelog/SBOM/hash y migraciones.

## 7. Secuencia de PR (dependencias y pruebas)

| PR | Trabajo | Depende | Evidencia |
|---|---|---|---|
| C-00 | baseline, CI local, test-matrix, inventario | main | scripts verify-local; matrices cerradas |
| C-01 | contratos dominio/capabilities y tests | C-00 | pruebas serialización/fallback |
| C-02 | autoridad BLE firmware y enrolamiento físico | C-01 | prueba teléfonos propio/ajeno |
| C-03 | transport router y state store | C-02 | reconexiones/readback y Wi-Fi sin fallback |
| C-04 | Compi UI/navigation/design tokens | C-03 | capturas reales y accessibility |
| C-05 | Studio persistence e import/export | C-04 | migraciones/ZIP traversal |
| C-06 | editor, plantillas, librerías | C-05 | tests edición/corrupción |
| C-07 | build backend POC y contrato | C-06 | compilación reproducible en target |
| C-08 | USB OTG/serial/flashing | C-07 | hardware real, recuperación |
| C-09 | Hardware Lab y pin reservations | C-03 | bancos GPIO/ADC/I²C |
| C-10 | Automation DSL and runtime | C-09 | autónomo y watchdog |
| C-11 | OTA firmado y recovery | C-02,C-03 | fault injection y rollback |
| C-12 | multiplaca/grupos | C-03,C-11 | aislamiento de estado |
| C-13 | Assist local + opt-in AI | C-09 | datos privados/consentimiento |
| C-14 | SDK/profiles/gallery | C-10,C-12 | compatibilidad de terceros |
| C-15 | release gate hardening | todos | aprobación QA real + signing |

PRs pequeñas, independientes cuando sea posible; squash merge solamente después de checks y evidencia. No crear quince ramas simultáneamente. Etiquetas sugeridas: `phase:V4`...`phase:V8`, `priority:P0`, `needs:hardware`, `blocked:security`, `area:android`, `area:firmware`.

## 8. Matriz de cobertura por estado

Cada feature lleva `NOT_STARTED | IMPLEMENTED_UNVERIFIED | SOFTWARE_VERIFIED | HARDWARE_VERIFIED | RELEASED`; nunca pasar de SOFTWARE_VERIFIED a RELEASED sin gate. Documentar comando, modelo de placa, teléfono Android, core/fqbn, SHA de firmware/APK, commit, pasos, expected/actual, logs sanitizados y resultado. Para V4 QA mínima: BLE discovery/bond/write/notify/revoke; RGB parity BLE/Wi-Fi; Wi-Fi sin Internet; reboot y powerloss; GPIO seguro; pruebas en 360/390/430dp. Para V5: importación maliciosa rechazada, build determinista, USB conect/disconnect/recovery. Para V6: ensayo bajo carga + reglas offline. Para V7: OTA corrupta/fallida/revertida. Para V8: compatibilidad por placa y release firmado.

## 9. Cierre operacional

Preparar `scripts/verify-local.ps1` como punto de entrada actual; no reescribirlo sin necesidad. Cada entrega conserva tests/lint Android, compilación ESP32 y host tests, presupuesto de firmware, hash y artefactos; GitHub Actions opcional si facturación bloqueada, pero validar de forma local equivalente. Registrar PASS/BLOCKED explícitos, no escribir `main updated` sin sha de merge. No introducir backend/cloud ni login por defecto. Actualizar README sólo cuando una función esté implementada y comprobada. La primera implementación real debe comenzar en C-00 y C-02, sin comprometer el protocolo BLE existente.
