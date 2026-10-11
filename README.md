# RGB Controller — Android Kotlin + ESP32 BLE

Aplicación Android Kotlin/Jetpack Compose para control RGB por BLE y experimentación ESP32 por Wi-Fi local protegido (exclusivamente en APK debug). No requiere nube.

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
El ESP32 **es el servidor de aplicación embebido**: expone características GATT BLE, valida mensajes y ejecuta animaciones. La V2 laboratorio agrega una API HTTP exclusiva del SoftAP WPA2 para GPIO, Wi-Fi, ADC y experimentos; no permite conexión HTTP por la red del router. No necesita base de datos remota. UUIDs y comandos exactos: `docs/PROTOCOLO_BLE.md`.

## Seguridad y estado
El prototipo BLE permite conexiones sin enrolamiento seguro. **Solo usar en pruebas supervisadas**, hasta implementar emparejamiento/bonding/autorización. Una compilación sin errores tampoco sustituye la prueba de Bluetooth y LED físicos.

Ver `docs/PLAN_MAESTRO.md` y `docs/CRITERIOS_DE_ACEPTACION.md`.

## Actualización V1.1
Tema oscuro mate/tema claro seleccionable, navegación compacta, conexión con estados explícitos, muestreo RGB continuo cada 60 ms, interfaz refinada de presets/efectos, MTU BLE 128 y validación del estado antes de READY. Auditoría, riesgos y verificaciones físicas pendientes: `docs/AUDITORIA_V1_1.md`.

## ESP32 Control Center V2 (en desarrollo)
Fundación modular F0/F1 sobre la versión RGB V1.1 respaldada en `baseline/rgb-controller-v1.1`. La V2 incorpora `PinManager`, `ModuleRegistry` y adaptador LED; La V2 agrega Wi-Fi SoftAP y GPIO de laboratorio bajo clave, pero la conectividad segura de producción, OTA y control LAN con TLS continúan pendientes. Consulta `docs/PLAN_MAESTRO_V2.md` y `docs/V2_FOUNDATION.md`.

## Wi-Fi y laboratorio (desarrollo experimental)
La V2 dispone de SoftAP de laboratorio y panel Android debug para GPIO, ADC, Wi-Fi y secuencias limitadas. No está habilitado control general desde Internet ni OTA. Ver `docs/WIFI_LAB_V2.md` para configuración y advertencias de seguridad.

**Requisito importante firmware V2:** confirma al menos 4 MB de flash; usa `Partition Scheme: Minimal SPIFFS (1.9MB APP with OTA)` para compilar. El esquema por defecto de 1.25MB por aplicación no alcanza. Se requiere prueba física antes de flashear otra placa. Ver [estado de entregas V2](docs/ESTADO_V2.md).

## Hardware ESP32 confirmado
La placa de prueba usa **ESP32-D0WD-V3 (rev. 3.1)** con **4 MB de flash** según consulta `esptool`. Para firmware Wi-Fi+BLE seleccionar **ESP32 Dev Module → Partition Scheme: Minimal SPIFFS (1.9MB APP with OTA)** en Arduino IDE. Flash compatible no equivale a OTA funcional. Consulta [hardware y QA](docs/HARDWARE_ESP32.md). No subir identificadores únicos de la placa al repo.

## Estado V3 incremental (laboratorio)

Se integraron mejoras de proyectos locales, editor visual de secuencias, telemetría ADC1, diagnósticos y control RGB completo en el SoftAP de laboratorio. Las compilaciones usan un perfil de ESP32 de 4 MB y CI controla el presupuesto de firmware; el release Android permanece deshabilitado hasta asegurar BLE y realizar QA física. **No es una versión lista para producción.** Ver [estado V3 y bloqueos F00–F16](docs/ESTADO_V3.md) y [seguimiento de integración](https://github.com/ErickRFM/leds/issues/28).
