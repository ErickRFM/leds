# V4 incremental 1 — Editor offline para revisión
**Estado:** implementación inicial de pantalla + plan, **NO firmware ejecutable**.

## Alcance implementado
- Pantalla `Programas` accesible desde `Ajustes`; mantiene las 5 pestañas previas.
- Importación por Android SAF `OpenDocument`, lectura UTF-8 estricta <=32 KiB y rechazo de controles/basura.
- Pegar portapapeles, seleccionar modo Lab/Arduino C++, inspección de texto (no sintaxis) y exportar texto con `CreateDocument`.
- No se agregaron endpoints, credenciales, modificaciones de firmware, flasheo o OTA.
- Ejecutar e Instalar intencionalmente bloqueados mientras no exista intérprete seguro/OTA verificado.
- Conserva rutas BLE, SoftAP, GPIO y RGB existentes sin reimplementarlas.

## Pruebas y límites honestos
- `ProgramSourcePolicy.kt` compilado localmente con `kotlinc-jvm 1.9.0`; ocho checks de clasificación, tamaño, controles, UTF-8 válido/inválido y BOM pasaron.
- Test JUnit adicional: `ProgramSourcePolicyTest.kt`, integrado a `testDebugUnitTest` para ejecución en Windows.
- El entorno del asistente carece de Android SDK/Gradle y no ha compilado `assembleDebug` ni validado Compose en teléfono. Requerir `verify-local.ps1` en Windows.
- No hay pruebas físicas realizadas de ESP32 por este incremento.
- `MAX_BYTES` es límite de importación de **texto en Android**, no tamaño límite de firmware Arduino .bin (que requiere ruta separada y validación OTA).
- Si una importación incluye código real, abrirlo no significa que sea compilable, seguro o compatible.

## Checklist de prueba de aceptación manual
- [ ] Instalar APK debug generada de SHA exacto de `main` después del merge.
- [ ] Ajustes → Programas: abrir/volver sin perder navegación RGB.
- [ ] Abrir `.txt` con `RGB 255 0 0\nWAIT 500`, verificar modo Lab.
- [ ] Abrir `.ino` con `setup()/loop()`, verificar modo Arduino.
- [ ] Pegar código y editar; alternar tipo de lenguaje sin modificar contenido.
- [ ] Importar archivo >32 KiB, UTF-8 corrupto y archivo binario: mostrar error sin crashear ni sobrescribir editor.
- [ ] Exportar `.txt` / `.ino` y verificar bytes y saltos de línea.
- [ ] Verificar que Ejecutar / Instalar están inhabilitados y el firmware ESP32 no ha cambiado.
- [ ] Ejecutar `scripts/verify-local.ps1` y comprobar RGB BLE/Wi-Fi y diagnósticos V3.
- [ ] Tomar capturas y verificar TalkBack, tamaño de texto, scroll y modo claro/oscuro.

## Siguientes PR bloqueados
- Editor con autosave y biblioteca de proyectos, refactor visual Centro.
- Runtime DSL: parser de sintaxis + intérprete + límites + scheduler.
- Arduino bridge local, empaquetado compatible, firma, OTA A/B y rollback, enrolamiento autorizado.
Estos incrementos no están terminados en este PR; ver `PLAN_MAESTRO_V4_PROGRAMACION_DUAL.md`.
