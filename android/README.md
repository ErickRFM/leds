# RGB Controller Android (Kotlin + Jetpack Compose)

Aplicación Android nativa bajo `android/`. Servidor BLE en el ESP32, no requiere internet.

## Requisitos
- Android Studio con SDK Platform 34, Build Tools 34 y JDK 17.
- Gradle 8.7. Los scripts `gradlew` y `gradlew.bat` se incluyen; el JAR binario se añadirá cuando se pueda distribuir de forma verificable.
- En ausencia de `gradle-wrapper.jar`, instala Gradle 8.7 y utiliza directamente `gradle` (como CI). No confundir scripts con wrapper completo.

## Compilar
```powershell
cd android
gradle testDebugUnitTest
gradle lintDebug
gradle assembleDebug
```
Salida: `android/app/build/outputs/apk/debug/app-debug.apk`. Alternativa: GitHub Actions -> ejecutar Android + ESP32 CI -> descargar artefacto `rgb-controller-debug` tras resultado `success`.

## BLE
Android 12+ solicita SCAN/CONNECT; en 11 e inferiores el escaneo necesita permiso de ubicación y servicios de ubicación según el dispositivo. Para prueba física: conectar LED RGB con resistencias a GPIO 27, 25, 26 y cargar `firmware/esp32_rgb/esp32_rgb.ino`.
