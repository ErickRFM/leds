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

### Probar V3 en Android y ESP32 físico

Para instalar una **versión de laboratorio**, abre las [últimas ejecuciones de CI](https://github.com/ErickRFM/leds/actions/workflows/android-ci.yml), selecciona una ejecución verde en `main`, descarga `rgb-controller-debug` y verifica su `SHA256SUMS.txt`. `esp32-lab-4mb-firmware` contiene binarios coincidentes pero **no flashea automáticamente**; antes de actualizar el ESP32, verifica particiones, cableado y recuperación USB. Sigue la [guía de pruebas reales](docs/PRUEBAS_REALES_V3.md).

En la app debug, abre `Ajustes → Centro ESP32`. El autodiagnóstico valida la API y genera un reporte sin contraseñas; no prueba que el LED esté cableado correctamente. Conexión Wi-Fi local requiere `ESP32-Control-Lab` (sin Internet) y ruta `192.168.4.1`. **Release público continúa bloqueado por seguridad BLE pendiente.**

## ESP Studio V4/V5: editor, proyectos y scripts (solo laboratorio debug)

En la versión actual de `main`, la navegación incluye **Inicio, LED, Código, Centro y Ajustes**. **LED** utiliza la misma lógica de control RGB y permite elegir Bluetooth o Wi-Fi de laboratorio; la conexión Wi-Fi sigue siendo el SoftAP protegido `ESP32-Control-Lab` en `192.168.4.1`, **sin TLS ni servicio público**.

**Código → Programas** permite escribir, pegar, importar TXT/INO/CPP/H, exportar y guardar hasta 12 proyectos privados en el teléfono. No se guarda la clave del ESP32 junto con el código. Puedes ejecutar desde la APK **debug**, usando la clave local, dos tipos de instrucciones limitadas y compatibles con firmware del mismo proyecto:

- **GPIO**: por ejemplo, `GPIO 19 HIGH`, `WAIT 500`, `GPIO 19 LOW`, `WAIT 500`, `STOP`. Máximo 16 pasos, 20 segundos, un GPIO de 18/19/21/22/23. El motor comprueba de nuevo permisos y recursos en el ESP32.
- **RGB**: instrucciones `RGB 255 0 0`, `WAIT 500`, `OFF`, `STOP`. El intérprete de firmware impone 32 instrucciones y 20 segundos y no permite C++ arbitrario. El control LED manual tiene prioridad.

Los programas guardados en **Mis proyectos** son archivos locales de Android, **no** programas instalados para autoarrancar en la placa. Un resultado de API correcto confirma estado lógico; **no demuestra que el LED físico funcione**.

### Arduino C++ verdadero: compilar en Windows, sin reemplazo inadvertido

El modo Arduino del editor **no ejecuta directamente** texto C++ en la placa. Exporta el sketch y usa el compilador gratuito Arduino CLI en Windows:

- `scripts/build-user-sketch.ps1`: compilar un archivo TXT/INO importado; la carga **USB** solo existe con puerto y reconocimiento explícito de que sustituirá el firmware, siguiendo `docs/V4_ARDUINO_CLI_USB.md`.
- `scripts/build-user-sketch.py`: generar un paquete de compilación offline y un manifiesto con hashes verificables, documentado en `docs/V5_ARDUINO_CLI_TEST_BUILD.md`. Este paquete **no está firmado ni aprobado para OTA**.

El perfil auditado se encuentra en `config/esp32-board.env` (ESP32 clásico 4 MB, Arduino-ESP32 3.0.7, `min_spiffs`). **Cargar un sketch Arduino arbitrario por USB reemplaza el programa principal y puede eliminar BLE/Wi-Fi/RGB**. No lo hagas sin respaldo, recuperación USB probada y aceptación del reemplazo.

### Pruebas y restricciones de publicación

Para verificar código en Windows, desde la raíz del repositorio:

```powershell
git fetch origin
git switch main
git pull --ff-only origin main
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\verify-local.ps1
```

Para la APK, selecciona una ejecución **verde de `main`** en [GitHub Actions](https://github.com/ErickRFM/leds/actions/workflows/android-ci.yml) y descarga `rgb-controller-debug` junto con el SHA-256. También existe `esp32-lab-4mb-firmware` para pruebas supervisadas. El script local no flashea el ESP32 sin petición expresa.

**Bloqueos vigentes:** autorización segura del BLE legado, seguridad de administración/aprovisionamiento demostrada, OTA firmado con rollback físico, pruebas en placa y teléfono, y aprobación de release. El funcionamiento como laboratorio debug **no** equivale a producto listo para producción. Sigue las incidencias [#42](https://github.com/ErickRFM/leds/issues/42), [#48](https://github.com/ErickRFM/leds/issues/48) y [#15](https://github.com/ErickRFM/leds/issues/15).
