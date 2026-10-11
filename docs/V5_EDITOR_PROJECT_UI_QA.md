# V5 — Biblioteca de proyectos integrada en Código (UI)

## Alcance
- Pantalla Código muestra Mis proyectos: nombrar, guardar/actualizar, abrir y eliminar proyectos locales.
- Conserva scripts GPIO, RGB y editor C++, sin duplicar tipos de conexión ni tocar el firmware.
- Se reutilizan CodeProjectStore + CodeProjectLibrary (#57), con AtomicFile en almacenamiento privado; hasta 12 proyectos y máximo 32 KiB por fuente.
- Confirmación antes de reemplazar el texto no guardado del editor o eliminar un proyecto.
- No se guardan claves de control: controlKey queda fuera del modelo y de la persistencia.
- Arduino C++ tiene tarjeta propia de ayuda sobre compilación PC y reemplazo de firmware, separada del motor GPIO.

## Pruebas manuales en APK debug (no afectan ESP32)
1. Código → Arduino C++ → escribir setup/loop → nombrar `Blink` → Guardar proyecto.
2. Salir de la pantalla o cerrar el proceso y volver: debe permanecer `Blink` y abrirse completo.
3. Editar sin guardar y pulsar Abrir otro proyecto: debe aparecer confirmación, Cancelar mantiene el borrador.
4. Guardar como `blink`: debe actualizar el proyecto `Blink`, no crear un duplicado.
5. Guardar script Lab; reabrir: debe recuperarse el modo Lab y el texto exacto.
6. Eliminar proyecto: debe pedir confirmación y no cambiar firmware.
7. Probar 12 proyectos válidos, 13.º rechazado y error visible (no pérdida de biblioteca).
8. Importar texto no válido o de más de 32 KiB: rechazo sin crashear ni borrar un proyecto previo.
9. Volver LED → BLE y Wi-Fi, Centro, Ajustes y regreso a Código; confirmar navegación y que el editor no pierde estado de forma sorpresiva.
10. Probar TalkBack, letra ampliada, scroll y rotación.

## Pruebas de ejecución (exigen ESP32 físico)
- GPIO 19 con circuito seguro: ejecución y parada con la clave de laboratorio volátil.
- RGB script: ejecutar, leer estado real, detener, observar LED.
- En C++, verificar que no se ofrece flasheo Android/OTA sin controles de seguridad.
- Desconectar Wi-Fi: debe mostrar error comprensible y NO conmutar silenciosamente a BLE o datos móviles.

## Gates
`testDebugUnitTest`, `lintDebug`, `assembleDebug`, firmware ESP32 y pruebas host.
**Sin prueba física no hay autorización de release ni declaración de OTA funcional.**