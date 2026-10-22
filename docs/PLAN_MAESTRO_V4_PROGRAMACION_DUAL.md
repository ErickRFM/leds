# PLAN MAESTRO V4 — ESP32 Control Center: programación dual y laboratorio independiente

**Corte:** 2026-10-10 (México). **Repositorio:** `ErickRFM/leds`. **Objetivo:** evolución incremental verificable de V3 a un centro ESP32 Android Kotlin, con programas declarativos ejecutables sin reflashear y proyectos Arduino C++ compilables y actualizables con recuperación segura.

> Este plan es una hoja de ruta, no una declaración de que V4 esté terminada. Mantener la APK `debug` y la prohibición de release hasta cerrar seguridad BLE y QA física. PR #44 (identidad Android) es un trabajo paralelo que debe revisarse e integrarse sin sobrescribirlo.

## 0. Principios invariantes y dependencias

1. No modificar UUIDs del servicio BLE RGB (`WRITE/STATE`) ni sus comandos `SET/GET/ON/OFF/FX` sin migración y pruebas.
2. Conservar el SoftAP `ESP32-Control-Lab` (192.168.4.1) y la política de enrutar por su Wi-Fi sin fallback a datos móviles. La clave V3 no es un enrolamiento seguro.
3. Mantener RGB en GPIO 27/25/26; impedir que un script capture pines reservados. `PinManager` arbitra recursos.
4. No reescribir `ExperimentRuntime` hasta estabilizar compatibilidad con sus secuencias V3 (máximo 16 pasos, 20 s, duración 100–2000 ms).
5. No usar un sketch Arduino subido como «plugin aislado». C++ nativo genera **nuevo firmware**, reemplaza la aplicación y requiere reinicio; conservar el núcleo exige incluirlo en el build y verificar el artefacto.
6. No habilitar flasheo/OTA remoto hasta verificar identidad, autorización, firma y A/B rollback con recuperación USB. Jamás asumir que «4 MB» garantiza que cualquier firmware cabe.
7. Sin nube obligatoria: Android ↔ ESP32 para scripts; compilador PC local como puente opcional para Arduino; importar `.bin` compatible y verificado será el camino móvil.
8. El `main` debe recibir PR pequeños verificados, no un mega-merge de trabajo incompleto. Mantener/etiquetar pendientes con evidencia.
9. Proteger secretos y privacidad: no persistir claves de control ni SSIDs/passwords en reportes, proyectos, capturas o logs.
10. Antes de ajustar pines, OTA o Wi-Fi, respaldar firmware y configuración recuperable. Ejecutar reset sólo con confirmación explícita.

## 1. Auditoría y preservación (M0, bloqueo de base)
**Código:** `firmware/esp32_rgb/`, `android/app/src/main/`, `scripts/verify-local.ps1`, `config/esp32-board.env`.
- Registrar `main` SHA, PRs, comportamiento actual RGB/BLE/SoftAP/identidad y tamaño de firmware.
- Congelar pruebas de regresión del parser V3, secuencias, readback RGB, timeout BLE, red sin Internet y UI existente.
- Revisar PR #44 y continuar el trabajo F00–F16 sin marcar como completos componentes sin evidencia.
**Salida:** línea base reproductible, matriz hardware/firmware/API/app, recuperación USB ensayada. **Gate:** tests host/Android, lint, firmware y smoke físico V3.

## 2. UX/UI modular (M1)
**Problema:** `LabHubScreen.kt` concentra conexión, autodiagnóstico, firmware, red, GPIO, ADC, secuencias y RGB en scroll único.
- Mantener tema oscuro mate/claro, Material3, tamaños accesibles, scroll y navegación atrás funcionales.
- Pantalla **Centro**: estado de conexión/identidad/seguridad; entradas **Programas**, **GPIO**, **Sensores**, **Diagnóstico** y **Red**.
- **RGB**: reutilizar controles RGB existentes; selector BLE / Wi-Fi, readback, estados diferenciales sin duplicar implementación.
- Dividir vistas de presentación y lógica de red, estado del dispositivo, mensajes y errores; componentes reutilizables para cards, acciones y estados.
- Mostrar requisitos y explicación de botones deshabilitados; nunca afirmar vinculación segura por el simple hecho de tener un ID.
**Salida:** prototipos y capturas reales, TalkBack, escalado de fuente, densidad compacta, pantallas pequeñas/rotación. **Gate:** pruebas navegación y screenshots.

