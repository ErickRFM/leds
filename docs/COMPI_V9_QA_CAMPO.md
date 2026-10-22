# COMPI V9 — navegación, Studio, telemetría GPIO real y BLE de solo lectura

**Origen:** observaciones de capturas reales de Android con ESP32-D0WD-V3 y 4 MB de flash, GPIO RGB rojo 27 / verde 25 / azul 26.  
**Rama de implementación:** `fix/compi-v9-navigation-studio-gpio-telemetry`.  
**Regla:** CI verde = listo para pruebas supervisadas, no certificación física ni distribución.

## Correcciones respaldadas por código

| Área | Problema observado | Solución V9 | Evidencia requerida |
|---|---|---|---|
| Navegación | Centro duplicado con Inicio; algunos caminos no regresan | Solo cuatro tabs Inicio/LED/Código/Ajustes; accesos Equipo y Hardware Lab en Inicio; AppBar anidada con Regresar; ruta legacy center redirigida | Probar Back del sistema, botón Regresar, Inicio, cambio de tabs y reentrada |
| Editor | El mismo texto muestra error de validación GPIO estando cargado RGB | Selector de motor arriba del editor; borradores y nombres independientes GPIO, RGB, Arduino C++; importación escoge motor compatible | Pegar RGB, alternar GPIO, escribir GPIO, alternar y recuperar ambos |
| Ejecución GPIO | Se esperaba que GPIO19 activara LED RGB 27/25/26 | Guía contextual en Studio: GPIO19 necesita LED independiente con resistencia; RGB utiliza el motor LED RGB | Cablear LED físico de prueba en GPIO19 o seleccionar RGB correctamente |
| Feedback | Operaciones en progreso poco visibles | Barras de progreso en escaneo/conexión BLE, Studio y panel GPIO | Observar antes/durante/después de comandos/timeout |
| GPIO real | El mapa actual enumera pines, no reservas vivas | `GET /v2/gpio/status` firmado por clave HTTP SoftAP con lecturas del `PinManager`, modo, dueño y valor lógico HIGH/LOW solo en salidas manuales | Comparar en GPIO antes/durante/después de HIGH, LOW, secuencia y liberación |
| Bluetooth | Solo veía la conexión y control RGB, sin inventario GPIO | Nueva característica GATT opcional **READ** `4b9d0005-7a5e-4d4c-ae88-c8c70d3ab211` con snapshot de ocho pines | Conexión BLE, lectura periódica y visualización de ocupación, sin Wi-Fi |
| BLE RGB desde Studio | No existía una acción rápida en Código | Respiración BLE integrada usando `FX,BREATHE,120`; detener con `FX,STOP` | Verificar recepción STATE y LED físico, sin afirmar que ejecuta fuente del editor |
| Seguridad | BLE legado no autentica propietario | No habilitar GPIO/Wi-Fi/scripts/OTA por escrituras BLE V1; modo release continúa bloqueado | Actualizar arquitectura de enrolamiento propietario antes de paridad privilegiada |

## Características y compatibilidad

**V8 y anteriores:** GATT servicio/comando/estado/ID continúa, RGB y efectos ya existentes sin cambios. La telemetría GPIO BLE V9 es opcional; Android debe mostrar sin datos si el firmware no anuncia la característica. No sustituir GATT estado ni ampliar permisos de escritura.

**V9:** el firmware refresca la lectura GATT periódicamente en el loop principal y la app la consulta solo cuando el enlace está Ready y no hay escrituras RGB en curso. Un GATT READ fallido no debe dejar un GPIO marcado como actualizado. Tabla BLE solo informativa: F libre, R RGB, H/L manual, E experimento, X ocupado por otro módulo.

**Wi-Fi local:** `GET /v2/gpio/status`, protegido con `X-Device-Key`, accesible solamente por la interfaz SoftAP `ESP32-Control-Lab` (192.168.4.1, APK debug). Incluye 14 pines, owner y modo de lease, valor HIGH/LOW **solo** para salidas manuales. No expone contraseñas, PIN secretos ni direcciones MAC. No mide tensión ni demuestra que exista LED conectado. Android actualiza lectura cada 3 segundos cuando GPIO está visible y descarta snapshots al salir/cambiar clave o ante error.

**No flashear solo el .ino:** usar repositorio entero y mismo perfil `config/esp32-board.env` (esp32:esp32:esp32:PartitionScheme=min_spiffs, core 3.0.7). Cambiar particiones puede alterar almacenamiento NVS; respaldar antes de flashear. Los tests/CI no flashean por cuenta propia.

## Secuencia de pruebas guiadas

