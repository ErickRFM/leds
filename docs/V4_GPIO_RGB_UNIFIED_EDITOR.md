# V4 · Unificación de scripts GPIO y RGB
- Conserva editor Android, importación TXT/INO, copiado/exportación, integración de GPIO de PR #53 y selector Bluetooth/Wi-Fi del módulo LED de PR #50.
- Selector en `Código → Laboratorio → Motor de ejecución`: `GPIO` (contrato V3 secuencia) o `LED RGB` (intérprete V4 `/v2/script/*` de PR #51).
- GPIO no puede reclamar RGB 25/26/27. RGB Script emite comandos existentes `SET,ON,OFF` desde un único motor cooperativo. No se ejecuta C++ nativo.
- Clave SoftAP 16 hex solo en memoria; la app nunca la guarda ni la copia con el proyecto.
- Interrupción/estado explícitos. La confirmación es lógica, no demuestra hardware.
- Requiere APK debug y firmware V4 sincronizados de una misma revisión. Versiones V3 devolverán 404 para RGB Script; GPIO conserva compatibilidad anterior.
- El modo Arduino C++ exporta fuente; `scripts/build-user-sketch.ps1` (PR #54) compila mediante Arduino CLI en Windows, y USB flash se confirma por separado.

**QA:** JUnit del parser RGB, GPIO y compatibilidad; Android tests/lint/APK; firmware host/Arduino; ejecutar GPIO19 y LED real con interrupciones BLE/Wi-Fi. Nunca declarar seguridad BLE ni OTA aprobada sin validación física.
