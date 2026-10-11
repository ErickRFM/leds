# V4 — Compilar Arduino C++ localmente, sin flashear

Esta herramienta es el primer paso del modo Arduino C++ real. Requiere Windows, PowerShell, Arduino CLI instalado y esp32:esp32@3.0.7.
El resultado es un archivo .bin local y manifest.json con hashes. La APK permite importar/editar/exportar .ino; aún no transmite al compilador.

## Pasos
1. Exporta tu archivo de código desde la app o crea un sketch .ino.
2. Crea una carpeta llamada MiSketch y guarda dentro MiSketch.ino (nombre de carpeta igual al archivo).
3. En PowerShell, desde la raíz del repo:
   powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\scripts\build-user-sketch.ps1 -SketchPath .\MiSketch
4. Revisa local-validation\arduino-projects\MiSketch\MiSketch.ino.bin y manifest.json.
5. Usa Arduino IDE/USB para pruebas físicas sólo cuando hayas respaldado el firmware existente y quieras realmente reemplazarlo.

## Límites y seguridad
- Nunca se flashea ni instala por OTA; el script no acepta parámetro de puerto.
- Solo admite ESP32 clásico con la placa auditada min_spiffs y Arduino-ESP32 3.0.7.
- El tamaño bajo un slot OTA no implica que sea seguro actualizar: no hay firma, autenticación ni rollback validado.
- Un sketch completo de terceros REEMPLAZARÍA la aplicación del ESP32 y normalmente perdería el Centro de Control BLE/Wi-Fi.
- Para conservar los servicios centrales, el futuro modo administrado debe integrar y probar el SDK núcleo en un build controlado.
- No compiles sketches de origen desconocido en una máquina sin aislamiento; el ecosistema de construcción carga componentes y herramientas locales.

## Próximo incremento
Un puente local PC ↔ Android con emparejamiento seguro, envío explícito de fuentes, logs y retorno del artefacto, seguido de OTA firmado y rollback probado. No exponer Arduino CLI ni un puente sin autenticación a la red.
