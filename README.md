# RGB Controller — Android Kotlin + ESP32 BLE

Aplicación Android Kotlin/Jetpack Compose para controlar un LED RGB conectado a un ESP32 por Bluetooth Low Energy, sin Wi-Fi ni nube.

## Empezar en Windows
1. Instala **Android Studio** y SDK Platform 34, Build Tools 34, JDK 17.
2. Clona este repositorio y abre la carpeta `android/` en Android Studio.
3. En la raíz del repositorio, ejecuta:

```powershell
git clone https://github.com/ErickRFM/leds.git
cd leds
powershell -ExecutionPolicy Bypass -File .\android\build-debug.ps1
```

El script descarga Gradle 8.7 desde el sitio oficial y compara su SHA-256 publicado, ejecuta tests/lint y compila el APK en `android/app/build/outputs/apk/debug/app-debug.apk`. Utiliza `-SkipTests` para compilación rápida de depuración (no equivale a tests aprobados).

Alternativamente, con Gradle 8.7 instalado: `cd android; gradle testDebugUnitTest lintDebug assembleDebug`.

**Nota del Wrapper:** el repositorio incluye `android/gradlew` y `android/gradlew.bat`, pero el JAR binario oficial no estaba disponible para incorporarlo mediante el conector actual. Por eso el script recomendado no depende del Wrapper; no invoques `gradlew` hasta incorporar `gradle-wrapper.jar` oficial.

## ESP32
1. Instala Arduino IDE y el paquete `esp32 by Espressif Systems` de la serie 3.x.
2. Abre `firmware/esp32_rgb/esp32_rgb.ino`.
3. Placa: **ESP32 Dev Module** para ESP32 clásico, o el modelo correspondiente a tu hardware compatible con BLE.
4. Conecta con una resistencia por color: GPIO 27 rojo, GPIO 25 verde, GPIO 26 azul; COM a 3V3 si es ánodo común (predeterminado). Cátodo común: COM a GND y cambia `COMMON_ANODE=false`.
5. Compila, sube y abre el Monitor Serie a 115200.
6. Instala APK, permite Bluetooth, busca `RGB-ESP32` y conecta. Prueba colores, brillo, presets, arcoíris, respiración, fade y detener.

## Backend
El ESP32 **es el servidor de aplicación embebido**: expone características GATT BLE, valida mensajes y ejecuta animaciones. No requiere API REST ni base de datos remota. UUIDs y comandos exactos: `docs/PROTOCOLO_BLE.md`.

## Seguridad y estado
El prototipo BLE permite conexiones sin enrolamiento seguro. **Solo usar en pruebas supervisadas**, hasta implementar emparejamiento/bonding/autorización. Una compilación sin errores tampoco sustituye la prueba de Bluetooth y LED físicos.

Ver `docs/PLAN_MAESTRO.md` y `docs/CRITERIOS_DE_ACEPTACION.md`.
