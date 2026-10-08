# Plan maestro de construcción — RGB Controller
Versión de planificación: 1.0 · 2026-10-08

## 0. Situación de partida
Repositorio inicial vacío; se inicia arquitectura independiente sin Expo, sin backend cloud y sin datos ficticios en tiempo de ejecución.
**No confundir documentación/fundación con APK listo o prueba física completada.**

## 1. Objetivo y alcance
Construir una app Android nativa en Kotlin/Jetpack Compose para conectar por BLE a un ESP32 y controlar en tiempo real un LED RGB de 4 pines: mezcla libre, presets, brillo, on/off, escenas y efectos.
- V1: un teléfono controla una placa ESP32 y un LED RGB; conexiones recuperables; modo offline; favoritos locales.
- V1.1: varios ESP32 guardados (uno activo), escenas persistentes en el ESP32, efectos avanzados.
- Futuro opcional: tira RGB con MOSFET y fuente externa, tiras direccionables (otro firmware), OTA, Wi-Fi/API/cloud.
- Fuera de V1: Expo, Firebase, login, MongoDB, API REST remota, control por Internet, control multiusuario remoto y tira LED alimentada por GPIO.

## 2. Decisión de arquitectura
**Frontend**: Android Kotlin, Jetpack Compose + Material 3, ViewModel + StateFlow, BLE GATT API, DataStore local, coroutines, Navigation Compose.
**Backend embebido**: ESP32 programado en C++ (Arduino-ESP32 3.x), GATT server, parser acotado, módulo PWM LEDC, estado LED, gestor de sesión y animaciones no bloqueantes.
**Transporte**: BLE local. Servicio y características GATT versionados; escrituras con respuesta, lectura y notificaciones para sincronizar estado.
**Hardware**: ESP32 clásico con BLE; GPIO 27/25/26; tres resistencias 220–330 Ω; LED de ánodo común (configurable a cátodo).
**Cloud**: ninguno en V1. El firmware cumple el rol de backend operativo; no desplegar servidores de relleno.

### Flujo de datos
Usuario -> Compose -> ViewModel -> Repository -> Android BluetoothGatt -> BLE WRITE -> ESP32 validación -> PWM -> LED.
ESP32 -> BLE READ/NOTIFY -> Repository -> StateFlow -> Compose.
Al recuperar una conexión, Android lee el estado REAL de ESP32; no debe suponer que se aplicó el último color enviado.

## 3. Estructura de repositorio
```
leds/
├── android/                  # Kotlin nativo (proyecto Gradle en fase F2)
│   └── README.md
├── firmware/
│   ├── esp32_rgb/
│   │   └── esp32_rgb.ino      # firmware inicial verificable en placa
│   └── README.md
├── docs/
│   ├── PLAN_MAESTRO.md
│   ├── ARQUITECTURA.md
│   ├── PROTOCOLO_BLE.md
│   ├── CABLEADO.md
│   └── CRITERIOS_DE_ACEPTACION.md
└── README.md
```

En la implementación Android, preferir carpetas `core/model`, `core/ble`, `core/data`, `feature/devices`, `feature/control`, `feature/scenes`, `feature/settings` dentro del módulo inicial `:app`. Separar módulos Gradle solo cuando exista una razón técnica, evitando sobre-ingeniería.

## 4. Pantallas frontend y comportamiento
1. **Dispositivos**: permiso contextual, escanear BLE, filtro por UUID del servicio, RSSI orientativo, conectar, olvidar, estado desconectado/conectando/conectado, recuperación y errores comprensibles.
2. **Control** (home): encabezado compacto dispositivo + conexión, rueda HSV, vista previa, barras R/G/B 0–255, brillo 0–255, apagar/encender, último estado REAL indicado.
3. **Presets**: rojo, verde, azul, blanco, amarillo, cian, magenta, naranja, violeta, favoritos editables con nombre; click aplica en dispositivo.
4. **Efectos**: respiración, arcoíris y fade; velocidad y stop; firmware ejecuta animación incluso si se cierra Android (fase F4).
5. **Ajustes**: tipo de LED (hardware/firmware, no cambiar a ciegas), reconexión, información de firmware, permisos y ayuda de cableado.
Diseño: oscuro/claro nativo, Material 3, superficies sutiles tipo glass sin perder contraste, tamaños compactos, jerarquía clara, animaciones pequeñas, accesibilidad y 360/390/430 dp.

## 5. Backend ESP32 (firmware)
Responsabilidades:
- Configurar PWM de 8 bits, 5 kHz; invertir niveles exclusivamente para ánodo común.
- GATT server con servicio, característica de comandos (WRITE) y estado (READ+NOTIFY).
- Parsear y validar comandos en rango sin asignaciones excesivas; rechazar datos malformados.
- Mantener estado {r,g,b,brightness,power,mode,speed}; al iniciar estado apagado.
- Aplicar nueva mezcla y emitir estado; conservar el último color al desconectar.
- Reiniciar advertising tras desconexión y permitir lectura del estado al reconectar.
- Efectos no bloqueantes basados en millis, sin delay de varios segundos (fase F4).
- Seguridad de emparejamiento/bonding + control de escritura para versión entregable; el prototipo abierto NO es seguro frente a teléfonos cercanos.
- Protección frente a spam de comandos, pruebas de desconexión y validación de memoria.

