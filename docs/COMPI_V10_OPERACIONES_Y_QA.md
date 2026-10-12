# Compi V10 — estabilización funcional y ruta segura a V11/V12

**Base:** main V9 (`6309235`), PR #75 aprobado.  
**Trabajo:** PR #76 (recuperación de Studio, contrato de capacidades, operación compartida y barreras debug).  
**Compatibilidad:** firmware y GATT V9 existentes, sin modificación de pinout, autorización o particiones flash.  
**IMPORTANTE:** CI de Android puede compilar pruebas UI; `connectedDebugAndroidTest` requiere teléfono o emulador para EJECUTAR las pruebas.

## 1. Estado del plan maestro

| Fase | Resultado de esta intervención | Gate no satisfecho |
|---|---|---|
| 0 Base y Git | Rama de feature sobre V9 y PR, CI obligatorio, firmware untouched | Ninguno para esta etapa |
| 1 Navegación | Se conserva V9 de 4 tabs y Back anidado, regresiones CI existentes | Validación real en Android con botón y gestos |
| 2 Studio | Borradores GPIO, RGB y Arduino con archivo atómico versionado, recuperación tras cierre y aviso de error, motor correcto; apertura de proyecto confirma reemplazo | Pruebas de escritura con almacenamiento casi lleno y muerte de proceso en teléfono |
| 3 Transporte | Reglas puras explícitas por operación/transporte; serialización de solicitudes HTTP compartida; operaciones de usuario presentan cola/ejecutando/confirmado/fallido/cancelado | Integración completa de transporte BLE seguro y soporte de capacidades negociadas por dispositivo |
| 4 GPIO real | Conserva telemetría V9: `GET /v2/gpio/status` autenticado y lectura BLE informativa | Verificación física de voltajes, leases y GPIO19 |
| 5 Feedback y recuperación | Studio y Lab exponen transiciones de operación verificables, sin afirmar que un 200 demuestra voltaje físico | Revisión TalkBack y tareas largas físicas |
| 6 Seguridad BLE | Guard de permisos prohíbe GPIO, scripts, Wi-Fi y OTA por BLE V1; Wi-Fi Lab también exige APK debug en tiempo de ejecución | **P0 release: enrolamiento criptográfico real en firmware, revocación, anti-replay, recuperación** |
| 7 Automatizaciones | El PR #71 permanece separado para reconciliar sobre V10; no se anuncian como disponibles | Arduino compile + pruebas reales del reloj y de conflicto manual/script |
| 8 QA | Unit tests nuevos JVM, lint/compile; trabajo entregado a CI | Instrumentación ejecutada en dispositivo, 30 ciclos reconexión, banco ESP32 |
| 9 Entrega | Merge únicamente con checks verdes, APK del SHA resultante | No etiquetar PRODUCTION_READY hasta cerrar 6/8 |

## 2. Contratos de aplicación y seguridad

**CompiDeviceController (objetivo V11):** sesión de dispositivo con ID público no privilegiado, capacidades de firmware verificadas, transportes independientes, ejecución compartida, timeouts y readback causal.

**DeviceCapabilityPolicy (V10):** `READ_RGB`, `SET_RGB`, `EFFECT_RGB` admitidos con conexión BLE V1; `READ_GPIO` BLE solo si existe característica GATT V9. `WRITE_GPIO`, ADC, scripts y configuración de Wi-Fi requieren SoftAP debug con clave local; no se conceden al BLE V1. OTA y automatizaciones sin validar denegadas. Un futuro BLE autorizado requiere **ambas** condiciones `secureBleAvailable` y `authorizedOwner`; el firmware debe confirmar realmente ambos estados, no solo UI.

**CompiLabSession:** credenciales solo en RAM; el `Mutex` arbitra las peticiones HTTP; `userOperation` publica QUEUED/RUNNING/CONFIRMED/FAILED/CANCELLED. La confirmación significa que HTTP respondió, nunca que el LED físico encendió. Cancela solicitudes si abandona pantalla y no interpreta resultado tardío como nueva sesión.

**EditorDraftStore:** archivo privado `compi-studio-drafts-v1.txt` en `filesDir`, `AtomicFile` y bloqueo `Mutex`. Formato `COMPI_EDITOR_DRAFTS_V1`, ocho campos Base64 UTF-8 y CRC32. Cada fuente limitada a 32 KiB, 160 KiB total. No incluye clave ESP32, SSID, MAC o tokens. Un archivo corrupto/incompatible provoca error y **no se sobrescribe automáticamente**; el usuario debe exportar código antes de recuperar manualmente. Autosave tras 500 ms de inactividad de edición; operaciones de cierres abruptos durante menos de ese plazo requieren test físico.

## 3. Matriz de pruebas de regresión

