# Compi V33 — Reproductor de audios locales Android

## Implementado
- Componente visual en la estación ambiental de Módulos, sin duplicar la navegación.
- Selector Android SAF `OpenMultipleDocuments(audio/*)`; permisos de lectura persistibles cuando el proveedor lo permite.
- Biblioteca local privada con URIs `content://` (sin copias de audio, sin subir archivos al ESP32, sin acceso indiscriminado al almacenamiento).
- Cola limitada a 50 pistas, selección, anterior/siguiente, reproducir, pausar, reanudar, detener y volumen.
- Motor `MediaPlayer` del teléfono con `prepareAsync()`, liberación al salir de la pantalla, fallos explícitos de formato/lectura y reproducción continua.
- Se conserva la biblioteca local entre sesiones por SharedPreferences; los proveedores que no entregan permisos persistibles pueden exigir volver a importar.
- Los avisos TTS existentes siguen separados; audio focus/ducking automático y mezcla de prioridad quedan pendientes de una fase posterior.

## Precaución: bocina física
La bocina del usuario es **pasiva 8 Ω / 0.5 W**, y **no se conecta a GPIO ni a la salida digital directa**.
No está disponible un DFPlayer Mini o un amplificador medido. Para reproducir desde el ESP32 se requiere:
1. Módulo reproductor/decodificador físico (opción DFPlayer Mini y tarjeta microSD).
2. Etapa de salida con potencia efectiva verificada para **NO superar 0.5 W** sobre 8 Ω.
3. Fuente adecuada, tierra común, niveles UART compatibles con 3.3V de ESP32, protoboard/cables.
4. Confirmación de modelo y medidas en banco. Un límite de volumen de software no garantiza potencia segura.
5. Fase separada de firmware, autorización BLE/Wi-Fi, estado sin hardware, pruebas de errores, código que no bloquee el loop, pruebas en ESP32.

La función entregada **reproduce por el teléfono**, NO controla todavía la bocina del ESP32. El firmware actual no tiene driver DFPlayer ni circuito físico detectado.

## QA
- Seleccionar MP3/OGG local desde el selector Android. Probar reproducir/pausar/continuar/siguiente/anterior/detener.
- Reiniciar aplicación: biblioteca retenida y pistas accesibles solo si existe permiso persistente.
- Borrar la pista desde la biblioteca, interrumpir audio, retirar el proveedor: la UI debe mostrar un fallo recuperable.
- Probar emuladores API 30/35, pruebas JVM de cola, y hardware Android real (PENDING_DEVICE_QA).
- Validar accesibilidad de botones y tema oscuro/claro, y que BLE, Wi-Fi, DHT11 y RGB no se alteren.
