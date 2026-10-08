# Android Kotlin

Ver [README principal](../README.md). Abre esta carpeta en Android Studio, selecciona JDK 17 y SDK 34.

En Windows, desde la raíz, ejecuta `powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1`. El script descarga Gradle 8.7 de su distribución oficial, verifica el hash, ejecuta tests, lint y build.

No confundas resultados de compilación de GitHub Actions con pruebas físicas BLE: primero flashea el ESP32, después instala el APK y verifica la conexión.

Pines del ESP32: GPIO 27 rojo, 25 verde y 26 azul; resistencias 220–330 Ω individuales. Firmware en `../firmware/esp32_rgb/esp32_rgb.ino`.

**Seguridad:** el firmware de laboratorio no autentica teléfonos cercanos.
