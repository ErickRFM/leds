# V4: Ejecución de scripts GPIO desde Programas

## Alcance
- Programa de laboratorio limitado: GPIO 18/19/21/22/23 HIGH/LOW seguido por WAIT 100–2000 ms; STOP opcional y # comentarios.
- Compilación local Kotlin a contrato firmware V3 (máx. 16 pasos, 20 segundos, un solo pin).
- Ejecutar/Detener invocan /v2/lab/sequence y /v2/lab/stop del firmware existente.
- Clave de control en RAM, solo debug, sin almacenamiento ni exportación de credenciales.
- No flashea, no instala, no ejecuta Arduino C++ ni scripts RGB, no altera BLE/Wi-Fi/particiones.

## Prueba manual en ESP32 físico
1. Instalar APK debug; conectarse a ESP32-Control-Lab y aceptar Wi-Fi sin Internet.
2. Abrir Código → Programas y cargar el ejemplo GPIO19.
3. Introducir clave local; confirmar 2 pasos, duración de 1000 ms.
4. Ejecutar y medir la salida GPIO19 con resistencia/circuito seguro.
5. Ejecutar una secuencia larga válida y pulsar Detener; confirmar salida LOW y GPIO liberado.
6. Comprobar que GPIO25 y espera sin GPIO son rechazados antes de transmitir.
7. Importar un .ino, editar/exportar, confirmar que instalar firmware continúa bloqueado.
8. Probar reconexión, RGB BLE y Wi-Fi, autodiagnóstico y navegación.

## QA y riesgos
- Los JUnit comprueban límites, pines reservados y contrato parser V3.
- Exigir Android testDebugUnitTest, lintDebug y assembleDebug; checks de firmware sin cambios.
- La clave V3 sobre HTTP SoftAP no equivale a autorización segura de producción; solo laboratorio aislado.
- Un 202 de la API no prueba el LED físico; la QA eléctrica sigue pendiente.
- Rollback: revertir cambios Android; el firmware existente no se modificó.
