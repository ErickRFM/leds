# V4 Arduino C++ · compilación gratuita y recuperación USB

Se puede **editar, importar, copiar y exportar** código Arduino `.ino` desde la APK (`Código → Arduino C++`). El ESP32 no compila texto C++ por sí solo. Para obtener un binario desde Windows (sin nube y sin coste de servicio), usa Arduino CLI y el perfil único `config/esp32-board.env`.

## Preparación

1. Instalar Arduino CLI (licencia GPL-3.0) y el core Espressif `esp32:esp32` versión `3.0.7`.
2. Exportar `programa.ino` de la app y transferirlo a Windows.
3. Ejecutar **compilación solamente**:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-user-sketch.ps1 -Source "C:\ruta\programa.ino"
```

Se genera `local-validation/user-sketch/ImportedSketch.ino.bin`, SHA256 y `build-info.txt`. No toca la placa, no modifica el firmware base. `.txt` con código Arduino C++ también se admite. Este flujo es para sketch de **archivo único**, no resuelve automáticamente librerías externas ni proyectos multiarcivos.

## Reemplazo nativo por USB: peligro explícito

Un `.ino` arbitrario que contenga `setup()/loop()` **reemplaza la aplicación de control**; NO queda encapsulado sobre la base BLE/Wi-Fi y no se puede garantizar que la app reconecte. Sólo para pruebas deliberadas y con recuperación USB previamente demostrada.

Si se decide hacer una sustitución completa, primero respaldar firmware/datos (incluidas particiones NVS) y conocer las opciones de recuperación; después se permite:

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-user-sketch.ps1 -Source "C:\ruta\programa.ino" -FlashUsb -Port COM5 -ReplaceCoreAcknowledgement REEMPLAZAR_FIRMWARE
```

El puerto es un **ejemplo**; comprobar el COM real. No flashear sin confirmación específica y respaldo. Para volver al centro ESP32, usar firmware oficial del repositorio y `scripts/verify-local.ps1 -SkipAndroid -UploadFirmware -Port COMx` según documentación vigente, validando antes el esquema de particiones y credenciales NVS.

## Qué queda por construir

- El compilador accesible **directamente desde Android** por un asistente de PC local autenticado (modo remoto controlado).
- Un build de C++ **administrado** que conserve las funciones base e impida redefinir `setup/loop`; incluso así C++ nativo no ofrece aislamiento de memoria.
- OTA firmado A/B, tamaño/particiones verificadas, rollback real, autorización segura de carga y recuperación probada.
- Pruebas físicas de compilados, pines, conexión y retorno al firmware original.

Este avance implementa **Arduino CLI USB**, no OTA ni ejecución arbitraria sin reflashear. El modo RGB Script ofrece ejecución temporal sin tocar el firmware base.
