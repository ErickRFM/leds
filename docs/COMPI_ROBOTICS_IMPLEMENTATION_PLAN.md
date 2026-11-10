# Compi Robotics — integración de grúa de dos ejes

Estado: AUDITADO / IMPLEMENTACIÓN INICIAL EN RAMA
Rama: `feat/compi-robotics-servo-crane`
Base: `main`
Hardware objetivo: ESP32-D0WD-V3 rev. 3.1, 4 MB flash.

## 1. Decisión de integración

Compi Robotics se incorpora al firmware único `firmware/esp32_rgb` y a la aplicación Android nativa existente en `android/app`. No crear un segundo sketch principal, una segunda app ni un servicio cloud.

**Transporte móvil:** reutilizar el BLE GATT existente (servicio y características WRITE/STATE, negociación de MTU, inbox acotado, sesión de propietario y autorización fail-closed). No introducir Bluetooth Classic SPP ni UUID alternativos. El control HTTP de SoftAP solo se considerará después de reutilizar la misma autorización y el backend único.

Los callbacks BLE solo validan/enfilan mensajes. El `loop()` del ESP32 ejecuta actuadores y módulos; nunca mover servos desde callbacks BLE.

## 2. Inventario y mapa GPIO auditado

Pines ocupados o reservados según el firmware actual:
- RGB: GPIO 27, 25, 26.
- DHT11: GPIO 16.
- LDR: GPIO 32.
- OLED SSD1306: GPIO 21 (SDA), 22 (SCL).
- DFPlayer UART2: GPIO 13 (RX ESP32), 14 (TX ESP32); BUSY GPIO 33.
- Botón de reporte de voz: GPIO 23 (pull-up interno).
- Botón BOOT/enrolamiento: GPIO 0.
- GPIO 18/19 aparecen permitidos para salida y laboratorio, pero deben ser adquiridos exclusivamente por el nuevo módulo.

### Asignación propuesta, pendiente de validar en placa

| Función | GPIO | Nota |
|---|---:|---|
| Servo base señal PWM | 18 | Salida PWM, lease exclusivo |
| Servo brazo señal PWM | 19 | Salida PWM, lease exclusivo |
| Joystick UP | 34 | Entrada solamente; pull-up externo |
| Joystick DOWN | 35 | Entrada solamente; pull-up externo |
| Joystick LEFT | 36 | Entrada solamente; pull-up externo |
| Joystick RIGHT | 39 | Entrada solamente; pull-up externo |
| Joystick MID | 17 | Entrada digital; pull-up externo recomendado |
| Joystick COM | GND | Contacto común activo en LOW |
| SET / RST del módulo | Sin conectar en primera iteración | Reservados para una fase posterior |

**Corrección importante:** GPIO 23 NO está libre: el firmware lo reserva para el botón físico de reporte de voz. No conectar allí MID ni ninguna otra tecla del joystick. GPIO 34/35/36/39 son input-only y carecen de pull-ups internos; usar resistencias externas de 10 kΩ a 3.3 V. No aplicar 5 V a ninguna entrada ESP32.

La asignación GPIO 17 como MID debe comprobarse contra el cableado real y el registro de pines antes de habilitar el módulo. El catálogo actual describe el DFPlayer con pin predeterminado 17, aunque el controlador real usa UART2 13/14; corregir esa metadata al implementar para que no induzca a error.

## 3. Alimentación y seguridad eléctrica

- Fuente externa regulada de 5 V para los dos SG90, dimensionada para picos simultáneos; objetivo recomendado 3 A.
- Unir GND de la fuente, ESP32 y joystick.
- No alimentar los servos desde 3V3 ni desde un GPIO. No usar el regulador de la placa como fuente de motores.
- Añadir condensador electrolítico de 470–1000 µF cerca de la distribución de servos, respetando polaridad y tensión nominal.
- Señales PWM desde GPIO18/19; confirmar niveles y cableado antes de conectar servos.
- Arranque con movimiento deshabilitado hasta recibir configuración segura; limitar recorrido inicialmente (por ejemplo 10–170°) y calibrar cada SG90 sin carga.
- Los SG90 no tienen encoder: la app reporta ángulo objetivo/comandado, no posición medida.
- STOP cancela la trayectoria y mantiene el último objetivo válido; no cortar PWM automáticamente porque el brazo podría caer. Si se requiere corte físico de potencia, usar interruptor dedicado y mecánica que soporte la carga.

## 4. Arquitectura de firmware

Añadir un único módulo compuesto `CraneRoboticsModule` (dos servos + joystick) al `ModuleRegistry`, con leases del `PinManager`. No superar `ModuleRegistry::kMaxModules = 8`; verificar cuántos módulos están registrados en cada perfil y mantener los sensores opcionales.

Responsabilidades separadas:
- `ServoAxis`: PWM 50 Hz, calibración min/max de pulso, límites angulares, conversión grados/pulso.
- `CraneMotionController`: dos objetivos, velocidad, rampa, parada y secuencias.
- `DigitalNavigationJoystick`: debounce, repetición y lectura de UP/DOWN/LEFT/RIGHT/MID.
- `CraneSafetyManager`: autorización remota, exclusividad de modo, timeout de comandos, límites y estado de arranque.
- `CraneProtocol`: parseo acotado y respuestas con request ID.

El módulo debe adquirir todas sus señales de forma atómica o liberar todas si falla el inicio. No apropiarse de GPIO ocupados ni cambiar las asignaciones de sensores existentes. Si no hay servos conectados, el resto de Compi debe iniciar normalmente y reportar el módulo como no disponible.

## 5. Control y arbitraje

