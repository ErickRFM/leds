# Biblioteca persistente de código V4

## Implementación
- Codec puro Kotlin CodeProjectLibrary para proyectos Arduino y scripts: 12 proyectos, 40 caracteres nombre, 32 KiB por fuente, formato CODE_PROJECTS_V1.
- Validación UTF-8, tipos, integridad estructural y nombres únicos; corrupción NO se interpreta como biblioteca vacía.
- CodeProjectStore escribe en almacenamiento privado de la app mediante AtomicFile y Mutex.
- Ninguna credencial ESP32 se incluye en el modelo ni se almacena.
- Pruebas JVM de roundtrip Unicode, actualización, corrupción, límites y nombres.

## Pendiente
Conectar la pantalla Programas cuando finalice la integración de GPIO/RGB; el editor se está modificando en los PR #52/#56.
Esta entrega aislada no cambia firmware, comunicación BLE/Wi-Fi, OTA, ni navegación. No expone el almacenamiento fuera del app sandbox.
Verificación requerida: testDebugUnitTest, lintDebug, assembleDebug y comprobar recuperación tras cierre de proceso antes de declarar funcional la UX.
