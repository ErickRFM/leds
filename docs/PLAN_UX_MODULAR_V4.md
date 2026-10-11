# ESP32 Control Center · Navegación modular V4 (incremento Android)

## Objetivo

Hacer de LED RGB una **funcionalidad** dentro de la plataforma, no la aplicación completa.
La comunicación es seleccionable Bluetooth o Wi-Fi, pero ambas usan los mismos comandos validados
(`SET`, `ON`, `OFF`, `FX`) y el mismo parser de estado `STATE`.

## Navegación

- **Inicio:** accesos a módulos.
- **LED:** selección Bluetooth/Wi-Fi; pestañas Control, Colores y Efectos.
- **Código:** editor local, pegar/importar `.txt/.ino/.cpp/.h` (64 KiB), copiar.
- **Centro:** equipos BLE, laboratorio, herramientas, diagnóstico y sensores.
- **Ajustes:** configuración global y acceso de compatibilidad al laboratorio.

Se reutilizan los controles y catálogo de efectos y el editor/program source policy previamente integrado en #47; la lógica física RGB permanece en el firmware.
No se reescriben pines, particiones, credenciales SoftAP ni protocolo BLE.

## Reglas de conexión

1. **Bluetooth:** usar `BleManager`; la pantalla solamente se habilita en `Ready`.
2. **Wi-Fi:** cliente `LocalLabClient` existente; solo APK debug, SoftAP protegido
   `ESP32-Control-Lab`, gateway explícito 192.168.4.1 y clave local de 16 hexadecimales.
3. **Sin fallback:** si se cae Wi-Fi, no se envía en secreto por BLE ni datos móviles.
4. **Lectura posterior:** tras cada comando Wi-Fi se consulta `/v2/status` y se parsea `STATE`.
5. **Estado aislado:** al cambiar de transporte se limpia la sesión Wi-Fi y no se reproducen
   comandos pendientes; no guardar clave de acceso en DataStore.

## Límites de esta entrega

- Ni el modo Wi-Fi de laboratorio ni BLE V1 son seguros para distribución general:
  faltan autorización de propietario, provisioning cifrado verificado y pruebas físicas.
- Importar código **no** significa compilar, ejecutarlo o cargarlo en la placa.
  La interfaz lo explica expresamente y mantiene la acción de flasheo inhabilitada.
- Los experimentos declarativos actuales se conservan en Centro.
- No fusionar el PR de compatibilidad `#46` automáticamente: no demuestra provisioning operativo.

## QA previo a merge

- Android: `testDebugUnitTest`, `lintDebug`, `assembleDebug`.
- Host C++ y compilación ESP32 con perfil fijado `esp32:esp32:esp32:PartitionScheme=min_spiffs`.
- Banco físico: BLE y Wi-Fi en control/paleta/efectos; pérdida de conexión; rotación y navegación;
  rechazo de clave inválida; sin degradación a otro transporte.
- Seguridad futura: asegurar GATT y STA, instalación USB segura/OTA firmado con rollback.