| ID | Pasos | Resultado exigido |
|---|---|---|
| ED01 | Escribir GPIO19; pasar a RGB y crear efecto; volver | Se conserva GPIO y RGB por separado |
| ED02 | Cambiar a Arduino C++ y escribir código | Arduino se guarda independientemente |
| ED03 | Cerrar app tras más de 500 ms y volver | Recupera motor, nombre y código de cada borrador |
| ED04 | Pegar/importar/abrir proyecto que pisa borrador | Diálogo previo; cancelar no pierde nada |
| ED05 | Corromper a propósito archivo de drafts en banco | Error visible y archivo original intacto |
| ED06 | Importar fuente de más de 32 KiB | Rechazo y contenido original conservado |
| ED07 | Guardar proyecto desde CodeProjectsPanel | Continúa sin incluir clave de control |
| OP01 | Comando HTTP válido (GPIO) | Estado RUNNING hasta respuesta, después CONFIRMED |
| OP02 | Cancelar pantalla en medio de una petición | CANCELLED, no falso éxito |
| OP03 | Clave inválida o sin SoftAP | FAILED con explicación y sin fallback celular |
| OP04 | Intentar usar HTTP desde variante no debuggable | Rechazo en LocalLabClient, no solo botón deshabilitado |
| OP05 | Sin BLE conectado: pedir RGB | Denegado |
| OP06 | BLE V1 conectado: RGB y efectos | Permitidos; GPIO write/ADC/scripts/OTA DENY |
| OP07 | BLE V9 con telemetría read-only | READ_GPIO permitido, GPIO WRITE DENY |
| OP08 | BLE secure de papel sin propietario | DENY incluso si se anunció una característica |
| HW01 | GPIO19 HIGH/LOW con LED secundario y resistencia | Nivel lógico y físico correspondientes |
| HW02 | Control RGB pines 27/25/26 | No reclamables como GPIO manual |
| HW03 | Alternar SoftAP ↔ BLE y cambiar de módulo | Sin sesión antigua ni comandos duplicados |
| HW04 | Cortar red durante script | No confirma ejecución; UI recuperable |
| NAV01 | Inicio→Hardware Lab→Regresar→Inicio | Sin rutas bloqueadas ni pestañas duplicadas |
| QA01 | `testDebugUnitTest`, `lintDebug`, `assembleDebugAndroidTest`, `assembleDebug` | Checks verdes |
| QA02 | `connectedDebugAndroidTest` en emulador/dispositivo | Tests REALMENTE ejecutados |
| QA03 | Firmwares ESP32 4 MB / partición min_spiffs con pruebas host | Compila; prueba física separada |

## 4. Ruta segura V11 — autorización BLE real (NO completada)

Diseño antes de implementar:

1. Verificación física de posesión en ESP32 con ventana corta, no derivada de ID o MAC pública.
2. Intercambio autenticado de claves por dispositivo, generación y rotación seguras, NVS protegido.
3. Sesión con nonce y número de secuencia anti-replay para comandos.
4. Autorización en firmware para operaciones elevadas (GPIO, scripts, Wi-Fi), no solo deshabilitar botones Android.
5. Revocación, factory reset mediante procedimiento físico y recuperación tras pérdida de teléfono.
6. Separación de autoridades entre control RGB básico y comandos privilegiados.
7. Fuzzing de GATT y pruebas con dos teléfonos, uno no autorizado y uno propietario.
8. Release solo al demostrar denegación de escritura privilegiada a no propietarios.

**No se implementa un supuesto PIN universal ni se exponen GPIO/OTA por BLE inseguro.**

## 5. Ruta V12 — automatizaciones y multiplaforma

- Reconciliar PR #71 antiguo sobre `main` V10 conservando L-02 (comando inválido no detiene scripts) y L-10 (callback BLE encola y loop muta PWM).
- `RgbAutomationClock` existe y tiene tests host, pero la integración del PR #71 aún requiere compilación completa y QA físico.
- Automatizaciones deben cancelar o rechazar ejecuciones concurrentes con scripts, límite explícito de duración, expiración y parada manual.
- Demostrar que el ESP32 ejecuta intervalos autónomos y que el control manual BLE/Wi-Fi tiene prioridad.
- Implementar perfiles de hardware como datos/contratos validados, nunca activar pines de arranque o flash por presunción.
- OTA requiere firma real, mecanismo de recuperación y rollback antes de habilitarse.

## 6. Compilación / prueba Windows

```powershell
git switch main
git pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1
Get-FileHash .\android\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256
```

Al flashear firmware ESP32 revisar `config/esp32-board.env`, Arduino core y esquema de particiones. No cargar un `.ino` aislado; el firmware incluye `core/` y `modules/`.

**Definición de completado V10 inicial:** build Android y firmware verdes sobre SHA exacto, PR mergeado, artefactos del merge, con limitaciones físicas documentadas. No considerar V11 o V12 completas ni distribución pública hasta pasar los gates descritos.
