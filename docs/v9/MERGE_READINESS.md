# Compi — preparación verificable del merge (2026-10-12)

## Resolución de PR redundante
- PR #71 cerrado **sin merge**: sustituido por #77. Evitar aplicar en paralelo ambos cambios del reloj de automatización.
- PR #77 es el candidato único de automatizaciones V12 (CI verde en `7ce934c`), pero sigue como borrador hasta validar el ESP32 físico. El estado verde de CI no confirma comportamiento eléctrico ni prioridad de comandos.
- PR #79 integra parcialmente la dirección visual V9 (CI verde en `8da2602`). Faltan captura de referencia y paridad visual/funcional de las cuatro vistas.

## Riesgo de integración cruzada
Ambos PR #77 y #79 cambian `feature/hub/LabHubScreen.kt`.
No ejecutar merges independientes sin reevaluar el archivo compartido; la integración debe retener:
- Comportamiento V12 para límites y detención de automatizaciones, validación de capacidades, credenciales efímeras y API de SoftAP.
- V9 para estructura visual de Hardware Lab, estado verdadero, scroll, pestañas y accesibilidad.
- Ambos conjuntos de pruebas.

## Puertas automáticas
1. Crear una rama de integración desde el HEAD revisado de `main`.
2. Incorporar V9 y luego portar selectivamente cambios V12 (o viceversa) resolviendo manualmente `LabHubScreen.kt`.
3. Ejecutar los tests de políticas de automatización y los tests de navegación en emulador.
4. Ejecutar el pipeline Android/firmware de CI en el SHA final combinado; los pipelines verdes por separado no bastan.
5. Verificar las cuatro capturas reales frente a las imágenes del usuario.
6. Ejecutar el banco físico ESP32 para acciones automatizadas; comprobar inicio/detención/límites/intervalos, interacción BLE/Wi-Fi, falta de credenciales y retorno a OFF.
7. Solo después permitir merge de la rama integrada a `main`.

## Bloqueos no resueltos
- Implementación V9 de LED integrado, Studio y Hardware no es pixel-perfect aún.
- Pruebas físicas de automatización V12 pendientes.
- No hay evidencia de build ni de instrumentos ejecutados sobre el **commit combinado**.
- Release de distribución sigue prohibido por el guard de autorización BLE.