## 3. Editor offline y proyectos (M2)
**Ruta nueva:** `feature/programs`, modelo `core/programs`.
- Escribir, pegar, importar `.txt`, `.ino`, `.cpp`, `.h` mediante SAF; detectar formato por extensión y contenido; no ejecutar contenido al abrir.
- Límite explícito de tamaño y UTF-8 válido, sin bytes binarios; indicadores con errores recuperables. Conservar saltos de línea y texto sin auto-corregir código.
- Distinguir **Lab Script** y **Arduino C++**. Identificación no es compilación ni ejecución. Sin habilitar botones de carga antes de conectar motor seguro.
- Autosave **solo local**, proyectos nombrados, versión/fecha, copia/renombrado/eliminación, historial básico y exportación sin secretos.
- En etapa siguiente: CodeMirror 6 empaquetado localmente en WebView sin Internet (evaluar rendimiento, a11y y MIT); el MVP usa Compose básico, sin dependencia web.
**Gate:** tests del clasificador/formato/límites y QA de documentos UTF-8, archivos enormes, cancelación, proveedor SAF y cambio de configuración.

## 4. Lenguaje del laboratorio (M3)
**Motor:** ampliar `ExperimentRuntime` mediante parser **declarativo** separado, no `eval` ni C++ arbitrario.
- Gramática versionada con `RGB r g b`, `WAIT ms`, `GPIO pin HIGH/LOW`, `READ ADC1`, `LOOP n`, `STOP`, sin acceso directo a flash, red, secretos o memoria.
- Validador de AST/bytecode: máximo de instrucciones, profundidad de bucles, tiempo, RAM y GPIO; diagnóstico número de línea y mensajes en español.
- Scheduler cooperativo no bloqueante; cancelación inmediata, watchdog y limpieza de GPIO, rollback de estado, prioridades de RGB vs automatización.
- Compatibilidad de secuencias V3: traducir la representación antigua o mantener ruta anterior con pruebas de igualdad.
**Gate:** property tests, fuzz de entrada, tiempos máximos, desconexión, rollover de `millis()`, reinicio y coexistencia BLE/Wi-Fi.

## 5. Programas autónomos del ESP32 (M4)
- Persistir scripts aprobados en almacenamiento de datos aislado por versión y CRC/hash, con cuota y dos versiones (activa/anterior).
- Catálogo **instalado, pendiente, en ejecución, falló, detenido**; subir por fragmentos autenticados e idempotentes, confirmar bytes/hash final.
- Elegir manual, programado o autoarranque **desactivado por defecto** hasta probar condiciones de seguridad y recuperación.
- Desactivar un script no elimina Bluetooth, Wi-Fi o servicios de mantenimiento. Un crash reinicia en modo seguro sin autoarranque.
**Gate:** 100 ciclos de ejecutar/detener, cortes de energía durante escritura, falta de espacio, almacenamiento corrupto, actualización compatible y prueba sin teléfono.

## 6. Arduino C++ real: compilación externa gratuita (M5)
- Editor compatible con `setup()/loop()`, librerías y sketch, errores por archivo/línea.
- Primera integración: asistente local de Windows que invoca **Arduino CLI**, ESP32 core fijado, dependencias permitidas y caché; servicio sólo loopback por defecto y emparejamiento explícito y autenticado si se usa LAN.
- El teléfono envía el proyecto al ayudante emparejado, observa `queued → compiling → tested → artifact`; descarga binario y manifiesto firmado o verifica una firma preconfigurada.
- Aislar proceso de compilación (sin ejecución directa de sketches), cuotas de CPU/tiempo/disco y prevención de path traversal; evitar abrir gRPC Arduino CLI directamente a la LAN.
- Importar `.bin` generado localmente sin requerir PC para instalarlo posteriormente.
- Compilación nativa completa **en Android sin PC** no se prometerá sin un prototipo reproducible de toolchain, almacenamiento, RAM, JDK/core y licencias.
**Gate:** sketch válido, sketch inválido, librerías incompatibles, sin PC, desconexión, hashes reproducibles, firmware core preservado.

