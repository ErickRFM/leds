# Biblioteca persistente de código V4

## Implementación
- Codec puro Kotlin CodeProjectLibrary para proyectos Arduino y scripts: 12 proyectos, 40 caracteres nombre, 32 KiB por fuente, formato CODE_PROJECTS_V1.
- Validación UTF-8, tipos, integridad estructural y nombres únicos; corrupción NO se interpreta como biblioteca vacía.
- CodeProjectStore escribe en almacenamiento privado de la app mediante AtomicFile y Mutex.
- Ninguna credencial ESP32 se incluye en el modelo ni se almacena.
- Pruebas JVM de roundtrip Unicode, actualización, corrupción, límites y nombres.

## UI y alcance
La pantalla Programas unificada conserva GPIO, RGB y Arduino C++, y añade el panel Mis proyectos: Guardar, Abrir y Eliminar con confirmación. Al abrir un proyecto se recupera el texto, nunca se envía a la placa por sí solo.
Esta entrega no modifica firmware, comunicación BLE/Wi-Fi, OTA ni rutas de navegación. No expone archivos fuera del almacenamiento privado de Android.

## Validación pendiente
Exigir testDebugUnitTest, lintDebug y assembleDebug; pruebas Android reales de guardado, cierre forzado, reapertura, rotación, corrupción recuperable y caracteres Unicode. Si hay fallo al leer una biblioteca, no se sobrescribe silenciosamente con cero proyectos.
