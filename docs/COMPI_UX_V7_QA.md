# Compi V7 — UX/UI, navegación y pruebas de aceptación

## Alcance verificable

Rama: `feat/compi-ux-master-v7`. Partida desde `main` posterior a PR #72. El trabajo cambia UI Android, no modifica firmware ni relaja las compuertas de publicación. No marca flasheo desde Android, OTA firmado o autorización BLE como terminados.

## Pantallas y flujos manuales (APK debug)

| ID | Pasos | Resultado esperado |
|---|---|---|
| UX-01 | Inicio → LED → Inicio, repetir 3 veces | Sin bloqueo, pantalla de Inicio correcta |
| UX-02 | LED desconectado → Buscar ESP32 → Inicio | Navegación disponible sin conexión |
| UX-03 | Inicio → Código → Inicio → Centro → Inicio | Rutas correctas y sin pantallas duplicadas |
| UX-04 | Centro → Hardware Lab → Diagnóstico/GPIO/Sensores/Experimentos | Cada sección aparece sin obligar a recorrer un formulario extenso |
| UX-05 | Hardware Lab sin clave → Diagnóstico | Mensaje visible y acción Configurar conexión; controles de red siguen protegidos |
| UX-06 | Introducir clave inválida; intentar operación | Validación de clave, sin comando remoto ni falsa conexión |
| UX-07 | Hardware Lab con ESP32: generar error en operación de sección inferior | Snackbar visible y explicación contextual, no solo texto al pie |
| UX-08 | Código: escribir un script; Pegar otro desde portapapeles | Diálogo de reemplazo; Cancelar conserva texto original |
| UX-09 | Código: importar contenido diferente con código existente | Confirma antes de sustituir; Cancelar conserva fuente |
| UX-10 | Código: importar/pegar con editor vacío | No exige confirmación |
| UX-11 | LED: cambiar BLE→Wi-Fi→BLE mientras hay petición Wi-Fi pendiente | Respuesta tardía no cambia el estado visible de BLE |
| UX-12 | LED: probar rueda, sliders RGB, brillo y switch con ESP32 real | Estado pendiente se distingue del confirmado, comandos correctos |
| UX-13 | Wi-Fi SoftAP sin Internet; cambiar entre pestañas | Ningún fallback a Internet/celular; sin claves guardadas |
| UX-14 | Ajustes: cambiar tema claro/oscuro/sistema | Pantallas legibles, preferencias persistentes |
| UX-15 | Ajustes → Compi Studio → Inicio | Navegación de pestañas sin duplicados |
| UX-16 | Probar sin ESP32, BLE apagado, permisos denegados y reconexión | Mensajes accionables y ningún éxito inventado |
| UX-17 | Fuente 200 %, TalkBack, 320dp y 360dp | Objetivos de acción alcanzables, textos esenciales visibles |
| UX-18 | Rotar, suspender y reanudar app durante edición/conexión | Cambios locales recuperables y conexión sin falso READY |

## Validación automatizada

- `testDebugUnitTest`: políticas de proyectos y reemplazo del editor, RGB y contratos existentes.
- `assembleDebugAndroidTest`: compila el paquete de pruebas Compose.
- `lintDebug`: fallos estáticos Android.
- `assembleDebug`: APK debug.
- `connectedDebugAndroidTest`: **requiere emulador o teléfono**, no equivale a `assembleDebugAndroidTest`.
- Los tests de firmware y hardware remoto del CI no demuestran cableado ni LED físico.

## Construcción en Windows

Desde la raíz del repo:

```powershell
git switch main
git pull --ff-only origin main
powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1
```

Script recomendado porque la distribución actual del repositorio no incluye `gradle-wrapper.jar` verificado. Requiere JDK 17 y Android SDK 34.

APK: `android/app/build/outputs/apk/debug/app-debug.apk`. Obtener hash con
`Get-FileHash .\android\app\build\outputs\apk\debug\app-debug.apk -Algorithm SHA256`.

También se permite descargar el artefacto `rgb-controller-debug` de una ejecución **verde del commit que se desea probar** en [GitHub Actions](https://github.com/ErickRFM/leds/actions/workflows/android-ci.yml), verificando `SHA256SUMS.txt`. Evitar APK antiguas.

## Riesgos explícitos pendientes para producción

1. Enrolamiento/autorización BLE legado y validación de propiedad: pendiente.
2. Conectividad Wi-Fi debug SoftAP sin TLS: solo laboratorio.
3. OTA firmada, recuperación y rollback físico: no aprobados.
4. Firmware Arduino arbitrario desde Android: no habilitado.
5. Pruebas físicas de LED, GPIO 27/25/26, ADC y reconexión real: ejecutar con placa y teléfono.
6. Pruebas reales de TalkBack, 320dp y dispositivos OEM: por realizar.

## Evidencia que debe registrarse

Anotar para cada UX-XX: dispositivo/Android, commit, firmware ESP32, red/transportes, resultado PASS/FAIL, captura/log **sin claves** y causa/corrección. No cerrar `PRODUCTION_READY` solo por CI verde.