## 7. Boot, actualización OTA y recuperación (M6, gate crítico)
- Inventariar particiones efectivas de 4 MB `min_spiffs`, tamaños del binario y espacio de programa/datos. Diseño A/B sólo si ambos slots admiten firmware + margen.
- Antes de habilitar OTA: vinculación segura (M8), paquete firmado, autenticación de cargador, validación de chip/board/partición, tamaño, checksum/firma y rollback real.
- No aceptar firmware C++ arbitrario como extensión que mantendrá servicios: exigir plantilla SDK/base firmada para modo administrado o señalar claramente **reemplazar firmware** como modo experto, con recuperación USB.
- Actualización transaccional, monitoreo post-reinicio, aceptación de imagen sólo tras healthcheck y retorno a versión anterior si falla.
**Gate:** cortar Wi-Fi/corriente, imagen dañada, firma inválida, firmware de otra placa, partición insuficiente, rollback y recuperación USB física.

## 8. Identidad, vinculación y control seguro (M7, previo a OTA)
- Seguir PR #43/44: identidad estable pública **no** equivale a autenticación.
- PoP físico único por equipo, ciclo de enrolamiento, pertenencia local, revocación, rotación de claves y recuperación consciente.
- Proteger GATT WRITE RGB/diagnósticos/API; impedir operaciones peligrosas sobre BLE/HTTP legacy abierto, sin cortar la vía de diagnóstico durante migración.
- Wi-Fi SoftAP sin Internet y credenciales STA: canal de transporte autenticado y protegido para operaciones críticas, sin repetir la clave V3 como «secure pairing».
**Gate:** pruebas de tercero sin credenciales, repetición/replay, reset, reconexión, abuso/rate limiting y revocación.

## 9. GPIO y periféricos físicos (M8)
- Registrar topología real RGB de GPIO27/25/26, polaridad y resistencias con prueba del LED físico.
- Salidas de laboratorio 18/19/21/22/23; ADC1 sin conflicto Wi-Fi; prohibir ADC2 concurrente Wi-Fi.
- Formularios validados con tabla/mapa de pines, bloqueo con dueño del recurso, condición eléctrica y advertencias de máximo 3.3 V.
- Implementar PWM genérico, I²C, sensores y potenciómetros de forma incremental y tras verificar cableado.
**Gate:** pruebas eléctricas e instrumentales y regresión de `PinManager`.

## 10. Herramientas avanzadas gratuitas (M9)
- Biblioteca visual de plantillas, editor de pasos, bloques Blockly opcional, generación de script versionado, no ejecutar JS remoto.
- Consola de logs con clasificación temporal y exportación redactada, diagnósticos por capas (app/red/firmware/GPIO), trazas sin credenciales.
- Dashboard sensores con límites de telemetría, exportación CSV, historial local acotado y reconexión.
- Multi-ESP32 sólo tras identidad/autorización y estado correcto por equipo.
**Gate:** mediciones de rendimiento (RAM/CPU/batería Android/latencia) y accesibilidad.

## 11. QA, packaging y release (M10)
**Automático:** `python scripts/check-contracts.py`, `scripts/verify-local.ps1`, tests C++ host, Kotlin JUnit, `lintDebug`, `assembleDebug`, `arduino-cli compile`, análisis de presupuesto por slot OTA, SBOM/licencias y verificadores de credenciales.
**Físico:** ESP32-D0WD-V3 4 MB en COM5 (puerto mutable), Android, RGB, BLE, SoftAP, Wi-Fi sin Internet, LED polaridad/corriente, scripts offline, modo seguro y recuperación USB.
**Artifacts:** APK debug + firmware.bin + SHA256 + commit + resultado de pruebas y registro B1–F1 y V4. Ningún test simulado sustituye hardware.
**Release:** mantener deshabilitado hasta aprobación de seguridad; debug debe permanecer instalable en todo incremento compatible.

