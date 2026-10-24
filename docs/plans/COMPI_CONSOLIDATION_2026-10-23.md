# Compi Aurora: consolidación ejecutable

Solicitud: integrar todos los cambios vigentes, una implementación por módulo,
Android y ESP32 conectados por BLE y Wi-Fi, sin estados de éxito inventados.
Base: `eccdd22`, después de actualizar origin el 23 de octubre de 2026.

## Decisiones de implementación

Conservar la arquitectura Kotlin/Compose y Arduino ESP32 existente. Comparar
ramas por ascendencia, equivalencia de parches y código vigente; no fusionar
historia obsoleta ni eliminar ramas con trabajo exclusivo. Integrar el PR 259
y completar sus brechas. La autorización BLE y la sesión Wi-Fi siguen siendo
obligatorias. La escritura UART significa solicitud aceptada, nunca sonido.

## Trabajo y evidencia

1. Inventario reproducible de todas las ramas y clasificación de diferencias.
2. Audio: una máquina de estado UART con checksum, SD, pistas, reproducción y
   caducidad; consultas y comandos equivalentes BLE/HTTP con correlación.
3. OLED: controlador único para escenas, contraste, inversión y pausa; estado
   confirmado y control por ambos transportes.
4. Android: integrar PR 259, usar esos contratos, invalidar respuestas de sesiones
   anteriores, controles físicos separados del reproductor local del teléfono.
5. Revisar sensores, persistencia LDR y RGB con humedad/histéresis contra las ramas;
   conservar las correcciones recientes de DHT11 y datos reales en gráficas.
6. Ejecutar tests host, contratos, unit tests Android, lint, APK, tests de
   navegación, compilación ESP32 y presupuesto de flash. Revisión independiente.
7. Publicar cambios comprobados, verificar CI y entregar APK y firmware del mismo
   commit con hashes. Registrar por separado la validación física pendiente.

## Interfaces y riesgos a revisar

Los nuevos frames BLE deben caber en el MTU, compartir la cola existente y pasar
por autorización en el loop. Android debe correlacionar ID y sesión antes de
aceptar ACK. HTTP y BLE llaman al mismo controlador. No usar nombres de archivos
o pistas inventados cuando la tarjeta no haya respondido. Un ACK del módulo no
demuestra que la bocina produzca sonido. No flashear hardware automáticamente.

## Seguimiento

- Base Android: pruebas, lint y APK aprobados.
- Auditoría Git/sensores: 234 ramas inventariadas, con SHAs y equivalencias;
  conservar la política RGB de cinco bandas y calibración LDR con rollback.
- Integración: PR 259 incorporado; audio/OLED por BLE y Wi-Fi; GPIO BLE de
  DHT11/LDR recuperado con parser acotado, nonce y lectura posterior.
- Revisión independiente: tres hallazgos corregidos con regresiones (UART
  tardío, reinicialización OLED, texto OLED con coma).
- Verificación final/CI y artefactos: en ejecución. Hardware: no ejecutado.