## 6. Fases, entregables y criterios de salida
| Fase | Entregables | Criterio de salida |
| --- | --- | --- |
| F0 Fundación | README, arquitectura, pinout, protocolo, firmware base, backlog GitHub | Contratos documentados, código versionado, sin declarar hardware validado |
| F1 Hardware y BLE | Flashear ESP32, verificar GPIO y COM, nRF Connect, READ/WRITE/NOTIFY, fotos y logs | En teléfono real: SET rojo/verde/azul, OFF y GET, reconexión correcta |
| F2 App Android MVP | Proyecto Gradle, Manifest/permisos, BLE repository, conexión, ViewModel, Compose dispositivos y control | Escaneo, conexión, rueda/slider RGB + brillo, apagado, estado sincronizado en dispositivo físico |
| F3 Experiencia | Presets, favoritos DataStore, tema, accesibilidad, UX errores, throttling y reconexión | Sin ANR ni caídas; favoritos persisten; 360/390/430 dp revisados |
| F4 Escenas y efectos | Comandos FX/STOP versionados, motor en firmware, control de velocidad, perfiles | Animación fluida sigue funcionando sin app; STOP vuelve a color estable |
| F5 Seguridad y release | Bonding/passkey o pairing físico, requisitos de autorización, tests, CI, release APK firmado | Sin conexiones/escrituras no autorizadas; CI verde y pruebas en dispositivo físico |

### Dependencias entre fases
F1 valida el contrato y pinout antes de cerrar F2. F3 depende de F2. F4 requiere ampliar protocolo SIN romper clientes v1; F5 cruza todas las capas.

## 7. Backlog técnico priorizado
**P0**
- Definir placa ESP32 exacta y tipo de LED ánodo/cátodo.
- Verificar resistencias individuales y alimentación eléctrica.
- Compilar y flashear firmware 3.x; revisar logs serial.
- BLE scan, permisos por SDK, conexión GATT, descubrimiento de servicios, escritura con respuesta, CCCD, notificaciones.
- Manejo de estados: idle/scanning/connecting/discovering/ready/disconnecting/error; tiempos de espera y cancelación.
- Serializar operaciones GATT; limitar slider a ~20 comandos/s, conservar último valor; no enviar mientras no esté listo.
- Releer estado tras reconexión y manejar foreground/background.
**P1**
- Rueda HSV -> RGB, presets, DataStore, favoritos, preview, color/brightness separación.
- Tests de parser, rangos, inversión ánodo/cátodo y UI ViewModel.
- Contraste, TalkBack, errores permisos y botón Bluetooth deshabilitado.
**P2**
- Efectos no bloqueantes en firmware, velocidad, transiciones, seguridad avanzada y eventual compatibilidad con tiras RGB.

## 8. CI, calidad y entrega
- GitHub Actions Android: Gradle checks, lint, unit tests, compilación debug, APK como artefacto (agregar al implementar `android/`).
- Firmware: arduino-cli compile target de la placa real con core fijado y prueba parser host cuando se extraiga de `.ino`.
- Tests unitarios Android: RGB 0–255, mapeo HSV, comandos malformados, reducer BLE, throttle/debounce, favoritos.
- Tests instrumentados: permisos y estados, navegación, disposición responsive, reconexión simulada.
- Hardware QA: BLE a 1 m / 5 m / 10 m según entorno, rotación, pantalla bloqueada, desconexión, reconexión, pérdida de energía, batería, latencia perceptual y reinicio.
- Entregables finales: APK debug y release, código firmware con instrucciones de flasheo, cableado, protocolo BLE, checklist de aceptación, release notes y SHA256.

## 9. Flujo Git y disciplina de cierre
- `main` estable. Crear rama por fase/vertical: `feat/esp32-ble-foundation`, `feat/android-ble-transport`, `feat/android-rgb-ui`, `feat/presets-scenes`, `feat/firmware-effects`, `fix/...`.
- PR pequeñas contra main; revisión de diferencia, pruebas y aceptación; squash merge solo con checks verdes.
- Cada issue describe **alcance, archivos, dependencias, tests, criterio de aceptación**. Cada PR referencia issue y evidencia de prueba.
- No marcar un test de hardware como pasado porque compila: exigir video/foto/log real.
- No mezclar cambios visuales con refactorización de transporte sin justificación.
- No incorporar credenciales, nombres MAC personales ni direcciones privadas al repositorio.

## 10. Definición de terminado (DoD)
Se considera V1 realmente lista cuando la app instala en Android físico y: encuentra ESP32, pide permisos correctos, establece BLE, controla tres colores PWM y brillo sin retardos visibles, muestra estado real, maneja OFF, recupera conexión, conserva favoritos, evita control BLE no autorizado y supera las pruebas registradas. Antes de esto: `FOUNDATION`, `IN_PROGRESS` o `READY_FOR_HARDWARE_TEST`, no `PRODUCTION_READY`.

## 11. Decisiones por confirmar en hardware
- ¿ESP32 DevKit V1/WROOM-32 clásico? Ver serigrafía real.
- ¿LED de ánodo común o cátodo común? Confirmar antes de alimentar.
- ¿Hay necesidad futura de tiras LED? Un GPIO no alimenta tiras; requerirá fuente independiente, MOSFET/driver y masa común.
- ¿Habrá botón físico de asociación? Recomendado para seguridad; inicialmente no previsto en el circuito.
