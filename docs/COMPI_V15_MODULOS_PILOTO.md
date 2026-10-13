# Compi V15 — biblioteca modular (primera integración)

Rama: `feat/compi-v15-modular-sensors-ui` basada en `main` 9fd5d17.
Alcance: primer incremento real de la arquitectura modular; NO declara la V15 completa ni certificación eléctrica.

## Qué quedó integrado

- **Biblioteca:** acceso desde Inicio y desde sugerencias en Compi Studio, con pantalla secundaria y navegación Back; conserva las cuatro tabs y la ruta LED existente.
- **RGB:** reutiliza `LedModuleScreen`, `RgbControlLink` y el transporte original. No duplica PWM ni BLE. Disponible con conexión RGB verificada por BLE o SoftAP.
- **Firmware ESP32:** nuevos `GET /v2/modules` y `GET /v2/modules/telemetry?moduleId=input.adc1.34`, ambos dentro del SoftAP existente, con `authorized()` (interfaz AP + `X-Device-Key`). No añade comandos escritos, OTA ni permisos BLE.
- **ADC:** el manifiesto anuncia la **capacidad** de entrada GPIO34; NO asegura que haya una sonda de humedad conectada. Telemetría incluye `adc_raw` 0..4095 y `moisture_percent:null` con calidad `RAW_UNCALIBRATED`.
- **Muestreo:** panel ADC abierto consulta aproximadamente cada dos segundos, con máximo 120 muestras y cancelación Compose al salir. Error/red perdida borra última muestra presentada.
- **Calibración local:** permite tomar valores seco y húmedo, valida diferencia de 100 puntos ADC, convierte en ambas polaridades con límite 0..100. Las referencias viven solo en RAM de la vista (no persisten ni controlan bombas).
- **UI reusable:** `CompiMetricValue`, `CompiAdcTrend`, `CompiModuleCard`; catálogo validado `ModuleManifest`, sólo renderizadores conocidos.
- **Compi Studio:** sugiere LED tras script RGB admisible o plantillas Arduino C++ con comentarios explícitos `// @compi-module ...`. Es una sugerencia previa al flasheo, no detección física ni ejecución de código arbitrario.

## Compatibilidad y seguridad

1. Firmware V13 sin `/v2/modules`: UI puede seguir mostrando LED verificado a través del flujo BLE/SoftAP existente; no inventa sensor ADC.
2. `schemaVersion` diferente, IDs repetidos, tamaño mayor a 4 KiB o módulo/mapeo de pines no conocido: se rechaza o se omite antes de renderizar.
3. GPIO27/25/26 continúan exclusivos del LED. ADC GPIO34 es de solo entrada y adquiere/restituye el lease para cada lectura; conflicto devuelve HTTP 409.
4. BLE V1 jamás gana lecturas ADC, GPIO WRITE, flash ni OTA. El canal Wi-Fi sigue limitado a debug y no debe usarse fuera de banco.
5. Importar un sketch C++ que sustituya el firmware de Compi puede eliminar estos servicios. La anotación `@compi-module` no los instala.
6. La placa clásica ESP32 es de 3.3 V; comprobar alimentación y salida de la sonda capacitiva antes de cablear. No alimentar motores/relés directamente desde GPIO.

## Pruebas de código añadidas

- `SoilCalibrationTest`: extremos, ambos sentidos ADC, clamping y rechazo de referencias inválidas.
- `ModuleManifestTest`: contrato válido, incompatibilidad, repetidos, pines falsos y carga excesiva.
- `ProjectModuleHintsTest`: script RGB frente a GPIO y anotaciones explícitas frente a C++ no reconocido.
- `HomeNavigationTest`: acceso desde Inicio, ausencia de sensor fantasma sin placa y retorno a Inicio; se corrigen etiquetas de la navegación V9 previamente obsoletas.

## Comandos de verificación (Windows; requeridos antes de merge)

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
cd ..
python scripts/check-contracts.py
python scripts/check-firmware-budget.py
arduino-cli compile --fqbn esp32:esp32:esp32:PartitionScheme=min_spiffs firmware/esp32_rgb
```

**Ajustar compilación Android al script `android/build-debug.ps1` si el JAR Gradle Wrapper no está disponible.** Para Arduino usar el perfil exacto de `config/esp32-board.env`. Un CI verde NO verifica sensores eléctricos.

## QA física pendiente (no marcar como PASS sin hardware)

- Probar firmware y APK derivados del MISMO SHA en ESP32-D0WD-V3 de 4 MB, Android real y sonda capacitiva con salida segura de 3.3 V.
- Validar BLE RGB después de navegar Biblioteca → LED → Inicio 30 veces.
- SoftAP correcto, clave errónea, STA denegada y lectura `/v2/modules` con Wi-Fi desconectado.
- Leer GPIO34 con sonda desconectada, seco/húmedo reales; contrastar ADC con multímetro, aplicar calibración y resetear.
- Ocupar GPIO34 desde otra operación: esperar HTTP 409 y cero información inventada.
- Verificar conversión, ADC ruido, reconexiones, latencia, pérdidas y restauración después de rotación/cierre de pantalla.
- Ejecutar tests instrumentados en teléfono/emulador, revisión visual 320/360/412 dp.
- No distribuir release: #78 BLE propietario y #81 QA físico continúan abiertos.

## Siguiente PR (NO implementado en este incremento)

Registro de módulos genérico (sin IDs rígidos), calibración persistente por dispositivo con almacenamiento privado/versionado, DHT22/OLED I²C físicos y sus widgets, combinaciones LED+humedad, contratos de comando seguros y QA de hardware. Conservar integración futura con la PR visual #79 abierta.
