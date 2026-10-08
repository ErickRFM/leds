# Android — Frontend Kotlin nativo

Esta carpeta reserva la futura aplicación Android. Aún **no** contiene un proyecto Gradle ni APK, para evitar confundir documentación de arquitectura con funcionalidad terminada.

## Stack elegido
- Kotlin + Android SDK (sin Expo ni puente web).
- Jetpack Compose, Material 3, Navigation Compose.
- ViewModel, StateFlow, coroutines.
- BLE GATT mediante `BluetoothManager`, `BluetoothLeScanner`, `BluetoothGatt`.
- DataStore para presets y preferencias, sin nube.
- Pruebas unitarias y Compose UI tests.

## Carpetas propuestas dentro de :app
```text
app/src/main/java/.../
├── core/model/      RgbColor, Device, ConnectionState
├── core/ble/        Scanner, GattConnection, CommandQueue, BleRepository
├── core/data/       SavedPresets, Settings
├── feature/devices/ DevicesScreen + ViewModel
├── feature/control/ ControlScreen + ColorWheel + Sliders
├── feature/scenes/  ScenesScreen + ViewModel
└── feature/settings/SettingsScreen
```

## Pantallas prioritarias
1. Devices: listado, conectar, desconectar y estado BLE.
2. Control: rueda HSV, barras RGB, brillo, power.
3. Presets: colores predeterminados y favoritos.
4. Effects: efectos ejecutados localmente por ESP32.
5. Settings: documentación, reconexión, versión de firmware.

## Reglas de implementación
- Sólo `BleRepository` interactúa con Bluetooth; nunca desde composables.
- UI muestra el estado confirmado desde STATE; aplicar estado optimista sólo cuando se diferencie claramente.
- Serializar GATT, hacer discoverServices, habilitar CCCD y leer estado tras reconectar.
- Limitar arrastres a ~20 Hz, cancelar envíos obsoletos; mantener último color.
- Permisos API 31+: SCAN/CONNECT; API <=30: revisar ACCESS_FINE_LOCATION de scan y manifest.
- Seguridad de pairing/autorización obligatoria antes de un release no experimental.
- No añadir servicios cloud hasta que exista caso de uso aprobado.

Ver `../docs/PLAN_MAESTRO.md`, `../docs/PROTOCOLO_BLE.md`.