## 12. Plan de entregas con dependencias

| Orden | PR (propuesto) | Objetivo | Depende de | Listo cuando |
| --- | --- | --- | --- | --- |
| 1 | `feat/v4-program-editor-master-plan` | Contrato del sistema dual + editor offline limitado | V3 main | UI no permite subir/ejecutar aún; parser probado |
| 2 | `refactor/v4-lab-navigation` | Centro modular + RGB sin duplicación | PR1 y PR44 | Funciones V3 accesibles y navegables |
| 3 | `feat/v4-script-parser-runtime` | AST seguro y runtime no bloqueante | PR1 | Fuzz y regresiones V3 |
| 4 | `feat/v4-project-library` | Proyectos locales/import/export | PR1 | Persistencia y migración de V3 |
| 5 | `security/v4-ble-ownership` | PoP, vinculación, permisos | PR43/44 | Control no autorizado rechazado |
| 6 | `feat/v4-device-program-storage` | Cargar scripts declarativos | PR3/5 | Upload idempotente, cuota, recuperación |
| 7 | `feat/v4-arduino-build-companion` | Compilador PC Arduino CLI | PR1 | Logs, hash y binario compatible |
| 8 | `feat/v4-ota-ab-rollback` | Instalar firmware firmado | PR5/7, particiones | rollback USB y QA física |
| 9 | `feat/v4-blockly-sensors` | Bloques y periféricos | PR3/6 | Consistencia entre bloques y AST |
| 10 | `qa/v4-full-regression` | Integración, APK y reportes | 1–9 | release gate evidenciado |

### Criterios de aceptación integrales
- Abrir/importar/pegar código desde Android funciona sin modificar firmware actual.
- Script compatible se instala y ejecuta realmente en placa sin PC ni Internet, sin romper núcleo/control.
- Sketch `.ino` real se compila desde agente local gratuito y genera binario verificado.
- OTA exige prueba criptográfica y admite rollback probado; no habilitar botón antes de cumplirlo.
- V3 RGB BLE/Wi-Fi + hardware persisten; todas las rutas de pantalla accesibles y errores comprensibles.
- PR abiertos se revisan individualmente: **nunca merge masivo a ciegas**.
- `main` queda instalable tras cada PR mergeado y acompañado de test log y hashes.

### Procedimiento local de pruebas en Windows
```powershell
git fetch origin
git switch main
git pull --ff-only origin main
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
# Después de instalar y verificar OTA/firmware en ESP32, probar la APK debug en el teléfono.
# Flash USB sólo si la persona lo confirma y tras verificar esquema de particiones/backup.
```

### Evaluación de herramientas sin costo
- **Kotlin + Jetpack Compose**: núcleo Android existente.
- **Android SAF/OpenDocument**: acceso por consentimiento a `.txt/.ino/.cpp/.h`, sin permiso amplio de almacenamiento.
- **CodeMirror 6 (MIT)**: candidato para editor resaltado, integrar assets locales. https://github.com/codemirror/dev
- **Blockly (Apache-2.0)**: candidato a bloques visuales, traductor a DSL seguro. https://github.com/RaspberryPiFoundation/blockly
- **Arduino CLI (GPL-3.0)**: compilador externo en PC, respetar licenciamiento/distribución. https://github.com/arduino/arduino-cli
- **ESP-IDF OTA**: mecanismos A/B, app verification y rollback según versión fijada del core. https://docs.espressif.com/projects/esp-idf/en/stable/esp32/api-reference/system/ota.html
- **LittleFS / NVS**: evaluar particiones reales antes de comprometer almacenamiento persistente de scripts.

### Control del cambio
Cada PR debe incluir: alcance, baseline SHA, contrato/endpoints, requisitos de seguridad, migración, matriz de tests y evidencias, riesgos de compatibilidad y rollback. El merge a `main` exige revisión y pruebas disponibles; si GitHub Actions está bloqueado, ejecutar localmente y adjuntar salida verificable. Una compilación no demuestra pruebas físicas ni habilita OTA/seguridad.
