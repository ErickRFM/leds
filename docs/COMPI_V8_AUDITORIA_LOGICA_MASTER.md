# Compi V8 — auditoría lógica, plan de remediación y protocolo de QA

**Base:** main `7f4680c` (V7, PR #73).  
**Intervención:** `fix/compi-v8-reliability-audit` (PR #74).  
**Hardware:** ESP32 clásico, GPIO R=27, G=25, B=26; polaridad configurada COMMON_ANODE=true.  
**Alcance:** fiabilidad de Android + ESP32, protocolos, sesiones, seguridad, código local, sensores y compatibilidad.  
**Restricción:** NO entregar release público hasta el enrolamiento/autorización BLE criptográfico y QA física.

## 1. Principios que no se pueden romper

1. Preservar cinco secciones: Inicio, LED, Código, Centro y Ajustes, incluido el retorno a Home corregido en PR #72.
2. No permitir ejecución directa de Arduino C++ desde una importación de TXT/INO. La función segura disponible ejecuta solo DSLs de GPIO/RGB con límites estrictos.
3. No añadir endpoints OTA, subir binarios ni habilitar variantes `release` sin firma, rollback, permisos, evidencia de hardware y autorización real.
4. La red HTTP `192.168.4.1` está limitada a SoftAP WPA2 y APK debug. Prohibido fallback a celular/LAN, escribir credenciales en DataStore o imprimirlas en logs.
5. La API de laboratorio requiere `X-Device-Key`, pero eso no equivale a propiedad ni enrolamiento BLE.
6. GPIO de LED (27/25/26) reservados. Experimentos de salida: 18/19/21/22/23; ADC1: GPIO34 y asignaciones validadas. No usar GPIO0 para prácticas.
7. La app solo muestra `READY` BLE después de MTU, CCCD, lectura inicial válida e identidad opcional.
8. No sustituir componentes de Compose funcionales ni introducir controles que simulen operaciones inexistentes.

## 2. Plan de trabajo ejecutable y trazabilidad

| Hallazgo | Refactorización / trabajo | Prueba de aceptación | Condición de cierre |
|---|---|---|---|
| L-01 P0 BLE sin autorización | Mantener release bloqueado; diseñar enrolamiento con prueba física de posesión, claves por dispositivo, vinculación, revocación y escritura GATT autenticada. No «arreglar» cambiando únicamente etiquetas o MAC/ID pública | Un tercero sin enrolar recibe DENY para SET/ON/OFF/FX; propietario autenticado sí; revocación y factory-reset real | **Pendiente: no declararlo resuelto** |
| L-02 P1 scripts interrumpidos por comandos inválidos | Validar antes de arbitrar; comando manual válido distinto de GET tiene prioridad. Canal BLE pasa por inbox del loop; Wi-Fi valida antes de detener | Comando FX/SET malformado durante script, script sigue; comando válido detiene; GET no detiene | CI host + banco real |
| L-03 P1 búsqueda vs reconexión BLE | Al iniciar escaneo cancelar callbacks y tareas de reconexión; cerrar GATT obsoleto, limpiar estado, registrar selección explícita | Desconectar con retry pendiente y buscar otro ESP32; ningún retry antiguo puede arrebatar la sesión | Test emulador/hardware |
| L-04 P1 MTU y falsa disponibilidad | No descubrir servicios si falla `requestMtu`; exigir callback de MTU suficiente antes de CCCD y READ. Timeout reconecta o falla con mensaje | Simular inicio MTU fallido, callback fallido, MTU <64, conexión abortada; con 128+ READY tras READ | Pruebas inyectadas y 2 teléfonos |
| L-05 P1 sesión Wi-Fi dividida | `CompiLabSession` retenida en ViewModel, RAM solamente; LED, Studio y Hardware Lab leen la misma clave; revisión monotónica invalida respuestas antiguas; `Mutex` serializa HTTP | Cambiar clave/transporte durante solicitud; respuesta antigua ignorada. Navegar entre módulos no pide reingresar clave, reiniciar proceso sí | CI sesión + teléfono |
| L-06 P1 confirmaciones falsas RGB | Versión monotónica para nuevas lecturas BLE/HTTP; confirmación exige READBACK nuevo y valores idénticos; timeout muestra fallo, NO éxito; una respuesta tardía correcta puede recuperar el estado | Cortar Bluetooth/red al mover slider; mensaje no dice «sincronizado». Tras reconectar GET/estado actual autoritativo | CI + placa |
| L-07 P1 perder contenido del editor | Todos los caminos que reemplazan código pasan por `EditorReplacementPolicy`: importar, pegar, ejemplos GPIO/RGB y proyectos | Con contenido diferente: pregunta y Cancelar conserva; archivo vacío no pregunta; exportar no flashea | CI + test UI |
| L-08 P2 último dispositivo BLE | Persistir MAC únicamente cuando `BleManager` publica READY verificado, observado a nivel de `MainAppNavHost`, no durante una pantalla transitoria | Navegar fuera de Dispositivos mientras conecta; al llegar a READY se recuerda la dirección | Dispositivo Android |
| L-09 P2 ADC oculto | Vincular muestreo a sección Sensores y clave actual; cancelarlo al abandonar la sección o la vista; cap 40 muestras/20 s | Cambiar de Sensores a GPIO durante muestreo; no siguen lecturas ocultas ni fuga de proceso | Test UI + placa |
| L-10 P1 concurrencia de motor RGB | Cola BLE de longitud fija, protegida con `portMUX`, consumida por el loop principal; comandos Wi-Fi/scripts se ejecutan en mismo contexto de motor; app serializa HTTP | 16 mensajes en FIFO, saturación rechazada, callback no toca PWM ni script; manual válido tiene prioridad | Host C++ y placa |

### Fases y dependencias

**Fase A — Línea base:** consultar main/PRs, revisar contratos, documentar hardware y proteger release.  
**Fase B — Estado BLE:** L-03, L-04, L-08. Se completa antes de verificar comandos fiables.  
**Fase C — Firmware RGB:** L-02 y L-10. Los callbacks GATT no deben alterar PWM/efectos directamente.  
**Fase D — Sesión compartida y arbitraje:** L-05, L-06; validar confirmación después de nuevo `STATE`.  
**Fase E — Edición y laboratorio:** L-07 y L-09; pruebas de recuperación de estado.  
**Fase F — QA integral:** CI verde en último commit, APK del `main` fusionado, casos Android/hardware reales y reporte de límites.  
**Fase G — Seguridad para release:** L-01 exige diseño/provisión criptográfica y QA adicional; mantener `release` inhabilitado hasta pasar el gate.

La Fase G **no** puede certificarse ejecutando `assembleDebug` ni simulando propietario a partir de una dirección MAC. Se trabaja por separado para no romper el BLE experimental existente.

## 3. Contratos de estado

### BLE

`Disconnected → Scanning / Connecting → Discovering (MTU válido) → CCCD → READ → Ready`.

Ante error, timeout, permisos retirados o enlace roto: limpiar servicios, comandos pendientes, estado RGB y conexión verificada. Un scan explícito invalida un retry anterior. Reintento máximo 3 con backoff; nunca duplicar un GATT activo.

**No confundir:** confirmación de escritura GATT ≠ lectura de estado RGB actualizado; la pantalla debe esperar un `STATE` nuevo.

### Wi-Fi laboratorio

`WithoutKey → Verifying → Ready(last verified) → Sending → Readback → Ready/Error`.

Al modificar clave/transporte se incrementa generación y se borran READBACK antiguos. El secreto solo vive en el ViewModel de la app, desaparece al cerrar su proceso. Operaciones de red con `Mutex` compartido para no superponer comandos RGB, scripts, GPIO ni sensores. Wi-Fi del ESP32 no implica Internet.

Un estado `Ready` Wi-Fi representa **última petición verificada**; la conectividad efectiva puede desaparecer entre solicitudes. Al volver a LED se revalida, y los fallos borran la disponibilidad. No afirmar que un LED físico encendió a partir de HTTP 200: es un estado lógico del firmware.

### Scripts GPIO/RGB

- GPIO: una salida por secuencia, GPIO aprobado, `WAIT` de 100–2000 ms, 16 pasos / 20 s; STOP y reset seguro.
- RGB: límite 512 B, 32 instrucciones, 20 s; admite RGB/WAIT/ON/OFF/STOP, no Arduino C++ arbitrario.
- Comando manual **válido** gana prioridad; comando malformado y GET no interrumpen; solicitudes conflictivas se rechazan con error.
- Experimentos deben completar y liberar recursos aun ante timeout o desconexión del usuario. Para pérdida de energía, salida segura al arrancar.

## 4. Batería de regresión Android

| Caso | Secuencia | Resultado esperado |
|---|---|---|
| A01 | Inicio→LED→Inicio, 5 veces | Home correcto |
| A02 | Centro→Dispositivos→Home | Sin ruta bloqueada |
| A03 | BLE apagado/permiso denegado→Buscar | Estado de error comprensible |
| A04 | Buscar mientras retry pendiente | No reaparece enlace antiguo |
| A05 | BLE MTU inválido/sin callback | No READY |
| A06 | CCCD/READ fallidos | No READY y cleanup |
| A07 | READY mientras abandonas Dispositivos | Dirección recordada |
| A08 | Slider RGB, desconectar antes de STATE | No éxito simulado |
| A09 | Slider RGB, STATE previo idéntico, sin respuesta nueva | No confirmación falsa |
| A10 | Respuesta tardía exacta después de timeout | Confirmación recuperada |
| A11 | LED Wi-Fi→Studio→Centro, misma sesión | Clave compartida en RAM |
| A12 | Cambiar clave/transporte durante request | Lectura vieja ignorada |
| A13 | Desconectar SoftAP y permitir datos móviles | No fallback WAN |
| A14 | Emitir >32 comandos Wi-Fi pendientes | Backpressure, conexión invalidada sin memoria infinita |
| A15 | Abrir editor con texto → cargar ejemplo GPIO | Diálogo previo |
| A16 | Cancelar sustitución | Texto original intacto |
| A17 | Cargar ejemplo RGB con texto previo | Misma protección |
| A18 | Guardar proyecto y reabrirlo | Contenido íntegro |
| A19 | Reiniciar app tras clave introducida | Clave borrada |
| A20 | Sensores: iniciar ADC→cambiar a GPIO | Muestreo cancelado |
| A21 | Scripts GPIO/RGB: Stop o error | Sin falso éxito, límites correctos |
| A22 | Comandos BLE+Wi-Fi seguidos a la placa | Estado serial y recursos consistentes |
| A23 | 320/360/393dp, TalkBack y fuente 200% | Sin controles esenciales recortados |
| A24 | Rotación/recreación con BLE conectado | Retención de ViewModel y readback real |
| A25 | Ajustes tema claro/oscuro y regresar | Sin duplicar rutas ni sesiones |
| A26 | SoftAP responde HTTP 403/409/timeout | Error recuperable, sin reset peligroso |

**Herramientas:** `testDebugUnitTest`, `assembleDebugAndroidTest`, `lintDebug`, `assembleDebug`, `connectedDebugAndroidTest` con teléfono/emulador. La compilación de tests UI no equivale a ejecutarlos.

## 5. Matriz de banco ESP32

1. **Cableado:** confirmar ánodo común real; resistencias individuales por canal 220–330Ω; alimentación, GND compartida y límite eléctrico de GPIO.
2. **PWM:** GPIO 27 rojo, 25 verde, 26 azul; verificar 0, 128, 255 y brillo; medir con multímetro o LED de banco.
3. **Efectos:** RAINBOW, BREATHE, FADE, ON/OFF, FX STOP. Tras desconectar teléfono confirmar autonomía del firmware.
4. **BLE:** conectar/desconectar/reconectar 30 veces; radio apagada durante WRITE/MTU/READ; validar no duplicar callbacks, no bloquear 5 s y no mostrar READY prematuro.
5. **Wi-Fi:** conectar únicamente a ESP32-Control-Lab; comprobar sin red, sin clave y 403, sin rutas de salida a Internet. No publicar clave o contraseña en capturas.
6. **Concurrencia:** ráfaga de 16+ comandos BLE junto a Wi-Fi/efecto; revisar estado final determinístico; correr GET mientras script sigue; malformado sin cancelación.
7. **GPIO:** probar únicamente pines permitidos con resistencias; comprobar relevo de recursos, 30 s timeout manual y secuencias 20 s.
8. **ADC:** entrada GPIO34 ≤3.3 V, 40 muestras/20 s; datos RAW, no valores de voltaje calibrados.
9. **Persistencia:** modificar RGB, esperar 2 s, reiniciar ESP32; validar snapshot y CRC; corromper snapshot solo en banco y comprobar arranque seguro.
10. **OTA:** comprobar únicamente preflight y presupuesto; NO flashear remoto ni promocionar la actualización a producción.

Registrar Android/OEM, versión, SHA de firmware, branch/commit, número de caso, PASS/FAIL, logs sin secretos y foto/captura. Mantener bitácora explícita; casos no ejecutados = `PENDIENTE`, nunca PASS.

## 6. Pruebas automáticas y artefactos

- `firmware-core-tests`: C++ para estados de pines, codec RGB, scripts limitados, cola BLE y prueba de release seguro.
- `firmware`: Arduino CLI con FQBN y core del perfil de placa; presupuesto flash y sonda de provisioning compile-only.
- `android`: unit test, compilación de instrumentación, lint y APK debug con SHA256.
- `windows-board-profile`: verificaciones USB de perfiles, preflight de sketch sin flasheo.

El CI remoto no dispone de tu ESP32 ni de tus resistencias; su resultado verifica contratos/build, NO cableado ni seguridad física.

**Windows, desde la raíz de Compi tras merge:**

```powershell
git switch main
git pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1
Get-FileHash .\android\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

O descargar artefacto `rgb-controller-debug` del job de `main` cuyo SHA coincida con el merge.

## 7. Automatizaciones V6 — PR #71

PR #71 integra un reloj RGB por intervalos y añade endpoints SoftAP. **No mezclar ni fusionar automáticamente** con V8: la lógica de arbitraje del RGB cambió. Antes de incorporarlo:

- Actualizar rama sobre `main` V8 sin perder cambios.
- Comprobar que comando manual BLE válido y Wi-Fi detenga automatización; GET/malformados no la aborten.
- Exclusión mutua entre `RgbScriptRuntime`, `RgbAutomationClock` y `ExperimentRuntime`.
- Ejecutar CI firmware y pruebas host.
- Validar físicamente intervalos, reinicio, exclusión con script y desconexión.
- Mantener desactivadas release y automatizaciones remotas de larga duración no autorizadas.

## 8. Gates de calidad y de seguridad

### Gate de integración V8
- Último commit de PR pasa **cuatro jobs**.
- Firmware y Android compilan; ninguna función vieja se ha retirado.
- Tests nuevos de sesión y cola incluidos.
- Revisión de diff sin secretos ni habilitaciones inseguras.
- Merge a `main` si CI verde; nueva ejecución de `main` verifica APK descargable.

### Gate de hardware
- Banco real verifica casos BLE/Wi-Fi/RGB/GPIO/ADC y electrónica, registra evidencia; no sustituir con CI.
- Si algún caso falla, corregir en nueva rama con regression test y repetir.

### Gate de release público
**Bloqueado.** Necesita posesión física comprobada, claves por ESP32, autorización GATT vinculada a propietario, revocación, mitigación de replay, OTA con verificación criptográfica real, rollback, recuperación y pruebas de seguridad. El ID público, una dirección MAC, el emparejamiento del sistema por sí solo y las contraseñas del SoftAP no son evidencia de propiedad.

## 9. Definición de completado

Se considera **V8 integrada para pruebas de laboratorio** al fusionar un PR con CI verde y guía de QA publicada.  
Se considera **V8 validada físicamente** solo después de completar y documentar la matriz de banco.  
Se considera **lista para distribución pública** solo tras superar el gate de seguridad, separado de UX y CI.

No usar el texto «todo funcional» para una prestación que dependa de equipos aún no comprobados.
