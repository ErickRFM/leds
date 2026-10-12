# Compi V9 · Implementación basada en referencias

Base: `bfb5c19ed7b208182dcf5f219e1d1db38bac9ac7` (main, V10).
Contrato visual: las cuatro imágenes suministradas por el usuario (Inicio, LED RGB, Compi Studio, Hardware Lab).

## Cambios integrados a esta rama
- Paleta mate oscura con violeta y superficies según referencias.
- Cuatro destinos primarios: Inicio, LED, Studio y Hardware; Ajustes pasa a ruta secundaria desde la barra superior.
- Reutilización de pantallas y servicios BLE/Wi-Fi/Studio existentes sin cambios de firmware.
- Inicio con hero de dispositivo basado en estado BLE real, accesos rápidos, módulos y estado de actividad sin datos falsos.
- Regresión Compose de navegación primaria y Ajustes (requiere emulador/teléfono para ejecución).

## Estado y limitaciones comprobables
- **Parcial:** no se ha reproducido todavía pixel-perfect LED, Studio y Hardware Lab.
- **Sin QA ejecutado:** el entorno de esta sesión no dispone de acceso de red Git ni Android SDK/emulador; los cambios se realizaron por el conector GitHub.
- No se ha instalado ni flasheado firmware. No se han probado BLE/Wi-Fi/GPIO/ADC físicos.
- No usar imágenes como pantallas rasterizadas. Implementar Compose con estados y acciones reales.
- El acceso de Wi-Fi local debe continuar restringido al laboratorio debug. Nunca escribir claves de control en código o capturas de producción.

## Trabajos necesarios para completar el contrato visual
1. Implementar `CompiGlassCard`, tipografías y espaciado calibrados en capturas Android 360dp, 412dp y 320dp.
2. LED: panel integrado rueda + sliders RGB/brillo, encendido, favoritos y efectos; reutilizar `RgbControlLink` y evitar respuestas de estado antiguas.
3. Studio: composición exacta editor/proyectos y herramientas, conservar borradores separados y validación V10.
4. Hardware: resumen de conexión/GPIO/ADC/diagnóstico con estado real, sin contraseñas en claro; mantener secciones avanzadas accesibles.
5. Añadir previews con datos ficticios SOLO en entorno de diseño/test, nunca en rutas runtime.
6. Ejecutar tests/lint/APK e instrumentación de navegación. Capturas pixel-by-pixel con las cuatro referencias y revisión manual.
7. Completar QA físico y gates de seguridad antes de cualquier distribución pública.

## Puertas para merge
- `testDebugUnitTest`, `lintDebug`, `assembleDebug` exitosos.
- Pruebas de navegación instrumentadas exitosas cuando haya Android conectado.
- Revisión de las cuatro capturas y funcionalidad real.
- No degradar garantías BLE V8/V10 y Wi-Fi local ni los bloqueos de release existentes.
