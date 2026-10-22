# Auditoría V1.1 — RGB Controller
Fecha: 2026-10-10 · Rama de trabajo: `feat/rgb-controller-v1-1-polish`

## Alcance y regla de honestidad
Se revisaron el código Android Compose, transporte GATT, modelos, firmware ESP32,
contrato UUID y el comportamiento de las pantallas. Las observaciones sobre
código son verificables por inspección; la conexión y la iluminación real
**no se han certificado** en este entorno, que carece de teléfono, ESP32 y
Android SDK para una compilación del APK.

## Riesgos encontrados y cambios
| Categoría | Hallazgo | Corrección V1.1 |
| --- | --- | --- |
| BLE / transporte | Un STATE extendido puede superar el MTU ATT inicial de 23 (20 bytes útiles para notify). | Android solicita MTU 128 antes de descubrir el servicio; firmware configura MTU 128. |
| BLE / confianza | READY podía publicarse aunque la trama STATE inicial no fuera válida. | READY exige parseo exitoso del estado leído. |
| BLE / bloqueo | CONNECTING/DISCOVERING no tenían watchdog. | Espera máxima de 15 segundos con reintentos acotados. |
| BLE / errores | El fallo al escribir podía dejar un GATT abierto en estado Error. | Se cierra y se sigue la ruta de recuperación acotada. |
| RGB / latencia | El efecto UI de 60ms era debounce puro: un arrastre continuo no enviaba hasta detenerse. | Muestreo cada 60ms mientras se arrastra y coalescing de comandos SET. |
| Dispositivos | Se guardaba la dirección aun si CONNECT fallaba; mensaje vacío indistinguible antes de buscar. | Guardar solo tras READY; separar inicio, buscando, vacío y error. |
| UI | Navegación con texto cortado, jerarquía poco clara y tema sin identidad. | Etiquetas compactas, tema mate y claro, pantalla de dispositivos y paneles revisados. |
| Presets | Los favoritos integrados se duplicaban en la cuadrícula. | Marcar favoritos en tarjetas originales sin duplicarlas. |
| Apariencia | Solo se seguía el tema del sistema. | Persistencia DataStore para Sistema/Oscuro/Claro. |

## Hallazgos y riesgos pendientes
1. **Seguridad P0 antes de distribución**: GATT firmware sigue aceptando escrituras
   sin autorización; implementar pairing/bonding, autorización y procedimiento de reset
   de confianza sin bloquear el flujo para pruebas de laboratorio.
2. **QA con placa**: verificar firmware Arduino-ESP32 3.x y hardware real (GPIO
   R27/G25/B26, resistencias individuales, ánodo/cátodo), advertising BLE,
   read/notify prolongado y reconexión.
3. **Infra CI**: workflow de Android/ESP32 existe pero su ejecución está
   condicionada al estado de GitHub Actions y runners de la cuenta.
4. **Permisos**: revisar API 26–35, Bluetooth apagado, denegación persistente,
   rotación, distintos fabricantes; el permiso nunca equivale a GATT Ready.
5. **Rendimiento y accesibilidad**: medir actualización RGB bajo arrastre continuo,
   efecto activo, textos grandes y orientación vertical. No hay métricas medidas aún.

## Validaciones exigidas para cerrar release
- `python scripts/check-contracts.py`
- `gradle testDebugUnitTest lintDebug assembleDebug` (Android SDK 34, Gradle 8.7 y JDK 17)
- `arduino-cli compile --fqbn esp32:esp32:esp32 firmware/esp32_rgb` (core 3.x)
- En ESP32 con monitor serie 115200, comprobar `RGB-ESP32`, GATT
  SET/GET/ON/OFF/FX/STOP y respuesta real de cada pin.
- Probar MTU negociado y notificaciones de `STATE,255,255,255,255,1,RAINBOW,1000`.
- Probar permisos, escaneo, conectar/desconectar, reconectar, rotación,
  efectos, favoritos y pantalla bloqueada.
- Generar APK y SHA256 solo después de compilación genuina.

**Política de estado:** desarrollo fusionado no implica QA físico ni
`PRODUCTION_READY`. Nunca marcar controles sin evidencia de laboratorio.
