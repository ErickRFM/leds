# V4 · RGB autónomo después de desconectar el teléfono

El ESP32 ya ejecuta efectos RGB desde firmware sin teléfono conectado. Este PR agrega persistencia de **color, brillo, encendido, modo y velocidad** en una sola entrada NVS versionada (`cc-v4-rgb/state`).

- El estado se restaura al arrancar antes de inicializar los módulos PWM RGB.
- No se escribe en flash por cada movimiento de slider: cambios consolidados después de 2 segundos sin comandos y máximo un reintento cada 5 segundos cuando falla.
- CRC-16/CCITT y formato fijo rechazan datos corruptos, versiones incompatibles, modos inválidos, velocidades fuera de rango o registros incompletos.
- Ante carga inválida se arranca con LED apagado y se conservan los demás namespaces NVS.
- **No se almacenan contraseñas, claves, MAC, SSID ni permisos de propietario en esta estructura.** Esta persistencia NO resuelve aún la autenticación del BLE V1.
- Este cambio no introduce cambios de pin: R27/G25/B26. No modifica credenciales SoftAP ni particiones.

## Pruebas para el banco físico

1. Instalar APK/firmware del mismo SHA, encender, conectar BLE (laboratorio), SET rojo/brillo 180, dejar 3s y desconectar teléfono.
2. Confirmar que el LED permanece rojo y que efectos activos siguen ejecutándose.
3. Esperar otros 3s, quitar la alimentación USB y volver a conectar. Confirmar color/brillo/ON/modo restaurados.
4. Mover slider repetidamente durante al menos 10s, parar 3s, cortar alimentación y reconectar. Confirmar último estado estable.
5. OFF, esperar 3s, reiniciar. Confirmar LED apagado.
6. Forzar pérdida de energía inmediatamente después del último SET (antes de 2s): último snapshot previo puede persistir. Esto es el compromiso documentado de limitar desgaste NVS.
7. No usar automatizaciones o relés de potencia con esta lógica hasta tener política de restauración segura por periférico.

Pruebas host C++ de versión, integridad y rangos integradas en GitHub Actions. Funcionamiento físico y ciclos de energía pendientes de validar.