### 1 — Actualizar app y firmware
- Confirmar SHA de `main` fusionado; descargar artefactos del **mismo commit**; validar SHA256.
- Instalar APK debug, compilar/flashear firmware completo del repo por USB con ESP32 clásico de 4 MB, perfil min_spiffs.
- Reiniciar ESP32, Monitor Serie a 115200. No enviar contraseñas al chat ni screenshots.
- Comprobar que BLE RGB V1 funciona como antes (ON, OFF, SET, FX BREATHE, FX STOP).

### 2 — Navegación (N01–N06)
- Inicio → Mis dispositivos → Regresar → Inicio.
- Inicio → Hardware Lab → Regresar → Inicio.
- LED → Mis dispositivos → Regresar → LED.
- Ajustes → Hardware Lab → Regresar → Ajustes.
- Abandonar pantallas por tab Inicio incluso en mitad de una sección.
- Reiniciar app y probar guardado de estado sin abrir pantallas obsoletas Centro.

### 3 — Studio y borradores (S01–S08)
- Código → Laboratorio → GPIO → cargar ejemplo GPIO19.
- Introducir fuente propia GPIO, pasar a LED RGB, cargar efecto/script RGB; regresar a GPIO y verificar contenido intacto.
- Crear proyecto Arduino C++, volver a Laboratorio, comprobar que cada fuente conserva su texto.
- Importar TXT/INO y confirmar motor detectado; si cambia la fuente actual, diálogo preventivo antes de reemplazar.
- Probar `GPIO 19 HIGH / WAIT 500 / GPIO 19 LOW / WAIT 500 / STOP` con LED de prueba y resistencia en GPIO19 (no con LED RGB de 27/25/26).
- Probar script `RGB 255 0 0 / WAIT 500 / OFF / STOP` únicamente en Wi-Fi local tras introducir la clave.
- Con BLE conectado, probar botón Respirar por BLE: ejecuta efecto integrado, NO ejecuta archivo cargado.
- Cortar Wi-Fi y comprobar errores recuperables, sin éxito falso.

### 4 — GPIO (G01–G10)
- Sin Wi-Fi: mapa en vivo no debe inventar datos. Con BLE V9 conectado: cuadro **solo lectura**, puede mostrar que RGB 25/26/27 está reservado.
- Wi-Fi conectado, clave válida: abrir GPIO; verificar reserva RGB y pines libres.
- GPIO19 HIGH → en siguiente lectura dueño MANUAL y nivel HIGH.
- GPIO19 LOW → MANUAL/LOW.
- Liberar salidas → FREE; comprobar nivel físico con LED test.
- Iniciar secuencia GPIO19 → EXPERIMENT durante ejecución, FREE al terminar.
- Dejar salida HIGH 30 s → vencimiento libera a FREE.
- GPIO25/26/27 deben mantenerse reservados y no habilitarse como GPIO manual.
- Error 403, red perdida, red ajena → mostrar error y vaciar datos obsoletos.
- Firmware V8 anterior → endpoint faltante; informar actualización requerida, nunca simular mapa.

### 5 — Seguridad y regresiones (Q01–Q08)
- Exigir Wi-Fi local con clave para operaciones GPIO/escrituras de red.
- BLE sin emparejamiento propietario no anuncia servicios de escritura GPIO ni exposición de credenciales.
- Aislar respuestas de laboratorio y mantener comandos BLE RGB en cola de GATT ordenada.
- Stress: cambiar tabs y regresar durante búsquedas y operaciones; sin bloqueos.
- TalkBack, texto 200%, ancho de 320/360dp; no controles cortados.
- Android CI testDebugUnitTest, assembleDebugAndroidTest, lintDebug y assembleDebug.
- Firmware host test gpio_telemetry_codec_test, check-contracts y Arduino CLI, tamaño flash.
- `connectedDebugAndroidTest` requiere emulador o teléfono (CI solo **compila** tests UI, no los ejecuta).

## Dependencias y trabajos bloqueados

**BLE V1** todavía acepta RGB desde equipos sin autorización propietaria. Dar acceso BLE a GPIO, carga de Wi-Fi, scripts arbitrarios o OTA elevaría los riesgos. Para paridad privilegiada BLE/Wi-Fi: enlazar propietario por un desafío verificado físicamente, claves por dispositivo, sesión cifrada, anti-replay, expiración, revocación, control de capacidades en ESP32 y pruebas físicas; no se declara integrado sin ello.

PR #71 de automatizaciones RGB sigue abierto y se debe reconciliar sobre V9 en rama separada; no fusionarlo a ciegas ni perder el motor de secuencias o pruebas.