- Modos: LOCAL (joystick físico), REMOTE (app) y SEQUENCE (presets/secuencias). Un solo propietario de movimiento a la vez.
- Prioridad de seguridad: STOP > cambio de modo > joystick local > órdenes remotas/automatización.
- Al pasar a REMOTE, ignorar entradas físicas de dirección salvo que se defina explícitamente una transferencia de control.
- Al desconectar BLE o expirar el lease remoto, cancelar el movimiento en curso y mantener el último objetivo; el control local puede continuar.
- Debounce y repetición temporizada sin `delay()`; loop no bloqueante.
- Límites independientes de base y brazo, velocidad máxima, aceleración y recorrido configurable.
- MID: alterna LOCAL/REMOTE solo con interacción local deliberada o confirma selección; no debe conceder autorización BLE.
- SET/RST quedan fuera del primer alcance. RST no debe confundirse con reset eléctrico del ESP32.

## 6. Contrato BLE compatible

Extender el parser existente y usar el mismo WRITE/STATE autenticado. Formato de línea ASCII acotado, versionado y con ID:

- `CRN,<id>,GET`
- `CRN,<id>,MODE,LOCAL|REMOTE|SEQUENCE`
- `CRN,<id>,TARGET,<baseDeg>,<armDeg>,<speed>`
- `CRN,<id>,AXIS,BASE|ARM,<deg>,<speed>`
- `CRN,<id>,STOP`
- `CRN,<id>,HOME`
- `CRN,<id>,PRESET,<name>`

Respuestas: `CRN,<id>,OK,...` o `CRN,<id>,ERR,<reason>`; estado consultable incluye modo, ángulos objetivo, movimiento, disponibilidad, límites y versión del módulo. Rechazar IDs duplicados/antiguos, valores fuera de rango, líneas truncadas y órdenes no autorizadas. Las escrituras GATT no equivalen a movimiento aplicado: Android espera respuesta/estado correlacionado.

No implementar estos comandos hasta que el parser host, el inbox, la autorización y el arbitraje estén probados.

## 7. Aplicación Android

Integrar dentro de la navegación y el workspace existentes, sin una app paralela:
- Nuevo módulo/pantalla Robotics accesible desde Estación/Hardware.
- Conexión reutilizada de `BleManager`, sesión autenticada y repositorio actual.
- Dos sliders/indicadores de ángulo, botones de presets 0/45/90/135/180, control de velocidad, joystick virtual, selección LOCAL/REMOTE/SEQUENCE y STOP.
- Estado de conexión, autorización, módulo disponible, último comando confirmado y errores.
- No mostrar una posición física medida: etiquetar “objetivo” o “ángulo ordenado”.
- Controles deshabilitados hasta BLE READY + propietario autenticado + módulo disponible.
- Layout responsive, accesible, botones grandes y STOP siempre visible.

## 8. Pruebas obligatorias

### Host/CI
- Parser válido, truncado, ID duplicado, caracteres inválidos, límites y autorización.
- Conversión grados/pulso y calibración de cada eje.
- Rampas, wrap-around de millis, debounce y repetición del joystick.
- Arbitraje de modos, STOP, timeout y desconexión.
- PinManager: conflicto con 13/14/16/17/21/22/23/25/26/27/32/33 y rollback de leases.
- Regresión de contratos BLE RGB, DHT11, LDR, OLED, DFPlayer, owner enrollment y comandos existentes.
- Android unit tests de codec, estados, correlación de respuestas y ViewModel.
- Compilar firmware de perfil normal y full-lab, Android tests/lint/APK debug.

### Hardware físico
- Verificar continuidad, tensión 5 V bajo carga, GND común y ausencia de brownout.
- Probar un servo sin brazo y después el segundo; comprobar sentido, límites y corriente.
- Probar joystick en cada dirección y MID; confirmar que no se activa por ruido.
- Probar dos servos simultáneos, STOP, cambio de modo y pérdida/reconexión BLE.
- Ejecutar sensores, OLED, RGB y DFPlayer simultáneamente con la grúa.
- Registrar foto, video, logs seriales y hashes de firmware/APK del mismo commit.

## 9. Secuencia de implementación

1. Cerrar pinout y revisar leases/catálogo; crear tests de conflictos.
2. Implementar codec y lógica de movimiento host-testable.
3. Implementar el módulo ESP32 con registro opcional y PWM.
4. Extender BLE autenticado y telemetría sin alterar UUID ni comandos antiguos.
5. Integrar la UI Android con el BLE manager actual.
6. CI completa, revisión de diff y PR draft; no merge sin CI verde.
7. Prueba de hardware supervisada y actualización de documentación/aceptación.

## 10. Estado de validación y bloqueos

Actualizado: 2026-11-09.

- Rama `feat/compi-robotics-servo-crane`, PR #296 en draft; `main` sin cambios.
- CI verde sobre el SHA `6b5856f7`: jobs `android`, `firmware`, `firmware-core-tests`, `windows-board-profile` y emuladores `mobile-ux-emulator (30|35)`.
- Compilación local con `COMPI_ENABLE_CRANE_ROBOTICS=1`: 1.873.133 bytes (95 %). Presupuesto OTA con perfil completo + grúa: 1.879.728 bytes y 86.352 bytes de margen sobre el mínimo de 65.536 → PASS.
- Artefactos del run `42716723468` (merge commit `307c74ce`, que corresponde al head `6b5856f7`): APK debug `6f2de5fa…` y firmware `1acbbbd…`; hashes completos en el `SHA256SUMS.txt` de cada artefacto.
- Sigue sin haber prueba física de los SG90, joystick, montaje, ni pérdida/reconexión BLE. No flashear ni habilitar en producción hasta completar las pruebas de hardware de la sección 8.
